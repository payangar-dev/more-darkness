package com.payangar.moredarkness.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import com.payangar.moredarkness.darkness.DarknessCalculator;
import com.payangar.moredarkness.darkness.LightmapAccess;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightTexture.class)
public class MixinLightTexture implements LightmapAccess {

    @Shadow
    private NativeImage lightPixels;

    @Shadow
    private float blockLightRedFlicker;

    @Shadow
    private boolean updateLightTexture;

    @Inject(
            method = "updateLightTexture",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/DynamicTexture;upload()V")
    )
    private void moreDarkness_beforeUpload(float partialTick, CallbackInfo ci) {
        if (DarknessCalculator.isActive() && lightPixels != null) {
            for (int block = 0; block < 16; block++) {
                for (int sky = 0; sky < 16; sky++) {
                    int color = DarknessCalculator.darken(lightPixels.getPixelRGBA(block, sky), block, sky);
                    lightPixels.setPixelRGBA(block, sky, color);
                }
            }
        }
    }

    @Override
    public float moreDarkness_prevFlicker() {
        return blockLightRedFlicker;
    }

    @Override
    public boolean moreDarkness_isDirty() {
        return updateLightTexture;
    }
}
