package mcb.mod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import mcb.build.Judge;
import mcb.mc.GameStates;
import mcb.schem.Schem;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/** {@code /mcb build|stop|pause|resume|status|judge|list} (operators: singleplayer with cheats on). */
public final class McbCommands {
    private McbCommands() { }

    /** A build that was asked for once and waits for the same command again (the confirm step). */
    private record Pending(String name, BlockPos origin, long untilMs) { }
    private static Pending pending;
    static final long CONFIRM_MS = 10_000;
    static final String[] EXTENSIONS = {"", ".schem", ".litematic", ".nbt", ".schematic"};

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        SuggestionProvider<CommandSourceStack> files = (ctx, b) -> SharedSuggestionProvider.suggest(listNames(ctx.getSource().getServer()), b);
        d.register(Commands.literal("mcb").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .then(Commands.literal("build")
                .then(Commands.argument("name", StringArgumentType.string()).suggests(files)
                    .executes(c -> build(c, null))
                    .then(Commands.argument("origin", BlockPosArgument.blockPos())
                        .executes(c -> build(c, BlockPosArgument.getBlockPos(c, "origin"))))))
            .then(Commands.literal("stop").executes(McbCommands::stop))
            .then(Commands.literal("pause").executes(c -> pause(c, true)))
            .then(Commands.literal("resume").executes(c -> pause(c, false)))
            .then(Commands.literal("status").executes(McbCommands::status))
            .then(Commands.literal("judge").executes(McbCommands::judge))
            .then(Commands.literal("list").executes(McbCommands::list)));
    }

    private static void say(CommandSourceStack s, String m) { s.sendSuccess(() -> Component.literal(m), false); }
    private static int fail(CommandSourceStack s, String m) { s.sendFailure(Component.literal(m)); return 0; }

    static List<String> listNames(MinecraftServer server) {
        Path dir = Job.schematicDir(server);
        List<String> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) return out;
        try (Stream<Path> s = Files.list(dir)) {
            s.filter(Files::isRegularFile).map(p -> p.getFileName().toString())
                .filter(n -> n.endsWith(".schem") || n.endsWith(".litematic") || n.endsWith(".nbt") || n.endsWith(".schematic"))
                .sorted().forEach(out::add);
        } catch (IOException e) { /* an unreadable folder lists nothing */ }
        return out;
    }

    private static Path find(MinecraftServer server, String name) {
        Path dir = Job.schematicDir(server);
        for (String ext : EXTENSIONS) {
            Path p = dir.resolve(name + ext).normalize();
            if (p.startsWith(dir) && Files.isRegularFile(p)) return p;
        }
        return null;
    }

    private static int build(CommandContext<CommandSourceStack> c, BlockPos origin) {
        CommandSourceStack src = c.getSource();
        MinecraftServer server = src.getServer();
        String name = StringArgumentType.getString(c, "name");
        if (Job.current != null) return fail(src, "[mcb] a build is running (" + Job.current.name + "); /mcb stop first");
        Path file = find(server, name);
        if (file == null) return fail(src, "[mcb] no schematic " + name + " in " + Job.schematicDir(server) + " (/mcb list)");
        Schem schem;
        try { schem = GameStates.reader().read(file); }
        catch (IOException | RuntimeException e) { return fail(src, "[mcb] cannot read " + file.getFileName() + ": " + e.getMessage()); }
        ServerLevel level = src.getLevel();
        if (origin == null) origin = ahead(src, schem);
        Job job = new Job(server, level, file.getFileName().toString(), schem, origin.getX(), origin.getY(), origin.getZ(), m -> src.sendSuccess(() -> m, true));
        if (!job.fits()) return fail(src, "[mcb] " + name + " (" + schem.sx + " x " + schem.sy + " x " + schem.sz + ") does not fit between y " + level.getMinY() + " and " + level.getMaxY() + " from y " + origin.getY());
        long now = System.currentTimeMillis();
        Pending p = pending;
        if (p == null || !p.name.equals(name) || !p.origin.equals(origin) || now > p.untilMs) {
            pending = new Pending(name, origin, now + CONFIRM_MS);
            say(src, "[mcb] " + job.name + ": " + job.target.blocks + " blocks, " + schem.sx + " x " + schem.sy + " x " + schem.sz + ", its corner (0,0,0) at " + origin.toShortString() + ".");
            say(src, "[mcb] This CLEARS the box " + job.boxText() + ": everything in it becomes air over a grass floor at y " + job.by0 + ", and every entity in it but players is removed. Nothing outside it is touched. It also sets the world's rules: " + Job.RULES + ".");
            int r = job.chunkRadius(), vd = server.getPlayerList().getViewDistance();
            if (r > vd) say(src, "[mcb] Note: the bot sees block changes only within the render distance (now " + vd + " chunks; this site wants " + r + "). Raise it in Video Settings first.");
            say(src, "[mcb] Run the same command again within 10 s to start.");
            return 1;
        }
        pending = null;
        Job.current = job;
        say(src, "[mcb] starting " + job.name + " - /mcb status, /mcb pause, /mcb stop; /tick sprint or /tick rate to speed it up");
        Job.queued = job;
        return 1;
    }

    /** Where the build goes when no spot is given: its box starts 3 blocks in front of the player, centred on the view. */
    private static BlockPos ahead(CommandSourceStack src, Schem schem) {
        Vec3 pos = src.getPosition();
        Vec2 rot = src.getRotation();
        int px = (int) Math.floor(pos.x), py = (int) Math.floor(pos.y), pz = (int) Math.floor(pos.z);
        // a first layout at the player's feet gives the box's offsets from the build's corner
        Job probe = new Job(src.getServer(), src.getLevel(), "?", schem, 0, py, 0, m -> { });
        int w = probe.bx1 - probe.bx0 + 1, d = probe.bz1 - probe.bz0 + 1;
        float yaw = ((rot.y % 360) + 360) % 360;  // 0 south, 90 west, 180 north, 270 east
        int bx0, bz0;
        if (yaw >= 315 || yaw < 45) { bx0 = px - w / 2; bz0 = pz + 3; }
        else if (yaw < 135) { bx0 = px - 3 - w + 1; bz0 = pz - d / 2; }
        else if (yaw < 225) { bx0 = px - w / 2; bz0 = pz - 3 - d + 1; }
        else { bx0 = px + 3; bz0 = pz - d / 2; }
        // build corner = box corner + (corner - box corner of the probe)
        return new BlockPos(bx0 + (0 - probe.bx0), py, bz0 + (0 - probe.bz0));
    }

    private static int stop(CommandContext<CommandSourceStack> c) {
        pending = null;
        Job j = Job.current;
        if (j == null) return fail(c.getSource(), "[mcb] no build is running");
        j.stopAsked = true;
        say(c.getSource(), "[mcb] stopping " + j.name + " (the build stays as it is)");
        return 1;
    }

    private static int pause(CommandContext<CommandSourceStack> c, boolean on) {
        Job j = Job.current;
        if (j == null) return fail(c.getSource(), "[mcb] no build is running");
        j.paused = on;
        say(c.getSource(), "[mcb] " + j.name + (on ? " paused (the world goes on; /mcb resume)" : " resumed"));
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> c) {
        Job j = Job.current != null ? Job.current : Job.last;
        if (j == null) return fail(c.getSource(), "[mcb] no build yet");
        StringBuilder sb = new StringBuilder("[mcb] " + j.name + ": " + j.phase);
        if (j.builder != null) {
            sb.append(", ").append(mcb.bot.ModView.phase(j.builder)).append(j.paused ? " (paused)" : "");
            Judge.Result r = Judge.judge(j.target, j.site.snapshot(mcb.mc.Convert.view(j.level)), new mcb.mc.GameSurvival(j.level));
            long right = r.targetCells - r.missing - r.wrong;
            sb.append(" | cells right ").append(right).append(" / ").append(r.targetCells)
              .append(", placed ").append(j.builder.placed).append(", scaffolding standing ").append(mcb.bot.ModView.scaffold(j.builder))
              .append(" | ticks ").append(j.builder.ticks).append(" of ").append(Job.TICK_LIMIT);
        }
        say(c.getSource(), sb.toString());
        return 1;
    }

    private static int judge(CommandContext<CommandSourceStack> c) {
        Job j = Job.current != null ? Job.current : Job.last;
        if (j == null || j.site == null) return fail(c.getSource(), "[mcb] no build to judge yet");
        Judge.Result r = Judge.judge(j.target, j.site.snapshot(mcb.mc.Convert.view(j.level)), new mcb.mc.GameSurvival(j.level));
        CommandSourceStack s = c.getSource();
        say(s, "[mcb] " + j.name + ": " + (r.pass ? "PASS" : "FAIL") + " - " + r.failedSections + " of " + r.sections + " sections fail (a section passes at 3 % bad or less); judged cells " + r.targetCells + ", missing " + r.missing + ", wrong " + r.wrong + ", left behind " + r.leftover + ", exempt " + r.exemptCells);
        int shown = 0;
        for (int[] sec : r.sectionList) {
            boolean bad = sec[3] == 0 ? sec[4] > 0 : (double) sec[4] / sec[3] > Judge.LIMIT;
            if (!bad && r.sectionList.size() > 24) continue;
            if (shown++ >= 24) { say(s, "  ..."); break; }
            say(s, String.format("  section %d,%d,%d (blocks %d..%d, %d..%d, %d..%d): %s %d bad of %d", sec[0], sec[1], sec[2],
                sec[0] * 16, sec[0] * 16 + 15, sec[1] * 16, sec[1] * 16 + 15, sec[2] * 16, sec[2] * 16 + 15, bad ? "FAIL" : "pass", sec[4], sec[3]));
        }
        if (!r.badByBlock.isEmpty()) {
            List<Map.Entry<String, Integer>> e = new ArrayList<>(r.badByBlock.entrySet());
            e.sort((a, b) -> b.getValue() - a.getValue());
            StringBuilder sb = new StringBuilder("  bad cells:");
            for (int i = 0; i < Math.min(8, e.size()); i++) sb.append(' ').append(e.get(i).getKey()).append('=').append(e.get(i).getValue()).append(';');
            say(s, sb.toString());
        }
        if (!r.firstBad.isEmpty()) say(s, "  first: " + r.firstBad + " (in the schematic's own coordinates)");
        return r.pass ? 1 : 0;
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        MinecraftServer server = c.getSource().getServer();
        List<String> names = listNames(server);
        if (names.isEmpty()) return fail(c.getSource(), "[mcb] no schematics in " + Job.schematicDir(server));
        say(c.getSource(), "[mcb] " + names.size() + " in " + Job.schematicDir(server) + ":");
        for (String n : names) say(c.getSource(), "  " + n);
        return names.size();
    }
}
