package mcb.game;

/**
 * The bot's game client: the only way the planner observes or affects the world. It offers what a person at a
 * keyboard and mouse has - turn the view, hold movement keys, left and right click, pick a hotbar slot, click slots in
 * an open screen - plus what that person sees: their own position and view, the block under the crosshair, their
 * inventory, the open screen, and the block changes the server reports. One {@link #tick()} is one game tick.
 *
 * <p>Two implementations ship with the mods: a server-side bot player (the singleplayer mod and the headless runs,
 * {@code mcb.env.BotClient}) and the player's own client ({@code mcbc.LocalClient} in the client mod). A different
 * body - another client framework, a protocol client - is another implementation of this interface.
 *
 * <p>Block states are global state ids and items are item ids of the running game version, as tabulated in
 * {@link mcb.build.StateInfo} and {@link mcb.build.ItemInfo}.
 */
public interface Client {
    // ---------------------------------------------------------------- observation
    double x();
    double y();
    double z();
    Vec3 velocity();
    boolean onGround();
    boolean horizontalCollision();
    float yaw();
    float pitch();
    /** The eye position. */
    Vec3 eye();
    /** How far the player can reach blocks. */
    double reach();
    boolean sneaking();
    float health();
    boolean dead();

    /** What the client knows of the world at (x, y, z): a state id (unloaded cells read as air). */
    int blockAt(int x, int y, int z);
    /** Whether the client has the chunk holding column (x, z). */
    boolean loaded(int x, int z);
    /** Whether the block at (x, y, z) receives a redstone signal from a neighbour (a TNT block there would go off). */
    boolean neighborSignal(int x, int y, int z);

    /** Number of block changes reported since the last {@link #clearChanges()}. */
    int changeCount();
    /** The backing array of {x, y, z, stateId} quadruples; valid entries: {@link #changeCount()}. */
    int[] changes();
    void clearChanges();
    /** Chunks that arrived since the last call, packed as {@code (x & 0xffffffff) | (z << 32)} (see {@link #chunkX}). */
    long[] takeLoadedChunks();
    static int chunkX(long packed) { return (int) packed; }
    static int chunkZ(long packed) { return (int) (packed >>> 32); }

    /** Inventory slot i: 0-8 the hotbar, 9-35 the main inventory. */
    Stack inventorySlot(int i);
    int selectedSlot();
    /** Any screen open (the bot's own inventory or a container). */
    boolean screenOpen();
    /** A container's screen open (a chest, a barrel ...). */
    boolean containerOpen();
    /** Slots of the open screen (a double chest: 54 of its own, then the 36 of the player's inventory). */
    int menuSize();
    Stack menuSlot(int slot);
    /** What kind of screen is open, for log messages. */
    String menuName();
    /** The block face under the crosshair as of the last tick (null if none within reach). */
    Hit lastHit();
    /** Whether a block is being broken (the attack button held on it). */
    boolean isBreaking();

    // ---------------------------------------------------------------- input for the next tick
    void look(float yaw, float pitch);
    void keys(boolean forward, boolean back, boolean left, boolean right, boolean jump, boolean sneak, boolean sprint);
    default void releaseKeys() { keys(false, false, false, false, false, false, false); }
    void sneak(boolean s);
    void jump(boolean j);
    /** Number key 1-9 (slot 0-8). */
    void selectSlot(int slot);
    /** One press of the use button (right click). */
    void clickUse();
    /** One press of the attack button (left click). */
    void clickAttack();
    void holdAttack(boolean held);
    void holdUse(boolean held);
    /** 'E': open the own inventory. While any screen is open, movement keys and world clicks are ignored. */
    void openInventory();
    /** Esc. */
    void closeScreen();
    /** A mouse click on a slot of the open screen (processed one per tick). */
    void clickSlot(int slot, int button, SlotClick type);
    int queuedSlotClicks();

    /** Runs one tick with the inputs given since the last one. */
    void tick();

    // ---------------------------------------------------------------- a real connection
    /** True while it is worth waiting another tick for the server to answer (the w-th wait). In process: never. */
    default boolean waitingForServer(int w) { return false; }
    /** What the bot is doing, for a status display. */
    default void note(String what) { }
}
