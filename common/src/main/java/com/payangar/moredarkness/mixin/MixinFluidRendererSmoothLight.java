package com.payangar.moredarkness.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.payangar.moredarkness.config.MoreDarknessConfig;
import com.payangar.moredarkness.darkness.WaterTurbidity;
import com.payangar.moredarkness.platform.Services;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * FIXME: fake - perception spike (smooth fluid lighting + turbidity layers).
 * Vanilla gives the whole top face of a fluid one light value (all four
 * vertices share it), so light steps block by block across water. This wraps
 * the top-face emission to light each corner with the average of the four
 * columns meeting there, and stacks extra translucent layers for turbidity.
 * WrapOperation instead of Redirect: water-rendering mods (Big Water) redirect
 * this exact call, and two redirects on one instruction crash the game. When
 * such a mod is present, it keeps the visible surface (original call) and we
 * only stack the turbidity layers beneath it.
 * Sodium replaces this renderer entirely: the mixin never runs there.
 */
@Mixin(FluidRenderer.class)
public class MixinFluidRendererSmoothLight {

    @Unique
    private static volatile Boolean moreDarkness_yieldToWaterMod;

    @WrapOperation(
            method = "tesselate",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/block/FluidRenderer;addFace(Lcom/mojang/blaze3d/vertex/VertexConsumer;FFFFFFFFFFFFFFFFFFFFIIZ)V",
                    ordinal = 0
            )
    )
    private void moreDarkness_smoothTopFace(
            FluidRenderer instance, VertexConsumer builder,
            float x0, float y0, float z0, float u0, float v0,
            float x1, float y1, float z1, float u1, float v1,
            float x2, float y2, float z2, float u2, float v2,
            float x3, float y3, float z3, float u3, float v3,
            int color, int lightCoords, boolean addBackFace,
            Operation<Void> original,
            @Local(argsOnly = true) BlockAndTintGetter level,
            @Local(argsOnly = true) BlockPos pos,
            @Local(argsOnly = true) FluidState fluidState) {
        if (!MoreDarknessConfig.getInstance().enableMod) {
            original.call(instance, builder, x0, y0, z0, u0, v0, x1, y1, z1, u1, v1,
                    x2, y2, z2, u2, v2, x3, y3, z3, u3, v3, color, lightCoords, addBackFace);
            return;
        }

        boolean yieldSurface = moreDarkness_shouldYield();
        if (yieldSurface) {
            // A water-rendering mod owns the visible surface (waves, its own
            // lighting); we only stack our turbidity layers underneath it.
            original.call(instance, builder, x0, y0, z0, u0, v0, x1, y1, z1, u1, v1,
                    x2, y2, z2, u2, v2, x3, y3, z3, u3, v3, color, lightCoords, addBackFace);
        }

        int self = moreDarkness_fluidLight(level, pos);
        int north = moreDarkness_fluidLight(level, pos.north());
        int south = moreDarkness_fluidLight(level, pos.south());
        int west = moreDarkness_fluidLight(level, pos.west());
        int east = moreDarkness_fluidLight(level, pos.east());
        int northWest = moreDarkness_fluidLight(level, pos.north().west());
        int northEast = moreDarkness_fluidLight(level, pos.north().east());
        int southWest = moreDarkness_fluidLight(level, pos.south().west());
        int southEast = moreDarkness_fluidLight(level, pos.south().east());

        // Vertex order of the top face: NW, SW, SE, NE
        int lightNW = moreDarkness_average(self, north, west, northWest);
        int lightSW = moreDarkness_average(self, south, west, southWest);
        int lightSE = moreDarkness_average(self, south, east, southEast);
        int lightNE = moreDarkness_average(self, north, east, northEast);

        // Turbidity from above: each extra coplanar layer compounds the
        // texture's translucency, so murky water hides its bottom. The
        // fractional part fades the last layer through its vertex alpha,
        // so biome borders blend over a few blocks instead of snapping.
        float turbidity = fluidState.is(FluidTags.WATER) ? WaterTurbidity.level(pos) : 0.0f;

        for (int layer = yieldSurface ? 1 : 0; layer <= 2; layer++) {
            float layerAlpha = layer == 0 ? 1.0f : Mth.clamp(turbidity - (layer - 1), 0.0f, 1.0f);
            if (layerAlpha < 0.05f) {
                break;
            }
            int layerColor = moreDarkness_scaleAlpha(color, layerAlpha);
            float dy = 0.002f * layer;
            moreDarkness_vertex(builder, x0, y0 - dy, z0, layerColor, u0, v0, lightNW);
            moreDarkness_vertex(builder, x1, y1 - dy, z1, layerColor, u1, v1, lightSW);
            moreDarkness_vertex(builder, x2, y2 - dy, z2, layerColor, u2, v2, lightSE);
            moreDarkness_vertex(builder, x3, y3 - dy, z3, layerColor, u3, v3, lightNE);
            if (addBackFace) {
                moreDarkness_vertex(builder, x3, y3 - dy, z3, layerColor, u3, v3, lightNE);
                moreDarkness_vertex(builder, x2, y2 - dy, z2, layerColor, u2, v2, lightSE);
                moreDarkness_vertex(builder, x1, y1 - dy, z1, layerColor, u1, v1, lightSW);
                moreDarkness_vertex(builder, x0, y0 - dy, z0, layerColor, u0, v0, lightNW);
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

    /** Same sampling as the vanilla private getLightCoords. */
    @Unique
    private static int moreDarkness_fluidLight(BlockAndTintGetter level, BlockPos pos) {
        return LightCoordsUtil.max(LightCoordsUtil.getLightCoords(level, pos), LightCoordsUtil.getLightCoords(level, pos.above()));
    }

    /** Averages the block and sky halves of packed light coords separately. */
    @Unique
    private static int moreDarkness_average(int a, int b, int c, int d) {
        int block = ((a & 0xFFFF) + (b & 0xFFFF) + (c & 0xFFFF) + (d & 0xFFFF)) / 4;
        int sky = ((a >>> 16) + (b >>> 16) + (c >>> 16) + (d >>> 16)) / 4;
        return (sky << 16) | block;
    }

    @Unique
    private static int moreDarkness_scaleAlpha(int color, float factor) {
        int alpha = (int) (((color >>> 24) & 0xFF) * factor);
        return (alpha << 24) | (color & 0xFFFFFF);
    }

    @Unique
    private static void moreDarkness_vertex(VertexConsumer builder, float x, float y, float z, int color, float u, float v, int lightCoords) {
        builder.addVertex(x, y, z, color, u, v, OverlayTexture.NO_OVERLAY, lightCoords, 0.0F, 1.0F, 0.0F);
    }
}
