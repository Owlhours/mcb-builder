package mcbc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** The checks made at the start of every tick of a run, before the bot gets its turn. Any hit stops the bot. */
public final class Safety {
    private Safety() { }

    public static final String CHAT = "chat open";

    /** @return why the bot must stop now, or null */
    public static String check(Minecraft mc, Job j) {
        LocalPlayer p = mc.player;
        ClientLevel level = mc.level;
        if (p == null || level == null) return "left the world";
        if (p.isDeadOrDying()) return "died";
        float hp = p.getHealth();
        float was = j.lastHealth;
        j.lastHealth = hp;
        if (Config.stopOnDamage && was >= 0 && hp < was - 0.01f) return "took damage (health " + fmt(was) + " -> " + fmt(hp) + ")";
        if (hp <= Config.minHealth) return "health low (" + fmt(hp) + ")";
        if (p.getFoodData().getFoodLevel() <= Config.minFood) return "hungry (food " + p.getFoodData().getFoodLevel() + "): eat, then /mcbc resume";
        if (p.isInLava() || p.isOnFire()) return "in lava or on fire";
        if (p.isInWater()) return "in water";
        if (!p.onGround() && !p.onClimbable() && p.getDeltaMovement().y < 0) {
            // where will it land, and will that hurt? (fall damage is floor(fall - 3); a ladder, scaffolding, vines,
            // water or a bed catch the fall)
            double total = p.fallDistance + landingDrop(level, p);
            if (total > 3.9) return "falling (" + fmt((float) total) + " blocks: it would hurt; feet at " + fmt((float) p.getY()) + ", fell " + fmt((float) p.fallDistance) + ", vy " + String.format(java.util.Locale.ROOT, "%.3f", p.getDeltaMovement().y) + ")";
        }
        // other players and hostile mobs
        if (Config.playerRadius > 0) for (Player o : level.players()) {
            if (o == p || o.isSpectator()) continue;
            double d = o.distanceTo(p);
            if (d <= Config.playerRadius) return "player " + o.getName().getString() + " " + Math.round(d) + " blocks away";
        }
        AABB box = p.getBoundingBox().inflate(Config.hostileRadius);
        for (Entity e : level.getEntities(p, box)) {
            if (e instanceof Enemy && e.isAlive() && e.distanceTo(p) <= Config.hostileRadius)
                return "hostile " + e.getType().getDescription().getString() + " " + Math.round(e.distanceTo(p)) + " blocks away";
        }
        // lava and fire nearby
        int r = Config.hazardRadius;
        BlockPos c = p.blockPosition();
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        for (int dy = -r; dy <= r; dy++) for (int dz = -r; dz <= r; dz++) for (int dx = -r; dx <= r; dx++) {
            BlockState s = level.getBlockState(q.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz));
            if (s.getFluidState().is(FluidTags.LAVA)) return "lava at " + q.toShortString();
            if (s.getBlock() instanceof BaseFireBlock) return "fire at " + q.toShortString();
        }
        // the owner
        if (mc.gui.screen() != null && !(mc.gui.screen() instanceof AbstractContainerScreen)) {
            if (mc.gui.screen() instanceof ChatScreen) return CHAT;
            return "screen open (" + mc.gui.screen().getClass().getSimpleName() + ")";
        }
        if (Config.pauseOnOwnerInput) {
            var o = mc.options;
            if (o.keyUp.isDown() || o.keyDown.isDown() || o.keyLeft.isDown() || o.keyRight.isDown()) return "you pressed a movement key";
            if (o.keyJump.isDown() || o.keyShift.isDown()) return "you pressed jump/sneak";
            if (o.keyAttack.isDown() || o.keyUse.isDown()) return "you clicked";
            if (o.keyInventory.isDown() || o.keyDrop.isDown() || o.keySwapOffhand.isDown()) return "you pressed an inventory key";
            for (var k : o.keyHotbarSlots) if (k.isDown()) return "you picked a hotbar slot";
        }
        if (j.recentSetbacks() > 5) return "the server set the player back " + j.recentSetbacks() + " times in a minute";
        return null;
    }

    /** How far the player still falls before it lands on something that does not catch it (0 if it is caught). */
    static double landingDrop(ClientLevel level, LocalPlayer p) {
        // every column the player's box is over: it lands on the highest thing under any of them
        double best = 64;
        var bb = p.getBoundingBox();
        for (int x = net.minecraft.util.Mth.floor(bb.minX); x <= net.minecraft.util.Mth.floor(bb.maxX - 1e-7); x++)
            for (int z = net.minecraft.util.Mth.floor(bb.minZ); z <= net.minecraft.util.Mth.floor(bb.maxZ - 1e-7); z++)
                best = Math.min(best, columnDrop(level, p.getY(), x, z));
        return best;
    }

    static double columnDrop(ClientLevel level, double feet, int x, int z) {
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        for (int y = net.minecraft.util.Mth.floor(feet); y > feet - 64 && y > level.getMinY(); y--) {
            BlockState s = level.getBlockState(q.set(x, y, z));
            if (!s.getFluidState().isEmpty()) return 0;
            if (s.is(net.minecraft.tags.BlockTags.CLIMBABLE) || s.getBlock() instanceof net.minecraft.world.level.block.ScaffoldingBlock
                || s.getBlock() instanceof net.minecraft.world.level.block.BedBlock || s.getBlock() instanceof net.minecraft.world.level.block.SlimeBlock
                || s.getBlock() instanceof net.minecraft.world.level.block.HayBlock || s.getBlock() instanceof net.minecraft.world.level.block.PowderSnowBlock) return 0;
            var shape = s.getCollisionShape(level, q);
            if (!shape.isEmpty()) return Math.max(0, feet - (y + shape.max(net.minecraft.core.Direction.Axis.Y)));
        }
        return 64;
    }

    static String fmt(float f) { return String.format(java.util.Locale.ROOT, "%.1f", f); }
}
