package mcbc;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * (MCB client) The rules oracle on the client: "if I click there, holding this, looking that way, what appears?",
 * answered by the game's own placement code (BlockItem.updatePlacementContext / getPlacementState) against the
 * client's world, with a probe player that is never added to the world. Nothing is sent to the server.
 *
 * The per-item tables and the "does it float" question need a lone block with nothing around it. There is no
 * scratch world on a client, so they use a 5 x 5 x 5 spot of air near the top of the world in a loaded chunk, and
 * set blocks there in the client's copy of the world only, putting the air back before the game runs again (the bot
 * thinks while the game thread waits, see {@link LocalClient}). The server never hears of it.
 */
public final class ClientOracle extends mcb.game.Oracle {
    private final LocalClient client;
    private final RemotePlayer probe;
    private final Method getPlacementState;
    private final Map<Integer, char[]> tables = new HashMap<>();

    public ClientOracle(LocalClient client) {
        this.client = client;
        ClientLevel level = client.level();
        this.probe = new RemotePlayer(level, new GameProfile(UUID.nameUUIDFromBytes("mcb:probe".getBytes()), "Probe"));
        try {
            getPlacementState = BlockItem.class.getDeclaredMethod("getPlacementState", BlockPlaceContext.class);
            getPlacementState.setAccessible(true);
        } catch (NoSuchMethodException e) { throw new RuntimeException(e); }
    }

    public static Vec3 viewVector(float pitch, float yaw) {
        float p = pitch * ((float) Math.PI / 180f), y = -yaw * ((float) Math.PI / 180f);
        float cy = net.minecraft.util.Mth.cos(y), sy = net.minecraft.util.Mth.sin(y), cp = net.minecraft.util.Mth.cos(p), sp = net.minecraft.util.Mth.sin(p);
        return new Vec3(sy * cp, -sp, cy * cp);
    }

    /** What the crosshair of a player with the client's eye position and the given view direction rests on (blocks only). */
    @Override public mcb.game.Hit pick(mcb.game.Client client, float yaw, float pitch) {
        LocalClient c = (LocalClient) client;
        Vec3 eye = c.player.getEyePosition();
        Vec3 view = viewVector(pitch, yaw);
        double r = c.reach();
        BlockHitResult hit = c.level().clip(new ClipContext(eye, eye.add(view.x * r, view.y * r, view.z * r), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, c.player));
        return hit.getType() == HitResult.Type.BLOCK ? mcb.mc.Convert.hit(hit) : null;
    }

    @Override public int predict(int itemId, mcb.game.Hit h, float yaw, float pitch, boolean sneak) { return predict(mcb.mc.BlockFacts.item(itemId), mcb.mc.Convert.hit(h), yaw, pitch, sneak); }

    private int predict(Item item, BlockHitResult hit, float yaw, float pitch, boolean sneak) {
        predictions++;
        if (!(item instanceof BlockItem bi)) return 0;
        probe.setYRot(yaw);
        probe.setYHeadRot(yaw);
        probe.setXRot(pitch);
        probe.setShiftKeyDown(sneak);
        try {
            BlockPlaceContext ctx = new BlockPlaceContext(probe, InteractionHand.MAIN_HAND, new ItemStack(item), hit);
            if (!ctx.canPlace()) return 0;
            BlockPlaceContext ctx2 = bi.updatePlacementContext(ctx);
            if (ctx2 == null) return 0;
            BlockState st = (BlockState) getPlacementState.invoke(bi, ctx2);
            if (st == null) return 0;
            BlockPos at = ctx2.getClickedPos();
            placedX = at.getX(); placedY = at.getY(); placedZ = at.getZ();
            return Block.getId(st);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 0;
        }
    }

    @Override public boolean survivesWithout(int x, int y, int z, int gx, int gy, int gz) { return survivesWithout(new BlockPos(x, y, z), new BlockPos(gx, gy, gz)); }

    private boolean survivesWithout(BlockPos at, BlockPos gone) {
        ClientLevel level = client.level();
        BlockState st = level.getBlockState(at);
        java.lang.reflect.InvocationHandler h = (proxy, m, args) -> {
            if (args != null && args.length == 1 && gone.equals(args[0])) {
                if (m.getName().equals("getBlockState")) return Blocks.AIR.defaultBlockState();
                if (m.getName().equals("getFluidState")) return net.minecraft.world.level.material.Fluids.EMPTY.defaultFluidState();
            }
            try { return m.isDefault() ? java.lang.reflect.InvocationHandler.invokeDefault(proxy, m, args) : m.invoke(level, args); }
            catch (java.lang.reflect.InvocationTargetException e) { throw e.getCause(); }
        };
        net.minecraft.world.level.LevelReader view = (net.minecraft.world.level.LevelReader) java.lang.reflect.Proxy.newProxyInstance(
            net.minecraft.world.level.LevelReader.class.getClassLoader(), new Class<?>[] {net.minecraft.world.level.LevelReader.class}, h);
        try { return st.canSurvive(view, at); } catch (RuntimeException e) { return true; }
    }

