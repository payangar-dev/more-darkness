package com.payangar.moredarkness.darkness;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;

/**
 * Interface injected into LightTexture via mixin to access internal state.
 */
public interface LightmapAccess {

    float moreDarkness_prevFlicker();

    boolean moreDarkness_isDirty();

    NativeImage moreDarkness_getLightPixels();

    DynamicTexture moreDarkness_getLightTexture();
}
