package mcb.schem;

import static mcb.schem.NbtWriter.c;
import static mcb.schem.NbtWriter.list;
import static mcb.schem.Samples.litematic;
import static mcb.schem.Samples.region;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Where a read schematic's box lies in the file's own coordinates (Schem.offX/offY/offZ): the client mod places the
 * file's origin where the owner says, so a floating build keeps its height and a region away from the origin keeps
 * its place. Nothing else reads these fields.
 */
class OffsetTest {
    @TempDir Path dir;

    @Test void croppingRecordsWhatItCut() {
        // 3 x 5 x 2, blocks only in layer 4, columns x = 1..2, row z = 1
        char[] ids = new char[3 * 5 * 2];
        ids[(4 * 2 + 1) * 3 + 1] = 7; ids[(4 * 2 + 1) * 3 + 2] = 7;
        Schem s = new Schem(3, 5, 2, ids).cropped();
        assertEquals(2, s.sx); assertEquals(1, s.sy); assertEquals(1, s.sz);
        assertEquals(1, s.offX); assertEquals(4, s.offY); assertEquals(1, s.offZ);
        // cropping again changes nothing, and a box with nothing to cut keeps offsets of 0
        Schem again = s.cropped();
        assertEquals(4, again.offY);
        Schem full = new Schem(1, 1, 1, new char[] {7}).cropped();
        assertEquals(0, full.offX + full.offY + full.offZ);
    }

    @Test void floatingLitematicKeepsItsHeight() throws Exception {
        // one region at (2, 0, -1) of the file, 3 x 5 x 1: layers 0-3 air, a row of stone in layer 4 (the bench's S3 shape)
        int[] cells = new int[15];
        cells[12] = 1; cells[13] = 1; cells[14] = 1;
        byte[] f = litematic(4903, region("r", 2, 0, -1, 3, 5, 1, list(c("Name", "minecraft:air"), c("Name", "minecraft:stone")), cells));
        Path p = Files.write(dir.resolve("floating.litematic"), f);
        Schem s = new SchemIO(new PaletteResolver()).read(p);
        assertEquals(1, s.sy);
        assertEquals(2, s.offX); assertEquals(4, s.offY); assertEquals(-1, s.offZ);
    }

    @Test void offsetsAreNotPartOfTheContent() {
        char[] ids = new char[8];
        ids[7] = 3;
        Schem a = new Schem(2, 2, 2, ids).cropped();
        Schem b = new Schem(1, 1, 1, new char[] {3});
        assertEquals(b.contentHash(), a.contentHash());
    }
}
