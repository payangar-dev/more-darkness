package com.payangar.moredarkness.darkness;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.payangar.moredarkness.mixin.PostChainAccessor;
import com.payangar.moredarkness.mixin.PostPassAccessor;
import java.util.Map;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import org.lwjgl.system.MemoryStack;

/**
 * PostPass bakes its JSON uniforms into immutable GPU buffers at chain
 * compile time, so dynamic values need the buffers swapped by hand: before
 * each run of a chain, every pass carrying the named block gets a freshly
 * created buffer holding one vec4. The replaced buffer is closed
 * immediately: since this always runs before the chain is (re)submitted,
 * the outgoing buffer (baked original included) is not referenced by any
 * in-flight draw of the current frame.
 */
public final class DynamicUniforms {

    private DynamicUniforms() {}

    public static void update(PostChain chain, String blockName, float x, float y, float z, float w) {
        for (PostPass pass : ((PostChainAccessor) chain).moreDarkness_getPasses()) {
            Map<String, GpuBuffer> uniforms = ((PostPassAccessor) pass).moreDarkness_getCustomUniforms();
            if (!uniforms.containsKey(blockName)) {
                continue;
            }
            GpuBuffer fresh;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                Std140Builder builder = Std140Builder.onStack(stack, 16);
                builder.putVec4(x, y, z, w);
                fresh = RenderSystem.getDevice().createBuffer(() -> "more_darkness " + blockName, 128, builder.get());
            }
            GpuBuffer previous = uniforms.put(blockName, fresh);
            if (previous != null) {
                previous.close();
            }
        }
    }
}
