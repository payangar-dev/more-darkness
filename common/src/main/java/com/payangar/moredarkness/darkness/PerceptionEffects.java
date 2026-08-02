package com.payangar.moredarkness.darkness;

import com.google.gson.JsonSyntaxException;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.payangar.moredarkness.Constants;
import com.payangar.moredarkness.mixin.PostChainAccessor;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;

/**
 * FIXME: fake - perception spike port (post passes, 1.21.1 backend).
 * Owns the hand-instantiated PostChains of the perception system. On this
 * version GameRenderer only manages a single vanilla postEffect, so the
 * chains are created lazily on the render thread, resized by polling the
 * main target dimensions, and fed per-frame uniforms through the effect
 * instances (plain GL uniforms in this era, no buffer swapping needed).
 *
 * Chain layout (old hand-parsed post JSON format):
 * - scotopic: mesopic desaturation, then the metering pyramid downsampling
 *   the pre-glare frame to a persistent-enough 1x1 target read back each
 *   frame with a synchronous 1x1 glGetTexImage (Screenshot-style), which is
 *   what drives EyeState.
 * - glare: Spencer-style prefilter + dual-filter bloom pyramid + composite,
 *   processed only while there is dazzle or retinal gain.
 * - dark_sight: depth-based radius crush, processed inside renderLevel
 *   before the hand depth clear, the only spot where the world depth
 *   buffer is still bound as an aux asset source.
 */
public final class PerceptionEffects {

    private static final float GLARE_BLOOM_INTENSITY = 0.2f;
    private static final float DARK_SIGHT_RADIUS_BLOCKS = 8.0f;
    private static final float NEAR_PLANE = 0.05f;

    private static final LazyChain SCOTOPIC = new LazyChain(
            ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "shaders/post/scotopic.json"));
    private static final LazyChain GLARE = new LazyChain(
            ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "shaders/post/glare.json"));
    private static final LazyChain DARK_SIGHT = new LazyChain(
            ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "shaders/post/dark_sight.json"));

    private static int lastWidth = -1;
    private static int lastHeight = -1;
    private static long lastFrameNanos;
    private static NativeImage measurePixel;

    private PerceptionEffects() {}

    /**
     * Runs at the vanilla post-effect site (after doEntityOutline): advances
     * the adaptation, desaturates, meters the pre-glare frame, then dazzles.
     */
    public static void processFrame(Minecraft minecraft, float tickDelta) {
        long now = System.nanoTime();
        float dtSeconds = lastFrameNanos == 0L
                ? 0.016f
                : Math.min((now - lastFrameNanos) / 1.0e9f, 0.1f);
        lastFrameNanos = now;
        EyeState.frameUpdate(dtSeconds);

        resizeIfNeeded(minecraft);

        PostChain scotopic = SCOTOPIC.get(minecraft);
        if (scotopic != null) {
            setUniform(scotopic, "Scotopic", EyeState.rodEngagement(), 0.0f, 0.0f, 0.0f);
            prepareState();
            scotopic.process(tickDelta);
            // Metering runs on the pre-glare image, so the veil cannot feed itself
            readBackLuminance(scotopic);
        }

        float glareStops = EyeState.glareStops();
        float retinalGain = EyeState.retinalGainStops();
        if (glareStops > 0.01f || retinalGain > 0.01f) {
            PostChain glare = GLARE.get(minecraft);
            if (glare != null) {
                float timeSeconds = (System.currentTimeMillis() % 100_000L) / 1000.0f;
                setUniform(glare, "Glare", glareStops, GLARE_BLOOM_INTENSITY, timeSeconds, retinalGain);
                prepareState();
                glare.process(tickDelta);
            }
        }
    }

    /**
     * Runs inside renderLevel, right after the level render and before the
     * depth clear that precedes the hand: the last point where the main
     * depth buffer still holds the world.
     */
    public static void processDarkSight(Minecraft minecraft, float tickDelta) {
        float floor = EyeState.darkSightFloor();
        if (floor <= 0.0015f) {
            return;
        }
        resizeIfNeeded(minecraft);
        PostChain darkSight = DARK_SIGHT.get(minecraft);
        if (darkSight == null) {
            return;
        }
        float far = minecraft.options.getEffectiveRenderDistance() * 16 * 4.0f;
        setUniform(darkSight, "DarkSight", DARK_SIGHT_RADIUS_BLOCKS, floor, NEAR_PLANE, far);
        prepareState();
        darkSight.process(tickDelta);
        // Restore what the hand rendering expects: main bound, depth test on
        RenderSystem.enableDepthTest();
        minecraft.getMainRenderTarget().bindWrite(true);
    }

    /** Same state resets vanilla applies before processing its postEffect. */
    private static void prepareState() {
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.resetTextureMatrix();
    }

    private static void resizeIfNeeded(Minecraft minecraft) {
        RenderTarget main = minecraft.getMainRenderTarget();
        if (main.width == lastWidth && main.height == lastHeight) {
            return;
        }
        lastWidth = main.width;
        lastHeight = main.height;
        SCOTOPIC.resize(main.width, main.height);
        GLARE.resize(main.width, main.height);
        DARK_SIGHT.resize(main.width, main.height);
    }

    /** Old PostChain uniforms are stateful effect uniforms: set on every pass carrying the name. */
    private static void setUniform(PostChain chain, String name, float x, float y, float z, float w) {
        for (PostPass pass : ((PostChainAccessor) chain).moreDarkness_getPasses()) {
            pass.getEffect().safeGetUniform(name).set(x, y, z, w);
        }
    }

    /**
     * Reads the 1x1 metering target back: the average luminance of what is
     * actually on screen, which is what the eye adapts to. A 1x1
     * glGetTexImage right after the chain ran is cheap enough per frame.
     */
    private static void readBackLuminance(PostChain chain) {
        RenderTarget measure = chain.getTempTarget("measure");
        if (measure == null) {
            return;
        }
        if (measurePixel == null) {
            measurePixel = new NativeImage(1, 1, false);
        }
        RenderSystem.bindTexture(measure.getColorTextureId());
        measurePixel.downloadTexture(0, false);
        int pixel = measurePixel.getPixelRGBA(0, 0);
        float r = (pixel & 0xFF) / 255.0f;
        float g = (pixel >> 8 & 0xFF) / 255.0f;
        float b = (pixel >> 16 & 0xFF) / 255.0f;
        EyeState.setScreenLuminance((r + g + b) / 3.0f);
    }

    /** Lazily created chain; a failed load logs once and stays off. */
    private static final class LazyChain {

        private final ResourceLocation location;
        private PostChain chain;
        private boolean broken;

        LazyChain(ResourceLocation location) {
            this.location = location;
        }

        PostChain get(Minecraft minecraft) {
            if (broken) {
                return null;
            }
            if (chain == null) {
                try {
                    chain = new PostChain(
                            minecraft.getTextureManager(),
                            minecraft.getResourceManager(),
                            minecraft.getMainRenderTarget(),
                            location);
                    chain.resize(minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight());
                } catch (IOException | JsonSyntaxException e) {
                    Constants.LOG.error("Failed to load post chain {}", location, e);
                    broken = true;
                    return null;
                }
            }
            return chain;
        }

        void resize(int width, int height) {
            if (chain != null) {
                chain.resize(width, height);
            }
        }
    }
}
