package com.payangar.moredarkness.mixin;

import com.payangar.moredarkness.darkness.DarknessCalculator;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapRenderStateExtractor.class)
public class MixinLightmapRenderStateExtractor {

    @Shadow
    @Final
    private GameRenderer renderer;

    // TAIL only covers the full-extraction path: the early returns
    // (needsUpdate == false, level/player null) skip this injection.
    @Inject(method = "extract", at = @At("TAIL"))
    private void moreDarkness_afterExtract(LightmapRenderState renderState, float partialTicks, CallbackInfo ci) {
        DarknessCalculator.apply(renderState, renderer.getMainCamera(), partialTicks);
    }
}
