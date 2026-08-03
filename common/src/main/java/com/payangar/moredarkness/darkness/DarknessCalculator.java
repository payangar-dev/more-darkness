package com.payangar.moredarkness.darkness;

import com.payangar.moredarkness.config.MoreDarknessConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.effect.MobEffects;
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

    /** Lightmap value the two vanilla 4% grey mixes give a fully unlit cell. */
    private static final float VANILLA_AMBIENT_FLOOR = 0.06f;

    /** Flat boost of the block light factor, widening the torch falloff. */
    private static final float TORCH_FALLOFF_BOOST = 1.5f;

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
        return vanillaSkyFactor * nightFactor(vanillaSkyFactor, camera, config, partialTicks) * adaptationGain();
    }

    /**
     * BlockFactor uniform: strength of the block light contribution.
     * Smoother torch falloff: flat boost of the block light contribution
     * (high levels already clamp at 1, so this mostly lifts the mid range),
     * then the dark-adaptation gain on top.
     */
    public static float blockFactor(float vanillaBlockFactor) {
        if (darkenedLevel() == null) {
            return vanillaBlockFactor;
        }
        return vanillaBlockFactor * TORCH_FALLOFF_BOOST * adaptationGain();
    }

    /**
     * Dark adaptation amplifies whatever light exists (rods gain), it
     * cannot create light: pitch black cells stay pitch black, dim ones
     * become readable once the eye is adapted. 1 when eye adaptation is off.
     */
    private static float adaptationGain() {
        if (!MoreDarknessConfig.getInstance().eyeAdaptation) {
            return 1.0f;
        }
        return 1.0f + 0.9f * EyeState.rodEngagement();
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
     *
     * <p>The floor is the configured cave ambient raised by dark adaptation
     * (0 by default -> pitch black caves until the eye adapts). The
     * adaptation floor is an absolute lightmap value on 26.1; here it is
     * expressed as a fraction of the ~{@value #VANILLA_AMBIENT_FLOOR} the
     * two grey mixes give an unlit cell.
     */
    public static float ambientFloorFactor() {
        if (darkenedLevel() == null) {
            return 1.0f;
        }
        MoreDarknessConfig config = MoreDarknessConfig.getInstance();
        float floor = config.caveDarkness;
        if (config.eyeAdaptation) {
            floor = Math.max(floor, EyeState.darkSightFloor() / VANILLA_AMBIENT_FLOOR);
        }
        return floor;
    }

    /** The level being rendered, or null when the mod leaves this frame alone. */
    private static ClientLevel darkenedLevel() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null) {
            return null;
        }

        MoreDarknessConfig config = MoreDarknessConfig.getInstance();
        if (!config.enableMod || !isDarkDimension(level, config)) {
            return null;
        }

        // Stand down for enhanced vision, as the 1.21.1 branch did. The 1.21.x
        // shader brightens by scaling the lightmap up towards 1, which cannot
        // lift a cell we drove to zero: night vision would be useless and the
        // scale would divide by zero. 26.1+ takes a max instead and is immune.
        if (hasEnhancedVision(player)) {
            return null;
        }
        return level;
    }

    private static boolean hasEnhancedVision(LocalPlayer player) {
        return player.hasEffect(MobEffects.NIGHT_VISION)
                || (player.hasEffect(MobEffects.CONDUIT_POWER) && player.getWaterVision() > 0.0f);
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
