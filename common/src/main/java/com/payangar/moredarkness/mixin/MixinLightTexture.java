package com.payangar.moredarkness.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import com.payangar.moredarkness.darkness.DarknessCalculator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Darkens the freshly computed lightmap pixels INSIDE updateLightTexture,
 * right before vanilla's own upload. This point only executes on dirty
 * ticks, produces a single upload, and guarantees that any mod copying the
 * pixels at RETURN of this method (Distant Horizons mirrors them into its
 * own LOD lightmap there, every frame) always sees the darkened version.
 * The previous shape (rescale + second upload from the GameRenderer call
 * site) left one bright frame per tick in such copies: DH LODs flickered
 * between light levels 20 times a second.
 */
@Mixin(LightTexture.class)
public class MixinLightTexture {

    @Shadow
    @Final
    private NativeImage lightPixels;

    @Shadow
    private float blockLightRedFlicker;

    @Inject(
            method = "updateLightTexture(F)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/texture/DynamicTexture;upload()V"
            )
    )
    private void moreDarkness_darkenBeforeUpload(float partialTicks, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        DarknessCalculator.updateLuminance(partialTicks, minecraft, minecraft.gameRenderer, this.blockLightRedFlicker);
        if (!DarknessCalculator.isActive()) {
            return;
        }
        for (int block = 0; block < 16; block++) {
            for (int sky = 0; sky < 16; sky++) {
                int color = DarknessCalculator.darken(this.lightPixels.getPixelRGBA(block, sky), block, sky);
                this.lightPixels.setPixelRGBA(block, sky, color);
            }
        }
    }
}
