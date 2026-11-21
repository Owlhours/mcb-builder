package mcb.mod;

import java.nio.file.Path;
import java.util.Map;
import mcb.bot.Builder;
import mcb.bot.Grid;
import mcb.build.Judge;
import mcb.build.Site;
import mcb.build.StateInfo;
import mcb.build.Target;
import mcb.env.Client;
import mcb.env.Env;
import mcb.env.Oracle;
import mcb.schem.Schem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;

/**
 * One build in a live world: the layout of the box (the bot's whole site - the build, the supply chests, the
 * margins it walks and puts scaffolding in - plus room for the oracle's trial placements), then, once confirmed,
 * the box cleared, the chests filled and the bot run to the end, as {@code mcb.run.BuildRun} does in the headless
 * runs. Nothing outside the box is changed (the world rules below aside, which the command names before it starts).
 */
public final class Job {
    /** 48 hours of game time, as in the runs. */
    public static final long TICK_LIMIT = 48L * 3600 * 20;

    static Job current, last;
    /** Asked for and confirmed; started after the next server tick (see {@code ServerTickMixin}). */
    static Job queued;
    private static boolean running;

    /** After every server tick (the mixin); inside a build's own ticks it does nothing. */
    public static void afterTick(MinecraftServer server) {
        Job j = queued;
        if (j == null || running || j.server != server) return;
        queued = null;
        running = true;
        try { j.run(); } finally { running = false; }
    }

    final MinecraftServer server;
    final ServerLevel level;
    final String name;
    final Target target;
    final int ox, oy, oz;
    // the box, inclusive
    final int bx0, by0, bz0, bx1, by1, bz1;
    final int marginX, depotDepth, top, scaffoldCount, towerCount;
    final BlockPos scratch;
    Env env;
    Site site;
    Grid grid;
    Builder builder;
    Client client;
    volatile boolean paused, stopAsked;
    String phase = "waiting to start";
    long startTick;
    Judge.Result result;
    private final java.util.function.Consumer<Component> tell;

    /** The layout only: nothing in the world is touched. */
    Job(MinecraftServer server, ServerLevel level, String name, Schem schem, int ox, int oy, int oz, java.util.function.Consumer<Component> tell) {
        StateInfo.init();
        this.server = server; this.level = level; this.name = name; this.tell = tell;
        this.target = new Target(schem);
        this.ox = ox; this.oy = oy; this.oz = oz;
        scaffoldCount = (int) Math.min(64L * 54 * 20, 64 * 27 + target.blocks * 3 + 64L * target.sy * 2);
        towerCount = (int) Math.min(64L * 54 * 20, 64 * 27 + target.blocks * 3 + 64L * target.sy * 8);
        // the chest rows as Site.setUp will lay them out (same counts)
        Site dry = new Site(null, target, ox, oy, oz);
        long stacks = stacks(dry.scaffoldItem, scaffoldCount) + stacks(net.minecraft.world.item.Items.SCAFFOLDING, towerCount);
        for (Map.Entry<net.minecraft.world.item.Item, Integer> e : dry.requirements().entrySet()) { int n = e.getValue(); stacks += stacks(e.getKey(), n + Math.max(4, n / 20)); }
        int perStack = 45 * Site.STACK_HEIGHT;
        int chestStacks = (int) Math.max(1, (stacks + perStack - 1) / perStack);
        int depotCols = Math.max(3, Math.min(24, (target.sx + 1) / 2));
        int depotRows = (chestStacks + depotCols - 1) / depotCols;
        depotDepth = Site.ROW_GAP + depotRows * Site.ROW_PITCH + 2;
        marginX = Math.max(6, depotCols * 2 - target.sx + 4);
        top = Math.min(8, level.getMaxY() - (oy + target.sy));
        // the grid (Grid's constructor): x0 = ox - marginX, z0 = oz - depotDepth, y0 = oy - 1, nx = sx + 2 marginX,
        // nz = sz + depotDepth + 6, ny = sy + 1 + top; the box: the grid, 2 more all round, 8 more east for the oracle
        int gx0 = ox - marginX, gz0 = oz - depotDepth, gx1 = gx0 + target.sx + 2 * marginX - 1, gz1 = gz0 + target.sz + depotDepth + 6 - 1;
        int gy1 = oy - 1 + target.sy + 1 + top - 1;
        bx0 = gx0 - 2; bz0 = gz0 - 2; bx1 = gx1 + 8; bz1 = gz1 + 2;
        by0 = oy - 1; by1 = Math.min(level.getMaxY(), Math.max(gy1, oy + 6));
        scratch = new BlockPos(gx1 + 5, oy + 3, (gz0 + gz1) / 2);
    }

    private static long stacks(net.minecraft.world.item.Item item, long count) {
        int max = new ItemStack(item).getMaxStackSize();
        return (count + max - 1) / max;
    }

    boolean fits() { return oy + target.sy + 4 <= level.getMaxY() && oy - 1 >= level.getMinY(); }

    String boxText() {
        return "(" + bx0 + " " + by0 + " " + bz0 + ") to (" + bx1 + " " + by1 + " " + bz1 + "), " + (bx1 - bx0 + 1) + " x " + (by1 - by0 + 1) + " x " + (bz1 - bz0 + 1);
    }

    /** The chunk radius the bot needs to see all of its site from its start (its client gets block changes only there). */
    int chunkRadius() {
        int vx = ox >> 4, vz = (oz - 2) >> 4;
        return Math.max(Math.max(vx - ((bx0 >> 4) - 1), ((bx1 >> 4) + 1) - vx), Math.max(vz - ((bz0 >> 4) - 1), ((bz1 >> 4) + 1) - vz)) + 1;
    }

