package mcb.bot;

import mcb.build.StateInfo;

/**
 * The bot's own picture of the build site: what it believes is in every cell (kept current from the block changes
 * its client is told about) and what the target wants there. World coordinates; arrays cover the target box plus a
 * margin for walking, the supply chests and scaffolding.
 */
public final class Grid {
    public final int x0, y0, z0, nx, ny, nz;       // array origin (world) and size
    public final int ox, oy, oz, sx, sy, sz;       // target box origin (world) and size
    public final char[] cur;                       // believed world state id
    public final char[] tgt;                       // target state id (0 = nothing)
    public final int groundState;

    public Grid(int ox, int oy, int oz, int sx, int sy, int sz, char[] target, int marginX, int marginNorth, int marginSouth, int marginTop, int groundState) {
        this(ox, oy, oz, sx, sy, sz, target, ox - marginX, oz - marginNorth, sx + 2 * marginX, sz + marginNorth + marginSouth, sy + 1 + marginTop, groundState, true);
    }

    /** (MCB client) A grid with explicit bounds (x0 .. x0+nx-1, oy-1 .. oy-2+ny, z0 .. z0+nz-1) that must hold the target box;
     *  {@code flatGround} fills the bottom layer with ground (the headless runs); without it the caller fills {@link #cur}. */
    public Grid(int ox, int oy, int oz, int sx, int sy, int sz, char[] target, int x0, int z0, int nx, int nz, int ny, int groundState, boolean flatGround) {
        this.ox = ox; this.oy = oy; this.oz = oz; this.sx = sx; this.sy = sy; this.sz = sz;
        this.x0 = x0; this.z0 = z0; this.y0 = oy - 1;
        this.nx = nx; this.nz = nz; this.ny = ny;
        if (ox < x0 || oz < z0 || ox + sx > x0 + nx || oz + sz > z0 + nz || sy + 1 > ny) throw new IllegalArgumentException("grid does not hold the box");
        long vol = (long) nx * ny * nz;
        if (vol > Integer.MAX_VALUE - 16) throw new IllegalArgumentException("site too large");
        cur = new char[(int) vol];
        tgt = new char[(int) vol];
        this.groundState = groundState;
        if (flatGround) java.util.Arrays.fill(cur, 0, nx * nz, (char) groundState); // the layer under the build is the ground
        for (int y = 0; y < sy; y++) for (int z = 0; z < sz; z++) {
            System.arraycopy(target, (y * sz + z) * sx, tgt, idx(ox, oy + y, oz + z), sx);
        }
    }

    /** (MCB client) What lies outside the grid: the real world as the client knows it (unknown = solid). Null: the
     *  headless assumption (ground below, air beside and above). */
    public interface Outside { int get(int x, int y, int z); }
    public Outside outside;

    public int idx(int x, int y, int z) { return ((y - y0) * nz + (z - z0)) * nx + (x - x0); }
    public boolean in(int x, int y, int z) { return x >= x0 && x < x0 + nx && y >= y0 && y < y0 + ny && z >= z0 && z < z0 + nz; }
    public int xOf(int i) { return x0 + i % nx; }
    public int zOf(int i) { return z0 + (i / nx) % nz; }
    public int yOf(int i) { return y0 + i / (nx * nz); }

    /** Believed state at a world position; below the site it is ground, elsewhere outside it is air. */
    public int get(int x, int y, int z) {
        if (!in(x, y, z)) { if (outside != null) return outside.get(x, y, z); return y < y0 ? groundState : 0; }
        return cur[idx(x, y, z)];
    }

    public int target(int x, int y, int z) { return in(x, y, z) ? tgt[idx(x, y, z)] : 0; }

    public void set(int x, int y, int z, int state) { if (in(x, y, z)) cur[idx(x, y, z)] = (char) (StateInfo.air[state] ? 0 : state); }

    public boolean inBox(int x, int y, int z) { return x >= ox && x < ox + sx && y >= oy && y < oy + sy && z >= oz && z < oz + sz; }

    // ------------------------------------------------------------------ standing
    /**
     * Height of the player's feet above the bottom of cell (x,y,z) in 1/16 block when standing centred in that
     * column with the feet inside the cell, or -1 if one cannot stand there.
     */
    static final boolean X_HAZ = System.getenv("MCB_NO_HAZ") == null, X_CARPET = System.getenv("MCB_NO_CARPET") == null;
    /** MCB_HOPPERFLOOR, a measurement that is off (it lost passing builds on its decision set): a hopper counts as a full block to
     *  stand on. Its top is a rim at 16 around a bowl at 11, and a player walks across a floor of hoppers like across
     *  any other - but as an uneven top it was nowhere to stand, and a bot that came down on one (it took away the
     *  chest it stood on) had no move left. */
    static final boolean HOPPER_FLOOR = System.getenv("MCB_HOPPERFLOOR") != null;
    private static byte[] standCol, standAny;
    private static void standTops() {
        byte[] c = StateInfo.colTop.clone(), a = StateInfo.anyTop.clone();
        if (HOPPER_FLOOR) for (int i = 0; i < c.length; i++) if (StateInfo.hopper[i]) { c[i] = 16; a[i] = 16; }
        standAny = a; standCol = c;
    }
    /** The top of a state's collision shape over its whole column / its highest point, in sixteenths, as the bot's
     *  footing sees them (stand16, and where the route search lands or needs a level floor). */
    static int colTopS(int s) { if (standCol == null) standTops(); return standCol[s]; }
    static int anyTopS(int s) { if (standCol == null) standTops(); return standAny[s]; }

