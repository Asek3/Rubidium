package me.jellysquid.mods.sodium.client.render.chunk.tasks;

import me.jellysquid.mods.sodium.client.SodiumClientMod;
import me.jellysquid.mods.sodium.client.compat.immersive.ImmersiveConnectionRenderer;
import me.jellysquid.mods.sodium.client.gl.compile.ChunkBuildContext;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
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
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.chunk.ChunkOcclusionDataBuilder;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.random.Xoroshiro128PlusPlusRandom;
import net.minecraftforge.client.model.data.ModelData;

import java.util.EnumMap;
import java.util.Map;

/**
 * Rebuilds all the meshes of a chunk for each given render pass with non-occluded blocks. The result is then uploaded
 * to graphics memory on the main thread.
 *
 * This task takes a slice of the world from the thread it is created on. Since these slices require rather large
 * array allocations, they are pooled to ensure that the garbage collector doesn't become overloaded.
 */
public class ChunkRenderRebuildTask extends ChunkRenderBuildTask {
    private final RenderSection render;
    private final ChunkRenderContext renderContext;
    private final int frame;

    public ChunkRenderRebuildTask(RenderSection render, ChunkRenderContext renderContext, int frame) {
        this.render = render;
        this.renderContext = renderContext;
        this.frame = frame;
    }
    
    private final Xoroshiro128PlusPlusRandom random = new Xoroshiro128PlusPlusRandom(42L);

    @Override
    public ChunkBuildResult performBuild(ChunkBuildContext buildContext, CancellationSource cancellationSource) {
        ChunkRenderData.Builder renderData = new ChunkRenderData.Builder();
        ChunkOcclusionDataBuilder occluder = new ChunkOcclusionDataBuilder();
        ChunkRenderBounds.Builder bounds = new ChunkRenderBounds.Builder();

        ChunkBuildBuffers buffers = buildContext.buffers;
        buffers.init(renderData, this.render.getChunkId());

        ChunkRenderCacheLocal cache = buildContext.cache;
        cache.init(this.renderContext);

        WorldSlice slice = cache.getWorldSlice();

        int minX = this.render.getOriginX();
        int minY = this.render.getOriginY();
        int minZ = this.render.getOriginZ();

        int maxX = minX + 16;
        int maxY = minY + 16;
        int maxZ = minZ + 16;

        Map<BlockPos, ModelData> modelDataMap = MinecraftClient.getInstance().world.getModelDataManager().getAt(new ChunkPos(ChunkSectionPos.getSectionCoord(minX), ChunkSectionPos.getSectionCoord(minZ)));

        BlockPos.Mutable blockPos = new BlockPos.Mutable();
        BlockPos.Mutable offset = new BlockPos.Mutable();

        for (int y = minY; y < maxY; y++) {
            if (cancellationSource.isCancelled()) {
                return null;
            }

            for (int z = minZ; z < maxZ; z++) {
                for (int x = minX; x < maxX; x++) {
                    BlockState blockState = slice.getBlockState(x, y, z);

                    if (blockState.isAir()) {
                        continue;
                    }

                    blockPos.set(x, y, z);
                    offset.set(x & 15, y & 15, z & 15);

                    boolean rendered = false;

                    if (blockState.getRenderType() == BlockRenderType.MODEL) {
                    	BakedModel model = cache.getBlockModels()
                                .getModel(blockState);
                    	ModelData modelData = modelDataMap.getOrDefault(blockPos, ModelData.EMPTY);
                        long seed = blockState.getRenderingSeed(blockPos);
                        for (RenderLayer layer : model.getRenderTypes(blockState, random, modelData)) {
                            if (cache.getBlockRenderer().renderModel(slice, blockState, blockPos, offset, model, buffers.get(layer), true, seed, modelData, layer, random)) {
                                rendered = true;
                            }
                    	}
                    }

                    FluidState fluidState = blockState.getFluidState();
                    
                    if (!fluidState.isEmpty()) {
                        RenderLayer layer = RenderLayers.getFluidLayer(fluidState);

                        if (cache.getFluidRenderer().render(slice, fluidState, blockPos, offset, buffers.get(layer))) {
                            rendered = true;
                        }
                    }

                    if (blockState.hasBlockEntity()) {
                        BlockEntity entity = slice.getBlockEntity(blockPos);

                        if (entity != null) {
                            BlockEntityRenderer<BlockEntity> renderer = MinecraftClient.getInstance().getBlockEntityRenderDispatcher().get(entity);

                            if (renderer != null) {
                                renderData.addBlockEntity(entity, !renderer.rendersOutsideBoundingBox(entity));
                                rendered = true;
                            }
                        }
                    }

                    if (blockState.isOpaqueFullCube(slice, blockPos)) {
                        occluder.markClosed(blockPos);
                    }

                    if (rendered) {
                        bounds.addBlock(x & 15, y & 15, z & 15);
                    }
                }
            }
        }

        Map<BlockRenderPass, ChunkMeshData> meshes = new EnumMap<>(BlockRenderPass.class);
        
        if(SodiumClientMod.immersiveLoaded)
	        ImmersiveConnectionRenderer.renderConnectionsInSection(
	                buildContext.buffers, buildContext.cache.getWorldSlice(), render.getChunkPos()
	    );

        for (BlockRenderPass pass : BlockRenderPass.VALUES) {
            ChunkMeshData mesh = buffers.createMesh(pass);

            if (mesh != null) {
                meshes.put(pass, mesh);
            }
        }

        renderData.setOcclusionData(occluder.build());
        renderData.setBounds(bounds.build(this.render.getChunkPos()));

        return new ChunkBuildResult(this.render, renderData.build(), meshes, this.frame);
    }
    
    @Override
    public void releaseResources() {
        this.renderContext.releaseResources();
    }
}
