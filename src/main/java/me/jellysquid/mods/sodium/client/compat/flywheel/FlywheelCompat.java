package me.jellysquid.mods.sodium.client.compat.flywheel;

import java.util.Collection;

import com.jozufozu.flywheel.backend.Backend;
import com.jozufozu.flywheel.backend.instancing.InstancedRenderRegistry;
import me.jellysquid.mods.sodium.client.SodiumClientMod;
import net.minecraft.block.entity.BlockEntity;

public class FlywheelCompat {

    /**
     * Filters a collection of TileEntities to avoid rendering conflicts with Flywheel.
     *
     * @param blockEntities The collection to be filtered.
     */
    public static void filterBlockEntityList(Collection<BlockEntity> blockEntities) {
        if (SodiumClientMod.flywheelLoaded && Backend.getInstance().canUseInstancing()) {
            InstancedRenderRegistry r = InstancedRenderRegistry.getInstance();
            blockEntities.removeIf(r::shouldSkipRender);
        }
    }

}