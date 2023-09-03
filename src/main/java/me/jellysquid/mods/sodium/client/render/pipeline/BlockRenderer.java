package me.jellysquid.mods.sodium.client.render.pipeline;

import me.jellysquid.mods.sodium.client.SodiumClientMod;
import me.jellysquid.mods.sodium.client.compat.ccl.CCLCompat;
import me.jellysquid.mods.sodium.client.compat.SinkingVertexBuilder;
import me.jellysquid.mods.sodium.client.model.light.LightMode;
import me.jellysquid.mods.sodium.client.model.light.LightPipeline;
import me.jellysquid.mods.sodium.client.model.light.LightPipelineProvider;
import me.jellysquid.mods.sodium.client.model.light.data.QuadLightData;
import me.jellysquid.mods.sodium.client.model.quad.ModelQuadView;
import me.jellysquid.mods.sodium.client.model.quad.blender.BiomeColorBlender;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadOrientation;
import me.jellysquid.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuffers;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkRenderData;
import me.jellysquid.mods.sodium.client.render.chunk.format.ModelVertexSink;
import me.jellysquid.mods.sodium.client.render.occlusion.BlockOcclusionCache;
import me.jellysquid.mods.sodium.client.util.color.ColorABGR;
import me.jellysquid.mods.sodium.client.util.rand.XoRoShiRoRandom;
import me.jellysquid.mods.sodium.client.world.biome.BlockColorsExtended;
import me.jellysquid.mods.sodium.common.util.DirectionUtil;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.color.block.BlockColorProvider;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockRenderView;
import net.minecraftforge.client.model.data.IModelData;

import java.util.List;
import java.util.Random;

import codechicken.lib.render.block.ICCBlockRenderer;
import net.minecraftforge.client.model.pipeline.VertexBufferConsumer;
import net.minecraftforge.client.model.pipeline.VertexLighterFlat;
import net.minecraftforge.client.model.pipeline.VertexLighterSmoothAo;
import net.minecraftforge.common.ForgeConfig;

public class BlockRenderer {
    private final Random random = new XoRoShiRoRandom();

    private final BlockColorsExtended blockColors;
    private final BlockOcclusionCache occlusionCache;

    private final QuadLightData cachedQuadLightData = new QuadLightData();

    private final BiomeColorBlender biomeColorBlender;
    private final LightPipelineProvider lighters;

    private final boolean useAmbientOcclusion;

    private final boolean experimentalForgeLightPipelineEnabled;

    public BlockRenderer(MinecraftClient client, LightPipelineProvider lighters, BiomeColorBlender biomeColorBlender) {
        this.blockColors = (BlockColorsExtended) client.getBlockColors();
        this.biomeColorBlender = biomeColorBlender;

        this.lighters = lighters;

        this.occlusionCache = new BlockOcclusionCache();
        this.useAmbientOcclusion = MinecraftClient.isAmbientOcclusionEnabled();

        this.experimentalForgeLightPipelineEnabled = ForgeConfig.CLIENT.experimentalForgeLightPipelineEnabled.get();
    }

    private ForgeBlockRenderer forgeBlockRenderer;

    private ForgeBlockRenderer getForgeBlockRenderer() {
        if(forgeBlockRenderer == null) {
            return forgeBlockRenderer = new ForgeBlockRenderer();
        }
        return forgeBlockRenderer;
    }

