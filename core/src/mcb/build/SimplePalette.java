package mcb.build;

/**
 * A handful of made-up block states that fill {@link StateInfo} and {@link ItemInfo} without Minecraft, for tests,
 * examples and experiments with the parts of the bot that only need the tables (the judge, the route search, the
 * bot's picture of the site).
 *
 * <p><b>This is not the game's data.</b> The ids are this class's own, and only the facts the examples need are set:
 * which states are air, full cubes, slabs, ladders, scaffolding, decoration and so on, their collision tops, their
 * items. In the real bot the Minecraft adapter fills the same tables from the game ({@code mcb.mc.BlockFacts}).
 *
 * <pre>
 *   SimplePalette.install();          // fills the tables (once per process)
 *   int stone = SimplePalette.STONE;  // a state id
 * </pre>
 */
public final class SimplePalette {
    private SimplePalette() { }

    // states (ids)
    public static final int AIR = 0, STONE = 1, DIRT = 2, GRASS = 3, BEDROCK = 4, PLANKS = 5, GLASS = 6,
        SLAB_BOTTOM = 7, SLAB_TOP = 8, SLAB_DOUBLE = 9, LADDER_NORTH = 10, LADDER_SOUTH = 11, SCAFFOLDING = 12,
        TORCH = 13, WATER = 14, LANTERN = 15, MOSS = 16, CHEST = 17;
    private static final int N = 18;
    // items
    public static final int I_AIR = 0, I_STONE = 1, I_DIRT = 2, I_GRASS = 3, I_PLANKS = 4, I_GLASS = 5, I_SLAB = 6,
        I_LADDER = 7, I_SCAFFOLDING = 8, I_TORCH = 9, I_LANTERN = 10, I_MOSS = 11, I_CHEST = 12;
    private static final String[] ITEMS = {"air", "stone", "dirt", "grass_block", "planks", "glass", "slab", "ladder", "scaffolding", "torch", "lantern", "moss_block", "chest"};

    /** Makes {@link StateInfo#init()} fill the tables from this palette (unless something else already did). */
    public static void install() {
        if (StateInfo.filler == null) StateInfo.filler = SimplePalette::fill;
        StateInfo.init();
    }

