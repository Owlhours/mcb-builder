package mcb.env;

import java.util.ArrayDeque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * (MCB client) The bot's game client, backed by the real {@code Minecraft} client and its {@code LocalPlayer}. Same
 * interface as the headless one: turn the view, hold movement keys, left/right click, pick a hotbar slot, click
 * slots in an open screen; one call to {@link #tick()} is (at least) one client tick of the real game.
 *
 * Threads. The bot runs on its own thread and the game on its render thread, in strict alternation: at the start of
 * every client tick ({@code Minecraft.tick}, after the queued packets are handled) the game hands the turn to the bot
 * and waits; the bot looks at the world, decides, sets its inputs and calls {@link #tick()}, which hands the turn
 * back and waits for the next tick. So the bot only ever reads the client's world while the game thread waits.
 * The game thread applies the inputs (see {@code mcbc.Driver}): view angles on the player, a movement input in
 * place of the keyboard's, the hotbar slot, screen clicks through {@code MultiPlayerGameMode.handleContainerInput},
 * and attack/use through the vanilla {@code Minecraft.startAttack / startUseItem / continueAttack} at the point of
 * the tick where the keyboard's clicks are handled. Nothing is sent that a vanilla client would not send.
 *
 * Beyond the headless client, three rules keep the clicks honest on a real server:
 *  - a click is only made with a view direction the server already has: if the angles changed since the last
 *    movement packet, the game first runs a tick that only turns the view (and the click is dropped if the
 *    crosshair no longer rests on the same face after it);
 *  - after a click that changed the world on the client (a placement, a finished break), the bot waits until the
 *    server has answered it (its block-changed acknowledgement), so what it sees afterwards is the server's world;
 *  - at most one use per tick.
 */
public final class Client {
    public final Minecraft mc;
    public final LocalPlayer player;

    // ---- inputs for the next tick (bot thread) ----
    private float yaw, pitch;
    private boolean kForward, kBack, kLeft, kRight, kJump, kSneak, kSprint;
    private boolean useHeld, attackHeld;
    private boolean useClick, attackClick;
    private int wantSlot = -1;
    private final ArrayDeque<int[]> screenClicks = new ArrayDeque<>();
    private boolean wantCloseScreen, wantOpenInventory;

    /** The frame the game thread applies at its next tick (written by the bot thread during its turn). */
    public static final class Frame {
        public float yaw, pitch;
        public boolean forward, back, left, right, jump, sneak, sprint;
        public boolean attackClick, useClick, attackHeld;
        public BlockPos expectPos; public int expectFace = -1;
        public int slot = -1;
        public boolean closeScreen, openInventory;
        public int[] slotClick;
        public boolean waitFrame;
    }
    public final Frame frame = new Frame();

    // ---- turn taking ----
    private final Semaphore botTurn = new Semaphore(0), gameTurn = new Semaphore(0);
    /** Set by the game thread: the build is to end; the next tick() throws {@link Stop}. */
    public volatile boolean stopAsked;
    /** Thrown inside the bot thread to end the build (an Error: the bot's own catch blocks let it through). */
    public static final class Stop extends Error { public Stop() { super("build stopped", null, false, false); } }

    // ---- counters ----
    public long ticks;            // bot ticks (calls of tick())
    public long realTicks;        // game ticks the bot was given (settle and wait ticks included)
    public long settleTicks, ackWaitTicks, droppedClicks, uses, attacks, breakHolds;
    public long thinkNanos;       // time the bot thread held the turn

    // ---- observations (written by the game thread, read by the bot thread in its turn) ----
    private int[] changes = new int[256];
    private int nChanges;
    private boolean suppress;
    private final it.unimi.dsi.fastutil.longs.LongArrayList loadedChunks = new it.unimi.dsi.fastutil.longs.LongArrayList();
    /** Set by the game thread during a tick: a block use was made / a block broke on the client. */
    public boolean usedThisTick, brokeThisTick;

    public Client(Minecraft mc) {
        this.mc = mc;
        this.player = mc.player;
        yaw = player.getYRot();
        pitch = player.getXRot();
    }

    // ------------------------------------------------------------------ turn taking
    /** Bot thread: wait for the first turn. */
    public void awaitFirstTurn() throws InterruptedException {
        botTurn.acquire();
        turnStart = System.nanoTime();
        if (stopAsked) throw new Stop();
    }
    private long turnStart;

    /** Game thread: give the turn to the bot. */
    public void giveTurn() { botTurn.release(); }
    /** Game thread: wait for the bot to hand the turn back (true), or give up after ms (false: it is still thinking). */
    public boolean awaitTurn(long ms) throws InterruptedException { return gameTurn.tryAcquire(ms, TimeUnit.MILLISECONDS); }

    /** Bot thread: hand the prepared frame to the game and wait for the next tick. */
    private void handOff() {
        thinkNanos += System.nanoTime() - turnStart;
        gameTurn.release();
        try { botTurn.acquire(); } catch (InterruptedException e) { throw new Stop(); }
        turnStart = System.nanoTime();
        realTicks++;
        if (stopAsked) throw new Stop();
    }

    /** Bot thread, last act: give the turn back for good. */
    public void finish() { gameTurn.release(); }

    // ------------------------------------------------------------------ observation
    public double x() { return player.getX(); }
    public double y() { return player.getY(); }
    public double z() { return player.getZ(); }
    public Vec3 velocity() { return player.getDeltaMovement(); }
    public boolean onGround() { return player.onGround(); }
    public boolean horizontalCollision() { return player.horizontalCollision; }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }
    public Vec3 eye() { return player.getEyePosition(); }
    public double reach() { return player.blockInteractionRange(); }
    public boolean sneaking() { return player.isShiftKeyDown(); }

    public BlockState block(BlockPos pos) { return mc.level.getBlockState(pos); }
    public ClientLevel level() { return mc.level; }

    public static boolean loaded(ClientLevel level, int x, int z) {
        var ch = level.getChunkSource().getChunk(x >> 4, z >> 4, ChunkStatus.FULL, false);
        return ch instanceof LevelChunk lc && !(lc instanceof net.minecraft.world.level.chunk.EmptyLevelChunk);
    }

    public int changeCount() { return nChanges; }
    public int[] changes() { return changes; }
    public void clearChanges() { nChanges = 0; }

    /** Game thread (ClientLevel.sendBlockUpdated): a block changed in the client's world. */
    public void addChange(BlockPos pos, BlockState state) {
        if (suppress) return;
        if (nChanges * 4 + 4 > changes.length) changes = java.util.Arrays.copyOf(changes, changes.length * 2);
        int i = nChanges++ * 4;
        changes[i] = pos.getX(); changes[i + 1] = pos.getY(); changes[i + 2] = pos.getZ(); changes[i + 3] = Block.getId(state);
    }
    /** The oracle's scratch placements (client copy only) are not world changes. */
    void suppressChanges(boolean s) { suppress = s; }

    /** Game thread: a chunk arrived. */
    public void chunkLoaded(int cx, int cz) { loadedChunks.add(net.minecraft.world.level.ChunkPos.pack(cx, cz)); }
    /** Bot thread: the chunks that arrived since the last call. */
    public long[] takeLoadedChunks() { long[] r = loadedChunks.toLongArray(); loadedChunks.clear(); return r; }

    public ItemStack inventorySlot(int i) { return player.getInventory().getItem(i); }
    public int selectedSlot() { return player.getInventory().getSelectedSlot(); }
    public boolean screenOpen() { return mc.gui.screen() != null; }
    public boolean containerOpen() { return player.containerMenu != player.inventoryMenu; }
    public AbstractContainerMenu menu() { return player.containerMenu; }

    /** The block face under the crosshair (blocks only, as the headless client), from the current eye and view. */
    public BlockHitResult lastHit() {
        if (screenOpen()) return null;
        Vec3 eye = eye(), view = Oracle.viewVector(player.getXRot(), player.getYRot());
        double r = reach();
        BlockHitResult hit = mc.level.clip(new ClipContext(eye, eye.add(view.x * r, view.y * r, view.z * r), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }
    private BlockHitResult pickAt(float yaw, float pitch) {
        Vec3 eye = eye(), view = Oracle.viewVector(pitch, yaw);
        double r = reach();
        BlockHitResult hit = mc.level.clip(new ClipContext(eye, eye.add(view.x * r, view.y * r, view.z * r), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }
    public boolean isBreaking() { return mc.gameMode != null && mc.gameMode.isDestroying(); }

    // ------------------------------------------------------------------ inputs
    public void look(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = Math.max(-90.0f, Math.min(90.0f, pitch));
    }
    public void keys(boolean forward, boolean back, boolean left, boolean right, boolean jump, boolean sneak, boolean sprint) {
        kForward = forward; kBack = back; kLeft = left; kRight = right; kJump = jump; kSneak = sneak; kSprint = sprint;
    }
    public void releaseKeys() { keys(false, false, false, false, false, false, false); }
    public void sneak(boolean s) { kSneak = s; }
    public void jump(boolean j) { kJump = j; }
    public void selectSlot(int slot) { wantSlot = slot; }
    public void clickUse() { useClick = true; }
    public void clickAttack() { attackClick = true; }
    public void holdAttack(boolean held) { attackHeld = held; }
    public void holdUse(boolean held) { useHeld = held; }
    public void openInventory() { wantOpenInventory = true; }
    public void closeScreen() { wantCloseScreen = true; }
    public void clickSlot(int slot, int button, ContainerInput type) { screenClicks.add(new int[] {slot, button, type.ordinal()}); }
    public int queuedSlotClicks() { return screenClicks.size(); }

    /** The rotation the server last had from this client (LocalPlayer's last sent angles). */
    public static java.util.function.Function<LocalPlayer, float[]> sentRotation;
    /** Block predictions the server has not answered yet. */
    public static java.util.function.ToIntFunction<ClientLevel> pendingPredictions;

    private boolean rotationSent() {
        float[] s = sentRotation.apply(player);
        return same(s[0], yaw) && s[1] == pitch && same(player.getYRot(), yaw) && player.getXRot() == pitch && s[0] == player.getYRot();
    }

    private static boolean same(float a, float b) { return Math.abs(net.minecraft.util.Mth.wrapDegrees(a - b)) < 1e-3f; }

    private long waitStart;
    /** For waits on the server (a screen to open ...): true while the wait that began at w == 0 is under 2 seconds. */
    public boolean waitingForServer(int w) {
        if (w == 0) waitStart = System.currentTimeMillis();
        return System.currentTimeMillis() - waitStart < 2000;
    }

    private void fill(Frame f, boolean clicks, boolean moveKeys) {
        f.yaw = yaw; f.pitch = pitch;
        f.forward = moveKeys && kForward; f.back = moveKeys && kBack; f.left = moveKeys && kLeft; f.right = moveKeys && kRight;
        f.jump = moveKeys && kJump; f.sprint = moveKeys && kSprint; f.sneak = kSneak;
        f.attackClick = clicks && attackClick; f.useClick = clicks && useClick; f.attackHeld = clicks && attackHeld;
        f.expectPos = null; f.expectFace = -1;
        f.slot = -1; f.closeScreen = false; f.openInventory = false; f.slotClick = null; f.waitFrame = !clicks;
    }

    // ------------------------------------------------------------------ the tick
    public void tick() {
        ticks++;
        boolean click = useClick || attackClick;
        BlockHitResult want = null;
        if (click && !screenOpen()) {
            want = pickAt(yaw, pitch);
            if (!rotationSent()) {
                // turn first: the server must have the view the click is made with
                fill(frame, false, false);
                if (wantSlot >= 0) { frame.slot = wantSlot; }
                usedThisTick = brokeThisTick = false;
                handOff();
                settleTicks++;
                BlockHitResult again = pickAt(yaw, pitch);
                if (want == null || again == null || !again.getBlockPos().equals(want.getBlockPos()) || again.getDirection() != want.getDirection()) {
                    useClick = attackClick = false; droppedClicks++; want = null;
                } else want = again;
            }
        }
        fill(frame, true, true);
        if (want != null) { frame.expectPos = want.getBlockPos().immutable(); frame.expectFace = want.getDirection().get3DDataValue(); }
        if (useClick) uses++;
        if (attackClick) attacks++;
        frame.slot = wantSlot;
        frame.closeScreen = wantCloseScreen;
        frame.openInventory = wantOpenInventory && !wantCloseScreen;
        frame.slotClick = wantCloseScreen ? null : screenClicks.poll();
        if (wantCloseScreen) screenClicks.clear();
        wantSlot = -1; wantCloseScreen = false; wantOpenInventory = false;
        useClick = false; attackClick = false;
        usedThisTick = brokeThisTick = false;
        handOff();
        // the server's answer to what this tick predicted, before the bot looks again
        if ((usedThisTick || brokeThisTick) && pendingPredictions != null) {
            long t0 = System.currentTimeMillis();
            int n = 0;
            while (pendingPredictions.applyAsInt(mc.level) > 0 && n < 200 && System.currentTimeMillis() - t0 < 3000) {
                fill(frame, false, false);
                usedThisTick = brokeThisTick = false;
                handOff();
                ackWaitTicks++;
                n++;
            }
        }
    }
}
