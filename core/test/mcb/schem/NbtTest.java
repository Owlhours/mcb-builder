package mcb.schem;

import static mcb.schem.NbtWriter.c;
import static mcb.schem.NbtWriter.list;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The NBT reader: every tag type, the game-compatible accessors, and malformed input. */
class NbtTest {
    static Nbt.CompoundTag roundTrip(Nbt.CompoundTag t) throws IOException {
        return Nbt.readCompressed(new ByteArrayInputStream(NbtWriter.gzip(t)));
    }

    /** Uncompressed NBT from a hand-written byte sequence (ints are written as single bytes). */
    static Nbt.CompoundTag raw(int... bytes) throws IOException {
        byte[] b = new byte[bytes.length];
        for (int i = 0; i < b.length; i++) b[i] = (byte) bytes[i];
        return Nbt.read(new DataInputStream(new ByteArrayInputStream(b)));
    }

    @Test
    void readsEveryTagType() throws IOException {
        Nbt.CompoundTag t = roundTrip(c("b", (byte) -5, "s", (short) 300, "i", 70000, "l", 1L << 40, "f", 1.5f, "d", -2.25,
                "str", "héllo \u0000 wörld", "ba", new byte[] {1, 2, 3}, "ia", new int[] {-1, 7}, "la", new long[] {Long.MIN_VALUE},
                "list", list(1, 2, 3), "comp", c("inner", "x"), "empty", list()));
        assertEquals(-5, t.getIntOr("b", 0));
        assertEquals(300, t.getIntOr("s", 0));
        assertEquals(70000, t.getIntOr("i", 0));
        assertEquals(1L << 40, t.getLongOr("l", 0));
        assertEquals(1.5, t.getDoubleOr("f", 0));
        assertEquals(-2.25, t.getDoubleOr("d", 0));
        assertEquals("héllo \u0000 wörld", t.getStringOr("str", null));
        assertArrayEquals(new byte[] {1, 2, 3}, t.getByteArray("ba"));
        assertArrayEquals(new int[] {-1, 7}, t.getIntArray("ia"));
        assertArrayEquals(new long[] {Long.MIN_VALUE}, t.getLongArray("la"));
        assertEquals(3, t.getListOrEmpty("list").getIntOr(2, 0));
        assertEquals("x", t.getCompoundOrEmpty("comp").getStringOr("inner", null));
        assertTrue(t.getListOrEmpty("empty").isEmpty());
        assertEquals(13, t.size());
    }

    @Test
    void missingOrWrongTypeGivesTheDefault() throws IOException {
        Nbt.CompoundTag t = roundTrip(c("s", "text", "n", 4));
        assertEquals(9, t.getIntOr("s", 9));
        assertEquals(9, t.getIntOr("nothing", 9));
        assertEquals("d", t.getStringOr("n", "d"));
        assertNull(t.getByteArray("n"));
        assertNull(t.getLongArray("s"));
        assertTrue(t.getCompoundOrEmpty("n").isEmpty());
        assertTrue(t.getListOrEmpty("s").isEmpty());
        Nbt.ListTag l = roundTrip(c("l", list(5))).getListOrEmpty("l");
        assertEquals(-1, l.getIntOr(1, -1));
        assertEquals(-1, l.getIntOr(-1, -1));
        assertTrue(l.getCompoundOrEmpty(0).isEmpty());
    }

    @Test
    void numbersConvertTheWayTheGameConvertsThem() {
        assertEquals((short) 70000, Nbt.NumericTag.ofInt(70000).shortValue());
        assertEquals(0x89abcdef, Nbt.NumericTag.ofLong(0x0123456789abcdefL).intValue());
        assertEquals(2, Nbt.NumericTag.ofDouble(2.9).intValue());
        assertEquals(-1, Nbt.NumericTag.ofDouble(-0.5).intValue());      // rounds down, not toward zero
        assertEquals(-1, Nbt.NumericTag.ofFloat(-0.5f).intValue());
        assertEquals(-1L, Nbt.NumericTag.ofDouble(-0.5).longValue());    // a double rounds down to long ...
        assertEquals(0L, Nbt.NumericTag.ofFloat(-0.5f).longValue());     // ... a float is truncated
        assertEquals(Integer.MAX_VALUE, Nbt.NumericTag.ofDouble(1e20).intValue());
        assertEquals((short) -1, Nbt.NumericTag.ofDouble(1e20).shortValue());   // floor to int first, then narrow
        assertEquals((byte) 0x34, Nbt.NumericTag.ofShort((short) 0x1234).byteValue());
    }

