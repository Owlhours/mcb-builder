package mcbc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mcb.bot.Builder;
import mcb.bot.Grid;
import mcb.bot.ModView;
import mcb.bot.Nav;
import mcb.build.Judge;
import mcb.build.Site;
import mcb.build.StateInfo;
import mcb.build.Target;
import mcb.env.Client;
import mcb.env.Oracle;
import mcb.schem.Schem;
import mcbc.mixin.DeltaTimerAccessor;
import mcbc.mixin.MinecraftAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

/**
 * One build with the real player: the layout (box, grid, containers), the preview, and the run (on the bot thread,
 * in lockstep with the game thread, see {@link Client}).
 */
public final class Job {
    enum State { NEW, RUNNING, PAUSED, DONE }

    final Minecraft mc;
    final String name;
    final Target target;
    final int ox, oy, oz;
    // the grid (the bot's whole site), inclusive bounds
    final int gx0, gz0, gx1, gz1, gy1;
    /** The supply containers: the block clicked, its other half (or null), the stand cell and look point. */
    final List<Container> containers = new ArrayList<>();
    final String layoutProblem;

    volatile State state = State.NEW;
    Client client;
    Builder builder;
    Site site;
    Grid grid;
    Thread thread;
    volatile String phase = "not started", pauseReason;
    volatile boolean finished;
    boolean late;
    long lateTicks;
    float lastHealth = -1;
    BotInput input;
    ClientInput savedInput;
    List<String> meteorSwitchedOff = new ArrayList<>();
    float appliedYaw = Float.NaN, appliedPitch = Float.NaN;
    boolean serverMoved;
    final ArrayDeque<Long> setbackTimes = new ArrayDeque<>();
    long setbacks, rejected;
    long startMs, endMs;
    Judge.Result result;
    String endText;
    List<int[]> notCleared = new ArrayList<>();
    String timerAtStart;
    boolean resumeAsked;

    static final class Container {
        BlockPos pos, other;
        int ax, ay, az;
        double px, py, pz;
        boolean reachable;
    }

