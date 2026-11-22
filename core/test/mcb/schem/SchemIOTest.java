package mcb.schem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The four readers on the generated samples ({@link Samples}), with the game-free {@link PaletteResolver}: sizes,
 * cropping, data versions, unknown blocks, the block in each cell, and the files that must be refused.
 */
class SchemIOTest {
    @TempDir static Path dir;

    @BeforeAll
    static void writeSamples() throws Exception {
        for (Samples.Sample s : Samples.all()) s.writeTo(dir);
    }

    /** A read schematic and the resolver that numbered it. */
    record Read(Schem s, PaletteResolver states) {
        String at(int x, int y, int z) { return states.state(s.get(x, y, z)); }
        /** The cells as rows "y: z0 | z1 ...", each cell a short block name ("." for air). */
        String picture() {
            StringBuilder sb = new StringBuilder();
            for (int y = 0; y < s.sy; y++) {
                if (y > 0) sb.append(" / ");
                for (int z = 0; z < s.sz; z++) {
                    if (z > 0) sb.append(" | ");
                    for (int x = 0; x < s.sx; x++) {
                        if (x > 0) sb.append(' ');
                        String st = states.state(s.get(x, y, z));
                        sb.append(s.get(x, y, z) == 0 ? "." : st.replaceFirst("^minecraft:", "").replaceFirst("\\[.*", "[]"));
                    }
                }
            }
            return sb.toString();
        }
    }

    static Read read(String name) throws IOException {
        PaletteResolver r = new PaletteResolver();
        return new Read(new SchemIO(r).read(dir.resolve(name)), r);
    }

    static void assertShape(Read r, String format, int dataVersion, int sx, int sy, int sz, long unknown) {
        assertEquals(format, r.s.format, "format");
        assertEquals(dataVersion, r.s.dataVersion, "data version");
        assertEquals(sx + "x" + sy + "x" + sz, r.s.sx + "x" + r.s.sy + "x" + r.s.sz, "size");
        assertEquals(unknown, r.s.unknownCells, "unknown cells");
    }

    // ---------------------------------------------------------------- Litematica

    @Test
    void litematicBasic() throws IOException {
        Read r = read("lit-basic.litematic");
        assertShape(r, "litematic", 4903, 3, 2, 2, 0);
        assertEquals("stone stone stone | . oak_stairs[] . / . . stone | oak_stairs[] oak_stairs[] oak_stairs[]", r.picture());
        assertEquals("minecraft:oak_stairs[facing=north,half=top,shape=straight,waterlogged=false]", r.at(1, 0, 1));
    }

    @Test
    void litematicRegionsNegativeSizesAndWidePalette() throws IOException {
        Read r = read("lit-regions.litematic");
        // region a: x 0..1, y 0, z 0..1; region b: x 7..10, y 3..4, z 0..2; the empty region is ignored
        assertShape(r, "litematic", 3955, 11, 5, 3, 4);
        assertEquals("minecraft:stone", r.at(0, 0, 0));
        assertEquals("minecraft:stone", r.at(1, 0, 1));
        assertEquals("minecraft:air", r.at(0, 0, 1));
        // region b, first cell (k = 0, value 0) is air; k = 1 is palette entry 5, k = 2 entry 10
        assertEquals("minecraft:air", r.at(7, 3, 0));
        assertEquals("minecraft:yellow_wool", r.at(8, 3, 0));
        assertEquals("minecraft:cyan_wool", r.at(9, 3, 0));
        // k = 23 is entry (23 * 5) % 21 = 10 -> cyan; k = 20 -> 16, the oak log
        assertEquals("minecraft:cyan_wool", r.at(10, 4, 2));
        assertEquals("minecraft:oak_log[axis=z]", r.at(7, 4, 2));
        assertTrue(r.states.states().contains("mymod:widget[power=3]"));
        for (int y = 1; y < 3; y++) for (int z = 0; z < 3; z++) for (int x = 0; x < 11; x++) assertEquals(0, r.s.get(x, y, z));
    }