    @Test
    void keysIterateInHashMapOrder() throws IOException {
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < 40; i++) keys.add("key" + (i * 7919 % 1000));
        Nbt.CompoundTag t = new Nbt.CompoundTag();
        HashMap<String, Integer> reference = new HashMap<>();
        for (String k : keys) { t.put(k, NbtWriter.i(1)); reference.put(k, 1); }
        assertEquals(new ArrayList<>(reference.keySet()), new ArrayList<>(roundTrip(t).keySet()));
    }

    @Test
    void mixedListsAreUnwrapped() throws IOException {
        // written as a list of compounds, the string and the int wrapped as {"": value}
        Nbt.ListTag l = roundTrip(c("l", list("a", 5, c("k", 1), c("", "kept", "other", 2)))).getListOrEmpty("l");
        assertEquals(4, l.size());
        assertEquals("a", l.getStringOr(0, null));
        assertEquals(5, l.getIntOr(1, 0));
        assertEquals(1, l.getCompoundOrEmpty(2).getIntOr("k", 0));
        assertEquals(2, l.getCompoundOrEmpty(3).size());   // two entries: not a wrapper
    }

    @Test
    void nestingIsLimited() throws IOException {
        assertEquals(Nbt.MAX_DEPTH, depth(roundTrip(nested(Nbt.MAX_DEPTH - 1))));   // the root counts as one level
        IOException e = assertThrows(IOException.class, () -> roundTrip(nested(Nbt.MAX_DEPTH)));
        assertTrue(e.getMessage().contains("deeper"));
    }

    static Nbt.CompoundTag nested(int levels) {
        Nbt.CompoundTag root = new Nbt.CompoundTag(), t = root;
        for (int i = 0; i < levels; i++) { Nbt.CompoundTag n = new Nbt.CompoundTag(); t.put("n", n); t = n; }
        return root;
    }

    static int depth(Nbt.CompoundTag t) {
        int d = 1;
        while (t.get("n") instanceof Nbt.CompoundTag n) { t = n; d++; }
        return d;
    }

    @Test
    void malformedInputIsAnIOException() {
        // root is not a compound
        assertThrows(IOException.class, () -> raw(8, 0, 0, 0, 1, 'x'));
        // invalid tag type inside a compound
        assertThrows(IOException.class, () -> raw(10, 0, 0, 99, 0, 1, 'k', 0));
        // negative array length
        assertThrows(IOException.class, () -> raw(10, 0, 0, 7, 0, 1, 'a', 0xff, 0xff, 0xff, 0xff, 0));
        // an array claiming 2 GB, with three bytes behind it: fails at the end of the data, without allocating 2 GB
        assertThrows(IOException.class, () -> raw(10, 0, 0, 7, 0, 1, 'a', 0x7f, 0xff, 0xff, 0xff, 1, 2, 3));
        assertThrows(IOException.class, () -> raw(10, 0, 0, 12, 0, 1, 'a', 0x7f, 0xff, 0xff, 0xff, 1, 2, 3));
        // a list of a billion elements with nothing behind it
        assertThrows(IOException.class, () -> raw(10, 0, 0, 9, 0, 1, 'l', 3, 0x40, 0, 0, 0));
        // a list with elements but no element type; a negative list length
        assertThrows(IOException.class, () -> raw(10, 0, 0, 9, 0, 1, 'l', 0, 0, 0, 0, 1, 0));
        assertThrows(IOException.class, () -> raw(10, 0, 0, 9, 0, 1, 'l', 3, 0xff, 0xff, 0xff, 0xff, 0));
        // cut short
        assertThrows(IOException.class, () -> raw(10, 0, 0, 3, 0, 1, 'i', 0, 0));
        // not gzip
        assertThrows(IOException.class, () -> Nbt.readCompressed(new ByteArrayInputStream(new byte[] {1, 2, 3, 4})));
    }

    @Test
    void emptyListsMayHaveAnyElementType() throws IOException {
        // the game accepts an empty list whatever its element type says, even an invalid one
        assertEquals(0, raw(10, 0, 0, 9, 0, 1, 'l', 99, 0, 0, 0, 0, 0).getListOrEmpty("l").size());
        assertEquals(0, raw(10, 0, 0, 9, 0, 1, 'l', 0, 0, 0, 0, 0, 0).getListOrEmpty("l").size());
    }

    @Test
    void rootNameIsSkippedUndecoded() throws IOException {
        // an invalid modified-UTF-8 root name (a lone continuation byte) is not an error: the name is never used
        Nbt.CompoundTag t = raw(10, 0, 1, 0x80, 1, 0, 1, 'b', 7, 0);
        assertEquals(7, t.getIntOr("b", 0));
    }

    @Test
    void largeArraysSurvive() throws IOException {
        byte[] big = new byte[3_000_000];
        for (int i = 0; i < big.length; i++) big[i] = (byte) (i * 31);
        long[] longs = new long[200_000];
        for (int i = 0; i < longs.length; i++) longs[i] = i * 0x9e3779b97f4a7c15L;
        Nbt.CompoundTag t = roundTrip(c("b", big, "l", longs));
        assertArrayEquals(big, t.getByteArray("b"));
        assertArrayEquals(longs, t.getLongArray("l"));
    }

    @Test
    void writerAndReaderAgreeOnTagIds() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        NbtWriter.write(new DataOutputStream(bytes), list(1.0f));
        assertEquals(Nbt.FLOAT, bytes.toByteArray()[0]);
        assertInstanceOf(Nbt.NumericTag.class, roundTrip(c("f", 1.0f)).get("f"));
        assertEquals(Nbt.FLOAT, roundTrip(c("f", 1.0f)).get("f").id());
    }
}
