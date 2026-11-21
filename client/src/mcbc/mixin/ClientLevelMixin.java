package mcbc.mixin;

import mcbc.Driver;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    @Inject(method = "sendBlockUpdated", at = @At("HEAD"))
    private void mcbc$changed(BlockPos pos, BlockState old, BlockState now, int flags, CallbackInfo ci) { Driver.blockChanged(pos, now); }

    /** A predicted block the server answered with something else: a rejected placement or break. */
    @Inject(method = "syncBlockState", at = @At("HEAD"))
    private void mcbc$sync(BlockPos pos, BlockState state, net.minecraft.world.phys.Vec3 playerPos, CallbackInfo ci) {
        if (((ClientLevel) (Object) this).getBlockState(pos) != state) Driver.predictionCorrected(pos, ((ClientLevel) (Object) this).getBlockState(pos), state);
    }

    @Inject(method = "onChunkLoaded", at = @At("HEAD"))
    private void mcbc$chunk(ChunkPos pos, CallbackInfo ci) { Driver.chunkLoaded(pos.x(), pos.z()); }
}
