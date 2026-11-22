package mcb.headless;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mcb.bot.Builder;
import mcb.bot.Grid;
import mcb.build.Judge;
import mcb.build.Site;
import mcb.build.StateInfo;
import mcb.build.Target;
import mcb.env.Client;
import mcb.env.Env;
import mcb.schem.Schem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

/**
 * One build with the client mod's planner settings, in the headless game: the planner as the client mod sets it up
 * ({@code mcbc.Job.run}), on the server-side bot player instead of a real client.
 *
 * <ul>
 * <li>fall damage on, and the client's {@link mcb.bot.Rules} (planned drops capped, nothing outside the box broken but
 *     the bot's own blocks, the world re-read, the timed climb out of scaffolding columns);</li>
 * <li>the client's supply model: the chests are set up and filled as for a headless run, then <i>registered</i> the way
 *     {@code /mcbc supply here} registers containers (an access point beside each), read by walking to each and
 *     opening it ({@code Supplies.survey}), and the loose block is chosen from what is there;</li>
 * <li>the grid laid out round the box and the containers, and read from the world (unknown cells as solid stone).</li>
 * </ul>
 *
 * This is how the client mod's planner can be checked without a Minecraft client: two versions of the planner that
 * play the same course here (the same ticks, counts and {@link Client#trace}) make the same decisions in the client
 * mod, which hands them the same observations. The real client adds latency, server acknowledgements and safety
 * stops on top; those are in the client mod's own {@code mcb.env.Client}, not in the planner.
 */
public final class ClientRun {
    /** What a client-mode run returns: the judge, and the bot's counters. */
    public static final class Outcome {
        public Judge.Result judge;
        public String status = "";
        public long ticks, placed, broken, scaffoldPlaced, scaffoldLeft, trips, refusedBreaks, fallBreaksRefused, trace, targetBlocks;
        public int surveyFailures;
        public boolean died;
    }

    private static mcb.env.Oracle oracle;

    /** The client mod's default (Config.maxFall16). */
    public static int maxFall16 = 56;

    // Site's depot layout, spelled out here so that this file also compiles against the client mod's old Site; but
    // stacked two high, not three: the client mod registers only containers it can open standing on the ground or
    // one block lower (mcbc.Job.findAccess), so a third chest would be refused.
    static final int ROW_GAP = 5, ROW_PITCH = 3, STACK_HEIGHT = 2;

    static final class Container { BlockPos pos, other; int ax, ay, az; double px, py, pz; boolean reachable; }

