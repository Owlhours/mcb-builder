package mcb.schem;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * Test helper: writes {@link Nbt} tags back to bytes, so the tests can make schematic files of their own. It also
 * has short builder functions ({@link #c}, {@link #list}, {@link #i} ...) for writing tag trees in test code.
 */
final class NbtWriter {
    private NbtWriter() { }

    /** A gzip-compressed file with this root compound (root name ""). */
    static byte[] gzip(Nbt.CompoundTag root) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes))) {
                out.writeByte(Nbt.COMPOUND);
                out.writeUTF("");
                write(out, root);
            }
            return bytes.toByteArray();
        } catch (IOException e) { throw new RuntimeException(e); }
    }

    /** Gzip-compresses raw bytes (for hand-made, possibly invalid NBT). */
    static byte[] gzipRaw(byte[] raw) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (GZIPOutputStream out = new GZIPOutputStream(bytes)) { out.write(raw); }
            return bytes.toByteArray();
        } catch (IOException e) { throw new RuntimeException(e); }
    }

    static void write(DataOutputStream out, Nbt.Tag tag) throws IOException {
        switch (tag) {
            case Nbt.NumericTag n -> {
                switch (n.id()) {
                    case Nbt.BYTE -> out.writeByte(n.byteValue());
                    case Nbt.SHORT -> out.writeShort(n.shortValue());
                    case Nbt.INT -> out.writeInt(n.intValue());
                    case Nbt.LONG -> out.writeLong(n.longValue());
                    case Nbt.FLOAT -> out.writeFloat(n.floatValue());
                    default -> out.writeDouble(n.doubleValue());
                }
            }
            case Nbt.StringTag s -> out.writeUTF(s.value());
            case Nbt.ByteArrayTag a -> { out.writeInt(a.value().length); out.write(a.value()); }
            case Nbt.IntArrayTag a -> { out.writeInt(a.value().length); for (int v : a.value()) out.writeInt(v); }
            case Nbt.LongArrayTag a -> { out.writeInt(a.value().length); for (long v : a.value()) out.writeLong(v); }
            case Nbt.ListTag l -> {
                // One element type for all; a list of mixed types is written as compounds, with every
                // non-compound value wrapped as {"": value} (what Minecraft writes since 1.21.5).
                int type = l.isEmpty() ? Nbt.END : l.get(0).id();
                for (Nbt.Tag t : l.items()) if (t.id() != type) type = -1;
                out.writeByte(type < 0 ? Nbt.COMPOUND : type);
                out.writeInt(l.size());
                for (Nbt.Tag t : l.items()) {
                    if (type < 0 && !(t instanceof Nbt.CompoundTag)) write(out, c("", t));
                    else write(out, t);
                }
            }
            case Nbt.CompoundTag c -> {
                for (Map.Entry<String, Nbt.Tag> e : c.entrySet()) {
                    out.writeByte(e.getValue().id());
                    out.writeUTF(e.getKey());
                    write(out, e.getValue());
                }
                out.writeByte(Nbt.END);
            }
        }
    }

    // ---------------------------------------------------------------- builders

    /** A compound from key, value pairs; a value may be a tag, a String, an Integer or a Short. */
    static Nbt.CompoundTag c(Object... kv) {
        Nbt.CompoundTag t = new Nbt.CompoundTag();
        for (int i = 0; i < kv.length; i += 2) t.put((String) kv[i], tag(kv[i + 1]));
        return t;
    }

    static Nbt.ListTag list(Object... items) {
        Nbt.ListTag l = new Nbt.ListTag();
        for (Object o : items) l.add(tag(o));
        return l;
    }

    static Nbt.Tag tag(Object o) {
        return switch (o) {
            case Nbt.Tag t -> t;
            case String s -> new Nbt.StringTag(s);
            case Integer v -> Nbt.NumericTag.ofInt(v);
            case Short v -> Nbt.NumericTag.ofShort(v);
            case Byte v -> Nbt.NumericTag.ofByte(v);
            case Long v -> Nbt.NumericTag.ofLong(v);
            case Float v -> Nbt.NumericTag.ofFloat(v);
            case Double v -> Nbt.NumericTag.ofDouble(v);
            case byte[] a -> new Nbt.ByteArrayTag(a);
            case int[] a -> new Nbt.IntArrayTag(a);
            case long[] a -> new Nbt.LongArrayTag(a);
            default -> throw new IllegalArgumentException("not a tag value: " + o);
        };
    }

    static Nbt.NumericTag i(int v) { return Nbt.NumericTag.ofInt(v); }
    static Nbt.NumericTag s(int v) { return Nbt.NumericTag.ofShort((short) v); }
    static Nbt.NumericTag b(int v) { return Nbt.NumericTag.ofByte((byte) v); }
}
