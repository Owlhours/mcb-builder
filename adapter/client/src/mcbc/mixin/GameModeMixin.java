package mcbc.mixin;

import mcbc.Driver;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class GameModeMixin {
    @Inject(method = "destroyBlock", at = @At("HEAD"))
    private void mcbc$destroy(BlockPos pos, CallbackInfoReturnable<Boolean> cir) { Driver.destroyed(); }

    @Inject(method = "startDestroyBlock", at = @At("RETURN"))
    private void mcbc$started(BlockPos pos, Direction face, CallbackInfoReturnable<Boolean> cir) { Driver.breakStarted((MultiPlayerGameMode) (Object) this, pos); }
}
