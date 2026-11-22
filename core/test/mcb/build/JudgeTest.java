package mcb.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mcb.schem.Schem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The judge's counting and its 3 % rule, on the made-up states of {@link SimplePalette}. */
class JudgeTest {
    @BeforeAll static void tables() { SimplePalette.install(); }

    /** A solid block of planks, sx * sy * sz, as a target. */
    static Target planks(int sx, int sy, int sz) {
        char[] ids = new char[sx * sy * sz];
        java.util.Arrays.fill(ids, (char) SimplePalette.PLANKS);
        return new Target(new Schem(sx, sy, sz, ids));
    }

    @Test void exactBuildPasses() {
        Target t = planks(4, 4, 4);
        Judge.Result r = Judge.judge(t, t.ids.clone());
        assertTrue(r.pass);
        assertEquals(0, r.missing + r.wrong + r.leftover);
        assertEquals(64, r.targetCells);
        assertEquals(1, r.sections);
    }

    @Test void countsMissingWrongAndLeftover() {
        Target t = new Target(new Schem(2, 1, 1, new char[] {(char) SimplePalette.PLANKS, 0}));
        char[] w = {(char) SimplePalette.STONE, (char) SimplePalette.DIRT};
        Judge.Result r = Judge.judge(t, w);
        assertEquals(1, r.wrong);
        assertEquals(1, r.leftover);
        assertEquals(0, r.missing);
        assertFalse(r.pass);
        r = Judge.judge(t, new char[2]);
        assertEquals(1, r.missing);
    }

    @Test void threePercentPerSection() {
        // 16 x 16 x 1 = 256 judged cells in one section: 7 bad cells is 2.7 % (passes), 8 is 3.1 % (fails)
        Target t = planks(16, 1, 16);
        char[] w = t.ids.clone();
        for (int i = 0; i < 7; i++) w[i] = 0;
        assertTrue(Judge.judge(t, w).pass);
        w[7] = 0;
        assertFalse(Judge.judge(t, w).pass);
    }

    @Test void sectionsAreSixteenCubed() {
        Target t = planks(17, 1, 1);   // two sections along x
        char[] w = t.ids.clone();
        w[16] = 0;                     // the second section's only cell is missing
        Judge.Result r = Judge.judge(t, w);
        assertEquals(2, r.sections);
        assertEquals(1, r.failedSections);
    }

    @Test void decorationIsExempt() {
        Target t = new Target(new Schem(2, 1, 1, new char[] {(char) SimplePalette.PLANKS, (char) SimplePalette.TORCH}));
        Judge.Result r = Judge.judge(t, new char[] {(char) SimplePalette.PLANKS, 0});
        assertTrue(r.pass);
        assertEquals(1, r.exemptCells);
        assertEquals(1, r.targetCells);
    }

    @Test void fluidsAreNotPartOfTheTarget() {
        Target t = new Target(new Schem(2, 1, 1, new char[] {(char) SimplePalette.PLANKS, (char) SimplePalette.WATER}));
        assertEquals(1, t.blocks);
    }
}
