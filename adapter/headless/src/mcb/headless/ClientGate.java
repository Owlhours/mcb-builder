package mcb.headless;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import mcb.env.Env;
import mcb.gate.Synth;
import mcb.schem.Schem;
import mcb.schem.StateResolver;

/**
 * Builds with the client mod's planner settings ({@link ClientRun}) in the headless game, one row each:
 * <pre>
 *   ClientGate synth [name ...]     the generated test builds (all when no name is given)
 *   ClientGate mcb &lt;file.mcb&gt; ...    normalised builds (as the harness's corpus holds them)
 * </pre>
 * Row: name, verdict, status, blocks, ticks, placed, broken, missing, wrong, leftover, failed sections, scaffolding
 * placed and left, trips, breaks refused (outside the box, over a drop), survey failures, died, trace.
 */
public final class ClientGate {
    public static void main(String[] a) throws Exception {
        PrintStream out = System.out;
        Path universe = Paths.get(System.getProperty("java.io.tmpdir"), "mcb-cgate-" + ProcessHandle.current().pid());
        Env env = Env.boot(universe);
        if (a[0].equals("synth")) {
            java.util.List<Synth.Build> builds = new java.util.ArrayList<>();
            if (a.length == 1) builds.addAll(Synth.all());
            for (int i = 1; i < a.length; i++) builds.add(Synth.get(a[i]));
            for (Synth.Build b : builds) { out.println(row(b.name, ClientRun.run(env, schem(b)))); out.flush(); }
        } else {
            for (int i = 1; i < a.length; i++) {
                Path f = Paths.get(a[i]);
                out.println(row(f.getFileName().toString().replace(".mcb", ""), ClientRun.run(env, Schem.load(f))));
                out.flush();
            }
        }
        try (var st = Files.walk(universe)) { st.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete()); } catch (Exception ignored) { }
        System.exit(0);
    }

    static Schem schem(Synth.Build b) {
        StateResolver r = new StateResolver();
        char[] ids = new char[b.cells.length];
        for (int i = 0; i < ids.length; i++) if (b.cells[i] != null) ids[i] = (char) r.resolve(b.cells[i], StateResolver.CURRENT);
        Schem s = new Schem(b.sx, b.sy, b.sz, ids).cropped();
        s.format = "synth";
        return s;
    }

    static String row(String name, ClientRun.Outcome o) {
        return String.join("\t", name, o.judge.pass ? "PASS" : "FAIL", o.status, "" + o.targetBlocks, "" + o.ticks, "" + o.placed, "" + o.broken,
            "" + o.judge.missing, "" + o.judge.wrong, "" + o.judge.leftover, "" + o.judge.failedSections, "" + o.scaffoldPlaced, "" + o.scaffoldLeft,
            "" + o.trips, "" + o.refusedBreaks, "" + o.fallBreaksRefused, "" + o.surveyFailures, "" + o.died, Long.toHexString(o.trace));
    }
}
