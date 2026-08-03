package com.payangar.moredarkness.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.payangar.moredarkness.config.MoreDarknessConfig;
import com.payangar.moredarkness.darkness.DynamicUniforms;
import com.payangar.moredarkness.darkness.EyeState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The dark sight radius needs per-pixel distance, so the pass runs inside
 * LevelRenderer's frame graph while the main depth buffer still holds the
 * level. 26.2 removed the late debug pass the 26.1 version hooked, so the
 * chain is appended right before the always-on-top pass instead (same
 * slot: after the transparency composite, before the debug overlays).
 */
@Mixin(LevelRenderer.class)
public class MixinLevelRendererDarkSight {

    @Unique
    private static final Identifier MORE_DARKNESS_DARK_SIGHT = Identifier.fromNamespaceAndPath("more_darkness", "dark_sight");

    @Unique
    private static final float DARK_SIGHT_RADIUS_BLOCKS = 8.0f;

    @Unique
    private static final float NEAR_PLANE = 0.05f;

    @Shadow
    @Final
    private GameRenderer gameRenderer;

    @Shadow
    @Final
    private ShaderManager shaderManager;

    @Shadow
    @Final
    private LevelTargetBundle targets;

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;addAlwaysOnTopPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V"
            )
    )
    private void moreDarkness_beforeAlwaysOnTopPass(
            GraphicsResourceAllocator resourceAllocator, DeltaTracker deltaTracker, boolean renderOutline,
            CameraRenderState cameraState, Matrix4fc modelViewMatrix, GpuBufferSlice terrainFog,
            Vector4f fogColor, boolean shouldRenderSky, CallbackInfo ci,
            @Local FrameGraphBuilder frame) {
        // The far veil follows the darkness of the scene, not the adaptation:
        // it must already be closed when entering a cave unadapted.
        MoreDarknessConfig config = MoreDarknessConfig.getInstance();
        float crush = EyeState.darkSightCrushFloor();
        if (!config.enableMod || !config.eyeAdaptation || crush <= 0.0015f) {
            return;
        }
        PostChain chain = this.shaderManager.getPostChain(MORE_DARKNESS_DARK_SIGHT, LevelTargetBundle.MAIN_TARGETS);
        if (chain == null) {
            return;
        }
        // depthFar is the exact far plane of the level projection on 26.2
        DynamicUniforms.update(chain, "DarkSightConfig", DARK_SIGHT_RADIUS_BLOCKS, crush, NEAR_PLANE, cameraState.depthFar);
        RenderTarget main = this.gameRenderer.mainRenderTarget();
        chain.addToFrame(frame, main.width, main.height, this.targets);
    }
}