    public boolean renderModel(BlockRenderView world, BlockState state, BlockPos pos, BakedModel model, ChunkModelBuffers buffers, boolean cull, long seed, IModelData modelData) {
        LightMode lightMode = this.getLightingMode(state, model, world, pos);
        LightPipeline lighter = this.lighters.getLighter(lightMode);
        Vec3d offset = state.getModelOffset(world, pos);

        boolean rendered = false;

        modelData = model.getModelData(world, pos, state, modelData);
        
        if(SodiumClientMod.cclLoaded) {
	        final MatrixStack mStack = new MatrixStack();
	        final SinkingVertexBuilder builder = SinkingVertexBuilder.getInstance();
	        for (final ICCBlockRenderer renderer : CCLCompat.getCustomRenderers(world, pos)) {
	            if (renderer.canHandleBlock(world, pos, state)) {
	                builder.reset();
	                rendered = renderer.renderBlock(state, pos, world, mStack, builder, random, modelData);
	                builder.flush(buffers);
	                
	                return rendered;
	            }
	        }
        }

        if(experimentalForgeLightPipelineEnabled) {
            final MatrixStack mStack = new MatrixStack();
            final SinkingVertexBuilder builder = SinkingVertexBuilder.getInstance();

            mStack.translate(offset.x, offset.y, offset.z);

            builder.reset();
            rendered = getForgeBlockRenderer().renderForgePipeline(getForgeBlockRenderer().getLighter(lightMode, builder, mStack.peek()), world, model, state, pos, cull, this.random, seed, modelData, buffers.getRenderData());
            builder.flush(buffers);

            return rendered;
        }
        
        for (Direction dir : DirectionUtil.ALL_DIRECTIONS) {
            this.random.setSeed(seed);

            List<BakedQuad> sided = model.getQuads(state, dir, this.random, modelData);

            if (sided.isEmpty()) {
                continue;
            }

            if (!cull || this.occlusionCache.shouldDrawSide(state, world, pos, dir)) {
                this.renderQuadList(world, state, pos, lighter, offset, buffers, sided, dir);

                rendered = true;
            }
        }

        this.random.setSeed(seed);

        List<BakedQuad> all = model.getQuads(state, null, this.random, modelData);

        if (!all.isEmpty()) {
            this.renderQuadList(world, state, pos, lighter, offset, buffers, all, null);

            rendered = true;
        }

        return rendered;
    }

    private void renderQuadList(BlockRenderView world, BlockState state, BlockPos pos, LightPipeline lighter, Vec3d offset,
                                ChunkModelBuffers buffers, List<BakedQuad> quads, Direction cullFace) {
    	ModelQuadFacing facing = cullFace == null ? ModelQuadFacing.UNASSIGNED : ModelQuadFacing.fromDirection(cullFace);
        BlockColorProvider colorizer = null;

        ModelVertexSink sink = buffers.getSink(facing);
        sink.ensureCapacity(quads.size() * 4);

        ChunkRenderData.Builder renderData = buffers.getRenderData();

        // This is a very hot allocation, iterate over it manually
        // noinspection ForLoopReplaceableByForEach
        for (int i = 0, quadsSize = quads.size(); i < quadsSize; i++) {
            BakedQuad quad = quads.get(i);

            QuadLightData light = this.cachedQuadLightData;
            lighter.calculate((ModelQuadView) quad, pos, light, cullFace, quad.getFace(), quad.hasShade());

            if (quad.hasColor() && colorizer == null) {
                colorizer = this.blockColors.getColorProvider(state);
            }

            this.renderQuad(world, state, pos, sink, offset, colorizer, quad, light, renderData);
        }

        sink.flush();
    }

    private void renderQuad(BlockRenderView world, BlockState state, BlockPos pos, ModelVertexSink sink, Vec3d offset,
                            BlockColorProvider colorProvider, BakedQuad bakedQuad, QuadLightData light, ChunkRenderData.Builder renderData) {
        ModelQuadView src = (ModelQuadView) bakedQuad;

        ModelQuadOrientation order = ModelQuadOrientation.orient(light.br);

        int[] colors = null;

        if (bakedQuad.hasColor()) {
            colors = this.biomeColorBlender.getColors(colorProvider, world, state, pos, src);
        }

        for (int dstIndex = 0; dstIndex < 4; dstIndex++) {
            int srcIndex = order.getVertexIndex(dstIndex);

            float x = src.getX(srcIndex) + (float) offset.getX();
            float y = src.getY(srcIndex) + (float) offset.getY();
            float z = src.getZ(srcIndex) + (float) offset.getZ();

            int color = ColorABGR.mul(colors != null ? colors[srcIndex] : src.getColor(srcIndex), light.br[srcIndex]);

            float u = src.getTexU(srcIndex);
            float v = src.getTexV(srcIndex);

            int lm = light.lm[srcIndex];

            sink.writeQuad(x, y, z, color, u, v, lm);
        }

        addSpriteData(src, renderData);
    }

