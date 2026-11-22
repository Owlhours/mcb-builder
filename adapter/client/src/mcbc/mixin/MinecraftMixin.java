package mcbc.mixin;

import mcbc.Driver;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void mcbc$tick(CallbackInfo ci) { Driver.preTick((Minecraft) (Object) this); }

    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void mcbc$keys(CallbackInfo ci) { Driver.keybinds((Minecraft) (Object) this); }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void mcbc$continue(boolean down, CallbackInfo ci) { if (Driver.holdBreak((Minecraft) (Object) this, down)) ci.cancel(); }

    @ModifyArg(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;continueAttack(Z)V"))
    private boolean mcbc$attack(boolean down) { return Driver.attackArg(down); }
}
