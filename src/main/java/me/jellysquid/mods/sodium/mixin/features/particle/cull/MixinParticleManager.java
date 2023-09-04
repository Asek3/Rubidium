package me.jellysquid.mods.sodium.mixin.features.particle.cull;

import me.jellysquid.mods.sodium.client.SodiumClientMod;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.render.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ParticleManager.class)
public class MixinParticleManager {
    @ModifyVariable(method = "render", at = @At("HEAD"), ordinal = 0)
    private Frustum checkOption(Frustum oldInstance) {
        boolean useCulling = SodiumClientMod.options().performance.useParticleCulling;
        return useCulling ? oldInstance : null;
    }
}
