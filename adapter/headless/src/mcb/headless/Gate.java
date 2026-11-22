package mcb.headless;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import mcb.env.Env;
import mcb.gate.Synth;
import mcb.schem.Schem;
import mcb.schem.StateResolver;

/**
 * The regression gate's runner: builds the generated test builds ({@link Synth}) in the headless game and prints
 * one result row per build. {@code gate/gate.sh} runs it and compares the rows with the expected ones.
 *
 * <pre>
 *   Gate run [name ...]        rows on stdout (all builds when no name is given), in the order given
 *   Gate export &lt;dir&gt; [name ...]   writes each build as a normalised .mcb file into &lt;dir&gt;/cache/&lt;2 hex&gt;/&lt;hash&gt;.mcb and
 *                              a row into &lt;dir&gt;/unique.tsv (the layout the harness and mcsim read), and prints
 *                              "&lt;name&gt; &lt;hash&gt;" lines
 *   Gate list                  the names, block counts and content digests
 * </pre>
 *
 * Row columns (tab-separated): name, digest, verdict, status, blocks, ticks, placed, skipped, missing, wrong, leftover,
 * sections, failed sections, scaffolding placed, scaffolding left, trips. Every column is behaviour: two runs
 * of the same bot give the same row.
 */
public final class Gate {
    public static final String HEADER = "name\tdigest\tverdict\tstatus\tblocks\tticks\tplaced\tskipped\tmissing\twrong\tleftover\tsections\tfailed\tscaffold\tscaffoldLeft\ttrips";

    public static void main(String[] a) throws Exception {
        PrintStream out = System.out;   // (the game's bootstrap routes System.out through its logger)
        String cmd = a.length == 0 ? "run" : a[0];
        List<String> names = new ArrayList<>();
        int from = cmd.equals("export") ? 2 : 1;
        for (int i = from; i < a.length; i++) names.add(a[i]);
        List<Synth.Build> builds = new ArrayList<>();
        if (names.isEmpty()) builds.addAll(Synth.all());
        else for (String n : names) {
            Synth.Build b = Synth.get(n);
            if (b == null) { System.err.println("no such test build: " + n); System.exit(2); }
            builds.add(b);
        }
        switch (cmd) {
            case "list" -> { for (Synth.Build b : builds) out.println(b.name + "\t" + b.sx + "x" + b.sy + "x" + b.sz + "\t" + b.blocks() + "\t" + b.digest()); }
            case "export" -> {
                net.minecraft.SharedConstants.tryDetectVersion();
                net.minecraft.server.Bootstrap.bootStrap();
                Path dir = Paths.get(a[1]);
                Path index = dir.resolve("unique.tsv");
                Files.createDirectories(dir);
                StringBuilder idx = new StringBuilder();
                for (Synth.Build b : builds) {
                    Schem s = schem(b);
                    String h = s.contentHash();
                    Path f = dir.resolve("cache").resolve(h.substring(0, 2)).resolve(h + ".mcb");
                    Files.createDirectories(f.getParent());
                    s.save(f);
                    idx.append(String.join("\t", h, "" + s.sx, "" + s.sy, "" + s.sz, "" + s.nonAir(), "0", s.format, "1", "synth:" + b.name)).append('\n');
                    out.println(b.name + "\t" + h);
                }
                Files.writeString(index, idx.toString(), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            }
            case "run" -> {
                Path universe = Paths.get(System.getProperty("java.io.tmpdir"), "mcb-gate-" + ProcessHandle.current().pid());
                Env env = Env.boot(universe);
                out.println(HEADER);
                for (Synth.Build b : builds) {
                    BuildRun.Outcome o = BuildRun.run(env, schem(b), BuildRun.TICK_LIMIT, false);
                    out.println(row(b, o));
                    out.flush();
                }
                try (var st = Files.walk(universe)) { st.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete()); } catch (Exception ignored) { }
            }
            default -> { System.err.println("usage: Gate run|export|list ..."); System.exit(2); }
        }
        System.exit(0);
    }

    /** The build as a normalised schematic of the running game version's state ids. */
    public static Schem schem(Synth.Build b) {
        StateResolver r = new StateResolver();
        char[] ids = new char[b.cells.length];
        for (int i = 0; i < ids.length; i++) {
            String c = b.cells[i];
            if (c == null) continue;
            int id = r.resolve(c, StateResolver.CURRENT);
            if (id < 0) throw new IllegalArgumentException(b.name + ": unknown block state " + c);
            ids[i] = (char) id;
        }
        Schem s = new Schem(b.sx, b.sy, b.sz, ids).cropped();
        s.format = "synth";
        s.dataVersion = StateResolver.CURRENT;
        return s;
    }

    static String row(Synth.Build b, BuildRun.Outcome o) {
        return String.join("\t", b.name, b.digest(), o.judge.pass ? "PASS" : "FAIL", o.status, "" + o.targetBlocks, "" + o.ticks, "" + o.placed, "" + o.skipped,
            "" + o.judge.missing, "" + o.judge.wrong, "" + o.judge.leftover, "" + o.judge.sections, "" + o.judge.failedSections,
            "" + o.scaffoldPlaced, "" + o.scaffoldLeft, "" + o.trips);
    }
}
