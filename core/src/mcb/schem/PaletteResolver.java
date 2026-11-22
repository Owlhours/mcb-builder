package mcb.schem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A {@link StateResolver} that needs no game: it writes each block state as a normalised string and gives each
 * distinct string its own number, 1, 2, 3 ... in the order first seen; 0 is {@code minecraft:air}.
 *
 * <p>The numbers in a {@link Schem} read with this resolver are therefore indices into {@link #states()}, not the
 * game's block-state ids, and they depend on the order the file lists its blocks. Use the same resolver object for
 * several files to number them all from one shared palette.
 *
 * <p>The normal form of a state is {@code namespace:name[key=value,...]} with the properties sorted by key and
 * left out when there are none; a name without a namespace gets {@code minecraft:}. Blocks from before 1.13
 * (numeric ids) become {@code legacy:<id>:<data>}.
 *
 * <p>What it does not do: it does not upgrade states from older game versions (a 1.12 file's
 * {@code minecraft:grass} stays {@code minecraft:grass}), does not check that a block exists, and does not fill in
 * default property values. Only {@code minecraft:air} is air; {@code cave_air} and {@code void_air} get numbers of
 * their own, as they do in the game. A palette entry with no name is unknown (-1).
 */
public final class PaletteResolver implements StateResolver {
    /** {@link Schem} stores states as {@code char}, so at most this many distinct states (air included). */
    public static final int MAX_STATES = 65536;
    public static final String AIR = "minecraft:air";

    private final List<String> states = new ArrayList<>();
    private final Map<String, Integer> index = new HashMap<>();

    public PaletteResolver() {
        states.add(AIR);
        index.put(AIR, 0);
    }

    /** All states seen so far; the position of a state is its number. Entry 0 is {@code minecraft:air}. */
    public List<String> states() { return Collections.unmodifiableList(states); }

    /** The state with this number. */
    public String state(int id) { return states.get(id); }

    @Override
    public int resolve(Nbt.CompoundTag state, int dataVersion) {
        String name = state.getStringOr("Name", "");
        Map<String, String> props = new TreeMap<>();
        for (Map.Entry<String, Nbt.Tag> e : state.getCompoundOrEmpty("Properties").entrySet()) {
            if (e.getValue() instanceof Nbt.StringTag s) props.put(e.getKey(), s.value());
        }
        return intern(name, props);
    }

    @Override
    public int resolve(String spec, int dataVersion) {
        int b = spec.indexOf('[');
        String name = (b < 0 ? spec : spec.substring(0, b)).trim();
        Map<String, String> props = new TreeMap<>();
        if (b >= 0) {
            int e = spec.lastIndexOf(']');
            if (e < b) e = spec.length();
            for (String kv : spec.substring(b + 1, e).split(",")) {
                int eq = kv.indexOf('=');
                if (eq > 0) props.put(kv.substring(0, eq).trim(), kv.substring(eq + 1).trim());
            }
        }
        return intern(name, props);
    }

    @Override
    public int resolveLegacy(int blockId, int data) {
        return blockId == 0 ? 0 : id("legacy:" + blockId + ":" + (data & 15));
    }

    /** The normal form of a state (see the class comment). */
    public static String normalise(String name, Map<String, String> properties) {
        StringBuilder sb = new StringBuilder();
        if (name.indexOf(':') < 0) sb.append("minecraft:");
        sb.append(name);
        if (!properties.isEmpty()) {
            sb.append('[');
            boolean first = true;
            for (Map.Entry<String, String> e : new TreeMap<>(properties).entrySet()) {
                if (!first) sb.append(',');
                sb.append(e.getKey()).append('=').append(e.getValue());
                first = false;
            }
            sb.append(']');
        }
        return sb.toString();
    }

    private int intern(String name, Map<String, String> props) {
        if (name.isEmpty()) return -1;
        String key = normalise(name, props);
        return key.equals(AIR) || key.startsWith(AIR + "[") ? 0 : id(key);
    }

    private int id(String key) {
        Integer id = index.get(key);
        if (id != null) return id;
        if (states.size() >= MAX_STATES) throw new IllegalStateException("more than " + (MAX_STATES - 1) + " distinct block states");
        states.add(key);
        index.put(key, states.size() - 1);
        return states.size() - 1;
    }
}