    /** The world rules of the runs (Env.applyWorldRules), set for this world. */
    void applyWorldRules() {
        GameRules r = level.getGameRules();
        r.set(GameRules.BLOCK_DROPS, false, server);
        r.set(GameRules.FALL_DAMAGE, false, server);
        r.set(GameRules.RANDOM_TICK_SPEED, 0, server);
        r.set(GameRules.ADVANCE_TIME, false, server);
        r.set(GameRules.ADVANCE_WEATHER, false, server);
        r.set(GameRules.SPAWN_MOBS, false, server);
        r.set(GameRules.SPAWN_MONSTERS, false, server);
        r.set(GameRules.SPAWN_PATROLS, false, server);
        r.set(GameRules.SPAWN_PHANTOMS, false, server);
        r.set(GameRules.SPAWN_WANDERING_TRADERS, false, server);
        r.set(GameRules.SPAWN_WARDENS, false, server);
        server.setDifficulty(Difficulty.PEACEFUL, true);
    }
    static final String RULES = "blockDrops, fallDamage, mob spawning, daylight and weather cycle off; randomTickSpeed 0; difficulty peaceful";

    /** Empties the box: air above a grass floor (its bottom layer), and no entities but players inside it. */
    long clearBox() {
        long n = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState(), grass = Blocks.GRASS_BLOCK.defaultBlockState();
        for (int y = by1; y >= by0; y--) for (int z = bz0; z <= bz1; z++) for (int x = bx0; x <= bx1; x++) {
            BlockState want = y == by0 ? grass : air;
            if (level.getBlockState(p.set(x, y, z)) != want) {
                // (block entities' contents go with them: no drops, flags 2 | 16 as Env.wipe)
                level.removeBlockEntity(p);
                level.setBlock(p, want, 2 | 16);
                n++;
            }
        }
        AABB box = new AABB(bx0, by0, bz0, bx1 + 1, by1 + 1, bz1 + 1);
        for (Entity e : level.getEntities((Entity) null, box, e -> !(e instanceof ServerPlayer))) e.discard();
        return n;
    }

    /** Runs the build to its end, on the server thread, from the server's loop (see {@link LiveLoop}). */
    void run() {
        LiveLoop loop = new LiveLoop(server);
        env = new Env(server, level, () -> {
            loop.tick();
            if (!server.isRunning()) throw new Stop();
            // (a paused game - the menu open in singleplayer - or /mcb pause: the world goes on, the bot waits)
            while ((server.isPaused() || paused) && !stopAsked && server.isRunning()) loop.tick();
            if (stopAsked || !server.isRunning()) throw new Stop();
        });
        startTick = server.getTickCount();
        try {
            phase = "setting up";
            applyWorldRules();
            long cleared = clearBox();
            site = new Site(env, target, ox, oy, oz);
            site.setUp(scaffoldCount, towerCount);
            env.pinSite(ox - marginX, oz - depotDepth, ox + target.sx + marginX, oz + target.sz + 6, ox, oz - 2);
            Oracle oracle = new Oracle(env, scratch);
            client = env.join("Builder", ox + 0.5, oy, oz - 2 + 0.5);
            for (int i = 0; i < 3; i++) client.tick();
            client.clearChanges();
            int ground = Block.getId(Blocks.GRASS_BLOCK.defaultBlockState());
            grid = new Grid(ox, oy, oz, target.sx, target.sy, target.sz, target.ids, marginX, depotDepth, 6, top, ground);
            builder = new Builder(client, grid, site, oracle);
            builder.maxTicks = TICK_LIMIT;
            tell.accept(Component.literal("[mcb] building " + name + " (" + target.blocks + " blocks; " + cleared + " blocks cleared, " + site.stock.size() + " stacks in the chests)"));
            phase = "building";
            try {
                builder.run();
                phase = "finished: " + builder.status;
            } catch (Stop s) {
                phase = "stopped";
            } catch (RuntimeException e) {
                phase = "crashed: " + e;
                mcb.mod.McbMod.LOG.error("[mcb] the bot crashed", e);
            }
        } catch (Stop s) {
            phase = "stopped";
        } catch (RuntimeException e) {
            phase = "crashed: " + e;
            mcb.mod.McbMod.LOG.error("[mcb] the build crashed", e);
        } finally {
            try {
                if (client != null) server.getPlayerList().remove(client.player);
                env.unpinSite();
            } catch (RuntimeException e) { McbMod.LOG.error("[mcb] clean-up", e); }
            current = null;
            last = this;
        }
        if (server.isRunning() && site != null) {
            result = Judge.judge(target, site.snapshot(), level);
            tell.accept(Component.literal("[mcb] " + name + ": " + phase + " after " + ticks() + " ticks - " + (result.pass ? "PASS" : "FAIL") + " (" + result.failedSections + " of " + result.sections + " sections fail; missing " + result.missing + ", wrong " + result.wrong + ", left behind " + result.leftover + "). /mcb judge for the sections."));
        }
    }

    long ticks() { return builder != null ? builder.ticks : 0; }

    /** Thrown from the server tick inside the bot's client to end the build (an Error: the bot's own catch blocks let it through). */
    static final class Stop extends Error {
        Stop() { super("build stopped", null, false, false); }
    }

    static Path schematicDir(MinecraftServer server) { return server.getServerDirectory().resolve("schematics"); }
}