    // ------------------------------------------------------------------ the scratch spot (client copy only)
    private BlockPos scratch;

    /** A 5 x 5 x 5 spot of air near the top of the world, in a loaded chunk next to the player. */
    private BlockPos scratch() {
        ClientLevel level = client.level();
        int top = level.getMaxY() - 3;
        if (scratch != null && allAir(level, scratch)) return scratch;
        int px = (int) Math.floor(client.x()), pz = (int) Math.floor(client.z());
        for (int dy = 0; dy < 40; dy += 5) for (int d = 0; d < 32; d += 5) {
            BlockPos p = new BlockPos(px + d, top - dy, pz);
            if (LocalClient.loaded(level, p.getX(), p.getZ()) && allAir(level, p)) { scratch = p; return p; }
        }
        throw new IllegalStateException("no scratch spot of air near the top of the world");
    }

    private static boolean allAir(ClientLevel level, BlockPos c) {
        for (int dx = -2; dx <= 2; dx++) for (int dy = -2; dy <= 2; dy++) for (int dz = -2; dz <= 2; dz++)
            if (!level.getBlockState(c.offset(dx, dy, dz)).isAir()) return false;
        return true;
    }

    private void put(BlockPos p, BlockState s) { client.level().setBlock(p, s, 2 | 16); }

    private byte[] floats;

    @Override public boolean floats(int stateId) {
        if (floats == null) floats = new byte[Block.BLOCK_STATE_REGISTRY.size()];
        if (floats[stateId] == 0) {
            BlockPos s = scratch();
            boolean ok;
            try { ok = Block.stateById(stateId).canSurvive(client.level(), s); } catch (RuntimeException e) { ok = true; }
            floats[stateId] = (byte) (ok ? 1 : 2);
        }
        return floats[stateId] == 1;
    }

    private static final Block[] BASES = {Blocks.STONE, Blocks.DIRT, Blocks.FARMLAND, Blocks.SAND, Blocks.SOUL_SAND, Blocks.END_STONE, Blocks.JUNGLE_LOG};

    @Override public char[] table(int itemId) {
        Item item = mcb.mc.BlockFacts.item(itemId);
        char[] t = tables.get(itemId);
        if (t != null) return t;
        t = new char[TABLE_SIZE];
        if (item instanceof BlockItem) {
            BlockPos scratch = scratch();
            client.suppressChanges(true);
            try {
                for (Block base : BASES) {
                    put(scratch, base.defaultBlockState());
                    boolean any = false;
                    try {
                        for (int face = 0; face < 6; face++) {
                            Direction dir = Direction.from3DDataValue(face);
                            for (int quad = 0; quad < 4; quad++) {
                                double a = (quad & 1) != 0 ? 0.75 : 0.25, b = (quad & 2) != 0 ? 0.75 : 0.25;
                                Vec3 loc = switch (face) {
                                    case 0 -> new Vec3(scratch.getX() + a, scratch.getY(), scratch.getZ() + b);
                                    case 1 -> new Vec3(scratch.getX() + a, scratch.getY() + 1, scratch.getZ() + b);
                                    case 2 -> new Vec3(scratch.getX() + a, scratch.getY() + b, scratch.getZ());
                                    case 3 -> new Vec3(scratch.getX() + a, scratch.getY() + b, scratch.getZ() + 1);
                                    case 4 -> new Vec3(scratch.getX(), scratch.getY() + b, scratch.getZ() + a);
                                    default -> new Vec3(scratch.getX() + 1, scratch.getY() + b, scratch.getZ() + a);
                                };
                                BlockHitResult hit = new BlockHitResult(loc, dir, scratch, false);
                                for (int yc = 0; yc < NYAW; yc++) for (int pc = 0; pc < NPITCH; pc++) for (int sn = 0; sn < 2; sn++) {
                                    int st = predict(item, hit, yawCentre(yc), pitchCentre(pc), sn == 1);
                                    if (st != 0) any = true;
                                    t[index(face, quad, yc, pc, sn == 1)] = (char) st;
                                }
                            }
                        }
                    } finally {
                        put(scratch, Blocks.AIR.defaultBlockState());
                    }
                    if (any) break;
                }
            } finally {
                client.suppressChanges(false);
            }
        }
        tables.put(itemId, t);
        return t;
    }
}
