package mcb.game;

/** A point or a vector in world coordinates (blocks). Immutable. */
public final class Vec3 {
    public final double x, y, z;
    public Vec3(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
    @Override public String toString() { return "(" + x + ", " + y + ", " + z + ")"; }
}