    // ------------------------------------------------------------------ layout (game thread)
    Job(Minecraft mc, String name, Schem schem, int ox, int oy, int oz, List<BlockPos> supplies) {
        StateInfo.init();
        this.mc = mc;
        this.name = name;
        this.target = new Target(schem);
        this.ox = ox; this.oy = oy; this.oz = oz;
        ClientLevel level = mc.level;
        int x0 = ox, z0 = oz, x1 = ox + target.sx - 1, z1 = oz + target.sz - 1;
        String problem = null;
        for (BlockPos p : supplies) {
            Container c = new Container();
            c.pos = p;
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof ChestBlock && s.getValue(ChestBlock.TYPE) != ChestType.SINGLE) c.other = p.relative(ChestBlock.getConnectedDirection(s));
            containers.add(c);
            x0 = Math.min(x0, p.getX() - 2); z0 = Math.min(z0, p.getZ() - 2); x1 = Math.max(x1, p.getX() + 2); z1 = Math.max(z1, p.getZ() + 2);
            if (c.other != null) { x0 = Math.min(x0, c.other.getX() - 2); z0 = Math.min(z0, c.other.getZ() - 2); x1 = Math.max(x1, c.other.getX() + 2); z1 = Math.max(z1, c.other.getZ() + 2); }
            if (p.getY() < oy - 1 || p.getY() > oy + target.sy + 4) problem = "the container at " + p.toShortString() + " is too far below or above the build's floor (y " + oy + ")";
        }
        int m = 6;
        gx0 = x0 - m; gz0 = z0 - m; gx1 = x1 + m; gz1 = z1 + m;
        int top = Math.min(8, level.getMaxY() - (oy + target.sy));
        gy1 = oy - 1 + target.sy + 1 + Math.max(0, top) - 1;
        if (oy - 1 < level.getMinY() || oy + target.sy + 2 > level.getMaxY()) problem = "the build does not fit between the bottom and the top of the world here";
        long vol = (long) (gx1 - gx0 + 1) * (gz1 - gz0 + 1) * (gy1 - oy + 2);
        if (vol > 60_000_000L) problem = "the site is too large (" + vol + " cells)";
        for (Container c : containers) {
            findAccess(level, c);
            if (!c.reachable && problem == null) problem = "no place to stand next to the container at " + c.pos.toShortString() + " (a free cell beside it, with floor, from which it can be clicked)";
        }
        layoutProblem = problem;
    }

    /** A cell beside the container to stand in (floor under it, two free cells), inside the grid, from which the
     *  middle of the container is within reach. */
    private void findAccess(ClientLevel level, Container c) {
        BlockPos[] blocks = c.other == null ? new BlockPos[] {c.pos} : new BlockPos[] {c.pos, c.other};
        double best = Double.MAX_VALUE;
        for (BlockPos b : blocks) for (Direction d : Direction.Plane.HORIZONTAL) for (int dy = -1; dy <= 0; dy++) {
            BlockPos s = b.relative(d).above(dy);
            if (s.getX() < gx0 || s.getX() > gx1 || s.getZ() < gz0 || s.getZ() > gz1 || s.getY() < oy - 1) continue;
            if (!standable(level, s)) continue;
            double ex = s.getX() + 0.5, ey = s.getY() + 1.62, ez = s.getZ() + 0.5;
            double px = b.getX() + 0.5, py = b.getY() + 0.45, pz = b.getZ() + 0.5;
            double dist = Math.sqrt((ex - px) * (ex - px) + (ey - py) * (ey - py) + (ez - pz) * (ez - pz));
            if (dist > 3.6) continue;
            double score = dist + (dy != 0 ? 0.5 : 0) + (s.getY() != oy ? 0.2 : 0);
            if (score < best) {
                best = score;
                c.ax = s.getX(); c.ay = s.getY(); c.az = s.getZ(); c.px = px; c.py = py; c.pz = pz; c.reachable = true;
            }
        }
    }

    private static boolean standable(ClientLevel level, BlockPos s) {
        BlockState feet = level.getBlockState(s), head = level.getBlockState(s.above()), floor = level.getBlockState(s.below());
        return feet.getCollisionShape(level, s).isEmpty() && head.getCollisionShape(level, s.above()).isEmpty()
            && floor.isFaceSturdy(level, s.below(), Direction.UP) && feet.getFluidState().isEmpty();
    }

    boolean running() { return state == State.RUNNING; }

    String boxText() {
        return "(" + ox + " " + oy + " " + oz + ") to (" + (ox + target.sx - 1) + " " + (oy + target.sy - 1) + " " + (oz + target.sz - 1) + "), " + target.sx + " x " + target.sy + " x " + target.sz;
    }
    String siteText() {
        return "(" + gx0 + " " + (oy - 1) + " " + gz0 + ") to (" + gx1 + " " + gy1 + " " + gz1 + ")";
    }

    /** What is in the box now that the target does not want: count, and those too hard to break by hand. */
    Preview.Plan plan() {
        ClientLevel level = mc.level;
        Preview.Plan p = new Preview.Plan();
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        int i = 0;
        for (int y = 0; y < target.sy; y++) for (int z = 0; z < target.sz; z++) for (int x = 0; x < target.sx; x++, i++) {
            q.set(ox + x, oy + y, oz + z);
            if (!Client.loaded(level, q.getX(), q.getZ())) { p.unloaded++; continue; }
            BlockState s = level.getBlockState(q);
            int w = Block.getId(s), t = target.ids[i];
            if (StateInfo.air[w]) continue;
            if (t != 0 && (Judge.rightCell(t, w) || StateInfo.loose[w] == StateInfo.loose[t])) { if (Judge.rightCell(t, w)) p.alreadyRight++; continue; }
            if (!s.getFluidState().isEmpty() && s.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock) { p.fluids.add(q.immutable()); continue; }
            if (isContainerBlock(q)) { p.containersInBox++; continue; }
            int bt = breakTicks(w);
            if (bt < 0 || bt > Config.maxClearTicks) p.tooHard.add(q.immutable());
            else p.toBreak.add(q.immutable());
        }
        // what the build still needs, against what the player carries (the containers are read at the start)
        Map<Item, Integer> need = new Site(target, ox, oy, oz, null).stillNeeded(level);
        Map<Item, Integer> carried = carried();
        for (var e : need.entrySet()) {
            int have = carried.getOrDefault(e.getKey(), 0);
            p.need.put(e.getKey(), e.getValue());
            p.carried.put(e.getKey(), have);
        }
        for (int cx = gx0 >> 4; cx <= gx1 >> 4; cx++) for (int cz = gz0 >> 4; cz <= gz1 >> 4; cz++)
            if (!Client.loaded(level, cx << 4, cz << 4)) p.unloadedChunks++;
        return p;
    }

    boolean isContainerBlock(BlockPos q) {
        for (Container c : containers) if (c.pos.equals(q) || q.equals(c.other)) return true;
        return false;
    }

    static int breakTicks(int state) {
        float h = StateInfo.state[state].getBlock().defaultDestroyTime();
        if (h < 0) return -1;
        return (int) Math.ceil(h * (StateInfo.state[state].requiresCorrectToolForDrops() ? 100 : 30) * 1.05f) + 1;
    }

    Map<Item, Integer> carried() {
        Map<Item, Integer> m = new LinkedHashMap<>();
        var inv = mc.player.getInventory();
        for (int i = 0; i < 36; i++) { ItemStack s = inv.getItem(i); if (!s.isEmpty()) m.merge(s.getItem(), s.getCount(), Integer::sum); }
        return m;
    }

    // ------------------------------------------------------------------ start / stop (game thread)
    void start() {
        client = new Client(mc);
        startMs = System.currentTimeMillis();
        lastHealth = mc.player.getHealth();
        timerAtStart = Compat.timerState();
        if (Compat.stopBaritone()) Driver.say("Baritone was pathing: cancelled.");
        meteorSwitchedOff = Compat.meteorOff();
        if (!meteorSwitchedOff.isEmpty()) Driver.say("Meteor modules off for the run: " + String.join(", ", meteorSwitchedOff));
        Nav.MAX_FALL16 = Config.maxFall16;
        installInput();
        state = State.RUNNING;
        phase = "starting";
        thread = new Thread(this::botMain, "MCB bot");
        thread.setDaemon(true);
        thread.start();
    }

    private void installInput() {
        if (input == null) input = new BotInput();
        if (mc.player.input != input) { savedInput = mc.player.input; mc.player.input = input; }
    }

    private void restoreInput() {
        if (mc.player != null && mc.player.input == input) mc.player.input = new KeyboardInput(mc.options);
        if (input != null) input.release();
        if (mc.gameMode != null && mc.gameMode.isDestroying()) mc.gameMode.stopDestroyBlock();
        Driver.clearClicks();
    }

    void pause(String why) {
        if (state != State.RUNNING) return;
        state = State.PAUSED;
        pauseReason = why;
        resumeAsked = false;
        restoreInput();
        if (!Safety.CHAT.equals(why)) Driver.say("paused: " + why + ". You have control. /mcbc resume to go on, /mcbc stop to end.");
        else McbcMod.event("paused: " + why);
    }

    void resume() {
        if (state != State.PAUSED) return;
        resumeAsked = true;
    }

    private void doResume() {
        resumeAsked = false;
        setbackTimes.clear();   // (the owner looked and said go on: the count starts again)
        lastHealth = mc.player.getHealth();
        appliedYaw = Float.NaN;
        installInput();
        state = State.RUNNING;
        if (!Safety.CHAT.equals(pauseReason)) Driver.say("resumed");
        pauseReason = null;
    }

    void stop(String why) {
        if (state == State.DONE) return;
        McbcMod.event("stop: " + why);
        if (client != null) client.stopAsked = true;
        if (client != null && thread != null && thread.isAlive()) {
            if (!late) client.giveTurn();
            try { thread.join(3000); } catch (InterruptedException ignored) { }
        }
        endText = endText == null ? "stopped: " + why : endText;
        end();
    }

    private void end() {
        state = State.DONE;
        endMs = System.currentTimeMillis();
        restoreInput();
        Compat.meteorOn(meteorSwitchedOff);
        if (Driver.job == this) Driver.job = null;
        Driver.last = this;
        Driver.say(endText + (result == null ? "" : "") );
        McbcMod.event("RESULT " + statsLine());
    }

    long recentSetbacks() {
        long now = System.currentTimeMillis();
        while (!setbackTimes.isEmpty() && now - setbackTimes.peekFirst() > 60_000) setbackTimes.pollFirst();
        return setbackTimes.size();
    }

    /** The last frames applied (for the log when the server moves the player). */
    private final String[] recent = new String[24];
    private static java.io.PrintWriter trace;
    static {
        if (Driver.DEBUG) try {
            trace = new java.io.PrintWriter(java.nio.file.Files.newBufferedWriter(net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("logs/mcb-trace.log"),
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND), true);
        } catch (java.io.IOException e) { trace = null; }
    }
    private int nRecent;
    private void remember(Client.Frame f) {
        var p = mc.player;
        recent[nRecent++ % recent.length] = String.format(java.util.Locale.ROOT, "t%d pos=%.3f,%.3f,%.3f v=%.3f,%.3f,%.3f ground=%s keys=%s%s%s%s%s%s%s use=%s att=%s/%s yaw=%.1f pitch=%.1f item=%s",
            client.realTicks, p.getX(), p.getY(), p.getZ(), p.getDeltaMovement().x, p.getDeltaMovement().y, p.getDeltaMovement().z, p.onGround(),
            f.forward ? "F" : "", f.back ? "B" : "", f.left ? "L" : "", f.right ? "R" : "", f.jump ? "J" : "", f.sneak ? "S" : "", f.sprint ? "R" : "",
            f.useClick, f.attackClick, f.attackHeld, f.yaw, f.pitch, p.getMainHandItem().getItem())
            + " act=" + (builder != null ? ModView.activity(builder) : "-") + " note=" + client.note + (f.waitFrame ? " wait" : "");
        if (trace != null) trace.println(System.currentTimeMillis() + " " + recent[(nRecent - 1) % recent.length]);
    }

    void onServerMoveTrace() { if (trace != null) trace.println(System.currentTimeMillis() + " SETBACK " + mc.player.position()); }

    void onServerMove() {
        onServerMoveTrace();
        if (Driver.DEBUG && client != null) {
            StringBuilder sb = new StringBuilder("frames before the server move:");
            for (int k = Math.max(0, nRecent - 12); k < nRecent; k++) sb.append("\n    ").append(recent[k % recent.length]);
            McbcMod.event(sb.toString());
        }
        serverMoved = true;
        if (state == State.RUNNING || state == State.PAUSED) { setbacks++; setbackTimes.add(System.currentTimeMillis()); McbcMod.event("server moved the player (setback or teleport) at " + mc.player.position()); }
    }

    // ------------------------------------------------------------------ every tick (game thread)
    void mainTick() throws RuntimeException {
        if (mc.player == null || mc.level == null || (client != null && mc.player != client.player)) { endText = "stopped: left the world"; stop("left the world"); return; }
        if (finished) { finishOnGameThread(); return; }
        if (state == State.PAUSED) {
            boolean noScreen = mc.gui.screen() == null;
            if ((resumeAsked || Safety.CHAT.equals(pauseReason)) && noScreen) doResume();
            else return;
        }
        if (state != State.RUNNING) return;
        // the owner turned the view (the mouse)
        if (Config.pauseOnOwnerInput && !Float.isNaN(appliedYaw) && !serverMoved && (mc.player.getYRot() != appliedYaw || mc.player.getXRot() != appliedPitch)) { pause("you turned the view"); return; }
        serverMoved = false;
        String why = Safety.check(mc, this);
        if (why != null) { pause(why); return; }
        if (Compat.baritonePathing()) { Compat.stopBaritone(); McbcMod.event("Baritone started pathing during the run: cancelled"); }
        long t0 = System.nanoTime();
        if (!late) client.giveTurn();
        boolean back;
        try { back = client.awaitTurn(late ? 0 : Config.maxThinkMs); } catch (InterruptedException e) { back = false; }
        if (!back) {
            late = true; lateTicks++;
            input.release();
            Driver.clearClicks();
            return;
        }
        late = false;
        if (finished) { finishOnGameThread(); return; }
        apply(client.frame);
        if (System.nanoTime() - t0 > 40_000_000L) {
            // the bot thought for longer than a tick: the game is not to catch up with a burst of ticks
            ((DeltaTimerAccessor) ((MinecraftAccessor) mc).mcbc$deltaTracker()).mcbc$setLastMs(net.minecraft.util.Util.getMillis());
        }
    }

    private void apply(Client.Frame f) {
        var p = mc.player;
        installInput();
        input.forward = f.forward; input.back = f.back; input.left = f.left; input.right = f.right;
        input.jump = f.jump; input.sneak = f.sneak; input.sprint = f.sprint;
        // the edge guard: a careful player sneaks where one more step would be a fall that hurts (vanilla sneaking
        // keeps a player on the block it stands on); the planner's own drops are no longer than is safe
        if (!f.sneak && p.onGround() && !(mc.level.getBlockState(p.blockPosition().below()).getBlock() instanceof net.minecraft.world.level.block.ScaffoldingBlock)
            && !(mc.level.getBlockState(p.blockPosition()).getBlock() instanceof net.minecraft.world.level.block.ScaffoldingBlock) && (f.forward || f.back || f.left || f.right || p.getDeltaMovement().horizontalDistanceSqr() > 1e-4) && dangerousStep(p)) {
            input.sneak = true; input.sprint = false; edgeGuards++;
        }
        // the view turns the short way round from where it is, as a mouse turns it (the yaw is never wrapped: a jump of
        // 360 degrees is something no mouse makes, and anticheats look for it)
        float yaw = p.getYRot() + net.minecraft.util.Mth.wrapDegrees(f.yaw - p.getYRot());
        p.setYRot(yaw);
        p.setXRot(f.pitch);
        p.setYHeadRot(yaw);
        appliedYaw = p.getYRot(); appliedPitch = p.getXRot();
        boolean screen = mc.gui.screen() != null;
        if (f.slot >= 0 && !screen && f.slot != p.getInventory().getSelectedSlot()) p.getInventory().setSelectedSlot(f.slot);
        if (f.closeScreen) {
            if (screen || p.containerMenu != p.inventoryMenu) p.closeContainer();
        } else if (f.openInventory && !screen) {
            mc.gui.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(p));
        } else if (f.slotClick != null && (screen || p.containerMenu != p.inventoryMenu)) {
            var menu = p.containerMenu;
            mc.gameMode.handleContainerInput(menu.containerId, f.slotClick[0], f.slotClick[1], net.minecraft.world.inventory.ContainerInput.values()[f.slotClick[2]], p);
        }
        if (!screen) Driver.setClicks(f); else Driver.clearClicks();
        if (Driver.DEBUG) remember(f);
    }

    long edgeGuards, signsClosed, lastUseTick = -1000;

    /** Would the player, a little further along its way, stand over a drop that hurts? */
    private boolean dangerousStep(net.minecraft.client.player.LocalPlayer p) {
        var level = mc.level;
        var v = p.getDeltaMovement();
        double sp = Math.sqrt(v.x * v.x + v.z * v.z);
        // the way it is going: its velocity, or where the keys push it (yaw), a third of a block on
        double ux, uz;
        if (sp > 0.02) { ux = v.x / sp; uz = v.z / sp; }
        else {
            float yaw = p.getYRot() * ((float) Math.PI / 180f);
            double fx = input.forward ? 1 : input.back ? -1 : 0, sx = input.left ? 1 : input.right ? -1 : 0;
            ux = -Math.sin(yaw) * fx + Math.cos(yaw) * sx; uz = Math.cos(yaw) * fx + Math.sin(yaw) * sx;
            double n = Math.sqrt(ux * ux + uz * uz); if (n < 1e-6) return false; ux /= n; uz /= n;
        }
        double ahead = Math.max(0.35, sp * 3);
        var box = p.getBoundingBox().move(ux * ahead, 0, uz * ahead);
        // still something to stand on there (within a step down)?
        if (!level.noCollision(p, box.move(0, -0.6, 0).setMinY(box.minY - 0.6).setMaxY(box.minY))) return false;
        // no: how far down, from the middle of where it would be
        double cx = p.getX() + ux * ahead, cz = p.getZ() + uz * ahead;
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        double feet = p.getY();
        for (int y = net.minecraft.util.Mth.floor(feet) - 1; y > feet - 40; y--) {
            BlockState s = level.getBlockState(q.set(net.minecraft.util.Mth.floor(cx), y, net.minecraft.util.Mth.floor(cz)));
            if (s.getBlock() instanceof net.minecraft.world.level.block.ScaffoldingBlock) return feet - (y + 1) > Config.maxFall16 / 16.0;
            if (!s.getFluidState().isEmpty() || s.is(net.minecraft.tags.BlockTags.CLIMBABLE)) return false;
            var shape = s.getCollisionShape(level, q);
            if (!shape.isEmpty()) return feet - (y + shape.max(Direction.Axis.Y)) > Config.maxFall16 / 16.0;
        }
        return true;
    }

    private void finishOnGameThread() {
        finished = false;
        if (state == State.DONE) return;
        end();
    }

    // ------------------------------------------------------------------ the run (bot thread)
    private void botMain() {
        try {
            client.awaitFirstTurn();
            run();
        } catch (Client.Stop s) {
            phase = "stopped";
        } catch (Throwable t) {
            phase = "crashed: " + t;
            endText = "the bot crashed: " + t + " (details in the log)";
            McbcMod.LOG.error("[mcbc] the bot crashed", t);
        } finally {
            finished = true;
            client.finish();
        }
    }

    private void run() {
        ClientLevel level = mc.level;
        phase = "reading the site";
        // the grid from the client's world
        int nx = gx1 - gx0 + 1, nz = gz1 - gz0 + 1, ny = gy1 - (oy - 1) + 1;
        int stone = Block.getId(Blocks.STONE.defaultBlockState());
        grid = new Grid(ox, oy, oz, target.sx, target.sy, target.sz, target.ids, gx0, gz0, nx, nz, ny, Block.getId(Blocks.GRASS_BLOCK.defaultBlockState()), false);
        grid.outside = (x, y, z) -> {
            if (!Client.loaded(level, x, z)) return stone;
            int id = Block.getId(level.getBlockState(new BlockPos(x, y, z)));
            return StateInfo.air[id] ? 0 : id;
        };
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        for (int y = oy - 1; y <= gy1; y++) for (int z = gz0; z <= gz1; z++) for (int x = gx0; x <= gx1; x++) {
            int id = Client.loaded(level, x, z) ? Block.getId(level.getBlockState(q.set(x, y, z))) : stone;
            grid.cur[grid.idx(x, y, z)] = (char) (StateInfo.air[id] ? 0 : id);
        }
        client.clearChanges();
        client.takeLoadedChunks();
        Oracle oracle = new Oracle(client);

        // the containers: read what is in them
        Site site0 = new Site(target, ox, oy, oz, carried());
        addPlaceholders(site0);
        if (!containers.isEmpty()) {
            phase = "reading the supply containers";
            Builder reader = new Builder(client, grid, site0, oracle);
            reader.maxTicks = Long.MAX_VALUE;
            reader.survey();
            for (String f : reader.surveyFailures) Driver.say("supply container " + f);
        }
        Map<Item, Integer> have = carried();
        for (Site.Stock s : site0.stock) if (s.count > 0) have.merge(s.item, s.count, Integer::sum);
        site = new Site(target, ox, oy, oz, have);
        site.stock.addAll(site0.stock);
        site.containerBlocks.addAll(site0.containerBlocks);

        // enough of everything?
        Map<Item, Integer> need = site.stillNeeded(level);
        List<String> shortList = new ArrayList<>();
        for (var e : need.entrySet()) {
            int h = have.getOrDefault(e.getKey(), 0);
            if (h < e.getValue()) shortList.add((e.getValue() - h) + " " + itemName(e.getKey()));
        }
        int loose = have.getOrDefault(site.scaffoldItem, 0), tower = have.getOrDefault(Items.SCAFFOLDING, 0);
        if (loose < 32) shortList.add((32 - loose) + " " + itemName(site.scaffoldItem) + " (temporary blocks: any of moss, dirt, netherrack, cobblestone ... the build does not use)");
        if (tower < 16) shortList.add((16 - tower) + " scaffolding");
        if (!shortList.isEmpty()) {
            endText = "not started: short of " + String.join(", ", shortList) + ". Put them in your inventory or a supply container, then /mcbc build again.";
            phase = "short of materials";
            return;
        }

        builder = new Builder(client, grid, site, oracle);
        builder.maxTicks = Long.MAX_VALUE;
        builder.shortage = item -> askFor(item);
        builder.verbose = Boolean.getBoolean("mcbc.verbose");

        // what is in the box and not wanted there: out first
        phase = "clearing the box";
        notCleared = builder.clearBox(i -> {
            int w = grid.cur[i];
            int bt = breakTicks(w);
            if (bt < 0 || bt > Config.maxClearTicks) return false;
            if (!grid.inBox(grid.xOf(i), grid.yOf(i), grid.zOf(i))) return false;
            if (StateInfo.state[w].getBlock() instanceof net.minecraft.world.level.block.LiquidBlock) return false;
            return !isContainerBlock(new BlockPos(grid.xOf(i), grid.yOf(i), grid.zOf(i)));
        });
        if (!notCleared.isEmpty()) Driver.say(notCleared.size() + " blocks in the box could not be cleared (first at " + notCleared.get(0)[0] + " " + notCleared.get(0)[1] + " " + notCleared.get(0)[2] + "); the build goes round them");

        phase = "building";
        builder.run();
        phase = "finished: " + builder.status;
        result = Judge.judge(target, site.snapshot(level), level);
        endText = name + ": " + builder.status + " - " + (result.pass ? "PASS" : "FAIL") + " (" + result.failedSections + " of " + result.sections + " sections fail; missing " + result.missing + ", wrong " + result.wrong + ", left behind " + result.leftover + "); "
            + builder.placed + " placed, " + client.realTicks + " ticks (" + String.format(java.util.Locale.ROOT, "%.1f", client.realTicks / Math.max(1.0, builder.placed)) + " a block), " + setbacks + " setbacks. /mcbc status for more.";
    }

    private void addPlaceholders(Site s) {
        for (Container c : containers) {
            Site.Stock st = new Site.Stock(Items.AIR, 0, c.pos.getX(), c.pos.getY(), c.pos.getZ(), 0);
            st.ax = c.ax; st.ay = c.ay; st.az = c.az; st.px = c.px; st.py = c.py; st.pz = c.pz;
            s.stock.add(st);
            s.containerBlocks.add(c.pos.asLong());
            if (c.other != null) s.containerBlocks.add(c.other.asLong());
        }
        if (containers.isEmpty()) {
            // inventory only: the start cell stands in for the supply place (the way-out checks go there)
            BlockPos at = mc.player.blockPosition();
            Site.Stock st = new Site.Stock(Items.AIR, 0, at.getX(), at.getY(), at.getZ(), 0);
            st.ax = at.getX(); st.ay = at.getY(); st.az = at.getZ(); st.px = at.getX() + 0.5; st.py = at.getY(); st.pz = at.getZ() + 0.5;
            s.stock.add(st);
        }
    }

    /** Bot thread: out of an item. Pause and ask; true when the owner resumed (there may be more now). */
    private boolean askFor(Item item) {
        int still = 0;
        Map<Item, Integer> need = site.stillNeeded(mc.level);
        still = need.getOrDefault(item, 0);
        String msg = "out of " + itemName(item) + ": " + still + " more needed. Put them in a supply container (or your inventory), then /mcbc resume.";
        mc.execute(() -> pause(msg));
        // the pause takes effect at the next tick: tick until the owner resumes (the bot does nothing meanwhile)
        client.releaseKeys();
        client.holdAttack(false);
        long t0 = System.currentTimeMillis();
        while (true) {
            builder.idle(1);
            if (state == State.RUNNING && System.currentTimeMillis() - t0 > 200) return true;
        }
    }

    static String itemName(Item it) { return new ItemStack(it).getHoverName().getString(); }

    // ------------------------------------------------------------------ status
    String status() {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append(": ").append(state == State.PAUSED ? "PAUSED (" + pauseReason + ")" : state.toString().toLowerCase()).append(", ").append(phase);
        if (builder != null) sb.append(" / ").append(ModView.phase(builder)).append("; placed ").append(builder.placed).append(", broken ").append(builder.broken)
            .append(", scaffolding standing ").append(ModView.scaffold(builder));
        if (client != null) {
            sb.append("; ticks ").append(client.realTicks).append(" (bot ").append(client.ticks).append(", turn-first ").append(client.settleTicks).append(", waiting for the server ").append(client.ackWaitTicks).append(")");
            if (builder != null && builder.placed > 0) sb.append(", ").append(String.format(java.util.Locale.ROOT, "%.1f", client.realTicks / (double) builder.placed)).append(" ticks a block");
            sb.append("; clicks dropped ").append(client.droppedClicks).append(", setbacks ").append(setbacks).append(", rejected by the server ").append(rejected).append(", late ticks ").append(lateTicks);
        }
        sb.append("; timer ").append(Compat.timerState());
        return sb.toString();
    }

    String statsLine() {
        long wall = (endMs > 0 ? endMs : System.currentTimeMillis()) - startMs;
        return "name=" + name + " origin=" + ox + "," + oy + "," + oz + " status=" + (builder == null ? phase : builder.status)
            + " pass=" + (result == null ? "n/a" : result.pass) + " failedSections=" + (result == null ? -1 : result.failedSections) + " sections=" + (result == null ? -1 : result.sections)
            + " missing=" + (result == null ? -1 : result.missing) + " wrong=" + (result == null ? -1 : result.wrong) + " leftover=" + (result == null ? -1 : result.leftover)
            + " placed=" + (builder == null ? 0 : builder.placed) + " broken=" + (builder == null ? 0 : builder.broken) + " refusedBreaks=" + (builder == null ? 0 : builder.refusedBreaks) + " fallBreaksRefused=" + (builder == null ? 0 : builder.fallBreaksRefused)
            + " realTicks=" + (client == null ? 0 : client.realTicks) + " botTicks=" + (client == null ? 0 : client.ticks)
            + " settleTicks=" + (client == null ? 0 : client.settleTicks) + " ackWaitTicks=" + (client == null ? 0 : client.ackWaitTicks)
            + " droppedClicks=" + (client == null ? 0 : client.droppedClicks) + " breakHolds=" + (client == null ? 0 : client.breakHolds) + " uses=" + (client == null ? 0 : client.uses)
            + " setbacks=" + setbacks + " rejected=" + rejected + " edgeGuards=" + edgeGuards + " lateTicks=" + lateTicks + " thinkMs=" + (client == null ? 0 : client.thinkNanos / 1_000_000)
            + " wallMs=" + wall + " timer=" + timerAtStart + " notCleared=" + notCleared.size() + " scaffoldLeft=" + (builder == null ? 0 : ModView.scaffold(builder))
            + " end=\"" + endText + "\"";
    }
}
