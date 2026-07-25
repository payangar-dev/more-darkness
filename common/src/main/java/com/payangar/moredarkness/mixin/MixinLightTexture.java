package com.payangar.moredarkness.mixin;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.payangar.moredarkness.darkness.DarknessCalculator;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Reshapes the lightmap UBO before it reaches core/lightmap.fsh.
 *
 * <p>updateLightTexture fills the std140 block in declaration order:
 * 0 AmbientLightFactor, 1 SkyFactor, 2 BlockFactor, 3 NightVisionFactor,
 * 4 DarknessScale, 5 DarkenWorldFactor, 6 BrightnessFactor, then two vec3.
 * Redirecting the writes rather than the values they come from keeps every
 * vanilla adjustment (end flash, boss overlay) upstream of the darkening.
 *
 * <p>An extra write is appended after BrightnessFactor: AmbientFloorFactor,
 * read by the shader this mod ships. It lands in padding the block already had
 * (7 floats reach 28 bytes, the following vec3 aligns to 32), so the UBO stays
 * 64 bytes and the vec3 writes do not move. If anything else wins the shader
 * override, the extra float is simply never read.
 */
@Mixin(LightTexture.class)
public class MixinLightTexture {

    @Redirect(
            method = "updateLightTexture",
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/buffers/Std140Builder;putFloat(F)Lcom/mojang/blaze3d/buffers/Std140Builder;",
                    ordinal = 0))
    private Std140Builder moreDarkness_ambientLightFactor(Std140Builder builder, float ambientLightFactor) {
        return builder.putFloat(DarknessCalculator.ambientLightFactor(ambientLightFactor));
    }

    // Trailing partialTicks is the enclosing method's argument, captured by Mixin.
    @Redirect(
            method = "updateLightTexture",
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/buffers/Std140Builder;putFloat(F)Lcom/mojang/blaze3d/buffers/Std140Builder;",
                    ordinal = 1))
    private Std140Builder moreDarkness_skyFactor(Std140Builder builder, float skyFactor, float partialTicks) {
        return builder.putFloat(DarknessCalculator.skyFactor(skyFactor, partialTicks));
    }

    @Redirect(
            method = "updateLightTexture",
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/buffers/Std140Builder;putFloat(F)Lcom/mojang/blaze3d/buffers/Std140Builder;",
                    ordinal = 6))
    private Std140Builder moreDarkness_ambientFloorFactor(Std140Builder builder, float brightnessFactor) {
        return builder.putFloat(brightnessFactor)
                .putFloat(DarknessCalculator.ambientFloorFactor());
    }
}
