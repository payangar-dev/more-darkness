package com.payangar.moredarkness.mixin;

import com.payangar.moredarkness.config.MoreDarknessConfig;
import com.payangar.moredarkness.darkness.WaterTurbidity;
import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.WaterFogEnvironment;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * FIXME: fake - perception spike (underwater turbidity), hardcoded values.
 * Vanilla water lets adapted eyes see 96 blocks (WATER_FOG_END_DISTANCE x
 * waterVision); real water is far murkier. Keeps vanilla's underwater eye
 * accustoming (waterVision) but caps visibility at swimming-hole levels.
 */
@Mixin(WaterFogEnvironment.class)
public class MixinWaterFogEnvironment {

    /** Seconds for the fog to blend when swimming across a biome border. */
    @Unique
    private static final float BLEND_SECONDS = 2.5f;

    @Unique
    private static float moreDarkness_smoothedEnd = Float.NaN;

    @Unique
    private static long moreDarkness_lastNanos;

    @Inject(method = "setupFog", at = @At("TAIL"))
    private void moreDarkness_murkyWater(FogData fog, Camera camera, ClientLevel level, float renderDistance, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!MoreDarknessConfig.getInstance().enableMod) {
            return;
        }
        float waterVision = camera.entity() instanceof LocalPlayer player ? player.getWaterVision() : 1.0f;

        // Fractional turbidity -> target distance, lerped between the grades
        float turbidity = WaterTurbidity.level(BlockPos.containing(camera.position()));
        float endClear = 30.0f + 18.0f * waterVision;
        float endNormal = 10.0f + 14.0f * waterVision;
        float endMurky = 3.0f + 2.0f * waterVision;
        float target = turbidity <= 1.0f
                ? Mth.lerp(turbidity, endClear, endNormal)
                : Mth.lerp(turbidity - 1.0f, endNormal, endMurky);

        // Temporal blend, reset on a fresh dive (no update for a while)
        long now = System.nanoTime();
        float dt = (now - moreDarkness_lastNanos) / 1.0e9f;
        moreDarkness_lastNanos = now;
        if (Float.isNaN(moreDarkness_smoothedEnd) || dt > 0.5f) {
            moreDarkness_smoothedEnd = target;
        } else {
            moreDarkness_smoothedEnd += (target - moreDarkness_smoothedEnd) * (1.0f - (float) Math.exp(-dt / BLEND_SECONDS));
        }

        fog.environmentalStart = 0.0f;
        fog.environmentalEnd = moreDarkness_smoothedEnd;
        fog.skyEnd = fog.environmentalEnd;
        fog.cloudEnd = fog.environmentalEnd;
    }
}
