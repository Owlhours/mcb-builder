package mcb.mod.mixin;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A build that was asked for starts right after a server tick, from the server's own loop - not as one of its tasks:
 * while a task runs, the loop's wait for the next tick never ends ({@code haveTime} is true inside a task), and the
 * build's ticks need that wait (it runs the chunk loading and everything else between two ticks).
 */
@Mixin(MinecraftServer.class)
public abstract class ServerTickMixin {
    @Inject(method = "processPacketsAndTick", at = @At("RETURN"))
    private void mcb$afterTick(boolean sprinting, CallbackInfo ci) {
        mcb.mod.Job.afterTick((MinecraftServer) (Object) this);
    }
}
