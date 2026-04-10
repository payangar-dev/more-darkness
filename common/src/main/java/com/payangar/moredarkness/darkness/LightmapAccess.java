package com.payangar.moredarkness.darkness;

/**
 * Interface injected into LightTexture via mixin to access internal state.
 */
public interface LightmapAccess {

    /**
     * Returns the block light red flicker value (fire flicker effect).
     */
    float moreDarkness_prevFlicker();

    /**
     * Returns true if the lightmap texture needs to be updated this frame.
     */
    boolean moreDarkness_isDirty();
}
