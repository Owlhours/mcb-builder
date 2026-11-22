package mcb.mc;

/**
 * Minecraft 26.2: the keys of a block state written as NBT ({@code NbtUtils.readBlockState}) are "Name" and
 * "Properties", the keys schematic files use too. Every version has its own copy of this class
 * (adapter/versions/&lt;mc&gt;/src) with the same members; see adapter/versions/README.md.
 */
final class StateTags {
    private StateTags() { }

    /** The keys of the block's id and of its properties. */
    static final String NAME = "Name", PROPERTIES = "Properties";
    /** Whether these are the keys schematic files have always used ("Name", "Properties"). */
    static final boolean LEGACY_KEYS = true;
}
