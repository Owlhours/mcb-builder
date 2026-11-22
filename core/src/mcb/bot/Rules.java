package mcb.bot;

/**
 * What the world the bot builds in is like, where that changes what the planner does. The defaults describe the
 * world the bot was developed and measured in (the headless runs and the singleplayer mod): fall damage off, nothing
 * but the build and its supply chests in the site, a world that changes only when the bot is told so.
 * {@link #client(int)} describes the client mod's world: a real server, fall damage on, the owner's blocks around the
 * build.
 *
 * <p>Each setting is one difference the client mod's planner used to make in its own copy of the planner.
 */
public final class Rules {
    /** Fall damage is on: planned drops are capped at {@link #maxFall16}, and a block that carries the bot over a
     *  harmful drop is not broken. */
    public boolean fallDamage;
    /** The furthest planned drop, in sixteenths of a block ({@link Nav#NO_FALL_LIMIT} when fall damage is off). */
    public int maxFall16 = Nav.NO_FALL_LIMIT;
    /** Nothing outside the build box is ever broken, except the bot's own temporary blocks; supply containers never. */
    public boolean ownBlocksOnly;
    /** The bot's picture of the site is refreshed from the client's world: the cells of every chunk that arrives, and
     *  every 128 ticks the cells around the bot (a change the bot was not told of is taken over). */
    public boolean readWorld;
    /** The jump out of the top of a scaffolding column is timed so that the player never comes to rest a hair above
     *  the column's top for a tick (a real server sets such a player back). {@code MCB_NO_CLIMBSIM} turns it off. */
    public boolean timedClimb;

    /** The headless runs' and the singleplayer mod's world. */
    public static Rules headless() { return new Rules(); }

    /** The client mod's world, with planned drops capped at {@code maxFall16} sixteenths of a block. */
    public static Rules client(int maxFall16) {
        Rules r = new Rules();
        r.fallDamage = true;
        r.maxFall16 = maxFall16;
        r.ownBlocksOnly = true;
        r.readWorld = true;
        r.timedClimb = true;
        return r;
    }
}
