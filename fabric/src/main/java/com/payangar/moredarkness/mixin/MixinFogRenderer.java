package com.payangar.moredarkness.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.payangar.moredarkness.fog.DepthFogCalculator;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FogRenderer.class)
public class MixinFogRenderer {

    @Inject(method = "setupFog", at = @At("TAIL"))
    private static void moreDarkness_modifyFog(Camera camera, FogRenderer.FogMode fogMode, float viewDistance, boolean thickFog, float partialTick, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        float endMultiplier = DepthFogCalculator.getFogEndMultiplier(client);
        float startMultiplier = DepthFogCalculator.getFogStartMultiplier(client);

        if (endMultiplier < 1.0f || startMultiplier < 1.0f) {
            float currentEnd = RenderSystem.getShaderFogEnd();
            float currentStart = RenderSystem.getShaderFogStart();

            RenderSystem.setShaderFogEnd(currentEnd * endMultiplier);
            RenderSystem.setShaderFogStart(currentStart * startMultiplier);
        }
    }

    @Inject(method = "setupColor", at = @At("TAIL"))
    private static void moreDarkness_modifyFogColor(Camera camera, float partialTick, net.minecraft.client.multiplayer.ClientLevel level, int renderDistance, float darkenWorldAmount, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        float colorFactor = DepthFogCalculator.getFogColorFactor(client);

        if (colorFactor < 1.0f) {
            float[] color = RenderSystem.getShaderFogColor();
            RenderSystem.setShaderFogColor(
                    color[0] * colorFactor,
                    color[1] * colorFactor,
                    color[2] * colorFactor
            );
        }
    }
}
