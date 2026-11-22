package mcb.mc;

import mcb.build.BlockView;
import mcb.build.Survival;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;

/**
 * The judge's survival rule ({@link Survival}) with the game's own {@code BlockState.canSurvive}, asked of a view that
 * answers block and fluid questions from the made-up world, says "full light" to light questions, and has no block
 * entities. Anything else it is asked falls through to {@code base} (a real level; recorded in
 * {@link #viewFallThrough}), or fails if there is none.
 */
public final class GameSurvival implements Survival {
    private final LevelReader base;
    private BlockView lastWorld;
    private LevelReader lastView;

    /** Methods of the view that fell through to the real level (for checking; should stay empty). */
    public static final java.util.Set<String> viewFallThrough = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public GameSurvival(LevelReader base) { this.base = base; }

    @Override public boolean canSurvive(int state, int x, int y, int z, BlockView world) {
        return Block.stateById(state).canSurvive(view(world), new BlockPos(x, y, z));
    }

    private LevelReader view(BlockView world) {
        if (world == lastWorld) return lastView;
        java.lang.reflect.InvocationHandler h = (proxy, m, args) -> {
            String n = m.getName();
            if (args != null && args.length >= 1 && args[0] instanceof BlockPos q) {
                if (n.equals("getBlockState") && args.length == 1) return Block.stateById(world.stateAt(q.getX(), q.getY(), q.getZ()));
                if (n.equals("getFluidState") && args.length == 1) return net.minecraft.world.level.material.Fluids.EMPTY.defaultFluidState();
                if (n.equals("getRawBrightness") || n.equals("getMaxLocalRawBrightness")) return 15;
                if (n.equals("getBlockEntity") && args.length == 1) return null;
            }
            if (n.equals("getBrightness") && args != null && args.length == 2) return 15;
            if (m.isDefault()) return java.lang.reflect.InvocationHandler.invokeDefault(proxy, m, args);
            viewFallThrough.add(n);
            if (base == null) throw new UnsupportedOperationException(n);
            try { return m.invoke(base, args); } catch (java.lang.reflect.InvocationTargetException e) { throw e.getCause(); }
        };
        lastView = (LevelReader) java.lang.reflect.Proxy.newProxyInstance(LevelReader.class.getClassLoader(), new Class<?>[] {LevelReader.class}, h);
        lastWorld = world;
        return lastView;
    }
}
