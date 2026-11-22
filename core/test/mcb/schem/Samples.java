package mcb.schem;

import static mcb.schem.NbtWriter.c;
import static mcb.schem.NbtWriter.i;
import static mcb.schem.NbtWriter.list;
import static mcb.schem.NbtWriter.s;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Small schematic files of every supported format, made in code for the tests (no schematic file is kept in the
 * repository). Each sample is a file name and its bytes. Some are deliberately broken, to check that they are
 * refused.
 *
 * <p>{@code java mcb.schem.Samples <dir>} writes them all into a directory, for trying them with other tools.
 */
final class Samples {
    private Samples() { }

    record Sample(String name, byte[] bytes) {
        Path writeTo(Path dir) throws Exception { return Files.write(dir.resolve(name), bytes); }
    }

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args[0]);
        Files.createDirectories(dir);
        for (Sample s : all()) s.writeTo(dir);
        System.out.println(all().size() + " samples written to " + dir);
    }

    static List<Sample> all() {
        List<Sample> out = new ArrayList<>();
        // ---- Litematica
        out.add(new Sample("lit-basic.litematic", litBasic()));
        out.add(new Sample("lit-regions.litematic", litRegions()));
        out.add(new Sample("lit-overlap.litematic", litOverlap()));
        out.add(new Sample("lit-old.litematic", litOld()));
        out.add(new Sample("lit-odd-palette.litematic", litOddPalette()));
        out.add(new Sample("lit-no-version.litematic", litematic(-1, region("r", 0, 0, 0, 2, 1, 1,
                list(c("Name", "minecraft:air"), c("Name", "minecraft:stone")), 1, 1))));
        out.add(new Sample("lit-all-air.litematic", litematic(4903, region("r", 0, 0, 0, 2, 2, 2,
                list(c("Name", "minecraft:air")), 0, 0, 0, 0, 0, 0, 0, 0))));
        out.add(new Sample("lit-no-regions.litematic", NbtWriter.gzip(c("MinecraftDataVersion", 4903, "Regions", c()))));
        out.add(new Sample("lit-zero-size.litematic", litematic(4903, region("r", 0, 0, 0, 0, 3, 3, list(c("Name", "minecraft:stone"))))));
        Nbt.CompoundTag truncated = region("r", 0, 0, 0, 8, 8, 8, list(c("Name", "minecraft:air"), c("Name", "minecraft:stone")), new int[512]);
        truncated.getCompoundOrEmpty("r").put("BlockStates", new Nbt.LongArrayTag(new long[3]));
        out.add(new Sample("lit-truncated.litematic", litematic(4903, truncated)));
        Nbt.CompoundTag noStates = region("r", 0, 0, 0, 1, 1, 1, list(c("Name", "minecraft:stone")), 0);
        noStates.getCompoundOrEmpty("r").put("BlockStates", new Nbt.StringTag("none"));
        out.add(new Sample("lit-no-blockstates.litematic", litematic(4903, noStates)));
        // ---- Sponge
        out.add(new Sample("sponge-v1.schem", spongeV1()));
        out.add(new Sample("sponge-v2.schem", spongeV2()));
        out.add(new Sample("sponge-v3.schem", spongeV3(true)));
        out.add(new Sample("sponge-v3-unwrapped.schem", spongeV3(false)));
        out.add(new Sample("sponge-duplicate-index.schem", spongeDuplicate()));
        out.add(new Sample("sponge-truncated.schem", NbtWriter.gzip(c("Version", 2, "DataVersion", 3465, "Width", s(4), "Height", s(1), "Length", s(1),
                "Palette", c("minecraft:stone", 0), "BlockData", new byte[] {0, 0}))));
        out.add(new Sample("sponge-no-data.schem", NbtWriter.gzip(c("Version", 2, "Width", s(1), "Height", s(1), "Length", s(1), "Palette", c("minecraft:stone", 0)))));
        out.add(new Sample("sponge-zero-width.schem", NbtWriter.gzip(c("Version", 2, "Width", s(0), "Height", s(1), "Length", s(1),
                "Palette", c("minecraft:stone", 0), "BlockData", new byte[0]))));
        out.add(new Sample("sponge-too-large.schem", NbtWriter.gzip(c("Version", 2, "Width", s(1000), "Height", s(1000), "Length", s(1000),
                "Palette", c("minecraft:stone", 0), "BlockData", new byte[0]))));
        out.add(new Sample("sponge-negative-index.schem", NbtWriter.gzip(c("Version", 2, "Width", s(1), "Height", s(1), "Length", s(1),
                "Palette", c("minecraft:stone", -1), "BlockData", new byte[] {0}))));
        // ---- structure .nbt
        out.add(new Sample("structure.nbt", structure()));
        out.add(new Sample("structure-palettes.nbt", structurePalettes()));
        out.add(new Sample("structure-mixed-list.nbt", structureMixed()));
        // ---- MCEdit .schematic
        out.add(new Sample("legacy.schematic", legacy(true)));
        out.add(new Sample("legacy-no-data.schematic", legacy(false)));
        out.add(new Sample("legacy-truncated.schematic", NbtWriter.gzip(c("Width", s(2), "Height", s(2), "Length", s(2), "Materials", "Alpha",
                "Blocks", new byte[] {1, 1, 1}, "Data", new byte[8]))));
        // ---- not schematics
        out.add(new Sample("unknown-layout.nbt", NbtWriter.gzip(c("foo", 1, "Width", s(3)))));
        out.add(new Sample("not-gzip.schem", "this is not a schematic".getBytes()));
        out.add(new Sample("root-not-compound.nbt", NbtWriter.gzipRaw(new byte[] {8, 0, 0, 0, 2, 'h', 'i'})));
        out.add(new Sample("cut-short-gzip.schem", java.util.Arrays.copyOf(NbtWriter.gzipRaw(rawPrefix()), 30)));
        out.add(new Sample("cut-short-nbt.schem", NbtWriter.gzipRaw(java.util.Arrays.copyOf(rawPrefix(), 40))));
        return out;
    }

    // ------------------------------------------------------------------------------------------------ Litematica

    /** 3 x 2 x 2, three palette entries. */
    static byte[] litBasic() {
        Nbt.ListTag pal = list(c("Name", "minecraft:air"), c("Name", "minecraft:stone"),
                c("Name", "minecraft:oak_stairs", "Properties", c("half", "top", "facing", "north", "shape", "straight", "waterlogged", "false")));
        // order: y, z, x
        int[] cells = {1, 1, 1, 0, 2, 0,
                       0, 0, 1, 2, 2, 2};
        return litematic(4903, region("Unnamed", 0, 0, 0, 3, 2, 2, pal, cells));
    }

    /**
     * Two regions: one with negative sizes (the box extends from its position toward smaller coordinates), a
     * 17-entry palette (5 bits per cell, so values cross 64-bit boundaries), a mod block, and cells whose palette
     * index is past the end of the palette; plus an empty region and a region that leaves a gap.
     */
    static byte[] litRegions() {
        List<Nbt.Tag> big = new ArrayList<>();
        big.add(c("Name", "minecraft:air"));
        String[] colours = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green"};
        for (String col : colours) big.add(c("Name", "minecraft:" + col + "_wool"));
        big.add(c("Name", "mymod:widget", "Properties", c("power", "3")));
        big.add(c("Name", "minecraft:oak_log", "Properties", c("axis", "z")));
        Nbt.ListTag pal = new Nbt.ListTag();
        for (Nbt.Tag t : big) pal.add(t);
        // region b: position (10, 4, 2), size (-4, -2, -3): x 7..10, y 3..4, z 0..2; 24 cells
        int[] cells = new int[24];
        for (int k = 0; k < cells.length; k++) cells[k] = k % 7 == 3 ? 0 : (k * 5) % 21;   // values 0..20; 17..20 are past the palette
        Nbt.CompoundTag regions = region("b", 10, 4, 2, -4, -2, -3, pal, cells);
        // region a: position (0, 0, 0), size (2, 1, 2), stone
        regions.put("a", region("a", 0, 0, 0, 2, 1, 2, list(c("Name", "minecraft:air"), c("Name", "minecraft:stone")), 1, 1, 0, 1).get("a"));
        regions.put("empty", region("empty", 50, 50, 50, 3, 0, 3, list(c("Name", "minecraft:stone"))).get("empty"));
        return litematic(3955, regions);
    }

    /**
     * Five overlapping regions over the same 2 x 2 x 2 box. Where several regions have a block, the one read
     * last wins, so this checks that regions are read in the same order as before.
     */
    static byte[] litOverlap() {
        Nbt.CompoundTag regions = new Nbt.CompoundTag();
        String[] names = {"zeta", "alpha", "Mid", "b2", "the roof"};
        String[] blocks = {"minecraft:stone", "minecraft:dirt", "minecraft:glass", "minecraft:bricks", "minecraft:sand"};
        for (int r = 0; r < names.length; r++) {
            int[] cells = new int[8];
            for (int k = 0; k < 8; k++) cells[k] = (k + r) % 3 == 0 ? 0 : 1;
            regions.put(names[r], region(names[r], 0, 0, 0, 2, 2, 2, list(c("Name", "minecraft:air"), c("Name", blocks[r])), cells).get(names[r]));
        }
        return litematic(4903, regions);
    }

    /** Data version 2586 (1.16.5): grass_path was renamed dirt_path later; the game's resolver upgrades it. */
    static byte[] litOld() {
        Nbt.ListTag pal = list(c("Name", "minecraft:air"), c("Name", "minecraft:grass_path"), c("Name", "minecraft:stone"),
                c("Name", "minecraft:oak_sign", "Properties", c("rotation", "4", "waterlogged", "false")));
        return litematic(2586, region("old", -3, 60, 7, 2, 2, 1, pal, 1, 2, 3, 1));
    }

    /**
     * Palette entries that are not quite right: a property stored as a number, an invalid property value, extra
     * keys, a name that is not a string, no name, no namespace, upper case. Each should resolve the same way every
     * time, whatever the resolver makes of it.
     */
    static byte[] litOddPalette() {
        Nbt.ListTag pal = list(c("Name", "minecraft:air"),
                c("Name", "minecraft:oak_stairs", "Properties", c("facing", 3, "half", "top")),
                c("Name", "minecraft:stone", "Extra", list(1, "x"), "Note", c("a", s(2))),
                c("Name", 5),
                c("Properties", c("axis", "y")),
                c("Name", "stone_bricks"),
                c("Name", "Minecraft:Stone"),
                c("Name", "minecraft:oak_log", "Properties", c("axis", "q")),
                c("Name", "minecraft:lever", "Properties", c("face", "wall", "facing", "north", "powered", "false")));
        return litematic(1952, region("odd", 0, 0, 0, 3, 3, 1, pal, 1, 2, 3, 4, 5, 6, 7, 8, 1));
    }

    static byte[] litematic(int dataVersion, Nbt.CompoundTag regions) {
        Nbt.CompoundTag root = c("Version", 6, "Metadata", c("Name", "test", "Author", "tests"), "Regions", regions);
        if (dataVersion >= 0) root.put("MinecraftDataVersion", i(dataVersion));
        return NbtWriter.gzip(root);
    }

    /** A Regions compound with one region; cells are palette indices in y, z, x order. */
    static Nbt.CompoundTag region(String name, int px, int py, int pz, int sx, int sy, int sz, Nbt.ListTag palette, int... cells) {
        int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(Math.max(1, palette.size() - 1)));
        Nbt.CompoundTag r = c("Position", c("x", px, "y", py, "z", pz), "Size", c("x", sx, "y", sy, "z", sz),
                "BlockStatePalette", palette, "BlockStates", pack(cells, bits),
                "Entities", list(), "TileEntities", list(), "PendingBlockTicks", list());
        return c(name, r);
    }

    /** Litematica's bit packing: value k occupies bits k*bits .. k*bits+bits-1, possibly across two longs. */
    static long[] pack(int[] values, int bits) {
        long[] out = new long[(int) (((long) values.length * bits + 63) / 64)];
        for (int k = 0; k < values.length; k++) {
            long bit = (long) k * bits;
            int li = (int) (bit >>> 6), off = (int) (bit & 63);
            long v = values[k] & ((1L << bits) - 1);
            out[li] |= v << off;
            if (off + bits > 64) out[li + 1] |= v >>> (64 - off);
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------------ Sponge

    static byte[] spongeV1() {
        Map<String, Integer> pal = new LinkedHashMap<>();
        pal.put("minecraft:air", 0);
        pal.put("minecraft:stone", 1);
        pal.put("minecraft:oak_log[axis=x]", 2);
        pal.put("oak_planks", 3);   // no namespace
        int[] cells = {1, 2, 3, 0, 1, 1, 0, 0, 2, 3, 3, 1};   // 3 x 2 x 2, order y, z, x
        return NbtWriter.gzip(c("Version", 1, "Width", s(3), "Height", s(2), "Length", s(2), "Offset", new int[] {0, 0, 0},
                "Palette", palette(pal), "PaletteMax", pal.size(), "BlockData", varints(cells)));
    }

    /** 200 palette entries, so indices above 127 take two bytes; index 150 is missing from the palette. */
    static byte[] spongeV2() {
        Map<String, Integer> pal = new LinkedHashMap<>();
        pal.put("minecraft:air", 0);
        String[] woods = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "crimson", "warped"};
        String[] facings = {"north", "south", "east", "west"};
        String[] halves = {"top", "bottom"};
        String[] shapes = {"straight", "inner_left", "outer_right"};
        int n = 1;
        outer:
        for (String w : woods) for (String f : facings) for (String h : halves) for (String sh : shapes) {
            if (n == 150) n++;
            pal.put("minecraft:" + w + "_stairs[facing=" + f + ",half=" + h + ",shape=" + sh + ",waterlogged=false]", n++);
            if (n >= 198) break outer;
        }
        pal.put("create:shaft[axis=y]", n++);
        pal.put("minecraft:stone", n);
        int[] cells = new int[5 * 4 * 6];
        for (int k = 0; k < cells.length; k++) cells[k] = k % 11 == 0 ? 0 : k % 13 == 0 ? 150 : (k * 37) % (n + 1);
        return NbtWriter.gzip(c("Version", 2, "DataVersion", 3465, "Width", s(5), "Height", s(4), "Length", s(6),
                "Palette", palette(pal), "PaletteMax", n + 1, "BlockData", varints(cells), "BlockEntities", list()));
    }

    /** Version 3: everything inside "Schematic", blocks in "Blocks" {Palette, Data}; Width stored as an int. */
    static byte[] spongeV3(boolean wrapped) {
        Map<String, Integer> pal = new LinkedHashMap<>();
        pal.put("minecraft:air", 0);
        pal.put("minecraft:glass", 1);
        pal.put("minecraft:oak_slab[type=top,waterlogged=false]", 2);
        pal.put("minecraft:lantern[hanging=true, waterlogged=false]", 3);   // a space after the comma
        pal.put("minecraft:weird[", 4);                                     // no closing bracket
        int[] cells = {0, 1, 2, 3, 4, 1, 1, 0};
        Nbt.CompoundTag body = c("Version", 3, "DataVersion", 4903, "Width", i(2), "Height", s(2), "Length", s(2),
                "Blocks", c("Palette", palette(pal), "Data", varints(cells), "BlockEntities", list()));
        return NbtWriter.gzip(wrapped ? c("Schematic", body) : body);
    }

    /** Two palette names share index 1; the one read last wins. */
    static byte[] spongeDuplicate() {
        Nbt.CompoundTag pal = c("minecraft:stone", 1, "minecraft:dirt", 1, "minecraft:air", 0, "minecraft:sand", 2, "minecraft:gravel", 2);
        return NbtWriter.gzip(c("Version", 2, "DataVersion", 4903, "Width", s(3), "Height", s(1), "Length", s(1),
                "Palette", pal, "BlockData", varints(new int[] {1, 2, 1})));
    }

    static Nbt.CompoundTag palette(Map<String, Integer> pal) {
        Nbt.CompoundTag t = new Nbt.CompoundTag();
        pal.forEach((k, v) -> t.put(k, i(v)));
        return t;
    }

    static byte[] varints(int[] values) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int v : values) {
            while ((v & ~0x7f) != 0) { out.write((v & 0x7f) | 0x80); v >>>= 7; }
            out.write(v);
        }
        return out.toByteArray();
    }

    // ------------------------------------------------------------------------------------------------ structure

    static byte[] structure() {
        Nbt.ListTag pal = list(c("Name", "minecraft:stone"),
                c("Name", "minecraft:oak_stairs", "Properties", c("facing", "east", "half", "bottom", "shape", "straight", "waterlogged", "false")),
                c("Name", "minecraft:structure_void"),
                c("Name", "mymod:thing"),
                c("Name", "minecraft:air"));
        Nbt.ListTag blocks = list(
                c("pos", list(0, 0, 0), "state", 0),
                c("pos", list(1, 0, 0), "state", 1),
                c("pos", list(2, 2, 2), "state", 2),
                c("pos", list(1, 1, 1), "state", 3),
                c("pos", list(0, 2, 1), "state", 0, "nbt", c("id", "minecraft:chest")),
                c("pos", list(2, 1, 0)),                    // no state: unknown
                c("pos", list(2, 0, 2), "state", 9),        // index past the palette: unknown
                c("pos", list(3, 0, 0), "state", 0),        // outside the box: skipped
                c("pos", list(0, -1, 0), "state", 0),       // outside the box: skipped
                c("pos", list(0, 1), "state", 0),           // only two coordinates: skipped
                c("pos", list(2, 2, 0), "state", 4));
        return NbtWriter.gzip(c("DataVersion", 3700, "size", list(3, 3, 3), "palette", pal, "blocks", blocks, "entities", list()));
    }

    /** "palettes" (random variants; the first is used), no DataVersion, and the size stored as doubles. */
    static byte[] structurePalettes() {
        Nbt.ListTag p0 = list(c("Name", "minecraft:stone"), c("Name", "minecraft:cobblestone"));
        Nbt.ListTag p1 = list(c("Name", "minecraft:dirt"), c("Name", "minecraft:sand"));
        Nbt.ListTag blocks = list(c("pos", list(0, 0, 0), "state", 0), c("pos", list(1, 0, 0), "state", 1), c("pos", list(1, 0, 1), "state", 1));
        return NbtWriter.gzip(c("size", list(2.9, 1.0, 2.5), "palettes", list(p0, p1), "blocks", blocks));
    }

    /**
     * A palette list with a string among the compounds: written as a mixed list (the string wrapped as
     * {"": value}); read back, that entry is not a compound, so it counts as an unknown block.
     */
    static byte[] structureMixed() {
        Nbt.ListTag pal = list(c("Name", "minecraft:stone"), "minecraft:dirt", c("Name", "minecraft:glass"));
        Nbt.ListTag blocks = list(c("pos", list(0, 0, 0), "state", 0), c("pos", list(1, 0, 0), "state", 1), c("pos", list(2, 0, 0), "state", 2));
        return NbtWriter.gzip(c("DataVersion", 4903, "size", list(3, 1, 1), "palette", pal, "blocks", blocks));
    }

    // ------------------------------------------------------------------------------------------------ MCEdit

    /** 3 x 2 x 2 with numeric ids: stone, red wool, oak stairs, a log, and an id above 255 made with AddBlocks. */
    static byte[] legacy(boolean withData) {
        byte[] blocks = {1, 35, 53, 0, 17, 1, 0, 0, 98, 5, 1, (byte) 200};
        byte[] data = {0, 14, 2, 0, 4, 3, 0, 0, 1, 2, 0, 0};
        byte[] add = new byte[6];
        add[5] = 0x10;   // cell 11 (odd: high nibble): id 200 + (1 << 8) = 456, which no version had
        Nbt.CompoundTag root = c("Width", s(3), "Height", s(2), "Length", s(2), "Materials", "Alpha", "Blocks", blocks,
                "AddBlocks", add, "Entities", list(), "TileEntities", list());
        if (withData) root.put("Data", new Nbt.ByteArrayTag(data));
        return NbtWriter.gzip(root);
    }

    /** The start of a valid file, for cutting short. */
    static byte[] rawPrefix() {
        byte[] full = NbtWriter.gzip(c("Version", 2, "Width", s(1), "Height", s(1), "Length", s(1), "Palette", c("minecraft:stone", 0), "BlockData", new byte[] {0}));
        try (var in = new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(full))) {
            return in.readAllBytes();
        } catch (java.io.IOException e) { throw new RuntimeException(e); }
    }
}