    @Test
    void litematicOverlappingRegionsAreReadInKeyOrder() throws IOException {
        Read r = read("lit-overlap.litematic");
        assertShape(r, "litematic", 4903, 2, 2, 2, 0);
        // The regions are read in the order the compound's keys iterate (HashMap order); a later region's block
        // replaces an earlier one, its air does not. Work out the expected result the same way, independently.
        String[] names = {"zeta", "alpha", "Mid", "b2", "the roof"};
        String[] blocks = {"minecraft:stone", "minecraft:dirt", "minecraft:glass", "minecraft:bricks", "minecraft:sand"};
        java.util.HashMap<String, Integer> order = new java.util.HashMap<>();
        for (int i = 0; i < names.length; i++) order.put(names[i], i);
        String[] expect = new String[8];
        for (String n : order.keySet()) {
            int ri = order.get(n);
            for (int k = 0; k < 8; k++) if ((k + ri) % 3 != 0) expect[k] = blocks[ri];
        }
        for (int k = 0; k < 8; k++) {
            int x = k % 2, z = (k / 2) % 2, y = k / 4;
            assertEquals(expect[k] == null ? "minecraft:air" : expect[k], r.at(x, y, z), "cell " + k);
        }
    }

    @Test
    void litematicDataVersions() throws IOException {
        Read old = read("lit-old.litematic");
        assertShape(old, "litematic", 2586, 2, 2, 1, 0);
        assertEquals("minecraft:grass_path", old.at(0, 0, 0));   // not upgraded: this resolver does no upgrading
        assertEquals(1343, read("lit-no-version.litematic").s.dataVersion);   // missing: assumed 1.12.2
    }

    @Test
    void litematicOddPaletteEntries() throws IOException {
        Read r = read("lit-odd-palette.litematic");
        assertShape(r, "litematic", 1952, 3, 3, 1, 2);   // the entries without a usable name are unknown
        assertEquals("minecraft:oak_stairs[half=top]", r.at(0, 0, 0));   // a numeric property value is ignored
        assertEquals("minecraft:stone", r.at(1, 0, 0));
        assertEquals("minecraft:stone_bricks", r.at(1, 1, 0));
        assertEquals("Minecraft:Stone", r.at(2, 1, 0));
        assertEquals("minecraft:oak_log[axis=q]", r.at(0, 2, 0));
    }

    // ---------------------------------------------------------------- Sponge

    @Test
    void spongeVersions() throws IOException {
        Read v1 = read("sponge-v1.schem");
        assertShape(v1, "sponge", 1519, 3, 2, 2, 0);   // no DataVersion: the first version that wrote Sponge files
        assertEquals("stone oak_log[] oak_planks | . stone stone / . . oak_log[] | oak_planks oak_planks stone", v1.picture());

        Read v2 = read("sponge-v2.schem");
        assertShape(v2, "sponge", 3465, 5, 4, 6, 9);   // index 150 is not in the palette: 9 cells
        assertTrue(v2.states.states().contains("create:shaft[axis=y]"));

        Read v3 = read("sponge-v3.schem");
        assertShape(v3, "sponge", 4903, 2, 2, 2, 0);
        assertEquals("minecraft:lantern[hanging=true,waterlogged=false]", v3.at(1, 0, 1));   // spaces trimmed
        assertEquals("minecraft:weird", v3.at(0, 1, 0));                                    // no closing bracket
        assertEquals(v3.s.contentHash(), read("sponge-v3-unwrapped.schem").s.contentHash());
    }

    @Test
    void spongeVarintsAboveOneByte() throws IOException {
        Read r = read("sponge-v2.schem");
        // cell k holds palette index (k * 37) % 199 unless k % 11 == 0 (air) or k % 13 == 0 (index 150, unknown);
        // check that every cell with a two-byte index landed where it belongs
        int checked = 0;
        for (int k = 0; k < 120; k++) {
            if (k % 11 == 0 || k % 13 == 0 || (k * 37) % 199 < 128) continue;
            int x = k % 5, z = (k / 5) % 6, y = k / 30;
            assertTrue(r.at(x, y, z).contains("_stairs") || r.at(x, y, z).equals("minecraft:stone") || r.at(x, y, z).startsWith("create:"), "cell " + k);
            checked++;
        }
        assertTrue(checked > 10);
    }

    @Test
    void spongeDuplicatePaletteIndex() throws IOException {
        Read r = read("sponge-duplicate-index.schem");
        assertShape(r, "sponge", 4903, 3, 1, 1, 0);
        // stone and dirt share index 1, sand and gravel index 2: the name that comes last in key order wins
        Nbt.CompoundTag pal = NbtWriter.c("minecraft:stone", 1, "minecraft:dirt", 1, "minecraft:air", 0, "minecraft:sand", 2, "minecraft:gravel", 2);
        String one = null, two = null;
        for (String k : pal.keySet()) { if (pal.getIntOr(k, 0) == 1) one = k; if (pal.getIntOr(k, 0) == 2) two = k; }
        assertEquals(one, r.at(0, 0, 0));
        assertEquals(two, r.at(1, 0, 0));
    }

