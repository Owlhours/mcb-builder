package mcb.headless;

import java.nio.file.Files;
import java.nio.file.Path;
import mcb.build.ItemInfo;
import mcb.build.StateInfo;
import mcb.env.Env;
import mcb.env.ServerOracle;
import mcb.game.Hit;
import mcb.game.Oracle;

/**
 * The placement oracle on its own: "if I click there, holding this, looking that way, what appears?", answered by
 * the game's own placement code in a headless server, without placing anything.
 *
 * <pre>
 *   gate/gate.sh builds the classes into build/headless; then, with SERVER_CP from tools/deps.sh (server_cp):
 *   java -cp build/headless:$SERVER_CP mcb.headless.OracleExample [item]      (default oak_stairs)
 * </pre>
 */
public final class OracleExample {
    public static void main(String[] a) throws Exception {
        java.io.PrintStream out = System.out;   // (the game's bootstrap sends System.out to its log)
        String itemName = a.length > 0 ? a[0] : "oak_stairs";
        Path universe = Files.createTempDirectory("mcb-oracle-");
        Env env = Env.boot(universe);
        StateInfo.init();
        ServerOracle oracle = new ServerOracle(env);
        int item = -1;
        for (int k = 0; k < ItemInfo.N; k++) if (ItemInfo.name[k].equals(itemName)) item = k;
        if (item < 0) { System.err.println("no such item: " + itemName); System.exit(2); }
        // 1. the table: what the item becomes for each face of a lone block, part of the face, view and sneaking
        char[] t = oracle.table(item);
        out.println("Clicking the top face of a lone block, holding " + itemName + ", not sneaking:");
        for (float yaw : new float[] {180, -90, 0, 90}) {
            int st = t[Oracle.index(Hit.UP, 0, Oracle.yawClass(yaw), Oracle.pitchClass(45), false)];
            out.println("  looking " + (yaw == 180 ? "north" : yaw == -90 ? "east" : yaw == 0 ? "south" : "west") + ": "
                + (st == 0 ? "nothing" : StateInfo.name(st)));
        }
        out.println("Clicking the upper half of the north face, looking south: " + StateInfo.name(t[Oracle.index(Hit.NORTH, 2, Oracle.yawClass(0), Oracle.pitchClass(0), false)]));
        // 2. one concrete click in the world: the top of the ground block under (64, y, 64)
        int x = 64, z = 64, y = mcb.env.Depot.groundTop(env, x, z) - 1;
        Hit hit = new Hit(x + 0.5, y + 1, z + 0.5, Hit.UP, x, y, z, false);
        int st = oracle.predict(item, hit, 0, 60, false);
        out.println("Clicking the ground at " + x + "," + y + "," + z + " from the north: " + (st == 0 ? "nothing" : StateInfo.name(st))
            + " would appear at " + oracle.placedX + "," + oracle.placedY + "," + oracle.placedZ);
        System.exit(0);
    }
}
