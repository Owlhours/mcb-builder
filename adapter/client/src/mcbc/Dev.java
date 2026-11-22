package mcbc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.Minecraft;

/**
 * Test hook, inert unless the game is started with -Dmcbc.control=&lt;file&gt;: every half second the file is read and
 * each new line is sent as a command from the player (client commands such as "mcbc build ..." run here), and a
 * one-line status is written next to it ("&lt;file&gt;.status"). Used by the headless lab tests; the owner never needs it.
 */
final class Dev {
    private Dev() { }
    private static final String FILE = System.getProperty("mcbc.control");
    private static int done, t;

    /** Writes what a schematic needs (item id, count per line) to &lt;file&gt;.needs. */
    private static void needs(String name) throws IOException {
        Path f = McbcMod.schematicDir().resolve(name);
        mcb.schem.Schem schem = mcb.mc.GameStates.reader().read(f);
        mcb.build.StateInfo.init();
        var site = new mcb.build.Site(new mcb.build.Target(schem), 0, 0, 0, null);
        StringBuilder sb = new StringBuilder();
        for (var e : site.requirements().entrySet()) sb.append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(e.getKey())).append(' ').append(e.getValue()).append('\n');
        sb.append("#size ").append(schem.sx).append(' ').append(schem.sy).append(' ').append(schem.sz).append('\n');
        Files.writeString(Path.of(FILE + ".needs"), sb.toString());
    }

    /** "!key w 40": holds a key mapping down for n ticks (a stand-in for the owner pressing it). */
    private static int keyTicks; private static net.minecraft.client.KeyMapping held;
    private static void key(Minecraft mc, String arg) {
        String[] a = arg.split(" ");
        var o = mc.options;
        held = switch (a[0]) { case "w" -> o.keyUp; case "s" -> o.keyDown; case "jump" -> o.keyJump; case "use" -> o.keyUse; case "attack" -> o.keyAttack; default -> o.keyUp; };
        keyTicks = a.length > 1 ? Integer.parseInt(a[1]) : 20;
        held.setDown(true);
    }

    static void tick(Minecraft mc) {
        if (held != null && --keyTicks <= 0) { held.setDown(false); held = null; }
        if (FILE == null || (t++ % 10) != 0) return;
        Path p = Path.of(FILE);
        try {
            if (mc.player != null && mc.getConnection() != null && Files.exists(p)) {
                List<String> lines = Files.readAllLines(p);
                while (done < lines.size()) {
                    String l = lines.get(done++).trim();
                    if (l.isEmpty() || l.startsWith("#")) continue;
                    McbcMod.event("control: " + l);
                    if (l.equals("!respawn")) { mc.player.respawn(); continue; }
                    if (l.startsWith("!needs ")) { needs(l.substring(7).trim()); continue; }
                    if (l.startsWith("!key ")) { key(mc, l.substring(5).trim()); continue; }
                    if (l.startsWith("!chat ")) { mc.getConnection().sendChat(l.substring(6)); continue; }
                    if (l.startsWith("/")) l = l.substring(1);
                    mc.getConnection().sendCommand(l);
                }
            }
            if (mc.player != null) {
                Job j = Driver.job != null ? Driver.job : Driver.last;
                var pl = mc.player;
                String s = String.format(java.util.Locale.ROOT, "pos=%.2f,%.2f,%.2f hp=%.1f food=%d job=%s", pl.getX(), pl.getY(), pl.getZ(), pl.getHealth(), pl.getFoodData().getFoodLevel(),
                    j == null ? "none" : (j.state + " | " + j.status().replace('\n', ' ')));
                Files.writeString(Path.of(FILE + ".status"), s + "\n");
            }
        } catch (IOException | RuntimeException e) { McbcMod.LOG.warn("[mcbc] control", e); }
    }
}
