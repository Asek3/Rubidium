package me.jellysquid.mods.sodium.mixin.features.render_layer;

import com.google.common.collect.ImmutableList;
import net.minecraft.client.render.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.util.List;

import static net.minecraft.client.render.RenderLayer.*;

@Mixin(RenderLayer.class)
public class MixinRenderLayer {

    private static final List<RenderLayer> layers =  ImmutableList.of(getSolid(), getCutoutMipped(), getCutout(), getTranslucent(), getTripwire());;

    @Overwrite
    public static List<RenderLayer> getBlockLayers() {
        return layers;
    }

}
