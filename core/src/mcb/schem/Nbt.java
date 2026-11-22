package mcb.schem;

import java.io.BufferedInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * A small, dependency-free reader for NBT ("Named Binary Tag"), the binary format Minecraft and the schematic
 * tools use: big-endian, usually gzip-compressed, with a named compound tag at the root.
 *
 * <p>All thirteen tag types are supported. The accessors on {@link CompoundTag} and {@link ListTag} deliberately
 * behave like the ones in the game's own NBT classes (a missing or wrongly typed entry gives the default; a number
 * of one type read as another converts the way the game converts it), because the schematic readers in this
 * package were first written against those classes and must give the same results.
 *
 * <p>Two details follow the game's reader exactly:
 * <ul>
 *   <li>A compound's keys iterate in {@link HashMap} order (not file order). Minecraft keeps compounds in a
 *       {@code HashMap}, and a schematic whose result depends on key order (overlapping Litematica regions, two
 *       Sponge palette entries with the same index) must come out the same.</li>
 *   <li>Since Minecraft 1.21.5 a list may hold values of different types; on disk such a list is a list of
 *       compounds in which each non-compound value is wrapped as {@code {"": value}}. When a list element is a
 *       compound with exactly one entry, named "", the reader stores the unwrapped value instead, as the game
 *       does.</li>
 * </ul>
 *
 * <p>Limits: containers (compounds and lists) may nest at most {@value #MAX_DEPTH} deep, like in the game. Array
 * and list lengths are not capped, but storage grows as data actually arrives, so a corrupt length cannot make
 * the reader allocate gigabytes up front. Any malformed input throws an {@link IOException}.
 */
public final class Nbt {
    private Nbt() { }

    /** Tag type ids, as written in the file. */
    public static final int END = 0, BYTE = 1, SHORT = 2, INT = 3, LONG = 4, FLOAT = 5, DOUBLE = 6, BYTE_ARRAY = 7,
            STRING = 8, LIST = 9, COMPOUND = 10, INT_ARRAY = 11, LONG_ARRAY = 12;
    /** How deep compounds and lists may nest (the game's limit). */
    public static final int MAX_DEPTH = 512;

    // ------------------------------------------------------------------------------------------------ the tags

    /** Any NBT value. */
    public sealed interface Tag permits NumericTag, StringTag, ByteArrayTag, IntArrayTag, LongArrayTag, ListTag, CompoundTag {
        /** The tag type id ({@link #BYTE} ... {@link #LONG_ARRAY}). */
        int id();
    }

    /**
     * A byte, short, int, long, float or double. The conversions between them are the game's: integers narrow by
     * dropping high bits, and floating-point values convert to int, short and byte by rounding down
     * ({@code Math.floor}); to long, a double rounds down but a float is truncated toward zero.
     */
    public static final class NumericTag implements Tag {
        private final int id;
        private final long integral;   // BYTE, SHORT, INT, LONG
        private final double real;     // FLOAT (exactly representable as double), DOUBLE

        private NumericTag(int id, long integral, double real) { this.id = id; this.integral = integral; this.real = real; }
        public static NumericTag ofByte(byte v) { return new NumericTag(BYTE, v, 0); }
        public static NumericTag ofShort(short v) { return new NumericTag(SHORT, v, 0); }
        public static NumericTag ofInt(int v) { return new NumericTag(INT, v, 0); }
        public static NumericTag ofLong(long v) { return new NumericTag(LONG, v, 0); }
        public static NumericTag ofFloat(float v) { return new NumericTag(FLOAT, 0, v); }
        public static NumericTag ofDouble(double v) { return new NumericTag(DOUBLE, 0, v); }

        @Override public int id() { return id; }
        private boolean isReal() { return id == FLOAT || id == DOUBLE; }
        private int floorInt() { return (int) Math.floor(real); }

        public long longValue() {
            if (!isReal()) return integral;
            return id == FLOAT ? (long) (float) real : (long) Math.floor(real);
        }
        public int intValue() { return isReal() ? floorInt() : (int) integral; }
        public short shortValue() { return isReal() ? (short) floorInt() : (short) integral; }
        public byte byteValue() { return isReal() ? (byte) floorInt() : (byte) integral; }
        public double doubleValue() { return isReal() ? real : (double) integral; }
        public float floatValue() { return isReal() ? (float) real : (float) integral; }

        @Override public String toString() {
            return switch (id) {
                case BYTE -> integral + "b";
                case SHORT -> integral + "s";
                case INT -> Long.toString(integral);
                case LONG -> integral + "L";
                case FLOAT -> (float) real + "f";
                default -> real + "d";
            };
        }
        @Override public boolean equals(Object o) {
            return o instanceof NumericTag n && n.id == id && n.integral == integral && Double.compare(n.real, real) == 0;
        }
        @Override public int hashCode() { return (id * 31 + Long.hashCode(integral)) * 31 + Double.hashCode(real); }
    }

    public record StringTag(String value) implements Tag {
        @Override public int id() { return STRING; }
    }

    /** A byte array. The array is the tag's own storage: do not modify it if the tag is shared. */
    public record ByteArrayTag(byte[] value) implements Tag {
        @Override public int id() { return BYTE_ARRAY; }
    }

    public record IntArrayTag(int[] value) implements Tag {
        @Override public int id() { return INT_ARRAY; }
    }

    public record LongArrayTag(long[] value) implements Tag {
        @Override public int id() { return LONG_ARRAY; }
    }

    /** A list. Element accessors take an index and return the default when the index is out of range. */
    public static final class ListTag implements Tag {
        private final java.util.List<Tag> items;

        public ListTag() { this.items = new ArrayList<>(); }
        private ListTag(java.util.List<Tag> items) { this.items = items; }

        @Override public int id() { return LIST; }
        public int size() { return items.size(); }
        public boolean isEmpty() { return items.isEmpty(); }
        /** The element, or null if the index is out of range. */
        public Tag get(int i) { return i >= 0 && i < items.size() ? items.get(i) : null; }
        public java.util.List<Tag> items() { return Collections.unmodifiableList(items); }
        public ListTag add(Tag t) { items.add(t); return this; }

        public int getIntOr(int i, int def) { return get(i) instanceof NumericTag n ? n.intValue() : def; }
        public String getStringOr(int i, String def) { return get(i) instanceof StringTag s ? s.value() : def; }
        public CompoundTag getCompoundOrEmpty(int i) { return get(i) instanceof CompoundTag c ? c : new CompoundTag(); }
        public ListTag getListOrEmpty(int i) { return get(i) instanceof ListTag l ? l : new ListTag(); }

        @Override public String toString() { return items.toString(); }
    }

    /**
     * A compound: named tags. Getters return the default (or null, or an empty compound / list) when the key is
     * missing or holds a different type; numeric getters accept any numeric type and convert it.
     */
    public static final class CompoundTag implements Tag {
        private final Map<String, Tag> tags = new HashMap<>();

        @Override public int id() { return COMPOUND; }
        public int size() { return tags.size(); }
        public boolean isEmpty() { return tags.isEmpty(); }
        public boolean contains(String key) { return tags.containsKey(key); }
        /** The value, or null. */
        public Tag get(String key) { return tags.get(key); }
        /** The keys, in {@link HashMap} order (see the class comment of {@link Nbt}). */
        public Set<String> keySet() { return Collections.unmodifiableSet(tags.keySet()); }
        /** The entries, in the same order as {@link #keySet()}. */
        public Set<Map.Entry<String, Tag>> entrySet() { return Collections.unmodifiableSet(tags.entrySet()); }
        /** Adds or replaces an entry. A replaced key keeps its place in the iteration order. */
        public CompoundTag put(String key, Tag value) { tags.put(key, value); return this; }

        public byte getByteOr(String key, byte def) { return tags.get(key) instanceof NumericTag n ? n.byteValue() : def; }
        public short getShortOr(String key, short def) { return tags.get(key) instanceof NumericTag n ? n.shortValue() : def; }
        public int getIntOr(String key, int def) { return tags.get(key) instanceof NumericTag n ? n.intValue() : def; }
        public long getLongOr(String key, long def) { return tags.get(key) instanceof NumericTag n ? n.longValue() : def; }
        public double getDoubleOr(String key, double def) { return tags.get(key) instanceof NumericTag n ? n.doubleValue() : def; }
        public String getStringOr(String key, String def) { return tags.get(key) instanceof StringTag s ? s.value() : def; }
        /** The byte array, or null if missing or not a byte array. */
        public byte[] getByteArray(String key) { return tags.get(key) instanceof ByteArrayTag a ? a.value() : null; }
        /** The int array, or null if missing or not an int array. */
        public int[] getIntArray(String key) { return tags.get(key) instanceof IntArrayTag a ? a.value() : null; }
        /** The long array, or null if missing or not a long array. */
        public long[] getLongArray(String key) { return tags.get(key) instanceof LongArrayTag a ? a.value() : null; }
        public CompoundTag getCompoundOrEmpty(String key) { return tags.get(key) instanceof CompoundTag c ? c : new CompoundTag(); }
        public ListTag getListOrEmpty(String key) { return tags.get(key) instanceof ListTag l ? l : new ListTag(); }

        @Override public String toString() { return tags.toString(); }
    }

    // ------------------------------------------------------------------------------------------------ reading

    /** Reads a gzip-compressed NBT file (the usual form of schematic files). */
    public static CompoundTag readCompressed(Path file) throws IOException {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
            return readCompressed(in);
        }
    }

    /** Reads gzip-compressed NBT from a stream. The stream is not closed. */
    public static CompoundTag readCompressed(InputStream in) throws IOException {
        GZIPInputStream gz = new GZIPInputStream(in);
        return read(new DataInputStream(new BufferedInputStream(gz)));
    }

    /**
     * Reads uncompressed NBT: a tag type byte, the root's name (skipped), and the root tag, which must be a
     * compound. Data after the root tag is not read.
     */
    public static CompoundTag read(DataInput in) throws IOException {
        int type = in.readByte();
        if (type != COMPOUND) throw new IOException("root tag must be a named compound tag (type " + type + ")");
        in.skipBytes(in.readUnsignedShort());   // the root's name: skipped, never decoded (as in the game)
        return (CompoundTag) new Reader(in).value(COMPOUND);
    }

    private static final class Reader {
        private final DataInput in;
        private int depth;

        Reader(DataInput in) { this.in = in; }

        Tag value(int type) throws IOException {
            switch (type) {
                case BYTE: return NumericTag.ofByte(in.readByte());
                case SHORT: return NumericTag.ofShort(in.readShort());
                case INT: return NumericTag.ofInt(in.readInt());
                case LONG: return NumericTag.ofLong(in.readLong());
                case FLOAT: return NumericTag.ofFloat(in.readFloat());
                case DOUBLE: return NumericTag.ofDouble(in.readDouble());
                case STRING: return new StringTag(in.readUTF());
                case BYTE_ARRAY: return new ByteArrayTag(bytes(length()));
                case INT_ARRAY: {
                    int n = length();
                    int[] a = new int[Math.min(n, 1 << 16)];
                    for (int i = 0; i < n; i++) {
                        if (i == a.length) a = java.util.Arrays.copyOf(a, (int) Math.min(n, 2L * a.length));
                        a[i] = in.readInt();
                    }
                    return new IntArrayTag(a);
                }
                case LONG_ARRAY: {
                    int n = length();
                    long[] a = new long[Math.min(n, 1 << 16)];
                    for (int i = 0; i < n; i++) {
                        if (i == a.length) a = java.util.Arrays.copyOf(a, (int) Math.min(n, 2L * a.length));
                        a[i] = in.readLong();
                    }
                    return new LongArrayTag(a);
                }
                case LIST: {
                    enter();
                    int elementType = in.readByte();
                    int n = in.readInt();
                    if (n < 0) throw new IOException("negative list length " + n);
                    if (elementType == END && n > 0) throw new IOException("list of " + n + " elements has no element type");
                    // An unknown element type is only an error if there is an element to read (as in the game).
                    java.util.List<Tag> items = new ArrayList<>(Math.min(n, 1 << 12));
                    for (int i = 0; i < n; i++) {
                        Tag t = value(elementType);
                        if (t instanceof CompoundTag c && c.size() == 1 && c.get("") != null) t = c.get("");   // unwrap {"": v}
                        items.add(t);
                    }
                    depth--;
                    return new ListTag(items);
                }
                case COMPOUND: {
                    enter();
                    CompoundTag c = new CompoundTag();
                    int t;
                    while ((t = in.readByte()) != END) {
                        String key = in.readUTF();
                        c.put(key, value(t));
                    }
                    depth--;
                    return c;
                }
                default: throw new IOException("invalid tag type " + type);
            }
        }

        private void enter() throws IOException {
            if (depth >= MAX_DEPTH) throw new IOException("NBT nested deeper than " + MAX_DEPTH);
            depth++;
        }

        private int length() throws IOException {
            int n = in.readInt();
            if (n < 0) throw new IOException("negative array length " + n);
            return n;
        }

        /** Reads n bytes, growing the buffer as data arrives rather than trusting n up front. */
        private byte[] bytes(int n) throws IOException {
            byte[] a = new byte[Math.min(n, 1 << 20)];
            int got = 0;
            while (got < n) {
                if (got == a.length) a = java.util.Arrays.copyOf(a, (int) Math.min(n, 2L * a.length));
                in.readFully(a, got, a.length - got);
                got = a.length;
            }
            return a;
        }
    }
}
