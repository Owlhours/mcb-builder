package mcb.headless;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import mcb.build.StateInfo;
import mcb.env.Env;
import mcb.schem.Schem;

/**
 * The client mod's placement of a floating build, headless: a file whose bottom layers are empty (a 5 x 5 platform 6
 * above the file's origin and a bridge 4 above it, nothing under them - an outside benchmark's "S3"). The readers crop
 * it to its blocks; the client puts the empty layers back (Schem.keepingHeight) so that the build lands where the
 * file says. This builds it with the client's settings (ClientRun) with the file's origin on the ground and checks
 * every target cell at its height above that origin. Exit 0 and "PASS" when all are right.
 */
public final class ClientFloatCheck {
    public static void main(String[] a) throws Exception {
        PrintStream out = System.out;
        Path universe = Files.createTempDirectory("mcb-float-");
        Env env = Env.boot(universe);
        StateInfo.init();
        // the file: 5 x 7 x 13, layers 0-3 empty
        int sx = 5, sy = 7, sz = 13;
        char[] ids = new char[sx * sy * sz];
        int planks = new mcb.mc.GameStates().resolve("minecraft:oak_planks", mcb.mc.GameStates.CURRENT);
        int stone = new mcb.mc.GameStates().resolve("minecraft:stone_bricks", mcb.mc.GameStates.CURRENT);
        for (int z = 0; z < 5; z++) for (int x = 0; x < 5; x++) ids[(6 * sz + z) * sx + x] = (char) planks;
        for (int z = 5; z < 13; z++) ids[(4 * sz + z) * sx + 2] = (char) stone;
        Schem read = new Schem(sx, sy, sz, ids).cropped();             // what the readers return
        Schem placed = read.keepingHeight();                            // what the client builds
        boolean shapeOk = read.offY == 4 && placed.sy == 7 && placed.offY == 0 && placed.sx == 5 && placed.sz == 13;
        // ClientRun puts the box at (64, ground, 64): the file's origin is there minus the sideways offsets (0 here)
        ClientRun.Outcome o = ClientRun.run(env, placed);
        // (the judge looked at the whole box, the empty layers included: the blocks stand 4 and 6 above the origin's
        // layer, and nothing was left under them; ClientRun wiped the site after judging)
        boolean ok = shapeOk && o.judge.pass && o.judge.missing == 0 && o.judge.wrong == 0 && o.judge.leftover == 0;
        out.println("float check: cropped offY=" + read.offY + " restored height=" + placed.sy + "; built " + o.status + ", "
            + o.judge + " ticks=" + o.ticks + " -> " + (ok ? "PASS" : "FAIL"));
        System.exit(ok ? 0 : 1);
    }
}
