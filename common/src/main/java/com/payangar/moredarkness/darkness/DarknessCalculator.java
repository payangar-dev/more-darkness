package com.payangar.moredarkness.darkness;

import com.payangar.moredarkness.config.MoreDarknessConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.MoonPhase;

/**
 * Darkens the lightmap based on moon phase, dimension and config.
 * Since 1.21.2 the lightmap is computed on the GPU (core/lightmap.fsh), so the
 * mod shapes the uniforms LightTexture uploads instead of rewriting pixels.
 */
public final class DarknessCalculator {

    /** Night value of the sky_light_factor track in the vanilla day timeline. */
    private static final float VANILLA_NIGHT_SKY_FACTOR = 0.24f;

    private DarknessCalculator() {}

    /**
     * SkyFactor uniform: how much of the sky light column reaches the world.
     * Scaled by the moon-phase curve at night, zeroed where there is no sky light.
     * The brightness (gamma) uniform is left untouched: the shader's
     * notGamma(0) == 0, so fully dark cells stay black at any gamma setting.
     */
    public static float skyFactor(float vanillaSkyFactor, float partialTicks) {
        ClientLevel level = darkenedLevel();
        if (level == null) {
            return vanillaSkyFactor;
        }
        if (!level.dimensionType().hasSkyLight()) {
            return 0.0f;
        }

        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        MoreDarknessConfig config = MoreDarknessConfig.getInstance();
        return vanillaSkyFactor * nightFactor(vanillaSkyFactor, camera, config, partialTicks);
    }

    /**
     * AmbientLightFactor uniform: how far the lightmap is lifted towards the
     * dimension's ambient colour. Scaled by caveDarkness, so 0 removes the lift
     * entirely. Only the Nether and the End have a non-zero vanilla lift.
     */
    public static float ambientLightFactor(float vanillaAmbientLight) {
        ClientLevel level = darkenedLevel();
        if (level == null) {
            return vanillaAmbientLight;
        }
        return vanillaAmbientLight * MoreDarknessConfig.getInstance().caveDarkness;
    }

    /**
     * AmbientFloorFactor uniform, added by this mod's copy of core/lightmap.fsh.
     * Scales the two hardcoded 4% grey mixes that otherwise hold unlit cells at
     * roughly 0.06, which is what stops Overworld caves from going black on
     * 1.21.x. 1.0 is vanilla, 0 removes the floor.
     */
    public static float ambientFloorFactor() {
        if (darkenedLevel() == null) {
            return 1.0f;
        }
        return MoreDarknessConfig.getInstance().caveDarkness;
    }

    /** The level being rendered, or null when the mod leaves this frame alone. */
    private static ClientLevel darkenedLevel() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }

        MoreDarknessConfig config = MoreDarknessConfig.getInstance();
        if (!config.enableMod || !isDarkDimension(level, config)) {
            return null;
        }
        return level;
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