    public static void fill() {
        ItemInfo.allocate(ITEMS.length);
        for (int k = 0; k < ITEMS.length; k++) { ItemInfo.name[k] = ITEMS[k]; ItemInfo.maxStack[k] = 64; }
        ItemInfo.AIR = I_AIR;
        ItemInfo.SCAFFOLDING = I_SCAFFOLDING;
        StateInfo.allocate(N);
        state(AIR, "air", "air", I_AIR, 0, 0);
        StateInfo.air[AIR] = true; StateInfo.noCollision[AIR] = true; StateInfo.walkThrough[AIR] = true; StateInfo.replaceable[AIR] = true;
        cube(STONE, "stone", I_STONE);
        cube(DIRT, "dirt", I_DIRT);
        cube(GRASS, "grass_block", I_GRASS);
        cube(BEDROCK, "bedrock", I_AIR);
        cube(PLANKS, "planks", I_PLANKS);
        cube(GLASS, "glass", I_GLASS);
        cube(MOSS, "moss_block", I_MOSS);
        // slabs: one block, three states; a double slab takes two of the item, and the first click makes a bottom slab
        state(SLAB_BOTTOM, "slab[type=bottom]", "slab", I_SLAB, 8, 0);
        state(SLAB_TOP, "slab[type=top]", "slab", I_SLAB, 16, 8);
        cube(SLAB_DOUBLE, "slab[type=double]", I_SLAB);
        StateInfo.blockName[SLAB_DOUBLE] = "slab"; StateInfo.itemCount[SLAB_DOUBLE] = 2; StateInfo.firstState[SLAB_DOUBLE] = (char) SLAB_BOTTOM;
        for (int s : new int[] {SLAB_BOTTOM, SLAB_TOP, SLAB_DOUBLE}) StateInfo.block[s] = SLAB_BOTTOM;
        // ladders: climbable, no obstacle to walking through
        for (int s : new int[] {LADDER_NORTH, LADDER_SOUTH}) {
            state(s, s == LADDER_NORTH ? "ladder[facing=north]" : "ladder[facing=south]", "ladder", I_LADDER, 0, 0);
            StateInfo.block[s] = LADDER_NORTH;
            StateInfo.noCollision[s] = true; StateInfo.walkThrough[s] = true; StateInfo.climbable[s] = true;
            StateInfo.rung[s] = 1; StateInfo.rungFacing[s] = (byte) (1 + (s == LADDER_NORTH ? 2 : 0));
            StateInfo.outline[s] = new float[] {0, 0, s == LADDER_NORTH ? 0.8125f : 0, 1, 1, s == LADDER_NORTH ? 1 : 0.1875f};
        }
        // scaffolding: stood on from above, climbed through from inside
        state(SCAFFOLDING, "scaffolding[distance=0,bottom=false]", "scaffolding", I_SCAFFOLDING, 16, 14);
        StateInfo.scaffolding[SCAFFOLDING] = true; StateInfo.walkThrough[SCAFFOLDING] = true; StateInfo.climbable[SCAFFOLDING] = true;
        // decoration: no collision box, so the judge exempts it
        state(TORCH, "torch", "torch", I_TORCH, 0, 0);
        StateInfo.noCollision[TORCH] = true; StateInfo.walkThrough[TORCH] = true; StateInfo.decorWide[TORCH] = true;
        StateInfo.outline[TORCH] = new float[] {0.4f, 0, 0.4f, 0.6f, 0.6f, 0.6f};
        state(LANTERN, "lantern", "lantern", I_LANTERN, 9, 0);
        StateInfo.decorWide[LANTERN] = true;
        // water: not part of a target (Target drops it)
        state(WATER, "water[level=0]", "water", I_AIR, 0, 0);
        StateInfo.fluid[WATER] = true; StateInfo.noCollision[WATER] = true; StateInfo.walkThrough[WATER] = true; StateInfo.replaceable[WATER] = true;
        StateInfo.outline[WATER] = new float[0];
        // a single chest
        state(CHEST, "chest[type=single]", "chest", I_CHEST, 14, 0);
        StateInfo.chest[CHEST] = true; StateInfo.chestSingle[CHEST] = (char) CHEST; StateInfo.interactive[CHEST] = true;
    }

    private static void cube(int s, String name, int item) {
        state(s, name, name, item, 16, 0);
        StateInfo.fullCube[s] = true; StateInfo.sturdyTop[s] = true;
    }

    /** A state with the facts every state needs; top/bottom: its collision box in sixteenths (top 0 = none). */
    private static void state(int s, String name, String block, int item, int top, int bottom) {
        StateInfo.name[s] = name;
        StateInfo.blockName[s] = block;
        StateInfo.descriptionId[s] = "block.simple." + block;
        StateInfo.block[s] = s;
        StateInfo.item[s] = item;
        StateInfo.itemCount[s] = (byte) (item == I_AIR ? 0 : 1);
        StateInfo.contentItem[s] = ItemInfo.NONE;
        StateInfo.colTop[s] = StateInfo.anyTop[s] = (byte) top;
        StateInfo.colBottom[s] = StateInfo.anyBottom[s] = (byte) bottom;
        StateInfo.noCollision[s] = top == 0;
        StateInfo.walkThrough[s] = top == 0;
        StateInfo.outline[s] = top == 0 ? new float[0] : new float[] {0, bottom / 16f, 0, 1, top / 16f, 1};
        StateInfo.destroyTime[s] = s == BEDROCK ? -1 : 1;
        StateInfo.dewater[s] = StateInfo.canon[s] = StateInfo.loose[s] = StateInfo.firstState[s] = (char) s;
        StateInfo.axis[s] = 1;
        StateInfo.defaults.putIfAbsent(block, s);
    }
}
