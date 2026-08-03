package com.payangar.moredarkness.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.payangar.moredarkness.config.MoreDarknessConfig;
import com.payangar.moredarkness.darkness.DynamicUniforms;
import com.payangar.moredarkness.darkness.EyeState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The dark sight radius needs per-pixel distance, and the main depth buffer
 * is only alive inside LevelRenderer's frame graph (GameRenderer clears it
 * before its own post-effect site). So the pass is appended to the frame
 * graph right before the late debug pass, via a redirect that then calls
 * the vanilla method untouched.
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
    private Minecraft minecraft;

    @Shadow
    @Final
    private LevelTargetBundle targets;

    @Redirect(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;addLateDebugPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Matrix4fc;)V"
            )
    )
    private void moreDarkness_beforeLateDebugPass(LevelRenderer instance, FrameGraphBuilder frame, CameraRenderState camera, GpuBufferSlice fog, Matrix4fc modelViewMatrix) {
        moreDarkness_addDarkSightPass(frame);
        ((LevelRendererInvoker) instance).moreDarkness_addLateDebugPass(frame, camera, fog, modelViewMatrix);
    }

    @Unique
    private void moreDarkness_addDarkSightPass(FrameGraphBuilder frame) {
        // The far veil follows the darkness of the scene, not the adaptation:
        // it must already be closed when entering a cave unadapted.
        MoreDarknessConfig config = MoreDarknessConfig.getInstance();
        float crush = EyeState.darkSightCrushFloor();
        if (!config.enableMod || !config.eyeAdaptation || crush <= 0.0015f) {
            return;
        }
        PostChain chain = this.minecraft.getShaderManager().getPostChain(MORE_DARKNESS_DARK_SIGHT, LevelTargetBundle.MAIN_TARGETS);
        if (chain == null) {
            return;
        }
        float far = this.minecraft.options.getEffectiveRenderDistance() * 16 * 4.0f;
        DynamicUniforms.update(chain, "DarkSightConfig", DARK_SIGHT_RADIUS_BLOCKS, crush, NEAR_PLANE, far);
        RenderTarget main = this.minecraft.getMainRenderTarget();
        chain.addToFrame(frame, main.width, main.height, this.targets);
    }
}
