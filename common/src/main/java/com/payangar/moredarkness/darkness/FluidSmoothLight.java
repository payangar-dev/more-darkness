package com.payangar.moredarkness.darkness;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.payangar.moredarkness.config.MoreDarknessConfig;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.material.FluidState;

/**
 * Smooth fluid lighting and turbidity layers.
 * Scratch state and math behind MixinLiquidBlockRendererSmoothLight: chunk
 * meshing runs tesselate concurrently on worker threads, so the state that
 * correlates the four corners of one top face lives in a ThreadLocal. Once
 * the face is complete, the recorded geometry is reused to stack the
 * translucent turbidity layers beneath it.
 */
public final class FluidSmoothLight {

    private static final ThreadLocal<Face> FACE = ThreadLocal.withInitial(Face::new);

    private FluidSmoothLight() {}

    /** Called at the head of each tesselate invocation. */
    public static void beginBlock() {
        Face face = FACE.get();
        face.count = 0;
        face.lit = false;
    }

    /**
     * Light for one top-face corner: the average of the packed light of the
     * four fluid columns meeting there (block and sky halves separately).
     * Vanilla gives the whole face one value, so light steps block by block
     * across water; this makes it continuous.
     */
    public static int cornerLight(BlockAndTintGetter level, BlockPos pos, float x, float z) {
        Face face = FACE.get();
        if (!face.lit) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    face.columns[(dx + 1) * 3 + dz + 1] = fluidLight(level, pos.offset(dx, 0, dz));
                }
            }
            face.lit = true;
        }
        // Geometry is chunk-local: the corner is the fractional part
        int dx = x - (pos.getX() & 15) < 0.5f ? -1 : 1;
        int dz = z - (pos.getZ() & 15) < 0.5f ? -1 : 1;
        return average(
                face.columns[4],
                face.columns[(dx + 1) * 3 + 1],
                face.columns[3 + dz + 1],
                face.columns[(dx + 1) * 3 + dz + 1]);
    }

    /**
     * Records one main-face vertex (vanilla order NW, SW, SE, NE) and, once
     * the face is complete, stacks the turbidity layers under it. The
     * backface vertices arrive after the fourth call and are ignored here.
     */
    public static void recordVertex(VertexConsumer consumer, BlockAndTintGetter level, BlockPos pos, FluidState fluidState,
            float x, float y, float z, float red, float green, float blue, float u, float v, int light) {
        Face face = FACE.get();
        if (face.count < 4) {
            face.x[face.count] = x;
            face.y[face.count] = y;
            face.z[face.count] = z;
            face.u[face.count] = u;
            face.v[face.count] = v;
            face.light[face.count] = light;
            face.red = red;
            face.green = green;
            face.blue = blue;
        }
        face.count++;
        if (face.count == 4) {
            emitLayers(consumer, level, pos, fluidState, face);
        }
    }

    /**
     * Turbidity from above: each extra coplanar layer compounds the
     * texture's translucency, so murky water hides its bottom. The
     * fractional part fades the last layer through its vertex alpha,
     * so biome borders blend over a few blocks instead of snapping.
     */
    private static void emitLayers(VertexConsumer consumer, BlockAndTintGetter level, BlockPos pos, FluidState fluidState, Face face) {
        if (!MoreDarknessConfig.getInstance().darkerWater || !fluidState.is(FluidTags.WATER)) {
            return;
        }
        float turbidity = WaterTurbidity.level(pos);
        boolean backFace = fluidState.shouldRenderBackwardUpFace(level, pos.above());
        for (int layer = 1; layer <= 2; layer++) {
            float layerAlpha = Mth.clamp(turbidity - (layer - 1), 0.0f, 1.0f);
            if (layerAlpha < 0.05f) {
                break;
            }
            float dy = 0.002f * layer;
            vertex(consumer, face, 0, dy, layerAlpha);
            vertex(consumer, face, 1, dy, layerAlpha);
            vertex(consumer, face, 2, dy, layerAlpha);
            vertex(consumer, face, 3, dy, layerAlpha);
            if (backFace) {
                vertex(consumer, face, 0, dy, layerAlpha);
                vertex(consumer, face, 3, dy, layerAlpha);
                vertex(consumer, face, 2, dy, layerAlpha);
                vertex(consumer, face, 1, dy, layerAlpha);
            }
        }
    }

    private static void vertex(VertexConsumer consumer, Face face, int corner, float dy, float alpha) {
        consumer.addVertex(face.x[corner], face.y[corner] - dy, face.z[corner])
                .setColor(face.red, face.green, face.blue, alpha)
                .setUv(face.u[corner], face.v[corner])
                .setLight(face.light[corner])
                .setNormal(0.0F, 1.0F, 0.0F);
    }

    /** Same sampling as the vanilla private getLightColor. */
    private static int fluidLight(BlockAndTintGetter level, BlockPos pos) {
        int below = LevelRenderer.getLightColor(level, pos);
        int above = LevelRenderer.getLightColor(level, pos.above());
        int block = Math.max(below & 0xFF, above & 0xFF);
        int sky = Math.max(below >> 16 & 0xFF, above >> 16 & 0xFF);
        return block | sky << 16;
    }

    /** Averages the block and sky halves of packed light coords separately. */
    private static int average(int a, int b, int c, int d) {
        int block = ((a & 0xFFFF) + (b & 0xFFFF) + (c & 0xFFFF) + (d & 0xFFFF)) / 4;
        int sky = ((a >>> 16) + (b >>> 16) + (c >>> 16) + (d >>> 16)) / 4;
        return sky << 16 | block;
    }

    /** Per-thread scratch for the top face currently being emitted. */
    private static final class Face {
        int count;
        boolean lit;
        final int[] columns = new int[9];
        final float[] x = new float[4];
        final float[] y = new float[4];
        final float[] z = new float[4];
        final float[] u = new float[4];
        final float[] v = new float[4];
        final int[] light = new int[4];
        float red;
        float green;
        float blue;
    }
}
