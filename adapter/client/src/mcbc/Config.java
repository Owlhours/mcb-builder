package mcbc;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Settings, in {@code config/mcb-client.properties} (written with the defaults on first start). Conservative defaults. */
public final class Config {
    private Config() { }

    /** Hostile mob within this many blocks of the player: stop. */
    public static double hostileRadius = 16;
    /** Another player within this many blocks: stop (0 = off). */
    public static double playerRadius = 64;
    /** Lava or fire within this many blocks: stop. */
    public static int hazardRadius = 4;
    /** Health at or below this: stop (any damage stops anyway). */
    public static float minHealth = 14;
    /** Food at or below this: stop (eat, then resume). */
    public static int minFood = 6;
    /** Stop on any damage at all. */
    public static boolean stopOnDamage = true;
    /** Pause when the owner presses a movement / attack / use / inventory key or turns the view. */
    public static boolean pauseOnOwnerInput = true;
    /** Longest the game waits for the bot to finish thinking before it lets the game go on without it (ms). */
    public static long maxThinkMs = 8000;
    /** Blocks that take longer than this to break by hand (ticks) are not cleared from the box. */
    public static int maxClearTicks = 200;
    /** Meteor modules switched off while the bot runs (and on again after). */
    public static String meteorOff = "auto-tool,auto-eat,speed-mine,SuperInstaMine,pickaxe-keeper,bow-spam,no-ghost-blocks,auto-walk,scaffold,freecam,kill-aura,auto-totem,auto-replenish,auto-armor,inventory-tweaks,nuker,auto-mine,anchor-aura,crystal-aura";
    /** Furthest safe drop, in sixteenths of a block (fall damage is floor(fall - 3)). */
    public static int maxFall16 = 56;
    /** After a death and respawn in the build's dimension, start the build again by itself once the player is
     *  healthy and nothing hostile is near (otherwise it waits for /mcbc resume). Off: a bot that died probably died
     *  for a reason. */
    public static boolean autoResumeAfterDeath = false;
    /** Write a line per bot event to logs/mcb-client.log. */
    public static boolean verboseLog = true;

    static Path file;

    public static void load(Path configDir) {
        file = configDir.resolve("mcb-client.properties");
        Properties p = new Properties();
        if (Files.exists(file)) {
            try (Reader r = Files.newBufferedReader(file)) { p.load(r); } catch (IOException e) { McbcMod.LOG.warn("[mcbc] config", e); }
        }
        hostileRadius = d(p, "hostileRadius", hostileRadius);
        playerRadius = d(p, "playerRadius", playerRadius);
        hazardRadius = (int) d(p, "hazardRadius", hazardRadius);
        minHealth = (float) d(p, "minHealth", minHealth);
        minFood = (int) d(p, "minFood", minFood);
        stopOnDamage = Boolean.parseBoolean(p.getProperty("stopOnDamage", "" + stopOnDamage));
        pauseOnOwnerInput = Boolean.parseBoolean(p.getProperty("pauseOnOwnerInput", "" + pauseOnOwnerInput));
        maxThinkMs = (long) d(p, "maxThinkMs", maxThinkMs);
        maxClearTicks = (int) d(p, "maxClearTicks", maxClearTicks);
        meteorOff = p.getProperty("meteorOff", meteorOff);
        maxFall16 = (int) d(p, "maxFall16", maxFall16);
        verboseLog = Boolean.parseBoolean(p.getProperty("verboseLog", "" + verboseLog));
        autoResumeAfterDeath = Boolean.parseBoolean(p.getProperty("autoResumeAfterDeath", "" + autoResumeAfterDeath));
        save();
    }

    private static double d(Properties p, String k, double def) {
        try { return Double.parseDouble(p.getProperty(k, "" + def).trim()); } catch (NumberFormatException e) { return def; }
    }

    static void save() {
        Properties p = new Properties();
        p.setProperty("hostileRadius", "" + hostileRadius);
        p.setProperty("playerRadius", "" + playerRadius);
        p.setProperty("hazardRadius", "" + hazardRadius);
        p.setProperty("minHealth", "" + minHealth);
        p.setProperty("minFood", "" + minFood);
        p.setProperty("stopOnDamage", "" + stopOnDamage);
        p.setProperty("pauseOnOwnerInput", "" + pauseOnOwnerInput);
        p.setProperty("maxThinkMs", "" + maxThinkMs);
        p.setProperty("maxClearTicks", "" + maxClearTicks);
        p.setProperty("meteorOff", meteorOff);
        p.setProperty("maxFall16", "" + maxFall16);
        p.setProperty("verboseLog", "" + verboseLog);
        p.setProperty("autoResumeAfterDeath", "" + autoResumeAfterDeath);
        try (Writer w = Files.newBufferedWriter(file)) { p.store(w, "MCB Builder (client). Delete a line to get its default back."); } catch (IOException e) { McbcMod.LOG.warn("[mcbc] config", e); }
    }
}
