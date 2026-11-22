package mcb.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Packed positions: the game's bit layout (26 bits x, 12 bits y, 26 bits z), and back. */
class PosTest {
    @Test void layout() {
        assertEquals(0L, Pos.pack(0, 0, 0));
        assertEquals(1L, Pos.pack(0, 1, 0));
        assertEquals(1L << 12, Pos.pack(0, 0, 1));
        assertEquals(1L << 38, Pos.pack(1, 0, 0));
        assertEquals(-1L, Pos.pack(-1, -1, -1));
    }

    @Test void roundTrip() {
        java.util.Random r = new java.util.Random(1);
        for (int i = 0; i < 100000; i++) {
            int x = r.nextInt(60000000) - 30000000, y = r.nextInt(4096) - 2048, z = r.nextInt(60000000) - 30000000;
            long p = Pos.pack(x, y, z);
            assertEquals(x, Pos.x(p)); assertEquals(y, Pos.y(p)); assertEquals(z, Pos.z(p));
        }
    }

    @Test void faces() {
        for (int f = 2; f < 6; f++) {
            assertEquals(f, Hit.counterClockwise(Hit.clockwise(f)));
            assertEquals(0, Hit.DX[f] + Hit.DX[Hit.clockwise(Hit.clockwise(f))]);   // half a turn points back
        }
        assertEquals(Hit.EAST, Hit.clockwise(Hit.NORTH));
    }
}
