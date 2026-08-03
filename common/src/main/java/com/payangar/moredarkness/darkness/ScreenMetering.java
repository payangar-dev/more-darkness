package com.payangar.moredarkness.darkness;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.payangar.moredarkness.mixin.PostChainAccessor;
import java.nio.ByteBuffer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;

/**
 * Reads back the 1x1 persistent target of the metering post chain: the
 * average luminance of what is actually on screen, which is what the eye
 * adapts to. The copy is asynchronous (same pattern as vanilla Screenshot),
 * so the value lands one or two frames later; the adaptation time constants
 * absorb that latency. At most one copy is in flight at a time.
 */
public final class ScreenMetering {

    private static final Identifier MEASURE_TARGET = Identifier.withDefaultNamespace("measure");

    private static GpuBuffer readbackBuffer;
    private static boolean inFlight;

    private ScreenMetering() {}

    public static void issueReadback(PostChain meteringChain) {
        if (inFlight) {
            return;
        }
        RenderTarget target = ((PostChainAccessor) meteringChain).moreDarkness_getPersistentTargets().get(MEASURE_TARGET);
        if (target == null) {
            return;
        }
        GpuTexture texture = target.getColorTexture();
        if (readbackBuffer == null) {
            readbackBuffer = RenderSystem.getDevice()
                    .createBuffer(() -> "more_darkness metering readback", 9, texture.getFormat().pixelSize());
        }
        inFlight = true;
        RenderSystem.getDevice().createCommandEncoder().copyTextureToBuffer(texture, readbackBuffer, 0L, () -> {
            try (GpuBuffer.MappedView view = RenderSystem.getDevice().createCommandEncoder().mapBuffer(readbackBuffer, true, false)) {
                ByteBuffer data = view.data();
                float r = (data.get(0) & 0xFF) / 255.0f;
                float g = (data.get(1) & 0xFF) / 255.0f;
                float b = (data.get(2) & 0xFF) / 255.0f;
                EyeState.setScreenLuminance((r + g + b) / 3.0f);
            } finally {
                inFlight = false;
            }
        }, 0);
    }
}
