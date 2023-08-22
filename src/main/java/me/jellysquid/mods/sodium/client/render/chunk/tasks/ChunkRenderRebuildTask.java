package me.jellysquid.mods.sodium.client.render.chunk.tasks;

import java.util.Map;

import me.jellysquid.mods.sodium.client.SodiumClientMod;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkGraphicsState;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderContainer;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildResult;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkMeshData;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkRenderBounds;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkRenderData;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPass;
import me.jellysquid.mods.sodium.client.render.pipeline.context.ChunkRenderCacheLocal;
import me.jellysquid.mods.sodium.client.util.task.CancellationSource;
import me.jellysquid.mods.sodium.client.world.WorldSlice;
import me.jellysquid.mods.sodium.client.world.cloned.ChunkRenderContext;
import net.coderbot.iris.compat.sodium.impl.block_context.ChunkBuildBuffersExt;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.chunk.ChunkOcclusionDataBuilder;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.model.ModelDataManager;
import net.minecraftforge.client.model.data.EmptyModelData;
import net.minecraftforge.client.model.data.IModelData;

/**
 * Rebuilds all the meshes of a chunk for each given render pass with non-occluded blocks. The result is then uploaded
 * to graphics memory on the main thread.
 *
 * This task takes a slice of the world from the thread it is created on. Since these slices require rather large
 * array allocations, they are pooled to ensure that the garbage collector doesn't become overloaded.
 */
public class ChunkRenderRebuildTask<T extends ChunkGraphicsState> extends ChunkRenderBuildTask<T> {
    private final ChunkRenderContainer<T> render;
        
    private final BlockPos offset;

    private final ChunkRenderContext context;

    public ChunkRenderRebuildTask(ChunkRenderContainer<T> render, ChunkRenderContext context, BlockPos offset) {
        this.render = render;
        this.offset = offset;
        this.context = context;
    }

    @Override
    public ChunkBuildResult<T> performBuild(ChunkRenderCacheLocal cache, ChunkBuildBuffers buffers, CancellationSource cancellationSource) {
        ChunkRenderData.Builder renderData = new ChunkRenderData.Builder();
        ChunkOcclusionDataBuilder occluder = new ChunkOcclusionDataBuilder();
        ChunkRenderBounds.Builder bounds = new ChunkRenderBounds.Builder();

        buffers.init(renderData);

        cache.init(this.context);

        WorldSlice slice = cache.getWorldSlice();

        int baseX = this.render.getOriginX();
        int baseY = this.render.getOriginY();
        int baseZ = this.render.getOriginZ();

        Map<BlockPos, IModelData> modelDataMap = ModelDataManager.getModelData(MinecraftClient.getInstance().world, new ChunkPos(ChunkSectionPos.getSectionCoord(baseX), ChunkSectionPos.getSectionCoord(baseZ)));
        
        BlockPos.Mutable pos = new BlockPos.Mutable();
        BlockPos renderOffset = this.offset;

        for (int relY = 0; relY < 16; relY++) {
            if (cancellationSource.isCancelled()) {
                return null;
            }

            for (int relZ = 0; relZ < 16; relZ++) {
                for (int relX = 0; relX < 16; relX++) {
                    BlockState blockState = slice.getBlockStateRelative(relX + 16, relY + 16, relZ + 16);

                    if (blockState.isAir()) {
                        continue;
                    }

                    // TODO: commit this separately
                    pos.set(baseX + relX, baseY + relY, baseZ + relZ);
                    buffers.setRenderOffset(pos.getX() - renderOffset.getX(), pos.getY() - renderOffset.getY(), pos.getZ() - renderOffset.getZ());

                    if (blockState.getRenderType() == BlockRenderType.MODEL) {
                    for (RenderLayer layer : SodiumClientMod.renderLayers) {
	                        if (!RenderLayers.canRenderInLayer(blockState, layer)) {
	                        	continue;
	                        }
	                        
	                        ForgeHooksClient.setRenderLayer(layer);
                            IModelData modelData = modelDataMap.getOrDefault(pos, EmptyModelData.INSTANCE);
	                        
	                        // Oculus Compat
	                        if (SodiumClientMod.oculusLoaded && buffers instanceof ChunkBuildBuffersExt) {
	                            ((ChunkBuildBuffersExt) buffers).iris$setMaterialId(blockState, (short) -1);
	                        }
	
	                        BakedModel model = cache.getBlockModels()
	                                .getModel(blockState);
	
	                        long seed = blockState.getRenderingSeed(pos);
	
	                        if (cache.getBlockRenderer().renderModel(slice, blockState, pos, model, buffers.get(layer), true, seed, modelData)) {
	                            bounds.addBlock(relX, relY, relZ);
	                        }
	                        
                        }
                    }

                    FluidState fluidState = blockState.getFluidState();

                    if (!fluidState.isEmpty()) {
                        for (RenderLayer layer : SodiumClientMod.renderLayers) {
                            if (!RenderLayers.canRenderInLayer(fluidState, layer)) {
                                continue;
                            }

                            if (SodiumClientMod.oculusLoaded && buffers instanceof ChunkBuildBuffersExt) {
                                // All fluids have a ShadersMod render type of 1, to match behavior of Minecraft 1.7 and earlier.
                                ((ChunkBuildBuffersExt) buffers).iris$setMaterialId(fluidState.getBlockState(), (short) 1);
                            }
                            
                            ForgeHooksClient.setRenderLayer(layer);

	                        if (cache.getFluidRenderer().render(slice, fluidState, pos, buffers.get(layer))) {
	                            bounds.addBlock(relX, relY, relZ);
	                        }
                        }
                    }

                    if (blockState.hasTileEntity()) {
                        BlockEntity entity = slice.getBlockEntity(pos);

                        if (entity != null) {
                            BlockEntityRenderer<BlockEntity> renderer = BlockEntityRenderDispatcher.INSTANCE.get(entity);

                            if (renderer != null) {
                                renderData.addBlockEntity(entity, !renderer.rendersOutsideBoundingBox(entity));

                                bounds.addBlock(relX, relY, relZ);
                            }
                        }
                    }

                    if (blockState.isOpaqueFullCube(slice, pos)) {
                        occluder.markClosed(pos);
                    }
                }
            }
        }
        
        ForgeHooksClient.setRenderLayer(null);

        for (BlockRenderPass pass : BlockRenderPass.VALUES) {
            ChunkMeshData mesh = buffers.createMesh(pass);

            if (mesh != null) {
                renderData.setMesh(pass, mesh);
            }
        }

        renderData.setOcclusionData(occluder.build());
        renderData.setBounds(bounds.build(this.render.getChunkPos()));

        return new ChunkBuildResult<>(this.render, renderData.build());
    }

    @Override
    public void releaseResources() {
        this.context.releaseResources();
    }
}
