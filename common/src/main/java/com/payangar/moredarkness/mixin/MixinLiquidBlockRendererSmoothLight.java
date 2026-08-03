package com.payangar.moredarkness.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.payangar.moredarkness.config.MoreDarknessConfig;
import com.payangar.moredarkness.darkness.FluidTopFace;
import com.payangar.moredarkness.darkness.WaterTurbidity;
import com.payangar.moredarkness.platform.Services;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

/**
 * Smooth fluid lighting and turbidity layers.
 * Vanilla gives the whole top face of a fluid one light value (all four
 * vertices share it), so light steps block by block across water. On 26.1
 * this wrapped the single addFace call emitting the top face; 1.21.11 has no
 * such call, the face is four separate vertex(...) calls. So the seam moved:
 * the getLightColor call that precedes them computes the four smoothed
 * corners (average of the four columns meeting at each corner), each wrapped
 * vertex call substitutes its corner's light, and the vertices are
 * accumulated so the stacked turbidity layers can be emitted once the fourth
 * corner lands. Both wraps are WrapOperation, not Redirect: water-rendering
 * mods (Big Water) taking over the same calls compose instead of crashing.
 * When such a mod is present it keeps the visible surface (light untouched)
 * and we only stack the turbidity layers beneath it.
 * Sodium replaces this renderer entirely: the mixin never runs there.
 */
@Mixin(LiquidBlockRenderer.class)
public class MixinLiquidBlockRendererSmoothLight {

    @Unique
    private static volatile Boolean moreDarkness_yieldToWaterMod;

