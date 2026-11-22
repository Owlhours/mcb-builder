package mcb.build;

/**
 * Per item lookup tables, indexed by the game's item id. Filled by the Minecraft adapter together with
 * {@link StateInfo} ({@code mcb.mc.BlockFacts.fill()}).
 */
public final class ItemInfo {
    private ItemInfo() { }

    /** "No item" where a table needs an entry that is not an item at all (unlike {@link #AIR}, which is one). */
    public static final int NONE = -1;
    /** The empty hand (the game's air item), and the scaffolding item; set by the filler. */
    public static int AIR, SCAFFOLDING;

    public static int N;
    /** The item's registry name without the namespace ({@code oak_planks}). */
    public static String[] name;
    /** How many make a full stack. */
    public static int[] maxStack;

    public static void allocate(int n) {
        name = new String[n];
        maxStack = new int[n];
        N = n;
    }

    /** The item's name (for logs); "none" for {@link #NONE}. */
    public static String name(int item) { return item < 0 ? "none" : name[item]; }

    /** A full stack of this item. */
    public static int maxStack(int item) { return maxStack[item]; }
}
