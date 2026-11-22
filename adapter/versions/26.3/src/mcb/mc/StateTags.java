package mcb.mc;

/**
 * Minecraft 26.3: the keys of a block state written as NBT ({@code NbtUtils.readBlockState}) are "id" and
 * "properties". Before 26.3 they were "Name" and "Properties" (26.3 keeps those as
 * NbtUtils.LEGACY_BLOCK_STATE_ID_TAG and LEGACY_BLOCKSTATE_PROPERTY_TAG); the game's data fixer renames them
 * (BlockStateFieldNamesFix) when it upgrades an older block state. Every version has its own copy of this class
 * (adapter/versions/&lt;mc&gt;/src) with the same members; see adapter/versions/README.md.
 */
final class StateTags {
    private StateTags() { }

    /** The keys of the block's id and of its properties. */
    static final String NAME = "id", PROPERTIES = "properties";
    /** Whether these are the keys schematic files have always used ("Name", "Properties"). */
    static final boolean LEGACY_KEYS = false;
}
