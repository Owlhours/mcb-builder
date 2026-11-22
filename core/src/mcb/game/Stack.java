package mcb.game;

import mcb.build.ItemInfo;

/** What an inventory or container slot holds: an item (an id, see {@link ItemInfo}) and how many. Immutable. */
public final class Stack {
    private final int item, count;
    public Stack(int item, int count) { this.item = item; this.count = count; }
    public int getItem() { return item; }
    public int getCount() { return count; }
    /** True when the slot holds nothing (no item, or a count of zero), as the game decides it. */
    public boolean isEmpty() { return item == ItemInfo.AIR || count <= 0; }
    public int getMaxStackSize() { return ItemInfo.maxStack[item]; }
    @Override public String toString() { return count + " " + ItemInfo.name(item); }
}