    public static Outcome run(Env env, Schem schem) {
        StateInfo.init();
        Outcome out = new Outcome();
        Target target = new Target(schem);
        out.targetBlocks = target.blocks;
        int ox = 64, oz = 64;
        int oy = env.level.getMinY();   // (the first air layer above the ground)
        { BlockPos.MutableBlockPos g = new BlockPos.MutableBlockPos(ox, oy, oz); while (!env.level.getBlockState(g).isAir()) g.setY(++oy); }
        env.setFallDamage(true);
        // ---- the supply chests: laid out and filled as for a headless run (same counts), but not known to the bot
        Site dry = new Site(target, ox, oy, oz, null);
        int scaffoldCount = (int) Math.min(64L * 54 * 20, 64 * 27 + target.blocks * 3 + 64L * target.sy * 2);
        int towerCount = (int) Math.min(64L * 54 * 20, 64 * 27 + target.blocks * 3 + 64L * target.sy * 8);
        List<BlockPos> chests = placeChests(env, target, dry, ox, oy, oz, scaffoldCount, towerCount);
        // ---- mcbc.Job's layout: the box, the containers, 2 more round them, then a margin of 6
        int x0 = ox, z0 = oz, x1 = ox + target.sx - 1, z1 = oz + target.sz - 1;
        List<Container> containers = new ArrayList<>();
        for (BlockPos p : chests) {
            Container c = new Container();
            c.pos = p;
            BlockState s = env.level.getBlockState(p);
            if (s.getBlock() instanceof ChestBlock && s.getValue(ChestBlock.TYPE) != ChestType.SINGLE) c.other = p.relative(ChestBlock.getConnectedDirection(s));
            containers.add(c);
            x0 = Math.min(x0, p.getX() - 2); z0 = Math.min(z0, p.getZ() - 2); x1 = Math.max(x1, p.getX() + 2); z1 = Math.max(z1, p.getZ() + 2);
            if (c.other != null) { x0 = Math.min(x0, c.other.getX() - 2); z0 = Math.min(z0, c.other.getZ() - 2); x1 = Math.max(x1, c.other.getX() + 2); z1 = Math.max(z1, c.other.getZ() + 2); }
        }
        int m = 6;
        int gx0 = x0 - m, gz0 = z0 - m, gx1 = x1 + m, gz1 = z1 + m;
        int top = Math.min(8, env.level.getMaxY() - (oy + target.sy));
        int gy1 = oy - 1 + target.sy + 1 + Math.max(0, top) - 1;
        for (Container c : containers) findAccess(env, c, gx0, gz0, gx1, gz1, oy);
        env.pinSite(gx0 - 2, gz0 - 2, gx1 + 2, gz1 + 2, ox, oz - 2);
        int seed = System.getenv("MCB_SEED") == null ? 0 : Integer.parseInt(System.getenv("MCB_SEED"));
        Client client = env.join("Bot" + (ox / 64), ox + 0.5 + Math.floorMod(seed, Math.max(1, Math.min(target.sx, 12))), oy, oz - 2 + 0.5);
        for (int i = 0; i < 3; i++) client.tick();
        client.clearChanges();
        // ---- the grid from the world (mcbc.Job.run)
        int nx = gx1 - gx0 + 1, nz = gz1 - gz0 + 1, ny = gy1 - (oy - 1) + 1;
        int stone = Block.getId(Blocks.STONE.defaultBlockState());
        Grid grid = new Grid(ox, oy, oz, target.sx, target.sy, target.sz, target.ids, gx0, gz0, nx, nz, ny, Block.getId(Blocks.GRASS_BLOCK.defaultBlockState()), false);
        var level = env.level;
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
        if (oracle == null) oracle = new mcb.env.Oracle(env);
        // ---- read the containers
        Site site0 = new Site(target, ox, oy, oz, carried(client));
        for (Container c : containers) {
            Site.Stock st = new Site.Stock(Items.AIR, 0, c.pos.getX(), c.pos.getY(), c.pos.getZ(), 0);
            st.ax = c.ax; st.ay = c.ay; st.az = c.az; st.px = c.px; st.py = c.py; st.pz = c.pz;
            site0.stock.add(st);
            site0.containerBlocks.add(c.pos.asLong());
            if (c.other != null) site0.containerBlocks.add(c.other.asLong());
        }
        Builder reader = ClientPlanner.builder(client, grid, site0, oracle, maxFall16);
        reader.maxTicks = Long.MAX_VALUE;
        reader.survey();
        out.surveyFailures = reader.surveyFailures.size();
        if (System.getenv("MCB_CLIENTRUN_DEBUG") != null) for (String f : reader.surveyFailures) System.out.println("SURVEY " + f + " (bot at " + client.x() + "," + client.y() + "," + client.z() + ")");
        Map<Item, Integer> have = carried(client);
        for (Site.Stock s : site0.stock) if (s.count > 0) have.merge(s.item, s.count, Integer::sum);
        Site site = new Site(target, ox, oy, oz, have);
        site.stock.addAll(site0.stock);
        site.containerBlocks.addAll(site0.containerBlocks);
        // ---- build
        Builder b = ClientPlanner.builder(client, grid, site, oracle, maxFall16);
        b.maxTicks = 48L * 3600 * 20;
        b.shortage = item -> false;
        try {
            b.clearBox(i -> {
                int w = grid.cur[i];
                float h = StateInfo.state[w].getBlock().defaultDestroyTime();
                int bt = h < 0 ? -1 : (int) Math.ceil(h * (StateInfo.state[w].requiresCorrectToolForDrops() ? 100 : 30) * 1.05f) + 1;
                if (bt < 0 || bt > 200) return false;
                if (!grid.inBox(grid.xOf(i), grid.yOf(i), grid.zOf(i))) return false;
                if (StateInfo.state[w].getBlock() instanceof net.minecraft.world.level.block.LiquidBlock) return false;
                return !site.containerBlocks.contains(BlockPos.asLong(grid.xOf(i), grid.yOf(i), grid.zOf(i)));
            });
            b.run();
        } catch (RuntimeException e) {
            b.status = "crash: " + e;
        }
        out.status = b.status;
        out.ticks = b.ticks; out.placed = b.placed; out.broken = b.broken; out.trips = b.trips;
        out.scaffoldPlaced = b.scaffoldPlaced; out.scaffoldLeft = b.scaffoldLeft();
        out.refusedBreaks = b.refusedBreaks; out.fallBreaksRefused = b.fallBreaksRefused; out.died = b.died;
        out.trace = client.trace;
        for (int i = 0; i < 5; i++) env.tickServer();
        out.judge = Judge.judge(target, site.snapshot(env.level), env.level);
        env.server.getPlayerList().remove(client.player);
        env.wipe(grid.x0, oy, grid.z0, grid.x0 + grid.nx - 1, oy + grid.ny, grid.z0 + grid.nz - 1);
        for (int i = 0; i < 3; i++) env.tickServer();
        env.unpinSite();
        env.setFallDamage(false);
        return out;
    }

