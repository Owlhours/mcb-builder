package mcb.headless;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import mcb.build.StateInfo;
import mcb.env.Env;
import mcb.schem.Schem;
import net.minecraft.world.item.ItemStack;

/**
 * The client mod running out of temporary blocks, headless. A tall glass tower (walls only: every block above the
 * second layer needs a pillar, a bridge or scaffolding to be reached) is built with the client's settings
 * ({@link ClientRun}) and only the temporary blocks the client asks for before it starts (32, and 16 scaffolding).
 * They run out on the way. The bot used to go on without them, skip every cell it could not reach and end short with
 * nothing said (an outside benchmark's all-glass builds); now it asks the owner (Builder.shortage, which pauses the
 * client mod with the reason). Two runs:
 * <ul>
 * <li>no one answers: the bot must have asked;</li>
 * <li>the owner hands over three stacks each time: the build must end complete, nothing left standing.</li>
 * </ul>
 * Exit 0 and "PASS" when both hold.
 */
public final class ClientShortageCheck {
    public static void main(String[] a) throws Exception {
        PrintStream out = System.out;
        Path universe = Files.createTempDirectory("mcb-short-");
        Env env = Env.boot(universe);
        StateInfo.init();
        int sx = 7, sy = 20, sz = 7;
        char[] ids = new char[sx * sy * sz];
        int glass = new mcb.mc.GameStates().resolve("minecraft:glass", mcb.mc.GameStates.CURRENT);
        for (int y = 0; y < sy; y++) for (int z = 0; z < sz; z++) for (int x = 0; x < sx; x++)
            if (x == 0 || z == 0 || x == sx - 1 || z == sz - 1) ids[(y * sz + z) * sx + x] = (char) glass;
        Schem tower = new Schem(sx, sy, sz, ids);
        ClientRun.looseSupply = 32;
        ClientRun.scaffoldingSupply = 16;
        int[] asked = new int[2];
        ClientRun.owner = (c, item) -> { asked[0]++; return false; };
        ClientRun.Outcome alone = ClientRun.run(env, tower);
        ClientRun.owner = (c, item) -> {
            asked[1]++;
            for (int k = 0; k < 3; k++) c.player.getInventory().add(new ItemStack(mcb.mc.BlockFacts.item(item), 64));
            return true;
        };
        ClientRun.Outcome helped = ClientRun.run(env, tower);
        boolean ok = asked[0] > 0 && asked[1] > 0 && helped.judge.pass && helped.judge.missing == 0 && helped.judge.wrong == 0 && helped.judge.leftover == 0;
        out.println("shortage check: no answer: asked " + asked[0] + "x, " + alone.status + " missing=" + alone.judge.missing + " leftover=" + alone.judge.leftover
            + "; owner hands over blocks: asked " + asked[1] + "x, " + helped.status + " " + helped.judge + " ticks=" + helped.ticks + " -> " + (ok ? "PASS" : "FAIL"));
        System.exit(ok ? 0 : 1);
    }
}
