package mcbc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.item.Item;

/**
 * The dry run: what a build would do, shown before anything is done. Particles (client-side only) outline the build
 * box (green), the bot's working area (yellow) and the supply containers (blue), and mark every block it would
 * break (red) and every block it cannot break by hand (purple), for a minute.
 */
public final class Preview {
    public static final class Plan {
        public final List<BlockPos> toBreak = new ArrayList<>(), tooHard = new ArrayList<>(), fluids = new ArrayList<>();
        public int alreadyRight, unloaded, unloadedChunks, containersInBox;
        public final Map<Item, Integer> need = new LinkedHashMap<>(), carried = new LinkedHashMap<>();
    }

    final Job job;
    final Plan plan;
    final long untilMs;
    final String key;
    int t;

    Preview(Job job, Plan plan, String key) {
        this.job = job; this.plan = plan; this.key = key;
        this.untilMs = System.currentTimeMillis() + 60_000;
    }

    boolean live() { return System.currentTimeMillis() < untilMs; }

    void tick(Minecraft mc) {
        if (!live() || mc.level == null) { if (Driver.preview == this) Driver.preview = null; return; }
        if ((t++ % 10) != 0) return;
        box(mc, job.ox, job.oy, job.oz, job.ox + job.target.sx, job.oy + job.target.sy, job.oz + job.target.sz, 0x33FF33, 1.0);
        box(mc, job.gx0, job.oy - 1, job.gz0, job.gx1 + 1, job.gy1 + 1, job.gz1 + 1, 0xFFDD22, 2.0);
        for (Job.Container c : job.containers) box(mc, c.pos.getX(), c.pos.getY(), c.pos.getZ(), c.pos.getX() + 1, c.pos.getY() + 1, c.pos.getZ() + 1, 0x3388FF, 0.25);
        int n = 0;
        for (BlockPos p : plan.toBreak) { if (n++ > 3000) break; dot(mc, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 0xFF2222); }
        for (BlockPos p : plan.tooHard) { if (n++ > 4000) break; dot(mc, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 0xAA22FF); }
    }

    private static void dot(Minecraft mc, double x, double y, double z, int color) {
        mc.level.addParticle(new DustParticleOptions(color, 1.0f), x, y, z, 0, 0, 0);
    }

    private static void box(Minecraft mc, int x0, int y0, int z0, int x1, int y1, int z1, int color, double step) {
        int n = 0;
        for (double x = x0; x <= x1; x += step) for (int y : new int[] {y0, y1}) for (int z : new int[] {z0, z1}) { dot(mc, x, y, z, color); if (++n > 2500) return; }
        for (double z = z0; z <= z1; z += step) for (int y : new int[] {y0, y1}) for (int x : new int[] {x0, x1}) { dot(mc, x, y, z, color); if (++n > 2500) return; }
        for (double y = y0; y <= y1; y += step) for (int x : new int[] {x0, x1}) for (int z : new int[] {z0, z1}) { dot(mc, x, y, z, color); if (++n > 2500) return; }
    }
}
