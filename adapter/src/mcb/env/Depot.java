package mcb.env;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import mcb.build.Site;
import mcb.build.StateInfo;
import mcb.build.Target;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

/**
 * The world side of a depot site (administrator actions, never the bot's): where the ground is, and the supply chests
 * the site sets up and fills. See {@link Site} for the layout.
 */
public final class Depot {
    private Depot() { }

    /** The first air layer above the ground at (x, z), from the bottom of the world. */
    public static int groundTop(Env env, int x, int z) {
        int gy = env.level.getMinY();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, gy, z);
        while (!env.level.getBlockState(p).isAir()) p.setY(++gy);
        return gy;
    }

    /** Whether the build, with room above it, fits under the top of the world. */
    public static boolean fitsWorld(Env env, Site site) { return site.oy + site.target.sy + 4 <= env.level.getMaxY(); }

    /** Places the site's supply chests and fills them: what the target needs plus a reserve, scaffolding and loose blocks. */
    public static void setUp(Env env, Site site, int scaffoldCount, int towerCount) {
        Target target = site.target;
        int ox = site.ox, oy = site.oy, oz = site.oz;
        List<Site.Stock> stock = site.stock;
        Map<Item, Integer> need = site.requirements();
        List<ItemStack> stacks = new ArrayList<>();
        addStacks(stacks, site.scaffoldItem, scaffoldCount);
        addStacks(stacks, net.minecraft.world.item.Items.SCAFFOLDING, towerCount);
        for (Map.Entry<Item, Integer> e : need.entrySet()) {
            int n = e.getValue();
            addStacks(stacks, e.getKey(), n + Math.max(4, n / 20));
        }
        // (the last row of every chest stays empty: room to put things back)
        int perChest = 45;
        int perStack = perChest * Site.STACK_HEIGHT;
        int chestStacks = Math.max(1, (stacks.size() + perStack - 1) / perStack);
        int depotCols = site.depotCols = Math.max(3, Math.min(24, (target.sx + 1) / 2));
        site.depotRows = (chestStacks + depotCols - 1) / depotCols;
        int si = 0;
        for (int k = 0; k < chestStacks; k++) {
            int row = k / depotCols, col = k % depotCols;
            int cx = ox + col * 2, cz = oz - Site.ROW_GAP - row * Site.ROW_PITCH;
            for (int h = 0; h < Site.STACK_HEIGHT && si < stacks.size(); h++) {
                int cy = oy + h;
                BlockPos west = new BlockPos(cx, cy, cz), east = west.east();
                BlockState base = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH);
                env.level.setBlock(west, base.setValue(ChestBlock.TYPE, ChestType.RIGHT), 2);
                env.level.setBlock(east, base.setValue(ChestBlock.TYPE, ChestType.LEFT), 2);
                ChestBlockEntity bw = (ChestBlockEntity) env.level.getBlockEntity(west), be = (ChestBlockEntity) env.level.getBlockEntity(east);
                for (int s = 0; s < perChest && si < stacks.size(); s++, si++) {
                    ItemStack st = stacks.get(si);
                    (s < 27 ? bw : be).setItem(s % 27, st.copy());
                    stock.add(new Site.Stock(st.getItem(), st.getCount(), cx, cy, cz, s));
                }
            }
        }
    }

    private static void addStacks(List<ItemStack> out, Item item, int count) {
        int max = new ItemStack(item).getMaxStackSize();
        while (count > 0) {
            int n = Math.min(max, count);
            out.add(new ItemStack(item, n));
            count -= n;
        }
    }

    /** Test helper: writes the target straight into the world (as a creative-mode paste would). */
    public static void pasteTarget(Env env, Site site, int flags) {
        Target target = site.target;
        int ox = site.ox, oy = site.oy, oz = site.oz;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int i = 0;
        for (int y = 0; y < target.sy; y++) for (int z = 0; z < target.sz; z++) for (int x = 0; x < target.sx; x++, i++) {
            int id = target.ids[i];
            if (id != 0) env.level.setBlock(p.set(ox + x, oy + y, oz + z), StateInfo.state[id], flags);
        }
    }
}
