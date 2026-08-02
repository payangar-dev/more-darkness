package com.payangar.moredarkness.darkness;

import com.payangar.moredarkness.config.MoreDarknessConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.LightmapRenderState;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.MoonPhase;
import org.joml.Vector3f;

/**
 * Darkens the lightmap based on moon phase, dimension and config.
 * Since 1.21.2 the lightmap is computed on the GPU (core/lightmap.fsh), so the
 * mod shapes its CPU-side inputs (LightmapRenderState) instead of rewriting pixels.
 */
public final class DarknessCalculator {

    /** Night value of the sky_light_factor track in the vanilla day timeline. */
    private static final float VANILLA_NIGHT_SKY_FACTOR = 0.24f;

    private DarknessCalculator() {}

    /**
     * Adjusts the extracted lightmap render state for the current frame.
     * The brightness (gamma) bypass is left untouched: the 26.1 shader's
     * notGamma(0) == 0, so fully dark cells stay black at any gamma setting.
     */
    public static void apply(LightmapRenderState renderState, Camera camera, float partialTicks) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        MoreDarknessConfig config = MoreDarknessConfig.getInstance();
        if (!config.enableMod || !isDarkDimension(level, config)) {
            return;
        }

        // Moon-phase night darkening: scale the sky light contribution
        if (level.dimensionType().hasSkyLight()) {
            renderState.skyFactor *= nightFactor(renderState.skyFactor, camera, config, partialTicks);
        } else {
            renderState.skyFactor = 0.0f;
        }

        // FIXME: fake - perception spike step 1, hardcoded knobs below.
        // Smoother torch falloff: flat boost of the block light contribution
        // (high levels already clamp at 1, so this mostly lifts the mid range).
        renderState.blockFactor *= 1.5f;

        // Dark adaptation amplifies whatever light exists (rods gain), it
        // cannot create light: pitch black cells stay pitch black, dim ones
        // become readable once the eye is adapted.
        float adaptationGain = 1.0f + 0.9f * EyeState.rodEngagement();
        renderState.blockFactor *= adaptationGain;
        renderState.skyFactor *= adaptationGain;

        // Ambient floor: configured cave ambient, raised by dark adaptation
        // (0 by default -> pitch black caves until the eye adapts)
        float caveAmbient = config.caveDarkness * 0.05f;
        float ambient = Math.max(caveAmbient, EyeState.darkSightFloor());
        renderState.ambientColor = new Vector3f(ambient, ambient, ambient);
    }

    /**
     * Multiplier for the vanilla sky light factor: 1 during the day, the
     * moon-phase brightness at night, blended through dusk/dawn.
     */
    private static float nightFactor(float skyFactor, Camera camera, MoreDarknessConfig config, float partialTicks) {
        // How "day" it is right now, derived from the vanilla timeline value
        // (1.0 at noon, VANILLA_NIGHT_SKY_FACTOR at deep night). The end flash
        // boost raises skyFactor, so flashes naturally reduce the darkening.
        float dayness = Mth.clamp(
                (skyFactor - VANILLA_NIGHT_SKY_FACTOR) / (1.0f - VANILLA_NIGHT_SKY_FACTOR),
                0.0f, 1.0f);

        float moonBrightness;
        if (config.moonPhaseEffect) {
            // Moon brightness: 1.0 (full moon) to 0.0 (new moon), squared for perceptual curve
            MoonPhase phase = camera.attributeProbe().getValue(EnvironmentAttributes.MOON_PHASE, partialTicks);
            float rawMoon = 1.0f - 0.25f * Math.min(phase.index(), MoonPhase.COUNT - phase.index());
            moonBrightness = rawMoon * rawMoon;
        } else {
            moonBrightness = 0.0f;
        }

        // Apply minimum moon brightness (starlight, atmospheric glow)
        moonBrightness = Math.max(config.minimumMoonBrightness, moonBrightness);

        return Mth.lerp(dayness, moonBrightness, 1.0f);
    }

    private static boolean isDarkDimension(Level level, MoreDarknessConfig config) {
        ResourceKey<Level> dim = level.dimension();
        if (dim == Level.OVERWORLD) return config.darkOverworld;
        if (dim == Level.NETHER) return config.darkNether;
        if (dim == Level.END) return config.darkEnd;
        // Modded dimensions: darken if they have sky light
        return level.dimensionType().hasSkyLight();
    }
}
