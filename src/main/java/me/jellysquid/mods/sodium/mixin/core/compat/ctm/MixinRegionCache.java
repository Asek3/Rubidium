package me.jellysquid.mods.sodium.mixin.core.compat.ctm;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import team.chisel.ctm.client.util.RegionCache;

import java.lang.ref.WeakReference;

@Pseudo
@Mixin(RegionCache.class)
public class MixinRegionCache {

    @Shadow
    private WeakReference<BlockView> passthrough;

    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
    public void directGetter(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        cir.setReturnValue(this.passthrough.get().getBlockState(pos));
    }

}
