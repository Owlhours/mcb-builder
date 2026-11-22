package mcb.example;

import mcb.build.Judge;
import mcb.build.SimplePalette;
import mcb.build.Target;
import mcb.schem.Schem;

/**
 * The judge on its own: a small target, a "world" that has one block wrong and one missing, and the verdict. No
 * Minecraft: the block tables come from {@link SimplePalette} (with the game, the adapter fills them and the judge's
 * impossible-cell rule uses the game's own survival rules; see core/src/mcb/build/README.md).
 */
public final class JudgeExample {
    public static void main(String[] args) {
        SimplePalette.install();
        // the target: a 4 x 2 x 1 wall of planks with a torch in front... (index (y * sz + z) * sx + x)
        int sx = 4, sy = 2, sz = 2;
        char[] ids = new char[sx * sy * sz];
        for (int y = 0; y < sy; y++) for (int x = 0; x < sx; x++) ids[(y * sz + 0) * sx + x] = (char) SimplePalette.PLANKS;
        ids[(0 * sz + 1) * sx + 1] = (char) SimplePalette.TORCH;
        Target target = new Target(new Schem(sx, sy, sz, ids));
        // ...and what was built: one plank is stone, one is missing, the torch is missing (decoration: exempt)
        char[] world = target.ids.clone();
        world[(0 * sz + 0) * sx + 2] = (char) SimplePalette.STONE;
        world[(1 * sz + 0) * sx + 3] = 0;
        world[(0 * sz + 1) * sx + 1] = 0;
        Judge.Result r = Judge.judge(target, world);
        System.out.println(r);
        System.out.println("bad cells: " + r.examples);
        System.out.println("section 0: " + r.sectionList.get(0)[4] + " bad of " + r.sectionList.get(0)[3] + " judged cells; it passes at 3 % or fewer");
    }
}
