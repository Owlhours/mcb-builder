package mcb.bot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import mcb.build.SimplePalette;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The route search and the bot's picture of the site on made-up states ({@link SimplePalette}). */
class NavTest {
    @BeforeAll static void tables() { SimplePalette.install(); }

    static Grid site() {
        int sx = 9, sy = 6, sz = 9;
        return new Grid(0, 1, 0, sx, sy, sz, new char[sx * sy * sz], 2, 2, 2, 2, SimplePalette.STONE);
    }

    static int moves(Nav nav, byte kind) { int n = 0; for (int k = 1; k < nav.length; k++) if (nav.move[k] == kind) n++; return n; }

    @Test void walksOnFlatGround() {
        Grid g = site();
        Nav nav = new Nav(g, new IntOpenHashSet());
        assertTrue(nav.search(1, 1, 1, 7, 1, 7, 10000, (x, y, z) -> false));
        assertEquals(nav.length - 1, moves(nav, Nav.WALK));
        // the route ends at the goal
        int k = (nav.length - 1) * 3;
        assertEquals(7, nav.path[k]); assertEquals(1, nav.path[k + 1]); assertEquals(7, nav.path[k + 2]);
    }

    @Test void jumpsUpSteps() {
        Grid g = site();
        for (int x = 0; x < 9; x++) for (int y = 1; y <= 3; y++) g.set(x, y, 4, SimplePalette.STONE);
        for (int k = 1; k <= 3; k++) for (int y = 1; y <= k; y++) g.set(1 + k, y, 5, SimplePalette.STONE);
        Nav nav = new Nav(g, new IntOpenHashSet());
        nav.towersOn = false;
        assertTrue(nav.search(1, 1, 8, 7, 4, 4, 100000, (x, y, z) -> false));
        assertEquals(3, moves(nav, Nav.JUMP));
        assertEquals(0, moves(nav, Nav.PILLAR) + moves(nav, Nav.BRIDGE));
    }

    @Test void noWayUpWithoutTemporaryBlocks() {
        Grid g = site();
        for (int x = 0; x < 9; x++) for (int y = 1; y <= 3; y++) g.set(x, y, 4, SimplePalette.STONE);
        Nav nav = new Nav(g, new IntOpenHashSet());
        nav.towersOn = false;
        assertFalse(nav.search(1, 1, 8, 7, 4, 4, 100000, (x, y, z) -> false));
        // allowed to put blocks down, it pillars up
        assertTrue(nav.search(1, 1, 8, 7, 4, 4, 100000, (x, y, z) -> true));
        assertTrue(moves(nav, Nav.PILLAR) >= 2);
    }

    @Test void standingHeights() {
        Grid g = site();
        assertEquals(0, g.stand16(3, 1, 3));                 // on the ground
        g.set(3, 1, 3, SimplePalette.SLAB_BOTTOM);
        assertEquals(8, g.stand16(3, 1, 3));                 // on a bottom slab in the feet cell
        g.set(4, 1, 4, SimplePalette.STONE);
        g.set(4, 3, 4, SimplePalette.STONE);
        assertEquals(-1, g.stand16(4, 2, 4));                // on the stone, but no room for the head
        g.set(5, 1, 5, SimplePalette.LADDER_SOUTH); g.set(5, 2, 5, SimplePalette.LADDER_SOUTH);
        assertTrue(g.hang(5, 2, 5));                         // on the ladder
    }
}
