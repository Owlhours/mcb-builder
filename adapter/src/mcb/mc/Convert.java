package mcb.mc;

import mcb.game.Hit;
import mcb.game.Stack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Between the game's types and the planner's ({@code mcb.game}). Lossless both ways for everything the bot uses. */
public final class Convert {
    private Convert() { }

    public static Hit hit(BlockHitResult b) {
        Vec3 l = b.getLocation();
        BlockPos p = b.getBlockPos();
        return new Hit(l.x, l.y, l.z, b.getDirection().get3DDataValue(), p.getX(), p.getY(), p.getZ(), b.isInside());
    }

    public static BlockHitResult hit(Hit h) {
        return new BlockHitResult(new Vec3(h.px, h.py, h.pz), Direction.from3DDataValue(h.face), new BlockPos(h.x, h.y, h.z), h.inside);
    }

    /** A level as the planner's read-only block view. */
    public static mcb.build.BlockView view(net.minecraft.world.level.BlockGetter level) {
        return (x, y, z) -> net.minecraft.world.level.block.Block.getId(level.getBlockState(new BlockPos(x, y, z)));
    }

    public static Stack stack(ItemStack s) {
        // (an empty stack reads as the air item with a count of 0, as the game's getItem() and getCount() give it)
        return s.isEmpty() ? new Stack(mcb.build.ItemInfo.AIR, 0) : new Stack(BlockFacts.itemId(s.getItem()), s.getCount());
    }
}
