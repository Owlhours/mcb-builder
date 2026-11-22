package mcb.mod.mixin;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds {@code /mcb} to the game's command tree when the server builds it. */
@Mixin(Commands.class)
public abstract class CommandsMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void mcb$register(Commands.CommandSelection selection, CommandBuildContext context, CallbackInfo ci) {
        mcb.mod.McbCommands.register(((Commands) (Object) this).getDispatcher());
    }
}
