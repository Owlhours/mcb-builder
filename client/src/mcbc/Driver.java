package mcbc;

import java.util.ArrayList;
import java.util.List;
import mcb.env.Client;
import mcbc.mixin.MinecraftAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** The game-thread side: entry points for the mixins, the registered supply containers, the current job. */
public final class Driver {
    private Driver() { }

    public static volatile Job job;
    public static Job last;
    /** Supply containers the owner pointed at (one block per container). */
    public static final List<BlockPos> supplies = new ArrayList<>();
    public static Preview preview;

    // what the bot asked for in this tick, for the keybind hook
    private static Client.Frame clicks;
    private static boolean attackHeld;

    /** Minecraft.tick HEAD: queued packets are handled; the world is as the bot will see it. */
    public static void preTick(Minecraft mc) {
        if (preview != null) preview.tick(mc);
        McbcMod.pollKey();
        Dev.tick(mc);
        Job j = job;
        if (j == null) return;
        clicks = null;
        attackHeld = false;
        try {
            j.mainTick();
        } catch (RuntimeException e) {
            McbcMod.LOG.error("[mcbc] driver", e);
            j.stop("internal error: " + e);
        }
    }

    /** Called by the job after the frame is applied: the clicks to make at the keybinds point of this tick. */
    static void setClicks(Client.Frame f) { clicks = f; attackHeld = f.attackHeld; }
    static void clearClicks() { clicks = null; attackHeld = false; }

    /** Minecraft.handleKeybinds HEAD (only with no screen open). */
    public static void keybinds(Minecraft mc) {
        Client.Frame f = clicks;
        Job j = job;
        if (f == null || j == null || !j.running()) return;
        clicks = null;
        MinecraftAccessor acc = (MinecraftAccessor) mc;
        boolean instant = false;
        if (f.attackClick) {
            // (too soon after the last break: the held attack begins it once the gap has passed)
            if (System.currentTimeMillis() - lastBreakEndMs < BREAK_GAP_MS) j.client.breakHolds++;
            else if (hitMatches(mc, f)) instant = acc.mcbc$startAttack();
            else j.client.droppedClicks++;
        }
        if (f.useClick) {
            boolean ok = hitMatches(mc, f);
            if (DEBUG) McbcMod.event("use: hit=" + describe(mc.hitResult) + " expect=" + f.expectPos + "/" + f.expectFace + " ok=" + ok + " yaw=" + mc.player.getYRot() + " pitch=" + mc.player.getXRot());
            if (ok) { acc.mcbc$startUseItem(); j.client.usedThisTick = true; }
            else j.client.droppedClicks++;
        }
        attackHeld = f.attackHeld && !instant;
    }

    static final boolean DEBUG = Boolean.getBoolean("mcbc.debug");
    static String describe(HitResult h) {
        if (h instanceof BlockHitResult b) return b.getType() + " " + b.getBlockPos().toShortString() + " " + b.getDirection();
        return h == null ? "null" : h.getType() + " " + h.getClass().getSimpleName();
    }

    private static boolean hitMatches(Minecraft mc, Client.Frame f) {
        HitResult h = mc.hitResult;
        if (!(h instanceof BlockHitResult b) || h.getType() != HitResult.Type.BLOCK) return false;
        return f.expectPos == null || (b.getBlockPos().equals(f.expectPos) && b.getDirection().get3DDataValue() == f.expectFace);
    }

    /** The argument of handleKeybinds' continueAttack(down): the bot's held attack counts as the button held. */
    public static boolean attackArg(boolean down) {
        Job j = job;
        return down || (attackHeld && j != null && j.running());
    }

    /** ClientLevel.sendBlockUpdated. */
    public static void blockChanged(BlockPos pos, BlockState now) {
        Job j = job;
        if (j != null && j.client != null) j.client.addChange(pos, now);
    }

    public static void chunkLoaded(int cx, int cz) {
        Job j = job;
        if (j != null && j.client != null) j.client.chunkLoaded(cx, cz);
    }

    /** ClientLevel.syncBlockState: the server's answer differs from what the client predicted (a rejected placement or break). */
    public static void predictionCorrected(BlockPos pos, BlockState predicted, BlockState server) {
        Job j = job;
        if (j == null || j.client == null) return;
        j.rejected++;
        McbcMod.event("the server corrected " + pos.toShortString() + ": " + predicted + " -> " + server);
    }

    // ---- break pacing (what Grim's FastBreak and the vanilla server's destroy check measure, in wall time) ----
    /** When the last block broke (finished or instantly), and when the current break started / how long it must take. */
    private static long lastBreakEndMs, breakStartMs, breakNeedMs;
    /** No new break sooner than this after the last one (Grim counts starts within 275 ms of a finish; vanilla's own
     *  delay between held breaks is 5 ticks = 250 ms). */
    static final long BREAK_GAP_MS = 320;

    /** MultiPlayerGameMode.startDestroyBlock returned: a break that takes time began. */
    public static void breakStarted(net.minecraft.client.multiplayer.MultiPlayerGameMode gm, BlockPos pos) {
        Job j = job;
        if (j == null || !gm.isDestroying()) return;
        Minecraft mc = Minecraft.getInstance();
        float dmg = mc.level.getBlockState(pos).getDestroyProgress(mc.player, mc.level, pos);
        breakStartMs = System.currentTimeMillis();
        breakNeedMs = dmg <= 0 ? Long.MAX_VALUE / 4 : (long) (Math.ceil(1.0 / dmg) * 50);
    }

    /** Minecraft.continueAttack HEAD: true = skip this tick's continue. While the bot runs: a break is not finished
     *  sooner in wall time than it takes at 20 ticks a second (Timer, catch-up ticks), and no new break is begun within
     *  BREAK_GAP_MS of the last one. */
    public static boolean holdBreak(Minecraft mc, boolean down) {
        Job j = job;
        if (j == null || !j.running() || !down || mc.gameMode == null) return false;
        long now = System.currentTimeMillis();
        if (mc.gameMode.isDestroying()) {
            var acc = (mcbc.mixin.GameModeAccessor) mc.gameMode;
            BlockPos pos = acc.mcbc$pos();
            float next = acc.mcbc$progress() + mc.level.getBlockState(pos).getDestroyProgress(mc.player, mc.level, pos);
            if (next >= 1.0f && now - breakStartMs < breakNeedMs + 30) { j.client.breakHolds++; return true; }
            return false;
        }
        if (now - lastBreakEndMs < BREAK_GAP_MS) { j.client.breakHolds++; return true; }
        return false;
    }

    /** MultiPlayerGameMode.destroyBlock: a block broke on the client. */
    public static void destroyed() {
        lastBreakEndMs = System.currentTimeMillis();
        Job j = job;
        if (j != null && j.client != null) j.client.brokeThisTick = true;
    }

    /** ClientPacketListener.handleMovePlayer: the server put the player somewhere (a setback, or a teleport). */
    public static void serverMovedPlayer() {
        Job j = job;
        if (j != null) j.onServerMove();
    }

    public static void say(String msg) {
        Minecraft mc = Minecraft.getInstance();
        Runnable r = () -> { if (mc.player != null) mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("[MCB] " + msg)); };
        if (mc.isSameThread()) r.run(); else mc.execute(r);
        McbcMod.event(msg);
    }
}
