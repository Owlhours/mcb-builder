package mcb.bot;

/** (the Fabric mod) What /mcb status shows of a running bot; read only. */
public final class ModView {
    private ModView() { }

    public static String phase(Builder b) {
        if (!b.mainDone) return b.cleaning ? "main pass (clearing scaffolding)" : "main pass";
        return b.cleaning ? "clean-up" : "end game";
    }

    public static int scaffold(Builder b) { return b.scaffold.size(); }
    public static String activity(Builder b) { return b.activity; }
    /** Cut off from the containers (no way there the bot may take, as things stand). */
    public static boolean stranded(Builder b) { return b.stranded; }
}
