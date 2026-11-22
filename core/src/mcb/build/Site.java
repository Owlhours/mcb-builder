package mcb.build;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import net.minecraft.world.level.Level;

/**
 * A build site: where the target goes in the world, and where the bot's supplies are. There are two kinds:
 *
 * <ul>
 * <li><b>A depot</b> (the headless runs, the singleplayer mod; {@link #depot} true): the site sets up its own supply
 * chests and fills them ({@link #setUp}). Layout, in world coordinates: the target's cell (0,0,0) is at (ox, oy, oz)
 * with oy the first air layer above the flat ground. Supply chests stand north of the build (smaller z): rows of
 * double chests stacked three high, each row facing south with a two-block aisle in front of it. The chests hold
 * every block item the target needs (plus a small reserve) and the temporary scaffolding blocks.</li>
 * <li><b>Registered containers</b> (the client mod): containers someone else owns and pointed at. Nothing is set up;
 * the caller adds one placeholder {@link Stock} per container with its access point, and what is in them is learnt
 * by opening them ({@code Supplies.survey}).</li>
 * </ul>
 */
public final class Site {
    public static final int ROW_GAP = 5;      // distance from the build to the first chest row
    public static final int ROW_PITCH = 3;
    public static final int STACK_HEIGHT = 3;

    /** True for a depot the site sets up itself; false for registered containers. */
    public final boolean depot;
    public final Target target;
    public final int ox, oy, oz;
    public final Item scaffoldItem;
    public final int scaffoldState;
    /** One entry per chest slot that holds something (registered containers, before the survey: one placeholder per
     *  container). */
    public final List<Stock> stock = new ArrayList<>();
    public int depotRows, depotCols;
    /** Registered containers: every block of every supply container (both halves of a double chest), as BlockPos longs. */
    public final List<Long> containerBlocks = new ArrayList<>();

    public static final class Stock {
        public Item item;
        public int count;
        public final int chestX, chestY, chestZ; // the depot: the west block of the double chest (click either half); else the container block the bot clicks
        public final int slot;                   // slot in the container screen (0..53 in a depot chest)
        /** Registered containers: where the bot stands to open it (a stand cell), and the point on the container it
         *  looks at. (A depot chest is opened from the aisle in front of it; see BuildState.standX.) */
        public int ax, ay, az;
        public double px, py, pz;
        public Stock(Item item, int count, int x, int y, int z, int slot) { this.item = item; this.count = count; chestX = x; chestY = y; chestZ = z; this.slot = slot; }
        public void setAccess(Stock o) { ax = o.ax; ay = o.ay; az = o.az; px = o.px; py = o.py; pz = o.pz; }
    }

    /** A depot site whose first air layer is oy (oy - 1 is the ground); {@code mcb.env.Depot.setUp} sets up and fills
     *  its chests. */
    public Site(Target target, int ox, int oy, int oz) {
        StateInfo.init();
        this.depot = true;
        this.target = target;
        this.ox = ox;
        this.oz = oz;
        this.oy = oy;
        // scaffold material: a quick-to-break full cube that the target itself does not use
        boolean[] used = new boolean[StateInfo.N];
        for (char c : target.ids) used[c] = true;
        Block[] candidates = {Blocks.MOSS_BLOCK, Blocks.PALE_MOSS_BLOCK, Blocks.DRIED_KELP_BLOCK, Blocks.HAY_BLOCK, Blocks.DIRT, Blocks.COBBLESTONE};
        Block chosen = candidates[0];
        for (Block b : candidates) {
            boolean inUse = false;
            for (BlockState s : b.getStateDefinition().getPossibleStates()) if (used[Block.getId(s)]) inUse = true;
            if (!inUse) { chosen = b; break; }
        }
        this.scaffoldItem = chosen.asItem();
        this.scaffoldState = Block.getId(chosen.defaultBlockState());
    }

    /** The loose-block candidates for registered containers (quick to break, full cubes), in order of preference. */
    public static final Block[] LOOSE = {Blocks.MOSS_BLOCK, Blocks.PALE_MOSS_BLOCK, Blocks.DRIED_KELP_BLOCK, Blocks.HAY_BLOCK, Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.NETHERRACK, Blocks.COBBLESTONE};

    /**
     * (The client mod) A site with registered containers, whose first air layer is oy.
     * @param have what the owner has (inventory and containers), item -> count; the loose block is the candidate the
     *             target does not use that the owner has most of (moss if none)
     */
    public Site(Target target, int ox, int oy, int oz, Map<Item, Integer> have) {
        StateInfo.init();
        this.depot = false;
        this.target = target;
        this.ox = ox; this.oy = oy; this.oz = oz;
        boolean[] used = new boolean[StateInfo.N];
        for (char c : target.ids) used[c] = true;
        Block chosen = null;
        int best = -1;
        for (Block b : LOOSE) {
            boolean inUse = false;
            for (BlockState s : b.getStateDefinition().getPossibleStates()) if (used[Block.getId(s)]) inUse = true;
            if (inUse) continue;
            int n = have == null ? 0 : have.getOrDefault(b.asItem(), 0);
            if (chosen == null || n > best) { chosen = b; best = n; }
        }
        if (chosen == null) chosen = Blocks.MOSS_BLOCK;
        this.scaffoldItem = chosen.asItem();
        this.scaffoldState = Block.getId(chosen.defaultBlockState());
    }

    /** Item requirements of the target: item -> count. */
    public Map<Item, Integer> requirements() {
        Map<Item, Integer> need = new LinkedHashMap<>();
        int[] perState = new int[StateInfo.N];
        for (char c : target.ids) perState[c]++;
        for (int id = 1; id < StateInfo.N; id++) {
            if (perState[id] == 0 || StateInfo.itemCount[id] == 0) continue;
            Item it = StateInfo.item[id];
            if (it == Items.AIR) continue;
            need.merge(it, perState[id] * StateInfo.itemCount[id], Integer::sum);
            Item extra = StateInfo.contentItem(id);
            if (extra != null) need.merge(extra, perState[id], Integer::sum);
        }
        return need;
    }

    /** Requirements for the cells that are not yet right in the world (what still has to be placed). */
    public Map<Item, Integer> stillNeeded(Level level) {
        Map<Item, Integer> need = new LinkedHashMap<>();
        char[] now = snapshot(level);
        for (int i = 0; i < target.ids.length; i++) {
            int t = target.ids[i];
            if (t == 0 || StateInfo.itemCount[t] == 0) continue;
            if (Judge.rightCell(t, now[i])) continue;
            Item it = StateInfo.item[t];
            if (it == Items.AIR) continue;
            need.merge(it, (int) StateInfo.itemCount[t], Integer::sum);
            Item extra = StateInfo.contentItem(t);
            if (extra != null) need.merge(extra, 1, Integer::sum);
        }
        return need;
    }

    /** The world's current content of the target box (for judging), as the given level sees it. */
    public char[] snapshot(Level level) {
        char[] out = new char[target.ids.length];
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int i = 0;
        for (int y = 0; y < target.sy; y++) for (int z = 0; z < target.sz; z++) for (int x = 0; x < target.sx; x++, i++) {
            out[i] = (char) Block.getId(level.getBlockState(p.set(ox + x, oy + y, oz + z)));
        }
        return out;
    }
}