    private void addSpriteData(ModelQuadView src, ChunkRenderData.Builder renderData) {
        Sprite sprite = src.rubidium$getSprite();

        if (sprite != null) {
            renderData.addSprite(sprite);
        }
    }

    private LightMode getLightingMode(BlockState state, BakedModel model, BlockRenderView world, BlockPos pos) {
        if (this.useAmbientOcclusion && model.isAmbientOcclusion(state) && state.getLightValue(world, pos) == 0) {
            return LightMode.SMOOTH;
        } else {
            return LightMode.FLAT;
        }
    }

    private class ForgeBlockRenderer {

        private final ThreadLocal<VertexLighterFlat> lighterFlat;
        private final ThreadLocal<VertexLighterSmoothAo> lighterSmooth;
        private final ThreadLocal<VertexBufferConsumer> consumerFlat = ThreadLocal.withInitial(VertexBufferConsumer::new);
        private final ThreadLocal<VertexBufferConsumer> consumerSmooth = ThreadLocal.withInitial(VertexBufferConsumer::new);

        public ForgeBlockRenderer() {
            BlockColors colors = MinecraftClient.getInstance().getBlockColors();
            lighterFlat = ThreadLocal.withInitial(() -> new VertexLighterFlat(colors));
            lighterSmooth = ThreadLocal.withInitial(() -> new VertexLighterSmoothAo(colors));
        }

        private VertexLighterFlat getLighter(LightMode lightMode, VertexConsumer vertexConsumer, MatrixStack.Entry entry) {
            VertexBufferConsumer consumer = null;
            VertexLighterFlat lighter = null;
            switch (lightMode) {
                case SMOOTH:
                    consumer = this.consumerSmooth.get();
                    lighter = this.lighterSmooth.get();
                    break;
                case FLAT:
                    consumer = this.consumerFlat.get();
                    lighter = this.lighterFlat.get();
                    break;
            }
            consumer.setBuffer(vertexConsumer);
            lighter.setParent(consumer);
            lighter.setTransform(entry);
            return lighter;
        }

        private boolean renderForgePipeline(VertexLighterFlat lighter, BlockRenderView world, BakedModel model, BlockState state, BlockPos pos, boolean checkSides, Random rand, long seed, IModelData modelData, ChunkRenderData.Builder renderData) {
            lighter.setWorld(world);
            lighter.setState(state);
            lighter.setBlockPos(pos);
            boolean empty = true;
            rand.setSeed(seed);
            List<BakedQuad> quads = model.getQuads(state, null, rand, modelData);
            if(!quads.isEmpty())
            {
                lighter.updateBlockInfo();
                empty = false;
                for(BakedQuad quad : quads)
                {
                    quad.pipe(lighter);
                    addSpriteData((ModelQuadView) quad, renderData);
                }
            }
            for(Direction side : DirectionUtil.ALL_DIRECTIONS)
            {
                rand.setSeed(seed);
                quads = model.getQuads(state, side, rand, modelData);
                if(!quads.isEmpty())
                {
                    if(!checkSides || occlusionCache.shouldDrawSide(state, world, pos, side))
                    {
                        if(empty) lighter.updateBlockInfo();
                        empty = false;
                        for(BakedQuad quad : quads)
                        {
                            quad.pipe(lighter);
                            addSpriteData((ModelQuadView) quad, renderData);
                        }
                    }
                }
            }
            lighter.resetBlockInfo();
            return !empty;
        }
    }
}
