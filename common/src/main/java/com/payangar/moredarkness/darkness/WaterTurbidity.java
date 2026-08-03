package com.payangar.moredarkness.darkness;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;

/**
 * Per-biome water turbidity, classified by biome key name so modded biomes
 * with matching names grade naturally.
 * Swamps hide everything, warm tropical oceans stay clear, everything else
 * sits in between. Reads the biome off the client level directly: fine for
 * loaded chunks, also from meshing threads (plain chunk data reads).
 */
public final class WaterTurbidity {

    public enum Grade {
        CLEAR(0.0f),
        NORMAL(1.0f),
        // Capped below 2: a full second layer reads as a wall, not water
        MURKY(1.6f);

        public final float value;

        Grade(float value) {
            this.value = value;
        }
    }

    private WaterTurbidity() {}

    public static Grade at(BlockPos pos) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return Grade.NORMAL;
        }
        return gradeAt(level, pos);
    }

    /**
     * Fractional turbidity, 0 (clear) to 2 (murky), averaged over a small
     * horizontal neighborhood so biome borders fade over a few blocks
     * instead of snapping on the 4-block biome grid.
     */
    public static float level(BlockPos pos) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return 1.0f;
        }
        float sum = gradeAt(level, pos).value
                + gradeAt(level, pos.offset(4, 0, 4)).value
                + gradeAt(level, pos.offset(-4, 0, 4)).value
                + gradeAt(level, pos.offset(4, 0, -4)).value
                + gradeAt(level, pos.offset(-4, 0, -4)).value;
        return sum / 5.0f;
    }

    private static Grade gradeAt(ClientLevel level, BlockPos pos) {
        String biome = level.getBiome(pos).getRegisteredName();
        if (biome.contains("swamp") || biome.contains("mangrove")) {
            return Grade.MURKY;
        }
        if (biome.contains("warm_ocean")) {
            return Grade.CLEAR;
        }
        return Grade.NORMAL;
    }
}
