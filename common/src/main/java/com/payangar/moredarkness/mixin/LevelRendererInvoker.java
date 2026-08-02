package com.payangar.moredarkness.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LevelRenderer.class)
public interface LevelRendererInvoker {

    @Invoker("addLateDebugPass")
    void moreDarkness_addLateDebugPass(FrameGraphBuilder frame, CameraRenderState camera, GpuBufferSlice fog, Matrix4fc modelViewMatrix);
}
