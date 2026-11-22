package mcb.game;

/**
 * Knowledge of the game's rules, as a player has it: "if I click there, holding this, looking that way, what
 * appears?". An implementation answers with the game's own placement code, without placing anything; it never
 * changes the world the bot builds in and gives the bot no way to act.
 *
 * <p>Two services: {@link #predict} for a concrete click in the real surroundings, and {@link #table} - what each
 * block item turns into for every combination of clicked face, part of the face, view direction and sneaking,
 * measured once against a lone support block. The table layout and the view classes are fixed here
 * ({@link #index}); they are the planner's, not the game's.
 */
public abstract class Oracle {
    public static final int NYAW = 32, NPITCH = 9;
    public static final int TABLE_SIZE = 6 * 4 * NYAW * NPITCH * 2;

    /** Number of {@link #predict} calls (statistics). */
    public long predictions;
    /** Where the state of the last successful {@link #predict} would appear. */
    public int placedX, placedY, placedZ;

    public static int yawClass(float yaw) {
        float y = yaw % 360f;
        if (y < -180f) y += 360f; else if (y >= 180f) y -= 360f;
        int k = (int) Math.floor((y + 180f) / 11.25f);
        return Math.max(0, Math.min(NYAW - 1, k));
    }
    public static float yawCentre(int k) { return -180f + 5.625f + 11.25f * k; }
    public static int pitchClass(float pitch) { return Math.max(0, Math.min(NPITCH - 1, Math.round((pitch + 80f) / 20f))); }
    public static float pitchCentre(int k) { return -80f + 20f * k; }
    public static int index(int face, int quad, int yawClass, int pitchClass, boolean sneak) {
        return (((face * 4 + quad) * NYAW + yawClass) * NPITCH + pitchClass) * 2 + (sneak ? 1 : 0);
    }

    /** What the crosshair of the client's player would rest on, looking along (yaw, pitch); null if nothing in reach. */
    public abstract Hit pick(Client c, float yaw, float pitch);

    /** The state that would appear (and where: {@link #placedX} ...) if the player used the item now on the given hit
     *  while looking along (yaw, pitch); 0 if nothing would be placed. */
    public abstract int predict(int item, Hit hit, float yaw, float pitch, boolean sneak);

    /** Would the block at (x, y, z) still stand if the block at (gx, gy, gz), next to it, were taken away? */
    public abstract boolean survivesWithout(int x, int y, int z, int gx, int gy, int gz);

    /** Can this state exist with nothing around it (true), or does it need something to sit on or hang from? */
    public abstract boolean floats(int stateId);

    /** See the class comment. Index with {@link #index}. Entries are state ids, 0 = that click places nothing. */
    public abstract char[] table(int item);
}
