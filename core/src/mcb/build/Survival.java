package mcb.build;

/**
 * The game's own "can this block stay here?" rule (a torch needs a wall, a flower needs soil ...), asked of a made-up
 * world: the judge's impossible-cell rule uses it on the target standing on flat ground. The Minecraft adapter
 * implements it with the game's code ({@code mcb.mc.GameSurvival}).
 */
@FunctionalInterface
public interface Survival {
    /** Whether {@code state} can stay at (x, y, z) in {@code world}. May throw; the caller counts that as "it can". */
    boolean canSurvive(int state, int x, int y, int z, BlockView world);
}
