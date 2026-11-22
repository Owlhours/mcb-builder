package mcb.game;

/**
 * A block position packed into a long, bit for bit as the game packs it (26 bits x, 12 bits y, 26 bits z), so that
 * positions the adapter hands over packed by the game's own code compare equal.
 */
public final class Pos {
    private Pos() { }
    private static final int XZ_BITS = 26, Y_BITS = 12;
    private static final long XZ_MASK = (1L << XZ_BITS) - 1, Y_MASK = (1L << Y_BITS) - 1;
    private static final int Z_OFFSET = Y_BITS, X_OFFSET = Y_BITS + XZ_BITS;

    public static long pack(int x, int y, int z) {
        return ((x & XZ_MASK) << X_OFFSET) | (y & Y_MASK) | ((z & XZ_MASK) << Z_OFFSET);
    }
    public static int x(long p) { return (int) (p << (64 - X_OFFSET - XZ_BITS) >> (64 - XZ_BITS)); }
    public static int y(long p) { return (int) (p << (64 - Y_BITS) >> (64 - Y_BITS)); }
    public static int z(long p) { return (int) (p << (64 - Z_OFFSET - XZ_BITS) >> (64 - XZ_BITS)); }
}
