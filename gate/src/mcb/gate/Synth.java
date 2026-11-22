package mcb.gate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

/**
 * The regression gate's test builds, generated in code: small structures that between them exercise what the bot
 * does (walls, doors, stairs and slabs in every orientation, overhangs and bridges that need scaffolding, towers,
 * ladders, trapdoors, gravity blocks, cobwebs, a magma floor, an enclosed room, iron doors, beds, chests, plants,
 * waterlogged blocks ...). They are this project's own work, so the gate can run anywhere, in CI included, without
 * the corpus of third-party schematics the bot was developed on.
 *
 * Every build is a box of block-state strings in the game's own syntax ({@code "oak_stairs[facing=north,half=top]"},
 * {@code null} = air). Nothing here depends on a Minecraft version; the adapter turns the strings into state ids.
 * Random content uses a fixed seed per build, so the builds never change unless this file does. Each build's
 * {@link Build#digest()} is recorded in the expected results, so a change here is caught, too.
 *
 * To add a build: append it to {@link #all()}. Never change an existing one; the expected rows would no longer mean
 * the same thing. (Adding one means re-recording the expected rows; see gate/README.md.)
 */
public final class Synth {
    private Synth() { }

    /** One test build: its name, size and cells (index (y * sz + z) * sx + x, null = air). */
    public static final class Build {
        public final String name;
        public final int sx, sy, sz;
        public final String[] cells;
        Build(String name, int sx, int sy, int sz) {
            this.name = name; this.sx = sx; this.sy = sy; this.sz = sz;
            this.cells = new String[sx * sy * sz];
        }
        public String get(int x, int y, int z) { return cells[(y * sz + z) * sx + x]; }
        void set(int x, int y, int z, String s) {
            if (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz) return;
            cells[(y * sz + z) * sx + x] = s;
        }
        void fill(int x0, int y0, int z0, int x1, int y1, int z1, String s) {
            for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) for (int x = x0; x <= x1; x++) set(x, y, z, s);
        }
        /** The four walls of a box (no floor, no roof). */
        void walls(int x0, int y0, int z0, int x1, int y1, int z1, String s) {
            for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) for (int x = x0; x <= x1; x++)
                if (x == x0 || x == x1 || z == z0 || z == z1) set(x, y, z, s);
        }
        public long blocks() { long n = 0; for (String c : cells) if (c != null) n++; return n; }
        /** A short fingerprint of the content (FNV-1a over size and cells), recorded with the expected results. */
        public String digest() {
            long h = 0xcbf29ce484222325L;
            String head = sx + "x" + sy + "x" + sz + ";";
            for (int i = 0; i < head.length(); i++) { h ^= head.charAt(i); h *= 0x100000001b3L; }
            for (String c : cells) {
                String s = c == null ? "." : c;
                for (int i = 0; i < s.length(); i++) { h ^= s.charAt(i); h *= 0x100000001b3L; }
                h ^= '|'; h *= 0x100000001b3L;
            }
            return String.format("%016x", h).substring(0, 12);
        }
    }

    private static final Map<String, Build> BUILDS = new LinkedHashMap<>();

    private static void def(String name, int sx, int sy, int sz, Consumer<Build> body) {
        Build b = new Build(name, sx, sy, sz);
        body.accept(b);
        BUILDS.put(name, b);
    }

    /** Every test build, in a fixed order. */
    public static List<Build> all() { return new ArrayList<>(BUILDS.values()); }

    /** The build of that name, or null. */
    public static Build get(String name) { return BUILDS.get(name); }

    static final String[] FACINGS = {"north", "east", "south", "west"};

    static {
        // a one-room hut: cobblestone walls, an oak door, glass windows, plank floor, a slab roof, a torch inside
        def("hut", 7, 5, 7, b -> {
            b.fill(0, 0, 0, 6, 0, 6, "oak_planks");
            b.walls(0, 1, 0, 6, 3, 6, "cobblestone");
            b.set(3, 1, 0, "oak_door[facing=south,half=lower,hinge=left,open=false]");
            b.set(3, 2, 0, "oak_door[facing=south,half=upper,hinge=left,open=false]");
            b.set(0, 2, 3, "glass"); b.set(6, 2, 3, "glass"); b.set(3, 2, 6, "glass");
            b.fill(0, 4, 0, 6, 4, 6, "oak_slab[type=bottom]");
            b.set(3, 2, 5, "wall_torch[facing=north]");
        });
        // stairs in every facing, both halves, in rows
        def("stairs", 8, 3, 9, b -> {
            b.fill(0, 0, 0, 7, 0, 8, "stone");
            for (int f = 0; f < 4; f++) for (int k = 0; k < 8; k++) {
                b.set(k, 1, f * 2, "stone_brick_stairs[facing=" + FACINGS[f] + ",half=bottom,shape=straight]");
                b.set(k, 2, f * 2 + 1, "oak_stairs[facing=" + FACINGS[(f + 1) % 4] + ",half=top,shape=straight]");
            }
            b.fill(0, 1, 1, 7, 1, 1, "stone"); b.fill(0, 1, 3, 7, 1, 3, "stone"); b.fill(0, 1, 5, 7, 1, 5, "stone"); b.fill(0, 1, 7, 7, 1, 7, "stone");
        });
        // slabs: bottom, top and double, in a checker over two layers
        def("slabs", 9, 3, 9, b -> {
            for (int z = 0; z < 9; z++) for (int x = 0; x < 9; x++) {
                int k = (x + 2 * z) % 3;
                b.set(x, 0, z, k == 0 ? "smooth_stone_slab[type=bottom]" : k == 1 ? "smooth_stone_slab[type=double]" : "stone_bricks");
                if ((x + z) % 2 == 0) b.set(x, 2, z, "birch_slab[type=top]");
            }
            b.fill(0, 1, 0, 0, 1, 8, "birch_planks"); b.fill(8, 1, 0, 8, 1, 8, "birch_planks");
        });
        // logs on all three axes: a frame with crossing beams
        def("logs", 7, 6, 7, b -> {
            for (int[] c : new int[][] {{0, 0}, {6, 0}, {0, 6}, {6, 6}}) b.fill(c[0], 0, c[1], c[0], 5, c[1], "oak_log[axis=y]");
            b.fill(1, 5, 0, 5, 5, 0, "oak_log[axis=x]"); b.fill(1, 5, 6, 5, 5, 6, "oak_log[axis=x]");
            b.fill(0, 5, 1, 0, 5, 5, "spruce_log[axis=z]"); b.fill(6, 5, 1, 6, 5, 5, "spruce_log[axis=z]");
            b.fill(1, 2, 3, 5, 2, 3, "birch_log[axis=x]");
        });
        // a tower with a ladder inside and battlements: tall, so the bot needs scaffolding columns
        def("tower", 5, 14, 5, b -> {
            b.walls(0, 0, 0, 4, 11, 4, "stone_bricks");
            b.set(2, 0, 0, null); b.set(2, 1, 0, null);
            for (int y = 0; y <= 11; y++) b.set(2, y, 3, "ladder[facing=north]");
            b.fill(0, 12, 0, 4, 12, 4, "stone_bricks");
            b.set(2, 12, 3, "oak_trapdoor[facing=north,half=bottom,open=false]");
            for (int x = 0; x <= 4; x += 2) { b.set(x, 13, 0, "stone_bricks"); b.set(x, 13, 4, "stone_bricks"); }
            b.set(0, 13, 2, "stone_bricks"); b.set(4, 13, 2, "stone_bricks");
        });
        // two piers and a deck between them, five blocks up: an overhang to reach from below
        def("bridge", 13, 7, 3, b -> {
            b.fill(0, 0, 0, 2, 5, 2, "andesite"); b.fill(10, 0, 0, 12, 5, 2, "andesite");
            b.fill(0, 6, 0, 12, 6, 2, "spruce_planks");
            for (int x = 0; x <= 12; x += 2) { b.set(x, 7 - 1, 0, "spruce_planks"); }
            b.fill(3, 5, 1, 9, 5, 1, "spruce_fence");
        });
        // a mushroom: a thin stem and a wide cap that hangs out over nothing
        def("mushroom", 9, 8, 9, b -> {
            b.fill(4, 0, 4, 4, 5, 4, "mushroom_stem");
            b.fill(1, 6, 1, 7, 6, 7, "red_mushroom_block");
            b.fill(2, 7, 2, 6, 7, 6, "red_mushroom_block");
            b.fill(0, 5, 2, 0, 5, 6, "red_mushroom_block"); b.fill(8, 5, 2, 8, 5, 6, "red_mushroom_block");
        });
        // a pen: fences, a fence gate, walls and panes (their connections are not judged)
        def("pen", 10, 3, 8, b -> {
            b.walls(0, 0, 0, 9, 0, 7, "oak_fence");
            b.set(4, 0, 0, "oak_fence_gate[facing=north,in_wall=false,open=false]");
            b.fill(0, 1, 7, 9, 1, 7, "cobblestone_wall");
            b.fill(0, 0, 7, 9, 0, 7, "cobblestone");
            b.fill(0, 2, 7, 9, 2, 7, "glass_pane");
        });
        // trapdoors on a wall: open and shut, top and bottom, every facing
        def("trapdoors", 9, 5, 3, b -> {
            b.fill(0, 0, 1, 8, 3, 1, "bricks");
            for (int k = 0; k < 8; k++) {
                String f = FACINGS[k % 4];
                b.set(k, 1 + (k / 4), 0, "spruce_trapdoor[facing=" + f + ",half=" + (k % 2 == 0 ? "top" : "bottom") + ",open=" + (k % 3 == 0) + "]");
                b.set(k, 2 + (k / 4), 2, "oak_trapdoor[facing=" + f + ",half=" + (k % 2 == 1 ? "top" : "bottom") + ",open=false]");
            }
        });
        // gravity blocks: sand and gravel resting on supports, concrete powder on a slab
        def("gravity", 8, 5, 4, b -> {
            b.fill(0, 0, 0, 7, 0, 3, "dirt");
            b.fill(0, 1, 0, 7, 1, 0, "stone");
            b.fill(0, 2, 0, 7, 3, 0, "sand");
            b.fill(0, 1, 2, 3, 3, 2, "gravel");
            b.fill(4, 1, 2, 7, 1, 2, "stone_slab[type=top]");
            b.fill(4, 2, 2, 7, 2, 2, "red_concrete_powder");
            b.set(2, 4, 0, "red_sand");
        });
        // furniture: a bed, a double chest, a single chest, a furnace, a barrel, a crafting table, a lantern
        def("furniture", 9, 3, 6, b -> {
            b.fill(0, 0, 0, 8, 0, 5, "spruce_planks");
            b.set(1, 1, 1, "red_bed[facing=south,occupied=false,part=foot]");
            b.set(1, 1, 2, "red_bed[facing=south,occupied=false,part=head]");
            b.set(3, 1, 4, "chest[facing=north,type=left,waterlogged=false]");
            b.set(4, 1, 4, "chest[facing=north,type=right,waterlogged=false]");
            b.set(6, 1, 4, "chest[facing=west,type=single,waterlogged=false]");
            b.set(7, 1, 1, "furnace[facing=east,lit=false]");
            b.set(7, 1, 2, "barrel[facing=up,open=false]");
            b.set(5, 1, 1, "crafting_table");
            b.set(5, 2, 1, "lantern[hanging=false,waterlogged=false]");
        });
        // a garden: flowers and short plants on grass, a flower pot, a tree with leaves
        def("garden", 9, 7, 9, b -> {
            b.fill(0, 0, 0, 8, 0, 8, "grass_block[snowy=false]");
            String[] plants = {"poppy", "dandelion", "blue_orchid", "oxeye_daisy", "short_grass", "fern", "cornflower"};
            Random r = new Random(7);
            for (int z = 0; z < 9; z++) for (int x = 0; x < 9; x++) if (r.nextInt(4) == 0 && (x < 3 || x > 5 || z < 3 || z > 5)) b.set(x, 1, z, plants[r.nextInt(plants.length)]);
            b.fill(4, 1, 4, 4, 4, 4, "oak_log[axis=y]");
            b.fill(2, 4, 2, 6, 5, 6, "oak_leaves[distance=1,persistent=true,waterlogged=false]");
            b.fill(3, 6, 3, 5, 6, 5, "oak_leaves[distance=1,persistent=true,waterlogged=false]");
            b.fill(4, 4, 4, 4, 5, 4, "oak_log[axis=y]");
            b.set(0, 1, 0, "potted_poppy");
        });
        // a stepped sandstone pyramid
        def("pyramid", 11, 6, 11, b -> {
            for (int y = 0; y < 6; y++) b.fill(y, y, y, 10 - y, y, 10 - y, y % 2 == 0 ? "sandstone" : "cut_sandstone");
        });
        // a house with a gable roof of stairs
        def("gable", 7, 7, 9, b -> {
            b.fill(0, 0, 0, 6, 0, 8, "stone_bricks");
            b.walls(0, 1, 0, 6, 3, 8, "oak_planks");
            b.set(3, 1, 0, null); b.set(3, 2, 0, null);
            for (int k = 0; k < 4; k++) {
                b.fill(k, 4 + k, 0, k, 4 + k, 8, "dark_oak_stairs[facing=east,half=bottom,shape=straight]");
                b.fill(6 - k, 4 + k, 0, 6 - k, 4 + k, 8, "dark_oak_stairs[facing=west,half=bottom,shape=straight]");
            }
            b.fill(3, 4, 0, 3, 6, 0, "oak_planks"); b.fill(1, 4, 0, 2, 4, 0, "oak_planks"); b.fill(4, 4, 0, 5, 4, 0, "oak_planks");
            b.set(2, 5, 0, "oak_planks"); b.set(4, 5, 0, "oak_planks");
        });
        // thin tall pillars with lanterns hanging under caps
        def("pillars", 9, 10, 3, b -> {
            for (int x = 0; x < 9; x += 4) {
                b.fill(x, 0, 1, x, 8, 1, "quartz_pillar[axis=y]");
                b.set(x, 9, 1, "chiseled_quartz_block");
                b.set(x, 9, 0, "quartz_slab[type=top]"); b.set(x, 9, 2, "quartz_slab[type=top]");
                b.set(x, 8, 0, "lantern[hanging=true,waterlogged=false]");
            }
        });
        // a platform in the air on one thin leg
        def("platform", 7, 8, 7, b -> {
            b.fill(3, 0, 3, 3, 6, 3, "spruce_fence");
            b.fill(0, 7, 0, 6, 7, 6, "spruce_planks");
        });
        // a room full of cobwebs (they wait until the first clean-up)
        def("webs", 6, 4, 6, b -> {
            b.walls(0, 0, 0, 5, 3, 5, "mossy_cobblestone");
            b.set(2, 0, 0, null); b.set(2, 1, 0, null);
            Random r = new Random(11);
            for (int y = 0; y < 3; y++) for (int z = 1; z < 5; z++) for (int x = 1; x < 5; x++) if (r.nextInt(3) == 0) b.set(x, y, z, "cobweb");
        });
        // a magma floor to cross
        def("magma", 9, 2, 9, b -> {
            b.fill(0, 0, 0, 8, 0, 8, "magma_block");
            b.walls(0, 1, 0, 8, 1, 8, "nether_bricks");
            b.set(4, 1, 0, null);
        });
        // a closed box: four walls, a floor and a roof; the bot must not shut itself in
        def("closed", 6, 6, 6, b -> {
            b.fill(0, 0, 0, 5, 0, 5, "polished_andesite");
            b.walls(0, 1, 0, 5, 4, 5, "polished_andesite");
            b.fill(0, 5, 0, 5, 5, 5, "polished_andesite");
            b.set(2, 2, 3, "glowstone");
        });
        // iron doors (they cannot be opened by hand, so they go last) with a button beside them
        def("irondoor", 7, 4, 3, b -> {
            b.fill(0, 0, 1, 6, 3, 1, "stone_bricks");
            b.set(2, 0, 1, "iron_door[facing=south,half=lower,hinge=left,open=false,powered=false]");
            b.set(2, 1, 1, "iron_door[facing=south,half=upper,hinge=left,open=false,powered=false]");
            b.set(2, 2, 1, "stone_bricks");
            b.set(4, 0, 1, "oak_door[facing=north,half=lower,hinge=right,open=false,powered=false]");
            b.set(4, 1, 1, "oak_door[facing=north,half=upper,hinge=right,open=false,powered=false]");
            b.set(3, 1, 2, "stone_button[face=wall,facing=south,powered=false]");
        });
        // waterlogged blocks (judged without their water) and a cell of water (not part of the target)
        def("wet", 6, 2, 4, b -> {
            b.fill(0, 0, 0, 5, 0, 3, "prismarine");
            b.set(1, 1, 1, "prismarine_slab[type=bottom,waterlogged=true]");
            b.set(2, 1, 1, "prismarine_stairs[facing=east,half=bottom,shape=straight,waterlogged=true]");
            b.set(3, 1, 1, "water[level=0]");
            b.set(4, 1, 1, "oak_fence[east=false,north=false,south=false,waterlogged=true,west=false]");
            b.set(1, 1, 2, "sea_lantern");
        });
        // a long low wall: mostly walking
        def("longwall", 28, 3, 1, b -> {
            b.fill(0, 0, 0, 27, 1, 0, "bricks");
            for (int x = 0; x < 28; x += 3) b.set(x, 2, 0, "brick_slab[type=bottom]");
        });
        // an arch: a curved span whose top is reached from columns
        def("arch", 11, 7, 2, b -> {
            for (int x = 0; x < 11; x++) {
                double d = (x - 5) / 5.0;
                int h = (int) Math.round(6 - 4 * d * d);
                for (int z = 0; z < 2; z++) {
                    b.set(x, h, z, "deepslate_bricks");
                    if (x == 0 || x == 10) b.fill(x, 0, z, x, h, z, "deepslate_bricks");
                }
            }
        });
        // a random rubble heap from a fixed seed: many block kinds, odd shapes
        def("rubble", 8, 5, 8, b -> {
            String[] kinds = {"stone", "cobblestone", "andesite", "granite", "diorite", "gravel", "oak_planks", "stone_brick_stairs[facing=north,half=bottom,shape=straight]",
                "stone_slab[type=bottom]", "mossy_stone_bricks", "coarse_dirt", "terracotta"};
            Random r = new Random(1234);
            for (int z = 0; z < 8; z++) for (int x = 0; x < 8; x++) {
                int h = 1 + r.nextInt(5);
                for (int y = 0; y < h; y++) if (y == 0 || r.nextInt(5) > 0) b.set(x, y, z, kinds[r.nextInt(kinds.length)]);
            }
            // (a gravel cell with nothing under it would fall; the target drops it, as it drops any unsupported gravity block)
        });
        // a staircase up a wall to a balcony, with a railing
        def("balcony", 8, 6, 5, b -> {
            b.fill(0, 0, 4, 7, 5, 4, "stone_bricks");
            for (int k = 0; k < 5; k++) b.set(1 + k, k, 3, "cobblestone_stairs[facing=east,half=bottom,shape=straight]");
            b.fill(5, 4, 1, 7, 4, 3, "smooth_stone");
            b.fill(5, 5, 1, 7, 5, 1, "oak_fence");
            b.set(7, 5, 2, "oak_fence"); b.set(5, 5, 2, "oak_fence");
        });
        // carpets, pressure plates, buttons, a lever, signs and torches: decoration the judge exempts, but the bot places
        def("decor", 7, 3, 5, b -> {
            b.fill(0, 0, 0, 6, 0, 4, "white_wool");
            b.fill(0, 0, 4, 6, 2, 4, "oak_planks");
            b.set(1, 1, 1, "red_carpet"); b.set(2, 1, 1, "blue_carpet");
            b.set(3, 1, 1, "oak_pressure_plate[powered=false]");
            b.set(4, 1, 3, "lever[face=wall,facing=north,powered=false]");
            b.set(5, 1, 3, "oak_button[face=wall,facing=north,powered=false]");
            b.set(1, 2, 3, "wall_torch[facing=north]");
            b.set(5, 1, 1, "torch");
            b.set(3, 2, 3, "oak_wall_sign[facing=north,waterlogged=false]");
        });

        // ---- larger builds (a few thousand blocks): restocking trips, long routes, many scaffolding columns
        // a keep: thick walls with corner turrets, two floors, stairs between them, windows and a door
        def("keep", 17, 13, 17, b -> {
            b.fill(0, 0, 0, 16, 0, 16, "stone_bricks");
            b.walls(1, 1, 1, 15, 9, 15, "stone_bricks");
            b.walls(2, 1, 2, 14, 9, 14, "cobblestone");
            for (int[] c : new int[][] {{0, 0}, {13, 0}, {0, 13}, {13, 13}}) {
                b.walls(c[0], 1, c[1], c[0] + 3, 11, c[1] + 3, "polished_andesite");
                b.fill(c[0], 12, c[1], c[0] + 3, 12, c[1] + 3, "andesite_slab[type=bottom]");
            }
            b.fill(3, 5, 3, 13, 5, 13, "spruce_planks");
            for (int k = 0; k < 4; k++) { b.set(4 + k, 1 + k, 12, "spruce_stairs[facing=east,half=bottom,shape=straight]"); b.set(4 + k, 5, 12, null); b.set(4 + k, 5, 13, null); }
            b.fill(8, 1, 1, 8, 3, 2, null);
            b.set(8, 1, 1, "spruce_door[facing=north,half=lower,hinge=left,open=false,powered=false]");
            b.set(8, 2, 1, "spruce_door[facing=north,half=upper,hinge=left,open=false,powered=false]");
            for (int x = 4; x <= 12; x += 4) { b.set(x, 7, 1, "glass_pane"); b.set(x, 7, 2, null); b.set(x, 7, 15, "glass_pane"); b.set(x, 7, 14, null); }
            b.fill(1, 10, 1, 15, 10, 15, "stone_brick_slab[type=bottom]");
            for (int x = 1; x <= 15; x += 2) { b.set(x, 10, 1, "stone_bricks"); b.set(x, 10, 15, "stone_bricks"); }
            for (int z = 1; z <= 15; z += 2) { b.set(1, 10, z, "stone_bricks"); b.set(15, 10, z, "stone_bricks"); }
        });
        // a hill: a random height map of grass over dirt over stone, with a path of slabs over it
        def("hill", 24, 10, 20, b -> {
            Random r = new Random(99);
            double[] p = {r.nextDouble() * 6, r.nextDouble() * 6, r.nextDouble() * 6};
            for (int z = 0; z < 20; z++) for (int x = 0; x < 24; x++) {
                double h = 4 + 2.5 * Math.sin(x / 4.0 + p[0]) + 2 * Math.cos(z / 3.5 + p[1]) + Math.sin((x + z) / 6.0 + p[2]);
                int top = Math.max(0, Math.min(8, (int) Math.round(h)));
                for (int y = 0; y <= top; y++) b.set(x, y, z, y == top ? "grass_block[snowy=false]" : y > top - 3 ? "dirt" : "stone");
                if (z == 10 && x % 2 == 0) b.set(x, top + 1, z, "oak_slab[type=bottom]");
            }
        });
        // a small village: four huts of different woods around a well
        def("village", 26, 7, 26, b -> {
            String[] woods = {"oak", "spruce", "birch", "acacia"};
            int[][] at = {{0, 0}, {17, 0}, {0, 17}, {17, 17}};
            for (int k = 0; k < 4; k++) {
                int x0 = at[k][0], z0 = at[k][1];
                String w = woods[k];
                b.fill(x0, 0, z0, x0 + 8, 0, z0 + 8, "cobblestone");
                b.walls(x0, 1, z0, x0 + 8, 4, z0 + 8, w + "_planks");
                for (int[] c : new int[][] {{0, 0}, {8, 0}, {0, 8}, {8, 8}}) b.fill(x0 + c[0], 1, z0 + c[1], x0 + c[0], 4, z0 + c[1], w + "_log[axis=y]");
                String face = k < 2 ? "south" : "north";
                int dz = k < 2 ? 8 : 0;
                b.set(x0 + 4, 1, z0 + dz, w + "_door[facing=" + face + ",half=lower,hinge=left,open=false,powered=false]");
                b.set(x0 + 4, 2, z0 + dz, w + "_door[facing=" + face + ",half=upper,hinge=left,open=false,powered=false]");
                b.set(x0 + 2, 2, z0 + dz, "glass_pane"); b.set(x0 + 6, 2, z0 + dz, "glass_pane");
                for (int i = 0; i <= 4; i++) {
                    b.fill(x0, 5 + i / 2, z0 + i, x0 + 8, 5 + i / 2, z0 + i, w + "_stairs[facing=south,half=bottom,shape=straight]");
                    b.fill(x0, 5 + i / 2, z0 + 8 - i, x0 + 8, 5 + i / 2, z0 + 8 - i, w + "_stairs[facing=north,half=bottom,shape=straight]");
                }
            }
            b.walls(11, 0, 11, 14, 1, 14, "cobblestone");
            b.fill(12, 0, 12, 13, 0, 13, "water[level=0]");
            b.fill(11, 2, 11, 11, 3, 11, "oak_fence"); b.fill(14, 2, 14, 14, 3, 14, "oak_fence");
            b.fill(11, 2, 14, 11, 3, 14, "oak_fence"); b.fill(14, 2, 11, 14, 3, 11, "oak_fence");
            b.fill(11, 4, 11, 14, 4, 14, "oak_slab[type=bottom]");
        });
        // a spire: a tall, thin, hollow tower tapering to a point (columns and the clean-up of tall scaffolding)
        def("spire", 7, 30, 7, b -> {
            for (int y = 0; y < 30; y++) {
                int r = y < 18 ? 3 : y < 24 ? 2 : y < 28 ? 1 : 0;
                b.walls(3 - r, y, 3 - r, 3 + r, y, 3 + r, y % 6 == 5 ? "polished_blackstone_bricks" : "blackstone");
            }
            b.set(3, 0, 0, null); b.set(3, 1, 0, null);
        });
        // a dome on four legs: overhangs all round, and a ceiling reached from inside
        def("dome", 15, 9, 15, b -> {
            for (int[] c : new int[][] {{1, 1}, {13, 1}, {1, 13}, {13, 13}}) b.fill(c[0], 0, c[1], c[0], 4, c[1], "red_nether_bricks");
            for (int y = 4; y < 9; y++) for (int z = 0; z < 15; z++) for (int x = 0; x < 15; x++) {
                double d = Math.sqrt((x - 7) * (x - 7) + (z - 7) * (z - 7) + (y - 2) * (y - 2) * 1.6);
                if (d > 6.2 && d <= 7.4) b.set(x, y, z, "white_terracotta");
            }
        });
    }
}
