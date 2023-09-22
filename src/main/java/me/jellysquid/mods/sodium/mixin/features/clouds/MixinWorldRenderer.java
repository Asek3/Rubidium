package me.jellysquid.mods.sodium.mixin.features.clouds;

import me.jellysquid.mods.sodium.client.render.CloudRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.math.Matrix4f;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class MixinWorldRenderer {
    @Shadow
    private @Nullable ClientWorld world;
    @Shadow
    private int ticks;

    @Shadow
    @Final
    private MinecraftClient client;

    private CloudRenderer cloudRenderer;

    /**
     * @author jellysquid3
     * @reason Optimize cloud rendering
     */
    @Overwrite
    public void renderClouds(MatrixStack matrices, Matrix4f projectionMatrix, float tickDelta, double x, double y, double z) {
        if (!this.world.getDimensionEffects().renderClouds(this.world, this.ticks, tickDelta, matrices, x, y, z, projectionMatrix)) {
            if (this.cloudRenderer == null) {
                this.cloudRenderer = new CloudRenderer(client.getResourceManager());
            }

            this.cloudRenderer.render(this.world, this.client.player, matrices, projectionMatrix, this.ticks, tickDelta, x, y, z);
        }
    }

    @Inject(method = "reload(Lnet/minecraft/resource/ResourceManager;)V", at = @At("RETURN"))
    private void onReload(CallbackInfo ci) {
        // will be re-allocated on next use
        if (this.cloudRenderer != null) {
            this.cloudRenderer.destroy();
            this.cloudRenderer = null;
        }
    }

    @Inject(method = "close", at = @At("RETURN"))
    private void onClose(CallbackInfo ci) {
        // will never be re-allocated, as the renderer is shutting down
        if (this.cloudRenderer != null) {
            this.cloudRenderer.destroy();
            this.cloudRenderer = null;
        }
    }
}