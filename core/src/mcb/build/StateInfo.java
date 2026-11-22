package mcb.build;

import java.util.HashMap;
import java.util.Map;

/**
 * Per block-state lookup tables: everything the planner, the judge and the target normalisation need to know about a
 * block state, indexed by the game's global state id (0 = air). The planner never sees the game's own block objects;
 * it sees these tables.
 *
 * <p>The tables are filled once per process by the Minecraft adapter for the running game version
 * ({@code mcb.mc.BlockFacts.fill()}), from the game's own block definitions. Anything else that can fill them - a
 * test palette, a table read from a file - can stand in: set {@link #filler}, or call {@link #allocate} and fill the
 * arrays before the first use.
 */
public final class StateInfo {
    private StateInfo() { }

    /** Fills the tables (see the class comment); run by {@link #init()} the first time. */
    public static Runnable filler;

    public static int N;
    /** The state's name in the game's syntax without the namespace, e.g. {@code oak_stairs[facing=north,...]} (logs). */
    public static String[] name;
    /** The block's registry name without the namespace ({@code oak_stairs}). */
    public static String[] blockName;
    /** The block's translation key ({@code block.minecraft.oak_stairs}). */
    public static String[] descriptionId;
    /** The block (a number per block, the same for all states of one block): "same block" is {@code block[a] == block[b]}. */
    public static int[] block;
    public static boolean[] air;          // air, cave_air, void_air
    public static boolean[] fluid;        // liquids and plants that only exist inside water
    public static boolean[] gravity;      // falls when unsupported (sand, gravel, concrete powder, anvil ...)
    public static boolean[] replaceable;  // placing "into" it replaces it (grass, snow layer ...)
    public static boolean[] fullCube;     // collision shape is the unit cube
    public static boolean[] noCollision;  // collision shape is empty
    public static boolean[] scaffolding;  // the scaffolding block: stood on from above, walked and climbed through otherwise
    public static boolean[] walkThrough;  // no obstacle to a player moving through the cell (no collision, or scaffolding)
    public static boolean[] sturdyTop;    // top face carries things that need a solid floor
    public static byte[] doorEdges;       // doors: which sides of the cell the leaf closes off (bit 0 east, 1 west, 2 south, 3 north)
    public static boolean[] handDoor;     // a door or gate that opens when clicked
    public static boolean[] closedGate;   // a closed fence gate
    /** Hurts a player who stands on it or in it: lit campfires, cactus, fire, wither roses. Never stood on or in. */
    public static boolean[] hazard;
    /** Hurts while stood on, but slowly (magma): allowed, with an eye on the bot's health. */
    public static boolean[] hot;
    /** Climbing: 1 = ladder; 2 = a trapdoor that opens by hand, open; 3 = the same, closed in the top half of its cell. */
    public static byte[] rung;
    /** For ladders and trapdoors: 1 + the 2D index of their facing. */
    public static byte[] rungFacing;
    public static boolean[] leaves;
    public static boolean[] climbable;
    public static boolean[] interactive;  // right-clicking it does something (so a block can only be placed against it while sneaking)
    public static boolean[] secondary;    // upper door half, bed head, upper half of a double plant: appears by itself with its primary part
    public static boolean[] late;         // iron doors, TNT: placed last so that they do not shut the builder in or out (or go off)
    public static boolean[] technical;    // not part of what a builder places: piston heads, moving pistons, portals, structure voids ...
    public static char[] dewater;         // same state with waterlogged=false
    public static char[] canon;           // representative of the state's equivalence class for judging
    public static char[] loose;           // canon, further ignoring what is set after placing (repeater delay, open/closed ...) or settles by itself (chest halves)
    public static int[] item;             // the item that places this block (ItemInfo.AIR if none)
    public static byte[] itemCount;       // how many of that item the state takes (double slab 2, 3 snow layers 3 ...)
    /** Potted plants: the plant that goes into the placed, empty pot; {@link ItemInfo#NONE} for everything else. */
    public static int[] contentItem;
    /** Collision box extent within the player's footprint when centred on the cell, in 1/16: top and bottom; top=0 if nothing. */
    public static byte[] colTop, colBottom;
    /** The same over the whole cell (not just the footprint): top of the highest and bottom of the lowest collision box, in 1/16. */
    public static byte[] anyTop, anyBottom;
    /** The outline shape (what the crosshair hits) as boxes in the cell, 6 floats each (min x y z, max x y z). */
    public static float[][] outline;
    /** Hardness ({@code < 0}: cannot be broken), and whether breaking it by hand drops nothing (takes longer). */
    public static float[] destroyTime;
    public static boolean[] needsTool;
    /** For states placed with several of their item (double slab, 3 candles ...): the state the first click makes. Else the state itself. */
    public static char[] firstState;
    /** Exempt from judging under the wider reading (decoration that is not a full block; see Judge). */
    public static boolean[] decorWide;
    /** Blocks only an operator in creative mode can place (command, structure, jigsaw and test blocks). */
    public static boolean[] gameMaster;

