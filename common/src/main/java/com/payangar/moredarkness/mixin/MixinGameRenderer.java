package com.payangar.moredarkness.mixin;

import com.payangar.moredarkness.darkness.DarknessCalculator;
import com.payangar.moredarkness.darkness.LightmapAccess;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {

    @Shadow
    private Minecraft minecraft;

    @Shadow
    private LightTexture lightTexture;

    @Inject(method = "renderLevel", at = @At(value = "HEAD"))
    private void moreDarkness_onRenderLevel(DeltaTracker deltaTracker, CallbackInfo ci) {
        LightmapAccess lightmap = (LightmapAccess) lightTexture;

        if (lightmap.moreDarkness_isDirty()) {
            DarknessCalculator.updateLuminance(
                    deltaTracker.getGameTimeDeltaTicks(),
                    minecraft,
                    (GameRenderer) (Object) this,
                    lightmap.moreDarkness_prevFlicker()
            );
        }
    }
}
