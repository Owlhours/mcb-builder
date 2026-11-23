package mcbc.mixin;

import mcbc.Driver;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class PacketListenerMixin {
    @Inject(method = "handleMovePlayer", at = @At("TAIL"))
    private void mcbc$moved(ClientboundPlayerPositionPacket p, CallbackInfo ci) { Driver.serverMovedPlayer(); }

    /** A container's contents arrived (they come in their own packet just after the screen opens). */
    @Inject(method = "handleContainerContent", at = @At("TAIL"))
    private void mcbc$contents(ClientboundContainerSetContentPacket p, CallbackInfo ci) { mcbc.LocalClient.contentsArrived = p.containerId(); }
}
