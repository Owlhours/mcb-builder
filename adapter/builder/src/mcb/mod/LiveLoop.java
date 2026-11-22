package mcb.mod;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.util.Util;

/**
 * One tick of a running server, the way its own loop runs one ({@code MinecraftServer.runServer}, 26.2 = 26.3): the tick
 * rate and {@code /tick sprint} decide when it is due, then the queued packets are handled and the server ticks
 * ({@code processPacketsAndTick}), then the server waits for the next tick's time while it runs its tasks
 * ({@code waitUntilNextTick}: chunk loading, and everything else the loop does between ticks).
 *
 * The build runs on the server thread, from the server loop right after a tick (`ServerTickMixin`), exactly as in the headless runs where the bot's
 * thread is the server thread: the bot's client half and this tick alternate, nothing else touches the world in
 * between. While the build runs, this method is the server's loop.
 */
final class LiveLoop {
    private final MinecraftServer server;
    private final MethodHandle processPacketsAndTick, waitUntilNextTick, startMeasuring, finishMeasuring;
    private final VarHandle nextTickTime, lastOverloadWarning, mayHaveDelayedTasks, delayedTasksMax;
    private final long overloadedThreshold, overloadedWarningInterval;

    LiveLoop(MinecraftServer server) {
        this.server = server;
        try {
            MethodHandles.Lookup l = MethodHandles.privateLookupIn(MinecraftServer.class, MethodHandles.lookup());
            processPacketsAndTick = l.findVirtual(MinecraftServer.class, "processPacketsAndTick", MethodType.methodType(void.class, boolean.class));
            waitUntilNextTick = l.findVirtual(MinecraftServer.class, "waitUntilNextTick", MethodType.methodType(void.class));
            startMeasuring = l.findVirtual(MinecraftServer.class, "startMeasuringTaskExecutionTime", MethodType.methodType(void.class));
            finishMeasuring = l.findVirtual(MinecraftServer.class, "finishMeasuringTaskExecutionTime", MethodType.methodType(void.class));
            nextTickTime = l.findVarHandle(MinecraftServer.class, "nextTickTimeNanos", long.class);
            lastOverloadWarning = l.findVarHandle(MinecraftServer.class, "lastOverloadWarningNanos", long.class);
            mayHaveDelayedTasks = l.findVarHandle(MinecraftServer.class, "mayHaveDelayedTasks", boolean.class);
            delayedTasksMax = l.findVarHandle(MinecraftServer.class, "delayedTasksMaxNextTickTimeNanos", long.class);
            overloadedThreshold = (long) l.findStaticVarHandle(MinecraftServer.class, "OVERLOADED_THRESHOLD_NANOS", long.class).get();
            overloadedWarningInterval = (long) l.findStaticVarHandle(MinecraftServer.class, "OVERLOADED_WARNING_INTERVAL_NANOS", long.class).get();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("this Minecraft is not the version the mod was built for", e);
        }
    }

    /** One server tick, paced like the server's own loop. */
    void tick() {
        ServerTickRateManager trm = server.tickRateManager();
        long perTick;
        if (!server.isPaused() && trm.isSprinting() && trm.checkShouldSprintThisTick()) {
            perTick = 0L;
            long now = Util.getNanos();
            nextTickTime.set(server, now);
            lastOverloadWarning.set(server, now);
        } else {
            perTick = trm.nanosecondsPerTick();
            long next = (long) nextTickTime.get(server);
            long behind = Util.getNanos() - next;
            if (behind > overloadedThreshold + 20L * perTick && next - (long) lastOverloadWarning.get(server) >= overloadedWarningInterval + 100L * perTick) {
                // (the loop's "Can't keep up!": it gives up on the ticks it is behind)
                next += behind / perTick * perTick;
                nextTickTime.set(server, next);
                lastOverloadWarning.set(server, next);
            }
        }
        boolean sprinting = perTick == 0L;
        nextTickTime.set(server, (long) nextTickTime.get(server) + perTick);
        try {
            processPacketsAndTick.invoke(server, sprinting);
            mayHaveDelayedTasks.set(server, true);
            delayedTasksMax.set(server, Math.max(Util.getNanos() + perTick, (long) nextTickTime.get(server)));
            startMeasuring.invoke(server);
            waitUntilNextTick.invoke(server);
            finishMeasuring.invoke(server);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
        if (sprinting) trm.endTickWork();
    }
}
