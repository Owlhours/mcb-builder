package mcb.schem;

import static mcb.schem.NbtWriter.c;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The game-free resolver: normal form of states and numbering. */
class PaletteResolverTest {
    @Test
    void numbersInOrderFirstSeen() {
        PaletteResolver r = new PaletteResolver();
        assertEquals(0, r.resolve("minecraft:air", 0));
        assertEquals(1, r.resolve("minecraft:stone", 0));
        assertEquals(2, r.resolve("minecraft:dirt", 0));
        assertEquals(1, r.resolve(c("Name", "minecraft:stone"), 0));
        assertEquals(3, r.states().size());
    }

    @Test
    void normalForm() {
        PaletteResolver r = new PaletteResolver();
        int a = r.resolve("oak_stairs[half=top, facing=north]", 0);
        int b = r.resolve(c("Name", "minecraft:oak_stairs", "Properties", c("facing", "north", "half", "top")), 4903);
        assertEquals(a, b);
        assertEquals("minecraft:oak_stairs[facing=north,half=top]", r.state(a));
        assertEquals("minecraft:stone", r.state(r.resolve("minecraft:stone[]", 0)));
        assertEquals("mymod:block[x=1]", r.state(r.resolve(" mymod:block [x=1]", 0)));
        assertEquals("legacy:35:14", r.state(r.resolveLegacy(35, 14)));
    }

    @Test
    void airAndUnknown() {
        PaletteResolver r = new PaletteResolver();
        assertEquals(0, r.resolve("air", 0));
        assertEquals(0, r.resolve(c("Name", "minecraft:air"), 0));
        assertEquals(1, r.resolve("minecraft:cave_air", 0));   // only plain air is air
        assertEquals(-1, r.resolve(c("Properties", c("a", "b")), 0));
        assertEquals(-1, r.resolve("", 0));
        assertEquals(0, r.resolveLegacy(0, 0));
    }
}
