package mcb.game;

/**
 * A block face under the crosshair, or one the bot means to click: the block (x, y, z), the face that is hit, and the
 * exact point on it. Faces are numbered as the game numbers directions (its "3D data value"):
 * 0 down (-y), 1 up (+y), 2 north (-z), 3 south (+z), 4 west (-x), 5 east (+x).
 */
public final class Hit {
    public static final int DOWN = 0, UP = 1, NORTH = 2, SOUTH = 3, WEST = 4, EAST = 5;
    /** Unit steps along each face's direction, indexed by face. */
    public static final int[] DX = {0, 0, 0, 0, -1, 1}, DY = {-1, 1, 0, 0, 0, 0}, DZ = {0, 0, -1, 1, 0, 0};
    public static final String[] FACE_NAMES = {"down", "up", "north", "south", "west", "east"};
    private static final int[] CW = {0, 1, EAST, WEST, NORTH, SOUTH}, CCW = {0, 1, WEST, EAST, SOUTH, NORTH};
    /** A horizontal face turned a quarter clockwise / anticlockwise, seen from above (north to east ...). */
    public static int clockwise(int face) { return CW[face]; }
    public static int counterClockwise(int face) { return CCW[face]; }

    /** The point on the face, in world coordinates. */
    public final double px, py, pz;
    /** The face (see the class comment). */
    public final int face;
    /** The block. */
    public final int x, y, z;
    /** True if the point is inside the block (the game's "inside" flag; false for every click the bot makes). */
    public final boolean inside;

    public Hit(double px, double py, double pz, int face, int x, int y, int z, boolean inside) {
        this.px = px; this.py = py; this.pz = pz; this.face = face; this.x = x; this.y = y; this.z = z; this.inside = inside;
    }

    public String faceName() { return FACE_NAMES[face]; }
    /** "x, y, z" of the block. */
    public String pos() { return x + ", " + y + ", " + z; }
    @Override public String toString() { return "Hit{" + pos() + " " + faceName() + "}"; }
}