    static Map<Item, Integer> carried(Client c) {
        Map<Item, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < 36; i++) { ItemStack s = c.inventorySlot(i); if (!s.isEmpty()) m.merge(s.getItem(), s.getCount(), Integer::sum); }
        return m;
    }

    /** The depot's chest layout (mcb.env.Depot.setUp), filled the same way; returns the west block of each double chest. */
    static List<BlockPos> placeChests(Env env, Target target, Site dry, int ox, int oy, int oz, int scaffoldCount, int towerCount) {
        Map<Item, Integer> need = dry.requirements();
        List<ItemStack> stacks = new ArrayList<>();
        addStacks(stacks, dry.scaffoldItem, scaffoldCount);
        addStacks(stacks, Items.SCAFFOLDING, towerCount);
        for (Map.Entry<Item, Integer> e : need.entrySet()) { int n = e.getValue(); addStacks(stacks, e.getKey(), n + Math.max(4, n / 20)); }
        int perChest = 45, perStack = perChest * STACK_HEIGHT;
        int chestStacks = Math.max(1, (stacks.size() + perStack - 1) / perStack);
        int depotCols = Math.max(3, Math.min(24, (target.sx + 1) / 2));
        List<BlockPos> out = new ArrayList<>();
        int si = 0;
        for (int k = 0; k < chestStacks; k++) {
            int row = k / depotCols, col = k % depotCols;
            int cx = ox + col * 2, cz = oz - ROW_GAP - row * ROW_PITCH;
            for (int h = 0; h < STACK_HEIGHT && si < stacks.size(); h++) {
                BlockPos west = new BlockPos(cx, oy + h, cz), east = west.east();
                BlockState base = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH);
                env.level.setBlock(west, base.setValue(ChestBlock.TYPE, ChestType.RIGHT), 2);
                env.level.setBlock(east, base.setValue(ChestBlock.TYPE, ChestType.LEFT), 2);
                ChestBlockEntity bw = (ChestBlockEntity) env.level.getBlockEntity(west), be = (ChestBlockEntity) env.level.getBlockEntity(east);
                for (int s = 0; s < perChest && si < stacks.size(); s++, si++) (s < 27 ? bw : be).setItem(s % 27, stacks.get(si).copy());
                out.add(west);
            }
        }
        return out;
    }

    private static void addStacks(List<ItemStack> out, Item item, int count) {
        int max = new ItemStack(item).getMaxStackSize();
        while (count > 0) { int n = Math.min(max, count); out.add(new ItemStack(item, n)); count -= n; }
    }

    /** mcbc.Job.findAccess: a cell beside the container to stand in (floor under it, two free cells), inside the grid,
     *  from which the middle of the container is within reach. */
    static void findAccess(Env env, Container c, int gx0, int gz0, int gx1, int gz1, int oy) {
        var level = env.level;
        BlockPos[] blocks = c.other == null ? new BlockPos[] {c.pos} : new BlockPos[] {c.pos, c.other};
        double best = Double.MAX_VALUE;
        for (BlockPos b : blocks) for (Direction d : Direction.Plane.HORIZONTAL) for (int dy = -1; dy <= 0; dy++) {
            BlockPos s = b.relative(d).above(dy);
            if (s.getX() < gx0 || s.getX() > gx1 || s.getZ() < gz0 || s.getZ() > gz1 || s.getY() < oy - 1) continue;
            BlockState feet = level.getBlockState(s), head = level.getBlockState(s.above()), floor = level.getBlockState(s.below());
            boolean standable = feet.getCollisionShape(level, s).isEmpty() && head.getCollisionShape(level, s.above()).isEmpty()
                && floor.isFaceSturdy(level, s.below(), Direction.UP) && feet.getFluidState().isEmpty();
            if (!standable) continue;
            double ex = s.getX() + 0.5, ey = s.getY() + 1.62, ez = s.getZ() + 0.5;
            double px = b.getX() + 0.5, py = b.getY() + 0.45, pz = b.getZ() + 0.5;
            double dist = Math.sqrt((ex - px) * (ex - px) + (ey - py) * (ey - py) + (ez - pz) * (ez - pz));
            if (dist > 3.6) continue;
            double score = dist + (dy != 0 ? 0.5 : 0) + (s.getY() != oy ? 0.2 : 0);
            if (score < best) { best = score; c.ax = s.getX(); c.ay = s.getY(); c.az = s.getZ(); c.px = px; c.py = py; c.pz = pz; c.reachable = true; }
        }
    }
}