    @WrapOperation(
            method = "tesselate",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/block/LiquidBlockRenderer;getLightColor(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;)I",
                    ordinal = 0
            )
    )
    private int moreDarkness_beginTopFace(
            LiquidBlockRenderer instance, BlockAndTintGetter level, BlockPos pos, Operation<Integer> original) {
        if (MoreDarknessConfig.getInstance().enableMod) {
            FluidTopFace.CURRENT.get().begin(
                    moreDarkness_cornerLight(level, pos, -1, -1),
                    moreDarkness_cornerLight(level, pos, 1, -1),
                    moreDarkness_cornerLight(level, pos, -1, 1),
                    moreDarkness_cornerLight(level, pos, 1, 1));
        }
        return original.call(instance, level, pos);
    }

    @WrapOperation(
            method = "tesselate",
            slice = @Slice(
                    from = @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/renderer/block/LiquidBlockRenderer;getLightColor(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;)I",
                            ordinal = 0
                    ),
                    to = @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/renderer/block/LiquidBlockRenderer;getLightColor(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;)I",
                            ordinal = 1
                    )
            ),
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/block/LiquidBlockRenderer;vertex(Lcom/mojang/blaze3d/vertex/VertexConsumer;FFFFFFFFI)V"
            )
    )
    private void moreDarkness_smoothTopFaceVertex(
            LiquidBlockRenderer instance, VertexConsumer builder,
            float x, float y, float z, float red, float green, float blue, float u, float v, int packedLight,
            Operation<Void> original,
            @Local(argsOnly = true) BlockAndTintGetter level,
            @Local(argsOnly = true) BlockPos pos,
            @Local(argsOnly = true) FluidState fluidState) {
        if (!MoreDarknessConfig.getInstance().enableMod) {
            original.call(instance, builder, x, y, z, red, green, blue, u, v, packedLight);
            return;
        }

        // Which corner of the block this vertex sits on: top-face x/z are
        // exactly the section-local block coordinates, plus 0 or 1
        int dx = Mth.clamp(Math.round(x) - (pos.getX() & 15), 0, 1);
        int dz = Mth.clamp(Math.round(z) - (pos.getZ() & 15), 0, 1);
        FluidTopFace face = FluidTopFace.CURRENT.get();
        int cornerLight = face.cornerLight(dx, dz);

        // A water-rendering mod owns the visible surface (waves, its own
        // lighting); we only stack our turbidity layers underneath it.
        boolean yieldSurface = moreDarkness_shouldYield();
        original.call(instance, builder, x, y, z, red, green, blue, u, v, yieldSurface ? packedLight : cornerLight);

        if (face.isComplete()) {
            // Back-face vertices of a face whose layers are already stacked
            return;
        }
        face.add(x, y, z, u, v, cornerLight, red, green, blue);
        if (face.isComplete()) {
            moreDarkness_stackTurbidityLayers(builder, level, pos, fluidState, face);
        }
    }

    /**
     * Turbidity from above: each extra coplanar layer compounds the texture's
     * translucency, so murky water hides its bottom. The fractional part
     * fades the last layer through its vertex alpha, so biome borders blend
     * over a few blocks instead of snapping.
     */
    @Unique
    private static void moreDarkness_stackTurbidityLayers(
            VertexConsumer builder, BlockAndTintGetter level, BlockPos pos, FluidState fluidState, FluidTopFace face) {
        float turbidity = MoreDarknessConfig.getInstance().darkerWater && fluidState.is(FluidTags.WATER)
                ? WaterTurbidity.level(pos)
                : 0.0f;
        boolean backFace = fluidState.shouldRenderBackwardUpFace(level, pos.above());

        for (int layer = 1; layer <= 2; layer++) {
            float layerAlpha = Mth.clamp(turbidity - (layer - 1), 0.0f, 1.0f);
            if (layerAlpha < 0.05f) {
                break;
            }
            float dy = 0.002f * layer;
            for (int i = 0; i < 4; i++) {
                moreDarkness_vertex(builder, face, i, dy, layerAlpha);
            }
            if (backFace) {
                for (int i = 3; i >= 0; i--) {
                    moreDarkness_vertex(builder, face, i, dy, layerAlpha);
                }
            }
        }
    }

    /** Water-rendering mods own the fluid surfaces when present. */
    @Unique
    private static boolean moreDarkness_shouldYield() {
        Boolean yield = moreDarkness_yieldToWaterMod;
        if (yield == null) {
            yield = Services.PLATFORM.isModLoaded("bigwater");
            moreDarkness_yieldToWaterMod = yield;
        }
        return yield;
    }

    /**
     * Smoothed light for the corner offset by (ox, oz): the average of the
     * four columns meeting there, each sampled like the vanilla private
     * getLightColor (max of the fluid block and the block above it).
     */
    @Unique
    private static int moreDarkness_cornerLight(BlockAndTintGetter level, BlockPos pos, int ox, int oz) {
        int self = moreDarkness_fluidLight(level, pos);
        int side1 = moreDarkness_fluidLight(level, pos.offset(ox, 0, 0));
        int side2 = moreDarkness_fluidLight(level, pos.offset(0, 0, oz));
        int corner = moreDarkness_fluidLight(level, pos.offset(ox, 0, oz));
        return moreDarkness_average(self, side1, side2, corner);
    }

    /** Same sampling as the vanilla private getLightColor. */
    @Unique
    private static int moreDarkness_fluidLight(BlockAndTintGetter level, BlockPos pos) {
        int below = LevelRenderer.getLightColor(level, pos);
        int above = LevelRenderer.getLightColor(level, pos.above());
        int block = Math.max(below & 0xFF, above & 0xFF);
        int sky = Math.max(below >> 16 & 0xFF, above >> 16 & 0xFF);
        return block | sky << 16;
    }

    /** Averages the block and sky halves of packed light coords separately. */
    @Unique
    private static int moreDarkness_average(int a, int b, int c, int d) {
        int block = ((a & 0xFFFF) + (b & 0xFFFF) + (c & 0xFFFF) + (d & 0xFFFF)) / 4;
        int sky = ((a >>> 16) + (b >>> 16) + (c >>> 16) + (d >>> 16)) / 4;
        return (sky << 16) | block;
    }

    @Unique
    private static void moreDarkness_vertex(VertexConsumer builder, FluidTopFace face, int i, float dy, float alpha) {
        builder.addVertex(face.x[i], face.y[i] - dy, face.z[i])
                .setColor(face.red, face.green, face.blue, alpha)
                .setUv(face.u[i], face.v[i])
                .setLight(face.light[i])
                .setNormal(0.0F, 1.0F, 0.0F);
    }
}
