package mcb.build;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * (MCB client) A build site in a real world: where the target goes and the supply containers the owner pointed at.
 * Unlike the headless runs nothing is set up here: the containers are the owner's own, and what is in them is
 * learnt by opening them ({@code Supplies.survey}).
 */
public final class Site {
    public final Target target;
    public final int ox, oy, oz;
    public final Item scaffoldItem;
    public final int scaffoldState;
    /** One entry per container slot that holds something (before the survey: one placeholder per container). */
    public final List<Stock> stock = new ArrayList<>();
    /** Every block of every supply container (both halves of a double chest), as BlockPos longs. */
    public final List<Long> containerBlocks = new ArrayList<>();

    public static final class Stock {
        public Item item;
        public int count;
        public final int chestX, chestY, chestZ; // the container block the bot clicks
        public final int slot;                   // slot in the container screen
        /** Where the bot stands to open it (a stand cell), and the point on the container it looks at. */
        public int ax, ay, az;
        public double px, py, pz;
        public Stock(Item item, int count, int x, int y, int z, int slot) { this.item = item; this.count = count; chestX = x; chestY = y; chestZ = z; this.slot = slot; }
        public void setAccess(Stock o) { ax = o.ax; ay = o.ay; az = o.az; px = o.px; py = o.py; pz = o.pz; }
    }

    /** The loose-block candidates (quick to break, full cubes), in order of preference. */
    public static final Block[] LOOSE = {Blocks.MOSS_BLOCK, Blocks.PALE_MOSS_BLOCK, Blocks.DRIED_KELP_BLOCK, Blocks.HAY_BLOCK, Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.NETHERRACK, Blocks.COBBLESTONE};

    /**
     * @param have what the owner has (inventory and containers), item -> count; the loose block is the candidate the
     *             target does not use that the owner has most of (moss if none)
     */
    public Site(Target target, int ox, int oy, int oz, Map<Item, Integer> have) {
        StateInfo.init();
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
