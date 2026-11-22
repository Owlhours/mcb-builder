package mcbc.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Invoker("startAttack") boolean mcbc$startAttack();
    @Invoker("startUseItem") void mcbc$startUseItem();
    @Accessor("deltaTracker") DeltaTracker.Timer mcbc$deltaTracker();
}
