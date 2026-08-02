package com.payangar.moredarkness.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.payangar.moredarkness.config.MoreDarknessConfig;
import com.payangar.moredarkness.darkness.WaterTurbidity;
import net.minecraft.client.Camera;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * FIXME: fake - perception spike port (underwater turbidity), hardcoded values.
 * Vanilla water lets adapted eyes see 96 blocks (fog end x waterVision);
 * real water is far murkier. Keeps vanilla's underwater eye accustoming
 * (waterVision) but caps visibility at swimming-hole levels, graded by the
 * biome turbidity and blended over time across biome borders.
 */
@Mixin(FogRenderer.class)
public class MixinFogRendererWater {

    /** Seconds for the fog to blend when swimming across a biome border. */
    @Unique
    private static final float BLEND_SECONDS = 2.5f;

    @Unique
    private static float moreDarkness_smoothedEnd = Float.NaN;

    @Unique
    private static long moreDarkness_lastNanos;

    @Inject(method = "setupFog", at = @At("TAIL"))
    private static void moreDarkness_murkyWater(Camera camera, FogRenderer.FogMode fogMode, float renderDistance, boolean isFoggy, float partialTick, CallbackInfo ci) {
        if (!MoreDarknessConfig.getInstance().enableMod) {
            return;
        }
        if (camera.getFluidInCamera() != FogType.WATER) {
            return;
        }
        Entity entity = camera.getEntity();
        // Blindness and darkness fog take priority in vanilla: leave them alone
        if (entity instanceof LivingEntity living
                && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS))) {
            return;
        }
        float waterVision = entity instanceof LocalPlayer player ? player.getWaterVision() : 1.0f;

        // Fractional turbidity -> target distance, lerped between the grades
        float turbidity = WaterTurbidity.level(BlockPos.containing(camera.getPosition()));
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

        RenderSystem.setShaderFogStart(0.0f);
        RenderSystem.setShaderFogEnd(moreDarkness_smoothedEnd);
    }
}
