package mcbc;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Other mods of the owner's client, by reflection (none of them is needed):
 *  - Baritone: whatever it is doing is cancelled when the bot starts, and again if it starts pathing while the bot
 *    runs (Baritone steers through the same player input);
 *  - Meteor: modules that would fight the bot for the hotbar, the mouse or the movement keys are switched off for
 *    the run and back on after; the state of Timer / Timer+ is reported.
 */
public final class Compat {
    private Compat() { }

    // ------------------------------------------------------------------ Baritone
    private static Object pathing;
    private static Method isPathing, cancelEverything;
    private static boolean baritoneTried;

    private static Object pathingBehavior() {
        if (baritoneTried) return pathing;
        baritoneTried = true;
        try {
            Class<?> api = Class.forName("baritone.api.BaritoneAPI");
            Object provider = api.getMethod("getProvider").invoke(null);
            Object primary = provider.getClass().getMethod("getPrimaryBaritone").invoke(provider);
            Method gp = Class.forName("baritone.api.IBaritone").getMethod("getPathingBehavior");
            pathing = gp.invoke(primary);
            Class<?> pb = Class.forName("baritone.api.behavior.IPathingBehavior");
            isPathing = pb.getMethod("isPathing");
            cancelEverything = pb.getMethod("cancelEverything");
        } catch (Throwable t) { pathing = null; }
        return pathing;
    }

    public static boolean baritonePresent() { return pathingBehavior() != null; }

    /** Cancels Baritone's processes; true if it was pathing. */
    public static boolean stopBaritone() {
        Object p = pathingBehavior();
        if (p == null) return false;
        try {
            boolean was = (Boolean) isPathing.invoke(p);
            cancelEverything.invoke(p);
            return was;
        } catch (Throwable t) { return false; }
    }

    public static boolean baritonePathing() {
        Object p = pathingBehavior();
        if (p == null) return false;
        try { return (Boolean) isPathing.invoke(p); } catch (Throwable t) { return false; }
    }

    // ------------------------------------------------------------------ Meteor
    private static Object meteorModule(String name) {
        if (!FabricLoader.getInstance().isModLoaded("meteor-client")) return null;
        try {
            Class<?> mods = Class.forName("meteordevelopment.meteorclient.systems.modules.Modules");
            Object m = mods.getMethod("get").invoke(null);
            return mods.getMethod("get", String.class).invoke(m, name);
        } catch (Throwable t) { return null; }
    }

    public static boolean meteorActive(String name) {
        Object m = meteorModule(name);
        if (m == null) return false;
        try { return (Boolean) m.getClass().getMethod("isActive").invoke(m); } catch (Throwable t) { return false; }
    }

    private static void toggle(Object m) {
        try { m.getClass().getMethod("toggle").invoke(m); } catch (Throwable t) { McbcMod.LOG.warn("[mcbc] meteor toggle", t); }
    }

    /** Switches off the configured Meteor modules that are on; returns their names (to switch on again). */
    public static List<String> meteorOff() {
        List<String> off = new ArrayList<>();
        for (String n : Config.meteorOff.split(",")) {
            n = n.trim();
            if (n.isEmpty()) continue;
            Object m = meteorModule(n);
            if (m != null && meteorActive(n)) { toggle(m); off.add(n); }
        }
        return off;
    }

    public static void meteorOn(List<String> names) {
        for (String n : names) { Object m = meteorModule(n); if (m != null && !meteorActive(n)) toggle(m); }
    }

    /** "timer" / "timer+" when on (reported in status; the bot keeps one view and one click per sent tick under it). */
    public static String timerState() {
        StringBuilder sb = new StringBuilder();
        for (String n : new String[] {"timer", "timer+"}) if (meteorActive(n)) sb.append(sb.length() > 0 ? ", " : "").append(n);
        return sb.length() == 0 ? "off" : sb.toString();
    }
}
