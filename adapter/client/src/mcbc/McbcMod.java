package mcbc;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import mcb.build.Judge;
import mcb.mc.GameStates;
import mcb.schem.Schem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** MCB Builder (client): the client commands /mcbc ..., the pause key, the event log. */
public final class McbcMod implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("mcb-client");
    static final String[] EXTENSIONS = {"", ".schem", ".litematic", ".nbt", ".schematic"};
    static KeyMapping pauseKey;
    private static PrintWriter eventLog;

    @Override
    public void onInitializeClient() {
        mcb.mc.BlockFacts.install();   // (the planner's block tables come from this game, filled at the first build)
        Path gameDir = FabricLoader.getInstance().getGameDir();
        Config.load(FabricLoader.getInstance().getConfigDir());
        try {
            Files.createDirectories(gameDir.resolve("logs"));
            eventLog = new PrintWriter(Files.newBufferedWriter(gameDir.resolve("logs/mcb-client.log"), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND), true);
        } catch (IOException e) { LOG.warn("[mcbc] event log", e); }
        LocalClient.sentRotation = p -> { var a = (mcbc.mixin.LocalPlayerAccessor) p; return new float[] {a.mcbc$yRotLast(), a.mcbc$xRotLast()}; };
        LocalClient.pendingPredictions = level -> ((mcbc.mixin.PredictionAccessor) ((mcbc.mixin.ClientLevelAccessor) level).mcbc$predictions()).mcbc$states().size();
        pauseKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.mcb-client.pause", com.mojang.blaze3d.platform.InputConstants.KEY_K, KeyMapping.Category.MISC));
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> register(d));
        LOG.info("[mcbc] MCB Builder (client) loaded");
    }

    public static void event(String m) {
        if (eventLog != null && Config.verboseLog) eventLog.println(LocalDateTime.now() + " " + m);
        LOG.info("[mcbc] " + m);
    }

    /** The pause key, polled every tick (from the tick hook). */
    static void pollKey() {
        while (pauseKey != null && pauseKey.consumeClick()) {
            Job j = Driver.job;
            if (j == null) continue;
            if (j.state == Job.State.RUNNING) j.pause("pause key");
            else if (j.state == Job.State.PAUSED) j.resume();
        }
    }

    // ------------------------------------------------------------------ commands
    private static void register(CommandDispatcher<FabricClientCommandSource> d) {
        d.register(literal("mcbc")
            .then(literal("list").executes(McbcMod::list))
            .then(literal("build")
                .then(literal("litematica").executes(c -> fromLitematica(c, false)))
                .then(argument("name", StringArgumentType.string()).suggests((c, b) -> { for (String n : listNames()) b.suggest(quoteIfNeeded(n)); return b.buildFuture(); })
                    .executes(c -> build(c, null, false))
                    .then(literal("here").executes(c -> build(c, null, false)))
                    .then(argument("x", IntegerArgumentType.integer()).then(argument("y", IntegerArgumentType.integer()).then(argument("z", IntegerArgumentType.integer())
                        .executes(c -> build(c, new BlockPos(IntegerArgumentType.getInteger(c, "x"), IntegerArgumentType.getInteger(c, "y"), IntegerArgumentType.getInteger(c, "z")), false)))))))
            .then(literal("preview")
                .then(literal("litematica").executes(c -> fromLitematica(c, true)))
                .then(argument("name", StringArgumentType.string()).suggests((c, b) -> { for (String n : listNames()) b.suggest(quoteIfNeeded(n)); return b.buildFuture(); })
                    .executes(c -> build(c, null, true))
                    .then(literal("here").executes(c -> build(c, null, true)))
                    .then(argument("x", IntegerArgumentType.integer()).then(argument("y", IntegerArgumentType.integer()).then(argument("z", IntegerArgumentType.integer())
                        .executes(c -> build(c, new BlockPos(IntegerArgumentType.getInteger(c, "x"), IntegerArgumentType.getInteger(c, "y"), IntegerArgumentType.getInteger(c, "z")), true)))))))
            .then(literal("pause").executes(c -> { Job j = Driver.job; if (j == null) return fail(c, "no build is running"); j.pause("/mcbc pause"); return 1; }))
            .then(literal("resume").executes(c -> { Job j = Driver.job; if (j == null || j.state != Job.State.PAUSED) return fail(c, "nothing is paused"); j.resume(); say(c, "resuming when the chat closes"); return 1; }))
            .then(literal("stop").executes(c -> { Job j = Driver.job; Driver.preview = null; if (j == null) return fail(c, "no build is running"); j.stop("/mcbc stop"); return 1; }))
            .then(literal("status").executes(McbcMod::status))
            .then(literal("judge").executes(McbcMod::judge))
            .then(literal("reload").executes(c -> { Config.load(FabricLoader.getInstance().getConfigDir()); say(c, "settings read from config/mcb-client.properties"); return 1; }))
            .then(literal("supply")
                .then(literal("add").executes(McbcMod::supplyAdd))
                .then(literal("here").executes(c -> supplyHere(c, 4)).then(argument("r", IntegerArgumentType.integer(1, 8)).executes(c -> supplyHere(c, IntegerArgumentType.getInteger(c, "r")))))
                .then(literal("list").executes(McbcMod::supplyList))
                .then(literal("clear").executes(c -> { Driver.supplies.clear(); say(c, "no supply containers"); return 1; }))));
    }

    private static String quoteIfNeeded(String n) { return n.matches("[A-Za-z0-9_.+-]+") ? n : "\"" + n + "\""; }
    static void say(CommandContext<FabricClientCommandSource> c, String m) { c.getSource().sendFeedback(Component.literal("[MCB] " + m)); event(m); }
    static int fail(CommandContext<FabricClientCommandSource> c, String m) { c.getSource().sendError(Component.literal("[MCB] " + m)); return 0; }

    static Path schematicDir() { return FabricLoader.getInstance().getGameDir().resolve("schematics"); }

    static List<String> listNames() {
        Path dir = schematicDir();
        List<String> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) return out;
        try (Stream<Path> s = Files.list(dir)) {
            s.filter(Files::isRegularFile).map(p -> p.getFileName().toString())
                .filter(n -> n.endsWith(".schem") || n.endsWith(".litematic") || n.endsWith(".nbt") || n.endsWith(".schematic"))
                .sorted().forEach(out::add);
        } catch (IOException e) { /* nothing */ }
        return out;
    }

    private static Path find(String name) {
        Path dir = schematicDir();
        for (String ext : EXTENSIONS) {
            Path p = dir.resolve(name + ext).normalize();
            if (p.startsWith(dir) && Files.isRegularFile(p)) return p;
        }
        return null;
    }

    private static int list(CommandContext<FabricClientCommandSource> c) {
        List<String> names = listNames();
        if (names.isEmpty()) return fail(c, "no schematics in " + schematicDir());
        say(c, names.size() + " in " + schematicDir() + ":");
        for (String n : names) say(c, "  " + n);
        return names.size();
    }

    private static int build(CommandContext<FabricClientCommandSource> c, BlockPos origin, boolean previewOnly) {
        String name = StringArgumentType.getString(c, "name");
        Path file = find(name);
        if (file == null) return fail(c, "no schematic " + name + " in " + schematicDir() + " (/mcbc list)");
        Schem schem;
        try { schem = GameStates.reader().read(file); }
        catch (IOException | RuntimeException e) { return fail(c, "cannot read " + file.getFileName() + ": " + e.getMessage()); }
        return start(c, file.getFileName().toString(), schem, origin, previewOnly);
    }

    /** The placement selected in Litematica (no rotation or mirror): its schematic file, at its origin. */
    private static int fromLitematica(CommandContext<FabricClientCommandSource> c, boolean previewOnly) {
        if (!FabricLoader.getInstance().isModLoaded("litematica")) return fail(c, "Litematica is not installed");
        try {
            Class<?> dm = Class.forName("fi.dy.masa.litematica.data.DataManager");
            Object mgr = dm.getMethod("getSchematicPlacementManager").invoke(null);
            Object pl = mgr.getClass().getMethod("getSelectedSchematicPlacement").invoke(mgr);
            if (pl == null) return fail(c, "no placement is selected in Litematica");
            Path file = (Path) pl.getClass().getMethod("getSchematicFile").invoke(pl);
            BlockPos origin = (BlockPos) pl.getClass().getMethod("getOrigin").invoke(pl);
            Object rot = pl.getClass().getMethod("getRotation").invoke(pl), mir = pl.getClass().getMethod("getMirror").invoke(pl);
            if (!"NONE".equals(String.valueOf(rot)) || !"NONE".equals(String.valueOf(mir))) return fail(c, "the placement is rotated or mirrored (" + rot + ", " + mir + "); only unrotated placements are supported");
            if (file == null || !Files.isRegularFile(file)) return fail(c, "the placement has no schematic file on disk (an in-memory schematic): save it first");
            Schem schem = GameStates.reader().read(file);
            return start(c, file.getFileName().toString(), schem, origin, previewOnly);
        } catch (ReflectiveOperationException | IOException | RuntimeException e) {
            return fail(c, "cannot read the Litematica placement: " + e);
        }
    }

    static String lastKey; static long lastKeyMs;

    private static int start(CommandContext<FabricClientCommandSource> c, String name, Schem schem, BlockPos origin, boolean previewOnly) {
        Minecraft mc = c.getSource().getClient();
        if (Driver.job != null) return fail(c, "a build is running (" + Driver.job.name + "); /mcbc stop first");
        if (origin == null) origin = ahead(mc, schem);
        Job job = new Job(mc, name, schem, origin.getX(), origin.getY(), origin.getZ(), new ArrayList<>(Driver.supplies));
        if (job.layoutProblem != null) return fail(c, name + ": " + job.layoutProblem);
        Preview.Plan plan = job.plan();
        String key = name + "@" + origin.toShortString() + "#" + Driver.supplies;
        long now = System.currentTimeMillis();
        boolean confirmed = !previewOnly && key.equals(lastKey) && now - lastKeyMs < 60_000;
        if (!confirmed) {
            lastKey = previewOnly ? null : key; lastKeyMs = now;
            Driver.preview = new Preview(job, plan, key);
            say(c, name + ": " + job.target.blocks + " blocks, " + schem.sx + " x " + schem.sy + " x " + schem.sz + "; box " + job.boxText() + " (green). The bot works in " + job.siteText() + " (yellow).");
            say(c, "Supply containers: " + (job.containers.isEmpty() ? "none (inventory only: /mcbc supply add looks at one)" : job.containers.size() + " (blue; read when the build starts)") + ".");
            say(c, "In the box: " + plan.alreadyRight + " cells already right; it would BREAK " + plan.toBreak.size() + " blocks (red)" + (plan.tooHard.isEmpty() ? "" : ", and leave " + plan.tooHard.size() + " too hard to break by hand (purple)") + (plan.fluids.isEmpty() ? "" : "; " + plan.fluids.size() + " fluid cells (it cannot remove those)") + (plan.containersInBox > 0 ? "; " + plan.containersInBox + " supply container blocks (left alone)" : "") + ". Nothing outside the box is broken; its temporary blocks outside the box are taken away again.");
            StringBuilder sb = new StringBuilder();
            int k = 0;
            for (Map.Entry<Integer, Integer> e : plan.need.entrySet()) {
                int have = plan.carried.getOrDefault(e.getKey(), 0);
                if (k++ < 12) sb.append(e.getValue()).append(' ').append(Job.itemName(e.getKey())).append(" (carried ").append(have).append("), ");
            }
            if (k > 12) sb.append("... ").append(k - 12).append(" more kinds, ");
            say(c, "Materials: " + sb + "plus temporary blocks (32+ moss/dirt/netherrack/cobblestone) and 16+ scaffolding.");
            if (plan.unloadedChunks > 0) say(c, "Warning: " + plan.unloadedChunks + " chunks of the site are not loaded: come closer or raise the render distance.");
            say(c, "Safety: it stops on damage, a hostile mob within " + (int) Config.hostileRadius + ", a player within " + (int) Config.playerRadius + ", lava or fire within " + Config.hazardRadius + ", low food, a fall, any key you press (and K). Timer: " + Compat.timerState() + ".");
            if (!previewOnly) say(c, "Run the same /mcbc build again within 60 s to start.");
            return 1;
        }
        if (plan.unloadedChunks > 0) return fail(c, plan.unloadedChunks + " chunks of the site are not loaded: come closer or raise the render distance");
        lastKey = null;
        Driver.preview = null;
        Driver.job = job;
        job.start();
        say(c, "starting " + name + " at " + origin.toShortString() + ". Press K or any movement key to pause; /mcbc status, /mcbc stop.");
        return 1;
    }

    /** Where the build goes when no spot is given: 3 blocks in front of the player, at the feet's height, centred on the view. */
    private static BlockPos ahead(Minecraft mc, Schem schem) {
        var p = mc.player;
        int px = p.getBlockX(), py = p.getBlockY(), pz = p.getBlockZ();
        float yaw = ((p.getYRot() % 360) + 360) % 360;  // 0 south, 90 west, 180 north, 270 east
        int w = schem.sx, d = schem.sz;
        if (yaw >= 315 || yaw < 45) return new BlockPos(px - w / 2, py, pz + 3);
        if (yaw < 135) return new BlockPos(px - 3 - w + 1, py, pz - d / 2);
        if (yaw < 225) return new BlockPos(px - w / 2, py, pz - 3 - d + 1);
        return new BlockPos(px + 3, py, pz - d / 2);
    }

    private static int status(CommandContext<FabricClientCommandSource> c) {
        Job j = Driver.job != null ? Driver.job : Driver.last;
        if (j == null) return fail(c, "no build yet");
        say(c, j.status());
        if (j.state == Job.State.DONE && j.endText != null) say(c, j.endText);
        return 1;
    }

    private static int judge(CommandContext<FabricClientCommandSource> c) {
        Job j = Driver.job != null ? Driver.job : Driver.last;
        if (j == null || j.site == null) return fail(c, "no build to judge yet");
        Judge.Result r = Judge.judge(j.target, j.site.snapshot(mcb.mc.Convert.view(c.getSource().getLevel())), new mcb.mc.GameSurvival(c.getSource().getLevel()));
        say(c, j.name + ": " + (r.pass ? "PASS" : "FAIL") + " - " + r.failedSections + " of " + r.sections + " sections fail; judged cells " + r.targetCells + ", missing " + r.missing + ", wrong " + r.wrong + ", left behind " + r.leftover + " (as this client sees the world)");
        if (!r.firstBad.isEmpty()) say(c, "  first: " + r.firstBad);
        return r.pass ? 1 : 0;
    }

    // ------------------------------------------------------------------ supply containers
    static boolean isContainer(BlockState s) {
        return s.getBlock() instanceof ChestBlock || s.getBlock() instanceof BarrelBlock || s.getBlock() instanceof ShulkerBoxBlock;
    }

    private static boolean addSupply(Minecraft mc, BlockPos p) {
        BlockState s = mc.level.getBlockState(p);
        if (!isContainer(s)) return false;
        if (s.getBlock() instanceof ChestBlock && s.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockPos other = p.relative(ChestBlock.getConnectedDirection(s));
            if (Driver.supplies.contains(other)) return false;
        }
        if (Driver.supplies.contains(p)) return false;
        Driver.supplies.add(p.immutable());
        return true;
    }

    private static int supplyAdd(CommandContext<FabricClientCommandSource> c) {
        Minecraft mc = c.getSource().getClient();
        HitResult h = mc.hitResult;
        if (!(h instanceof BlockHitResult b) || h.getType() != HitResult.Type.BLOCK) return fail(c, "look at a chest, barrel or shulker box");
        if (!addSupply(mc, b.getBlockPos())) return fail(c, "not a container, or already registered");
        say(c, "supply container at " + b.getBlockPos().toShortString() + " (" + Driver.supplies.size() + " in all)");
        return 1;
    }

    private static int supplyHere(CommandContext<FabricClientCommandSource> c, int r) {
        Minecraft mc = c.getSource().getClient();
        BlockPos at = mc.player.blockPosition();
        int n = 0;
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-r, -r, -r), at.offset(r, r, r))) if (addSupply(mc, p)) n++;
        say(c, n + " supply containers added (" + Driver.supplies.size() + " in all)");
        return n;
    }

    private static int supplyList(CommandContext<FabricClientCommandSource> c) {
        if (Driver.supplies.isEmpty()) return fail(c, "no supply containers (/mcbc supply add, /mcbc supply here)");
        for (BlockPos p : Driver.supplies) say(c, "  " + p.toShortString() + " " + c.getSource().getLevel().getBlockState(p).getBlock().getName().getString());
        return Driver.supplies.size();
    }

    static Schem schemOf(Path p) throws IOException { return Schem.load(p); }
}