    // ---- block kinds the planner treats specially
    public static boolean[] chest;        // chests and trapped chests
    public static boolean[] door;         // any door
    public static boolean[] handTrapdoor; // a trapdoor that opens by hand (not iron)
    public static boolean[] fenceGate;
    public static boolean[] pistonBase;   // a piston or sticky piston (not its head)
    public static boolean[] machine;      // piston bases and heads, observers, slime and honey blocks: parts of machines
    public static boolean[] tnt, hopper, bamboo, bambooShoot, cobweb, ice, chain;

    // ---- properties
    /** Chests: 0 single, 1 left, 2 right; the state's facing (face numbering), the same state as a single chest, and
     *  the direction (face numbering) of the other half (for left and right). */
    public static byte[] chestType, chestFacing, chestLink;
    public static char[] chestSingle;
    /** Doors: the lower half; the facing (face numbering); the hinge on the right. */
    public static boolean[] doorLower, doorHingeRight;
    public static byte[] doorFacing;
    /** Pistons: their facing (face numbering). */
    public static byte[] facing;
    /** The axis property (0 x, 1 y, 2 z; 1 for states without one). */
    public static byte[] axis;
    /** Scaffolding: its distance, and its bottom flag. */
    public static byte[] scaffoldDistance;
    public static boolean[] scaffoldBottom;

    /** Default state of each block, by registry name without the namespace. */
    public static final Map<String, Integer> defaults = new HashMap<>();

    /** Properties a player sets by right-clicking the placed block (judged leniently in one place, see loose). */
    public static final java.util.Set<String> ADJUSTABLE = java.util.Set.of("delay", "mode", "open", "inverted", "note");

    public static boolean ready() { return N > 0; }

    /** Makes sure the tables are filled (runs {@link #filler} the first time). */
    public static void init() {
        if (ready()) return;
        if (filler == null) throw new IllegalStateException("the block-state tables are not filled: the Minecraft adapter (mcb.mc.BlockFacts) or a test palette must set StateInfo.filler first");
        filler.run();
        if (!ready()) throw new IllegalStateException("StateInfo.filler left the tables empty");
    }

    /** Allocates every table for n states (for fillers). */
    public static void allocate(int n) {
        name = new String[n]; blockName = new String[n]; descriptionId = new String[n]; block = new int[n];
        air = new boolean[n]; fluid = new boolean[n]; gravity = new boolean[n]; replaceable = new boolean[n]; fullCube = new boolean[n];
        noCollision = new boolean[n]; scaffolding = new boolean[n]; walkThrough = new boolean[n]; sturdyTop = new boolean[n];
        doorEdges = new byte[n]; handDoor = new boolean[n]; closedGate = new boolean[n]; hazard = new boolean[n]; hot = new boolean[n];
        rung = new byte[n]; rungFacing = new byte[n]; leaves = new boolean[n]; climbable = new boolean[n]; interactive = new boolean[n];
        secondary = new boolean[n]; late = new boolean[n]; technical = new boolean[n];
        dewater = new char[n]; canon = new char[n]; loose = new char[n]; item = new int[n]; itemCount = new byte[n]; contentItem = new int[n];
        colTop = new byte[n]; colBottom = new byte[n]; anyTop = new byte[n]; anyBottom = new byte[n]; outline = new float[n][];
        destroyTime = new float[n]; needsTool = new boolean[n]; firstState = new char[n];
        decorWide = new boolean[n]; gameMaster = new boolean[n];
        chest = new boolean[n]; door = new boolean[n]; handTrapdoor = new boolean[n]; fenceGate = new boolean[n]; pistonBase = new boolean[n];
        machine = new boolean[n]; tnt = new boolean[n]; hopper = new boolean[n]; bamboo = new boolean[n]; bambooShoot = new boolean[n];
        cobweb = new boolean[n]; ice = new boolean[n]; chain = new boolean[n];
        chestType = new byte[n]; chestFacing = new byte[n]; chestLink = new byte[n]; chestSingle = new char[n];
        doorLower = new boolean[n]; doorHingeRight = new boolean[n]; doorFacing = new byte[n]; facing = new byte[n]; axis = new byte[n];
        scaffoldDistance = new byte[n]; scaffoldBottom = new boolean[n];
        defaults.clear();
        N = n;
    }

    /** The default state of a block by registry name ({@code "moss_block"}), or -1 if the game has no such block. */
    public static int defaultState(String blockName) { return defaults.getOrDefault(blockName, -1); }

    /** "Decorative" for judging purposes: a block with no collision box at all (flowers, torches, signs, rails, redstone dust ...). */
    public static boolean decorative(int id) { return noCollision[id] && !air[id]; }

    /** The wider reading of "decorative" (see {@link #decorWide}). */
    public static boolean decorativeWide(int id) { return decorWide[id]; }

    /** Blocks only an operator in creative mode can place (the earlier attempt took them out of its targets). */
    public static boolean gameMaster(int id) { return gameMaster[id]; }

    public static String name(int id) { return name[id]; }
}
