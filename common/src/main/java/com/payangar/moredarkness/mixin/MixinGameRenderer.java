package com.payangar.moredarkness.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import com.payangar.moredarkness.darkness.DarknessCalculator;
import com.payangar.moredarkness.darkness.LightmapAccess;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {

    @Shadow
    private Minecraft minecraft;

    @Shadow
    private LightTexture lightTexture;

    @Unique
    private boolean moreDarkness_wasDirty;

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void moreDarkness_onRenderLevel(DeltaTracker deltaTracker, CallbackInfo ci) {
        LightmapAccess lightmap = (LightmapAccess) lightTexture;
        moreDarkness_wasDirty = lightmap.moreDarkness_isDirty();

        if (moreDarkness_wasDirty) {
            DarknessCalculator.updateLuminance(
                    deltaTracker.getGameTimeDeltaTicks(),
                    minecraft,
                    (GameRenderer) (Object) this,
                    lightmap.moreDarkness_prevFlicker()
            );
        }
    }

    @Inject(
            method = "renderLevel",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LightTexture;updateLightTexture(F)V",
                    shift = At.Shift.AFTER)
    )
    private void moreDarkness_afterLightmapUpdate(DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!moreDarkness_wasDirty || !DarknessCalculator.isActive()) return;

        LightmapAccess lightmap = (LightmapAccess) lightTexture;
        NativeImage pixels = lightmap.moreDarkness_getLightPixels();
        if (pixels == null) return;

        for (int block = 0; block < 16; block++) {
            for (int sky = 0; sky < 16; sky++) {
                int color = DarknessCalculator.darken(pixels.getPixelRGBA(block, sky), block, sky);
                pixels.setPixelRGBA(block, sky, color);
            }
        }
        lightmap.moreDarkness_getLightTexture().upload();
    }
}
