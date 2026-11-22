package mcb.example;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import mcb.bot.Grid;
import mcb.bot.Nav;
import mcb.build.SimplePalette;

/**
 * The route search on its own: a site with a wall across it, steps up to the wall, and a route from one side to
 * the top of the wall; then the same without the steps, where the route needs temporary blocks. No Minecraft: the
 * block tables come from {@link SimplePalette}.
 *
 * <pre>
 *   javac -cp fastutil.jar -d out $(find core/src core/examples -name '*.java' -path '*mcb/b*' -o -name NavExample.java)
 *   java -cp fastutil.jar:out mcb.example.NavExample
 * </pre>
 */
public final class NavExample {
    public static void main(String[] args) {
        SimplePalette.install();
        // a 9 x 6 x 9 box at (0, 1, 0), nothing to build in it (target all air), flat stone ground under it,
        // and 2 cells of margin all round
        int sx = 9, sy = 6, sz = 9;
        Grid g = new Grid(0, 1, 0, sx, sy, sz, new char[sx * sy * sz], 2, 2, 2, 2, SimplePalette.STONE);
        // a wall across the site, three blocks high, with steps up its south face (1, 2 and 3 blocks high)
        for (int x = 0; x < sx; x++) for (int y = 1; y <= 3; y++) g.set(x, y, 4, SimplePalette.STONE);
        for (int k = 1; k <= 3; k++) for (int y = 1; y <= k; y++) g.set(1 + k, y, 5, SimplePalette.STONE);
        Nav nav = new Nav(g, new IntOpenHashSet());
        nav.towersOn = false;                        // no scaffolding columns: use what stands there
        // from (1,1,8) on the ground to the top of the wall at (7,4,4); no temporary blocks anywhere
        boolean found = nav.search(1, 1, 8, 7, 4, 4, 100000, (x, y, z) -> false);
        print("with the steps", nav, found);
        // without the steps: the only way up is to put blocks down (pillar up, bridge across)
        for (int k = 1; k <= 3; k++) for (int y = 1; y <= k; y++) g.set(1 + k, y, 5, SimplePalette.AIR);
        found = nav.search(1, 1, 8, 7, 4, 4, 100000, (x, y, z) -> true);
        print("without the steps, temporary blocks allowed", nav, found);
    }

    static final String[] MOVES = {"walk", "jump", "drop", "pillar", "bridge", "dig", "clear", "mine", "climb", "descend", "tower", "gate", "ladder", "wing"};

    static void print(String what, Nav nav, boolean found) {
        System.out.println(what + ": " + (found ? "route found, cost " + nav.goalCost + ", " + nav.length + " places" : "no route"));
        for (int k = 0; found && k < nav.length; k++)
            System.out.println("  " + (k == 0 ? "start" : MOVES[nav.move[k]]) + " -> " + nav.path[k * 3] + "," + nav.path[k * 3 + 1] + "," + nav.path[k * 3 + 2]);
    }
}