    // ---------------------------------------------------------------- structure, MCEdit

    @Test
    void structure() throws IOException {
        Read r = read("structure.nbt");
        // structure_void and air are air; the cropped box is x 0..1, y 0..2, z 0..1
        assertShape(r, "structure", 3700, 2, 3, 2, 2);
        assertEquals("stone oak_stairs[] | . . / . . | . mymod:thing / . . | stone .", r.picture());

        Read p = read("structure-palettes.nbt");
        assertShape(p, "structure", 500, 2, 1, 2, 0);   // size [2.9, 1.0, 2.5] reads as 2 x 1 x 2
        assertEquals("stone cobblestone | . cobblestone", p.picture());

        Read m = read("structure-mixed-list.nbt");
        assertShape(m, "structure", 4903, 3, 1, 1, 1);   // the string palette entry is not a block state
        assertEquals("stone . glass", m.picture());
    }

    @Test
    void legacy() throws IOException {
        Read r = read("legacy.schematic");
        assertShape(r, "legacy", 1343, 3, 2, 2, 0);
        assertEquals("legacy:1:0", r.at(0, 0, 0));
        assertEquals("legacy:35:14", r.at(1, 0, 0));
        assertEquals("legacy:456:0", r.at(2, 1, 1));   // 200 plus 1 << 8 from AddBlocks
        Read nd = read("legacy-no-data.schematic");
        assertEquals("legacy:35:0", nd.at(1, 0, 0));   // no Data array: data 0 everywhere
    }

    // ---------------------------------------------------------------- refused files

    @Test
    void refusedFiles() {
        List<String> refused = List.of("lit-all-air.litematic", "lit-no-regions.litematic", "lit-zero-size.litematic",
                "lit-truncated.litematic", "lit-no-blockstates.litematic", "sponge-truncated.schem", "sponge-no-data.schem",
                "sponge-zero-width.schem", "sponge-too-large.schem", "legacy-truncated.schematic", "unknown-layout.nbt",
                "not-gzip.schem", "root-not-compound.nbt", "cut-short-gzip.schem", "cut-short-nbt.schem");
        for (String name : refused) assertThrows(IOException.class, () -> read(name), name);
        // a negative palette index is refused too, but with an unchecked exception (as before the rewrite)
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> read("sponge-negative-index.schem"));
    }

    @Test
    void everySampleIsReadOrRefusedAsListed() throws Exception {
        // guards the lists above: a new sample must be added to one of the tests
        try (var files = Files.list(dir)) {
            for (Path f : files.toList()) {
                String n = f.getFileName().toString();
                try { read(n); }
                catch (Exception e) {
                    if (!n.matches("lit-(all-air|no-regions|zero-size|truncated|no-blockstates).*|sponge-(truncated|no-data|zero-width|too-large|negative-index).*|legacy-truncated.*|unknown-layout.*|not-gzip.*|root-not-compound.*|cut-short.*")) {
                        fail(n + " was refused: " + e);
                    }
                }
            }
        }
    }

    @Test
    void sharedResolverNumbersAcrossFiles() throws IOException {
        PaletteResolver r = new PaletteResolver();
        SchemIO io = new SchemIO(r);
        Schem a = io.read(dir.resolve("lit-basic.litematic"));
        Schem b = io.read(dir.resolve("sponge-v1.schem"));
        assertEquals(a.get(0, 0, 0), b.get(0, 0, 0));   // both minecraft:stone, one number
        assertEquals("minecraft:stone", r.state(a.get(0, 0, 0)));
    }

    @Test
    void normalFormRoundTrip() throws IOException {
        Schem s = read("lit-regions.litematic").s;
        Path f = dir.resolve("out/regions.mcb");
        s.save(f);
        Schem back = Schem.load(f);
        assertEquals(s.contentHash(), back.contentHash());
        assertEquals(s.format, back.format);
        assertEquals(s.dataVersion, back.dataVersion);
        assertEquals(s.unknownCells, back.unknownCells);
    }
}
