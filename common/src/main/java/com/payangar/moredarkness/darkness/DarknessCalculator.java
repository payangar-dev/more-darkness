package com.payangar.moredarkness.darkness;

import com.payangar.moredarkness.config.MoreDarknessConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

/**
 * Computes a darkened lightmap based on moon phase, dimension and config.
 * The lightmap is a 16x16 texture: blockLight (x) x skyLight (y).
 */
public final class DarknessCalculator {

    private static final float[][] LUMINANCE = new float[16][16];
    private static boolean active = false;

    private DarknessCalculator() {}

    public static boolean isActive() {
        return active;
    }

    /**
     * Recalculates the target luminance matrix for the current frame.
     * Called once per frame when the lightmap is dirty.
     */
    public static void updateLuminance(float tickDelta, Minecraft client, GameRenderer renderer, float prevFlicker) {
        ClientLevel world = client.level;
        if (world == null || client.player == null) {
            active = false;
            return;
        }

        MoreDarknessConfig config = MoreDarknessConfig.getInstance();

        if (!config.enableMod || !isDarkDimension(world, config)) {
            active = false;
            return;
        }

        // Don't darken when player has night vision, conduit power, or lightning flash
        if (client.player.hasEffect(MobEffects.NIGHT_VISION)
                || (client.player.hasEffect(MobEffects.CONDUIT_POWER) && client.player.getWaterVision() > 0)
                || world.getSkyFlashTime() > 0) {
            active = false;
            return;
        }

        active = true;

        float dimSkyFactor = skyFactor(world, config);
        float ambient = world.getSkyDarken(1.0f);
        DimensionType dim = world.dimensionType();

        for (int skyIndex = 0; skyIndex < 16; ++skyIndex) {
            // Sky light curve: 1 - (1 - s/15)^4
            float skyFactor = 1f - skyIndex / 15f;
            skyFactor = 1 - skyFactor * skyFactor * skyFactor * skyFactor;
            skyFactor *= dimSkyFactor;

            // Ambient minimum based on sky contribution
            float min = skyFactor * 0.05f;
            float rawAmbient = ambient * skyFactor;
            float minAmbient = rawAmbient * (1 - min) + min;
            float skyBase = LightTexture.getBrightness(dim, skyIndex) * minAmbient;

            min = 0.35f * skyFactor;
            float skyRed = skyBase * (rawAmbient * (1 - min) + min);
            float skyGreen = skyBase * (rawAmbient * (1 - min) + min);
            float skyBlue = skyBase;

            // World darkening effect (boss bar, etc.)
            if (renderer.getDarkenWorldAmount(tickDelta) > 0.0f) {
                float darken = renderer.getDarkenWorldAmount(tickDelta);
                skyRed = skyRed * (1.0f - darken) + skyRed * 0.7f * darken;
                skyGreen = skyGreen * (1.0f - darken) + skyGreen * 0.6f * darken;
                skyBlue = skyBlue * (1.0f - darken) + skyBlue * 0.6f * darken;
            }

            for (int blockIndex = 0; blockIndex < 16; ++blockIndex) {
                // Block light curve
                float blockFactor = 1f - blockIndex / 15f;
                blockFactor = 1 - blockFactor * blockFactor * blockFactor * blockFactor;

                float blockBase = blockFactor * LightTexture.getBrightness(dim, blockIndex) * (prevFlicker * 0.1f + 1.5f);
                min = 0.4f * blockFactor;
                float blockGreen = blockBase * ((blockBase * (1 - min) + min) * (1 - min) + min);
                float blockBlue = blockBase * (blockBase * blockBase * (1 - min) + min);

                float red = skyRed + blockBase;
                float green = skyGreen + blockGreen;
                float blue = skyBlue + blockBlue;

                // Cave ambient: when skyIndex == 0, add configured cave ambient light
                if (skyIndex == 0 && config.caveDarkness > 0.0f) {
                    float caveAmbient = config.caveDarkness * 0.05f;
                    red += caveAmbient;
                    green += caveAmbient;
                    blue += caveAmbient;
                }

                // Minimum brightness to prevent total black artifacts
                float f = Math.max(skyFactor, blockFactor);
                min = 0.03f * f;
                red = red * (0.99f - min) + min;
                green = green * (0.99f - min) + min;
                blue = blue * (0.99f - min) + min;

                // End dimension special coloring
                if (world.dimension() == Level.END) {
                    red = skyFactor * 0.22f + blockBase * 0.75f;
                    green = skyFactor * 0.28f + blockGreen * 0.75f;
                    blue = skyFactor * 0.25f + blockBlue * 0.75f;
                }

                // Gamma correction
                float gamma = client.options.gamma().get().floatValue() * f;
                float invRed = 1.0f - Mth.clamp(red, 0.0f, 1.0f);
                float invGreen = 1.0f - Mth.clamp(green, 0.0f, 1.0f);
                float invBlue = 1.0f - Mth.clamp(blue, 0.0f, 1.0f);
                invRed = 1.0f - invRed * invRed * invRed * invRed;
                invGreen = 1.0f - invGreen * invGreen * invGreen * invGreen;
                invBlue = 1.0f - invBlue * invBlue * invBlue * invBlue;
                red = red * (1.0f - gamma) + invRed * gamma;
                green = green * (1.0f - gamma) + invGreen * gamma;
                blue = blue * (1.0f - gamma) + invBlue * gamma;

                red = Mth.clamp(red, 0.0f, 1.0f);
                green = Mth.clamp(green, 0.0f, 1.0f);
                blue = Mth.clamp(blue, 0.0f, 1.0f);

                LUMINANCE[blockIndex][skyIndex] = luminance(red, green, blue);
            }
        }
    }