    /** WALK_WAY (revision "walkway", Rust `Grid::hang_floor`): the bottom of a hanging piece of scaffolding counts as a
     *  floor (stand16), as it is one to whoever comes in from the side or from above: from the first walkway piece on
     *  (BuildState.setHangFloor). Rust also forgets its stand16 cache, scan words and dry pockets here; Java has none. */
    public boolean hangFloor;
    private static boolean[] hangPiece;
    private static byte[] scafDist;
    private static void scafTables() {
        boolean[] h = new boolean[StateInfo.N]; byte[] d = new byte[StateInfo.N];
        for (int s = 0; s < StateInfo.N; s++) {
            if (!StateInfo.scaffolding[s]) continue;
            d[s] = StateInfo.scaffoldDistance[s];
            h[s] = d[s] != 0 && StateInfo.scaffoldBottom[s];
        }
        scafDist = d; hangPiece = h;
    }
    /** WALK_WAY (Rust stand_info bit 18): a piece of scaffolding that hangs on its neighbours (distance != 0, bottom). */
    static boolean hangPiece(int s) { if (hangPiece == null) scafTables(); return hangPiece[s]; }
    /** The `distance` of a scaffolding state (0 for anything else). */
    static int scafDistance(int s) { if (hangPiece == null) scafTables(); return scafDist[s]; }

    public int stand16(int x, int y, int z) {
        int a = get(x, y, z), b = get(x, y - 1, z);
        if (X_HAZ && (StateInfo.hazard[a] || StateInfo.hazard[b])) return -1;   // (a lit campfire, magma, cactus ...: it would hurt)
        int h;
        int ta = colTopS(a);
        if (hangFloor && hangPiece(a)) {
            h = 2;                                                  // (WALK_WAY) inside a hanging piece of scaffolding, on its bottom
        } else if (StateInfo.walkThrough[a]) {
            int tb = colTopS(b);
            if (tb < 16 || anyTopS(b) != tb) return -1;   // nothing to stand on, or an uneven top (stairs ...)
            h = tb - 16;
        } else if (ta > 0 && ta < 16 && StateInfo.colBottom[a] == 0 && anyTopS(a) == ta) {
            h = ta;                                                 // a slab, carpet, snow layer ... in the feet cell
            // (a carpet on a fence: the fence reaches up through it, and that is what carries the feet)
            int over = colTopS(b) - 16;
            if (X_CARPET && over > h) { if (anyTopS(b) != colTopS(b)) return -1; h = over; }
        } else return -1;
        // head room: the body occupies [h, h + 28.8] sixteenths above the cell bottom; anything solid in those cells is in the way
        int top = h + 29;
        int s1 = get(x, y + 1, z);
        if (X_HAZ && StateInfo.hazard[s1]) return -1;
        if (!StateInfo.walkThrough[s1] && 16 + StateInfo.anyBottom[s1] < top) return -1;
        if (top > 32) {
            int s2 = get(x, y + 2, z);
            if (!StateInfo.walkThrough[s2] && 32 + StateInfo.anyBottom[s2] < top) return -1;
        }
        return h;
    }

    public boolean passable(int x, int y, int z) { return StateInfo.walkThrough[get(x, y, z)]; }
    /**
     * Climbing: 0 = nothing to climb in this cell; 1 = a ladder; 2 = an open trapdoor that carries on the ladder under
     * it (same facing: the game lets one climb it); 3 = such a trapdoor, closed - to be opened by hand first.
     */
    public int rung(int x, int y, int z) {
        int s = get(x, y, z), k = StateInfo.rung[s];
        if (k <= 1) return k;
        int b = get(x, y - 1, z);
        return StateInfo.rung[b] == 1 && StateInfo.rungFacing[b] == StateInfo.rungFacing[s] ? k : 0;
    }

    /** Can the player be on the ladder in cell (x,y,z), feet at the bottom of the cell, centred: room for the head? */
    public boolean hang(int x, int y, int z) {
        if (rung(x, y, z) == 0) return false;
        int s1 = get(x, y + 1, z);
        return StateInfo.walkThrough[s1] || StateInfo.rung[s1] != 0;
    }

    public boolean isTower(int x, int y, int z) { return StateInfo.scaffolding[get(x, y, z)]; }
    public boolean solidCube(int x, int y, int z) { return StateInfo.fullCube[get(x, y, z)]; }
    public boolean isAir(int x, int y, int z) { return get(x, y, z) == 0; }
}
