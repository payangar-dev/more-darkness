package com.payangar.moredarkness.fog;

import com.payangar.moredarkness.config.MoreDarknessConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/**
 * Calculates fog parameters based on player depth (Y position).
 * Deeper underground = thicker, darker fog.
 */
public final class DepthFogCalculator {

    private DepthFogCalculator() {}

    /**
     * Returns a depth factor between 0.0 (surface) and 1.0 (deep underground).
     */
    public static float getDepthFactor(Minecraft client) {
        if (client.level == null || client.player == null) return 0.0f;

        // Only apply in overworld
        if (client.level.dimension() != Level.OVERWORLD) return 0.0f;

        MoreDarknessConfig config = MoreDarknessConfig.getInstance();
        if (!config.enableDepthFog) return 0.0f;

        float playerY = (float) client.player.getY();
        float seaLevel = client.level.getSeaLevel();
        float startY = seaLevel + config.depthFogStartY;
        float deepY = client.level.getMinBuildHeight();

        // Map Y position: startY (factor 0) to deepY (factor 1)
        float factor = Mth.clamp((startY - playerY) / (startY - deepY), 0.0f, 1.0f);

        // Apply smoothstep for natural transition
        return factor * factor * (3.0f - 2.0f * factor);
    }

    /**
     * Returns the fog end distance multiplier (1.0 = normal, lower = closer fog).
     */
    public static float getFogEndMultiplier(Minecraft client) {
        float depth = getDepthFactor(client);
        if (depth <= 0.0f) return 1.0f;

        MoreDarknessConfig config = MoreDarknessConfig.getInstance();
        // Lerp between 1.0 (no fog) and minDistance (deepest)
        return Mth.lerp(depth, 1.0f, config.depthFogMinDistance);
    }

    /**
     * Returns the fog start distance multiplier.
     * Fog starts closer as depth increases.
     */
    public static float getFogStartMultiplier(Minecraft client) {
        float depth = getDepthFactor(client);
        if (depth <= 0.0f) return 1.0f;

        // Fog starts at 60% of end distance when deep
        return Mth.lerp(depth, 1.0f, 0.0f);
    }

    /**
     * Returns fog color darkening factor (1.0 = normal, 0.0 = black).
     */
    public static float getFogColorFactor(Minecraft client) {
        float depth = getDepthFactor(client);
        if (depth <= 0.0f) return 1.0f;

        // Darken fog color towards black as depth increases
        return 1.0f - depth * 0.8f;
    }
}
