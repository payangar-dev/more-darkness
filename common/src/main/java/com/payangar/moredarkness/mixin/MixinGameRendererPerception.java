package com.payangar.moredarkness.mixin;

import com.payangar.moredarkness.config.MoreDarknessConfig;
import com.payangar.moredarkness.darkness.PerceptionEffects;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * FIXME: fake - perception spike port, steps 2 and 3.
 * Runs the perception post chains right where vanilla runs its spectator
 * post effects (after doEntityOutline). Scotopic runs first so the glare
 * veil itself is not desaturated. The depth-based dark sight pass cannot
 * live there: on 1.21.1 the depth buffer is cleared before the hand render,
 * so it runs inside renderLevel right after the level render instead.
 */
@Mixin(GameRenderer.class)
public class MixinGameRendererPerception {

    @Shadow
    private Minecraft minecraft;

    @Inject(
            method = "render(Lnet/minecraft/client/DeltaTracker;Z)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;doEntityOutline()V",
                    shift = At.Shift.AFTER
            )
    )
    private void moreDarkness_perceptionPasses(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        if (!MoreDarknessConfig.getInstance().enableMod || this.minecraft.level == null) {
            return;
        }
        PerceptionEffects.processFrame(this.minecraft, deltaTracker.getGameTimeDeltaTicks());
    }

    @Inject(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void moreDarkness_darkSightPass(DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!MoreDarknessConfig.getInstance().enableMod || this.minecraft.level == null) {
            return;
        }
        PerceptionEffects.processDarkSight(this.minecraft, deltaTracker.getGameTimeDeltaTicks());
    }
}
