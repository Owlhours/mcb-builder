package mcb.mc;

import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.Dynamic;
import java.util.HashMap;
import java.util.Map;
import mcb.schem.Nbt;
import mcb.schem.SchemIO;
import mcb.schem.StateResolver;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.BlockStateData;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The game's {@link StateResolver}: turns block states as written in schematic files (any game version) into the
 * running game's global block-state ids, using the game's own data fixers for the version upgrade and its own
 * parser for the result. Returns -1 for blocks that do not exist in this game version (mod blocks).
 *
 * <p>The game's registries must be set up first (they are inside a running game; elsewhere call
 * {@code SharedConstants.tryDetectVersion()} and {@code Bootstrap.bootStrap()}). Each instance caches its answers;
 * not thread-safe.
 */
public final class GameStates implements StateResolver {
    /** The data version of the game this adapter is for (26.2). */
    public static final int CURRENT = 4903;
    private static DataFixer fixer;
    private final Map<String, Integer> cache = new HashMap<>();

    /** A schematic reader that gives the game's block-state ids: what the mods and the headless runner use. */
    public static SchemIO reader() {
        return new SchemIO(new GameStates());
    }

    private static DataFixer fixer() {
        if (fixer == null) fixer = DataFixers.getDataFixer();
        return fixer;
    }

    /** {Name, Properties} compound as found in litematic / structure palettes. */
    @Override
    public int resolve(Nbt.CompoundTag state, int dataVersion) {
        return resolve(toGame(state), dataVersion);
    }

    /** The same, for a compound that is already one of the game's tags. */
    public int resolve(CompoundTag state, int dataVersion) {
        String key = dataVersion + "|" + state;
        Integer c = cache.get(key);
        if (c != null) return c;
        int id = resolveUncached(state.copy(), dataVersion);
        cache.put(key, id);
        return id;
    }

    /** "minecraft:oak_stairs[facing=north,half=top]" as found in Sponge palettes. */
    @Override
    public int resolve(String spec, int dataVersion) {
        String key = dataVersion + "|" + spec;
        Integer c = cache.get(key);
        if (c != null) return c;
        int id = resolveUncached(parseSpec(spec), dataVersion);
        cache.put(key, id);
        return id;
    }

    /** Pre-1.13 numeric block id + data nibble (MCEdit .schematic). */
    @Override
    public int resolveLegacy(int blockId, int data) {
        String key = "L|" + blockId + ":" + data;
        Integer c = cache.get(key);
        if (c != null) return c;
        Dynamic<?> d = BlockStateData.getTag((blockId << 4) | (data & 15));
        Tag t = d.convert(NbtOps.INSTANCE).getValue();
        int id = t instanceof CompoundTag ct ? resolveUncached(ct.copy(), 1451) : -1;
        if (blockId != 0 && id == 0) id = -1; // the fixer's table answers "air" for ids it does not know
        cache.put(key, id);
        return id;
    }

    static CompoundTag parseSpec(String spec) {
        CompoundTag t = new CompoundTag();
        int b = spec.indexOf('[');
        String name = b < 0 ? spec : spec.substring(0, b);
        if (name.indexOf(':') < 0) name = "minecraft:" + name;
        t.putString("Name", name.trim());
        if (b >= 0) {
            int e = spec.lastIndexOf(']');
            if (e < b) e = spec.length();
            CompoundTag props = new CompoundTag();
            for (String kv : spec.substring(b + 1, e).split(",")) {
                int eq = kv.indexOf('=');
                if (eq > 0) props.putString(kv.substring(0, eq).trim(), kv.substring(eq + 1).trim());
            }
            if (!props.isEmpty()) t.put("Properties", props);
        }
        return t;
    }

    private int resolveUncached(CompoundTag tag, int dataVersion) {
        try {
            if (dataVersion > 0 && dataVersion < CURRENT) {
                Dynamic<Tag> upgraded = fixer().update(References.BLOCK_STATE, new Dynamic<>(NbtOps.INSTANCE, tag), dataVersion, CURRENT);
                if (upgraded.getValue() instanceof CompoundTag ct) tag = ct;
            }
            String name = tag.getStringOr("Name", "");
            Identifier id = Identifier.tryParse(name);
            if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) return -1;
            BlockState st = NbtUtils.readBlockState(BuiltInRegistries.BLOCK, tag);
            return Block.getId(st);
        } catch (RuntimeException e) {
            return -1;
        }
    }

    /**
     * The game's tag for a tag read by {@link Nbt}: the same value, and for compounds the same keys put in the
     * same order, so the game's {@code HashMap} inside it is laid out exactly as if the game had read the file.
     */
    public static CompoundTag toGame(Nbt.CompoundTag c) {
        CompoundTag t = new CompoundTag();
        for (Map.Entry<String, Nbt.Tag> e : c.entrySet()) t.put(e.getKey(), toGame(e.getValue()));
        return t;
    }

    public static Tag toGame(Nbt.Tag tag) {
        return switch (tag) {
            case Nbt.NumericTag n -> switch (n.id()) {
                case Nbt.BYTE -> ByteTag.valueOf(n.byteValue());
                case Nbt.SHORT -> ShortTag.valueOf(n.shortValue());
                case Nbt.INT -> IntTag.valueOf(n.intValue());
                case Nbt.LONG -> LongTag.valueOf(n.longValue());
                case Nbt.FLOAT -> FloatTag.valueOf(n.floatValue());
                default -> DoubleTag.valueOf(n.doubleValue());
            };
            case Nbt.StringTag s -> StringTag.valueOf(s.value());
            case Nbt.ByteArrayTag a -> new ByteArrayTag(a.value().clone());
            case Nbt.IntArrayTag a -> new IntArrayTag(a.value().clone());
            case Nbt.LongArrayTag a -> new LongArrayTag(a.value().clone());
            case Nbt.ListTag l -> {
                ListTag out = new ListTag();
                for (Nbt.Tag item : l.items()) out.add(toGame(item));
                yield out;
            }
            case Nbt.CompoundTag c -> toGame(c);
        };
    }
}
