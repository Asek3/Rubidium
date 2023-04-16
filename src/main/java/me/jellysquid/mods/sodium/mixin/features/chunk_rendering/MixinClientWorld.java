package me.jellysquid.mods.sodium.mixin.features.chunk_rendering;

import java.util.function.Supplier;
import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
import me.jellysquid.mods.sodium.client.world.BiomeSeedProvider;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.registry.RegistryEntry;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientWorld.class)
public class MixinClientWorld implements BiomeSeedProvider
{
    @Unique
    private long biomeSeed;

    @Inject(method = "markChunkRenderability", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/WorldChunk;setShouldRenderOnUpdate(Z)V", shift = At.Shift.AFTER))
    private void postLightUpdate(int chunkX, int chunkZ, CallbackInfo ci) {
        SodiumWorldRenderer.instance()
                .onChunkLightAdded(chunkX, chunkZ);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void captureSeed(ClientPlayNetworkHandler netHandler, ClientWorld.Properties properties, RegistryKey<World> registryRef, RegistryEntry registryEntry, int loadDistance, int simulationDistance, Supplier profiler, WorldRenderer worldRenderer, boolean debugWorld, long seed, CallbackInfo ci) {
        this.biomeSeed = seed;
    }

    @Override
    public long getBiomeSeed() {
        return this.biomeSeed;
    }
}