    /**
     * Darkens a lightmap pixel to match the pre-calculated target luminance.
     * Preserves hue, only scales brightness.
     */
    public static int darken(int color, int blockIndex, int skyIndex) {
        float lTarget = LUMINANCE[blockIndex][skyIndex];
        float r = (color & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = ((color >> 16) & 0xFF) / 255f;
        float l = luminance(r, g, b);
        float f = l > 0 ? Math.min(1, lTarget / l) : 0;

        if (f == 1f) return color;

        return 0xFF000000
                | Math.round(f * r * 255)
                | (Math.round(f * g * 255) << 8)
                | (Math.round(f * b * 255) << 16);
    }

    /**
     * sRGB perceptual luminance.
     */
    public static float luminance(float r, float g, float b) {
        return r * 0.2126f + g * 0.7152f + b * 0.0722f;
    }

    /**
     * Computes sky light factor based on time of day and moon phase.
     * Returns 0..1 where 0 = full darkness, 1 = vanilla brightness.
     */
    private static float skyFactor(Level world, MoreDarknessConfig config) {
        if (!world.dimensionType().hasSkyLight()) {
            return 0;
        }

        float angle = world.getTimeOfDay(0);

        // Daytime (angle 0.25 - 0.75): gradually transition to night
        if (angle > 0.25f && angle < 0.75f) {
            // Weight for transition: 0 at deep day, 1 at sunrise/sunset
            float transitionWeight = Math.max(0, (Math.abs(angle - 0.5f) - 0.2f)) * 20;
            transitionWeight = transitionWeight * transitionWeight * transitionWeight;

            float moonBrightness;
            if (config.moonPhaseEffect) {
                // Moon brightness: 0.25 (new moon) to 1.0 (full moon), squared for perceptual curve
                float rawMoon = world.getMoonBrightness();
                moonBrightness = rawMoon * rawMoon;
            } else {
                moonBrightness = 0;
            }

            // Apply minimum moon brightness (starlight, atmospheric glow)
            moonBrightness = Math.max(config.minimumMoonBrightness, moonBrightness);

            return Mth.lerp(transitionWeight, moonBrightness, 1f);
        } else {
            // Full night
            return 1;
        }
    }

    private static boolean isDarkDimension(Level world, MoreDarknessConfig config) {
        ResourceKey<Level> dim = world.dimension();
        if (dim == Level.OVERWORLD) return config.darkOverworld;
        if (dim == Level.NETHER) return config.darkNether;
        if (dim == Level.END) return config.darkEnd;
        // Modded dimensions: darken if they have sky light
        return world.dimensionType().hasSkyLight();
    }
}
