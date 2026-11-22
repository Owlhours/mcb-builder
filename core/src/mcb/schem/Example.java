package mcb.schem;

import java.nio.file.Path;
import java.util.Arrays;

/**
 * A minimal use of the readers, with nothing but a JDK: reads a schematic file and prints its size and how many
 * blocks of each state it has, most common first.
 *
 * <pre>
 *   javac -d /tmp/schem core/src/mcb/schem/*.java
 *   java -cp /tmp/schem mcb.schem.Example my-house.litematic
 * </pre>
 */
public final class Example {
    private Example() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("usage: java mcb.schem.Example <file.litematic|.schem|.nbt|.schematic>");
            System.exit(2);
        }
        PaletteResolver states = new PaletteResolver();
        Schem s = new SchemIO(states).read(Path.of(args[0]));
        System.out.println(s.format + " (data version " + s.dataVersion + "): " + s.sx + " x " + s.sy + " x " + s.sz
                + ", " + s.nonAir() + " blocks, " + s.unknownCells + " unknown");
        // With a PaletteResolver the numbers in s.ids are positions in states.states().
        long[] count = new long[states.states().size()];
        for (char id : s.ids) count[id]++;
        Integer[] order = new Integer[count.length];
        for (int i = 0; i < order.length; i++) order[i] = i;
        Arrays.sort(order, (a, b) -> Long.compare(count[b], count[a]));
        for (int id : order) {
            if (id != 0 && count[id] > 0) System.out.printf("%10d  %s%n", count[id], states.state(id));
        }
    }
}
