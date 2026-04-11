package com.payangar.moredarkness.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import com.payangar.moredarkness.darkness.LightmapAccess;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(LightTexture.class)
public class MixinLightTexture implements LightmapAccess {

    @Shadow @Final
    private NativeImage lightPixels;

    @Shadow @Final
    private DynamicTexture lightTexture;

    @Shadow
    private float blockLightRedFlicker;

    @Shadow
    private boolean updateLightTexture;

    @Override
    public float moreDarkness_prevFlicker() {
        return blockLightRedFlicker;
    }

    @Override
    public boolean moreDarkness_isDirty() {
        return updateLightTexture;
    }

    @Override
    public NativeImage moreDarkness_getLightPixels() {
        return lightPixels;
    }

    @Override
    public DynamicTexture moreDarkness_getLightTexture() {
        return lightTexture;
    }
}
