package mcb.schem;

/**
 * Turns the block states written in a schematic file into the numbers stored in a {@link Schem}.
 *
 * <p>Schematic files describe blocks in three ways, one method each. Every method returns a number from 0 to
 * 65535, where 0 means air, or -1 for a block the resolver does not know; {@link SchemIO} stores unknown blocks as
 * air and counts them in {@link Schem#unknownCells}.
 *
 * <p>Two implementations:
 * <ul>
 *   <li>{@link PaletteResolver} (in this package, no dependencies): gives every distinct block state its own
 *       number, in the order first seen. Use it to read schematics without Minecraft.</li>
 *   <li>The game's resolver in the Minecraft adapter ({@code mcb.mc.GameStates}): upgrades old states with the
 *       game's data fixers and returns the game's own global block-state ids. The bot uses that one.</li>
 * </ul>
 *
 * <p>The data version is the game version that wrote the file (Minecraft's "DataVersion" number, e.g. 1343 for
 * 1.12.2, 4903 for 26.2); a resolver that upgrades old states needs it.
 */
public interface StateResolver {
    /**
     * A block state as a compound tag, as found in Litematica and structure palettes:
     * {@code {Name: "minecraft:oak_stairs", Properties: {facing: "north", half: "top"}}}. The whole palette entry
     * is passed, as it is in the file.
     */
    int resolve(Nbt.CompoundTag state, int dataVersion);

    /** A block state as text, as found in Sponge palettes: {@code "minecraft:oak_stairs[facing=north,half=top]"}. */
    int resolve(String spec, int dataVersion);

    /**
     * A block from before Minecraft 1.13: a numeric block id (1 to 4095; never 0, which is air) and its 4-bit data
     * value, as found in MCEdit {@code .schematic} files.
     */
    int resolveLegacy(int blockId, int data);
}
