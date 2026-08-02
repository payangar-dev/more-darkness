package com.payangar.moredarkness.darkness;

import net.minecraft.util.Mth;

/**
 * FIXME: fake - throwaway spike (perception system, steps 1 and 3c).
 * Eye adaptation state kept in log2 luminance (EV), engine auto-exposure
 * style: photopic-to-photopic changes (snow vs sky) are fractions of a stop
 * and stay under the deadband, while cave-to-daylight spans several stops.
 * Driven every frame by the average luminance of what is on screen.
 */
public final class EyeState {

    /** Seconds to adapt to darkness / back to light (gameplay-tuned). */
    private static final float DARK_ADAPTATION_SECONDS = 12.0f;
    private static final float LIGHT_ADAPTATION_SECONDS = 1.2f;
    /** Screen luminance floor applied before log2 (~-8 EV). */
    private static final float MIN_LUMINANCE = 0.004f;
    /** Mismatch tolerated without any dazzle, in stops. */
    private static final float DEADBAND_STOPS = 0.7f;
    /** Cap of the transient overexposure (screen vs adaptation), in stops. */
    private static final float MAX_TRANSIENT_STOPS = 1.4f;
    /** Cap of the dark-adapted retinal gain (bloom prefilter), in stops. */
    private static final float MAX_RETINAL_GAIN_STOPS = 4.5f;
    /** Daylight reference the retinal gain is measured against (log2 0.4). */
    private static final float PHOTOPIC_EV = -1.32f;
    /** Dark sight ramps in from this adaptation level (log2 0.15)... */
    private static final float DARK_START_EV = -2.74f;
    /** ...and reaches full strength here (log2 ~0.03). */
    private static final float DARK_FULL_EV = -5.0f;
    /** Lightmap floor granted by full dark adaptation. Deliberately tiny:
     *  adaptation amplifies existing light (see the gain in
     *  DarknessCalculator), it cannot create light in true pitch black. */
    private static final float MAX_DARK_SIGHT = 0.02f;

    /** EV the eye is currently adapted to (daylight start). */
    private static float adaptationEv = -1.0f;
    /** EV of the latest measured average screen luminance. */
    private static float screenEv = -1.0f;

    private EyeState() {}

    /** Fed by the readback of the metering post chain. */
    public static void setScreenLuminance(float luminance) {
        screenEv = (float) (Math.log(Math.max(luminance, MIN_LUMINANCE)) / Math.log(2.0));
    }

    /** Advances the adaptation with the real elapsed time of the frame. */
    public static void frameUpdate(float dtSeconds) {
        float tau = screenEv < adaptationEv ? DARK_ADAPTATION_SECONDS : LIGHT_ADAPTATION_SECONDS;
        adaptationEv += (screenEv - adaptationEv) * (1.0f - (float) Math.exp(-dtSeconds / tau));
    }

    /**
     * How far the rods have taken over, 0 (photopic, cones only) to 1
     * (fully dark-adapted). Gates both the dark sight floor and the
     * scotopic desaturation: an unadapted eye in the dark sees black,
     * not gray (rod-cone break).
     */
    public static float rodEngagement() {
        return Mth.clamp((DARK_START_EV - adaptationEv) / (DARK_START_EV - DARK_FULL_EV), 0.0f, 1.0f);
    }

    /** Extra lightmap floor earned by being dark-adapted right now. */
    public static float darkSightFloor() {
        return MAX_DARK_SIGHT * rodEngagement();
    }

    /**
     * Transient overexposure in stops: how much brighter the screen is than
     * the eye's current state. Drives the full-frame exposure boost, fades
     * as the eye re-adapts.
     */
    public static float glareStops() {
        return Mth.clamp(screenEv - adaptationEv - DEADBAND_STOPS, 0.0f, MAX_TRANSIENT_STOPS);
    }

    /**
     * Dark-adapted retinal gain in stops: how much the eye currently
     * amplifies whatever it sees. Drives the bloom prefilter, so bright
     * spots (a cave exit seen from the dark) halo locally even while the
     * frame average stays dark.
     */
    public static float retinalGainStops() {
        return Mth.clamp(PHOTOPIC_EV - adaptationEv - DEADBAND_STOPS, 0.0f, MAX_RETINAL_GAIN_STOPS);
    }
}
