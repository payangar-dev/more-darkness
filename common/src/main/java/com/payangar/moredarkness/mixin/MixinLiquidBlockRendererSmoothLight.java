package com.payangar.moredarkness.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.payangar.moredarkness.config.MoreDarknessConfig;
import com.payangar.moredarkness.darkness.FluidSmoothLight;
import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Smooth fluid lighting and turbidity layers.
 * Vanilla gives the whole top face of a fluid one light value (all four
 * vertices share it), so light steps block by block across water. This wraps
 * the top-face vertex emission (the slice between the top-face and
 * bottom-face light lookups) to light each corner with the average of the
 * four columns meeting there, and stacks extra translucent layers for
 * turbidity. WrapOperation instead of Redirect so other mods wrapping the
 * same calls compose instead of crashing. Sodium replaces this renderer
 * entirely: the mixin never runs there.
 */
@Mixin(LiquidBlockRenderer.class)
public class MixinLiquidBlockRendererSmoothLight {

    @Inject(method = "tesselate", at = @At("HEAD"))
    private void moreDarkness_beginBlock(CallbackInfo ci) {
        FluidSmoothLight.beginBlock();
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
    private void moreDarkness_smoothTopFace(
            LiquidBlockRenderer renderer, VertexConsumer consumer,
            float x, float y, float z, float red, float green, float blue,
            float u, float v, int packedLight,
            Operation<Void> original,
            @Local(argsOnly = true) BlockAndTintGetter level,
            @Local(argsOnly = true) BlockPos pos,
            @Local(argsOnly = true) FluidState fluidState) {
        if (!MoreDarknessConfig.getInstance().enableMod) {
            original.call(renderer, consumer, x, y, z, red, green, blue, u, v, packedLight);
            return;
        }
        int cornerLight = FluidSmoothLight.cornerLight(level, pos, x, z);
        original.call(renderer, consumer, x, y, z, red, green, blue, u, v, cornerLight);
        FluidSmoothLight.recordVertex(consumer, level, pos, fluidState, x, y, z, red, green, blue, u, v, cornerLight);
    }
}
