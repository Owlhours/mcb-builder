package mcb.build;

/** A read-only view of the blocks of a world: the state id at (x, y, z). */
@FunctionalInterface
public interface BlockView {
    int stateAt(int x, int y, int z);
}
