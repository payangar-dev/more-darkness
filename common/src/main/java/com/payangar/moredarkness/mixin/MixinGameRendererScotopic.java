package com.payangar.moredarkness.mixin;

import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.payangar.moredarkness.config.MoreDarknessConfig;
import com.payangar.moredarkness.darkness.DynamicUniforms;
import com.payangar.moredarkness.darkness.EyeState;
import com.payangar.moredarkness.darkness.ScreenMetering;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * FIXME: fake - perception spike steps 2 and 3.
 * Runs the perception post chains right where vanilla runs its spectator
 * post effects (after doEntityOutline), on the same main-target-only bundle.
 * The main depth buffer is already cleared at this point, which is fine:
 * both passes only read color. Scotopic runs first so the glare veil itself
 * is not desaturated.
 */
@Mixin(GameRenderer.class)
public class MixinGameRendererScotopic {

    @Unique
    private static final Identifier MORE_DARKNESS_SCOTOPIC = Identifier.fromNamespaceAndPath("more_darkness", "scotopic");

    @Unique
    private static final Identifier MORE_DARKNESS_GLARE = Identifier.fromNamespaceAndPath("more_darkness", "glare");

    @Unique
    private static final Identifier MORE_DARKNESS_METERING = Identifier.fromNamespaceAndPath("more_darkness", "metering");

    @Unique
    private static final float GLARE_BLOOM_INTENSITY = 0.2f;

    @Unique
    private static long moreDarkness_lastFrameNanos;

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private CrossFrameResourcePool resourcePool;

    @Inject(
            method = "render(Lnet/minecraft/client/DeltaTracker;Z)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;doEntityOutline()V",
                    shift = At.Shift.AFTER
            )
    )
    private void moreDarkness_scotopicPass(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        if (!MoreDarknessConfig.getInstance().enableMod || this.minecraft.level == null) {
            return;
        }

        long now = System.nanoTime();
        float dtSeconds = moreDarkness_lastFrameNanos == 0L
                ? 0.016f
                : Math.min((now - moreDarkness_lastFrameNanos) / 1.0e9f, 0.1f);
        moreDarkness_lastFrameNanos = now;
        EyeState.frameUpdate(dtSeconds);

        PostChain chain = this.minecraft.getShaderManager().getPostChain(MORE_DARKNESS_SCOTOPIC, LevelTargetBundle.MAIN_TARGETS);
        if (chain != null) {
            DynamicUniforms.update(chain, "ScotopicConfig", EyeState.rodEngagement(), 0.0f, 0.0f, 0.0f);
            chain.process(this.minecraft.getMainRenderTarget(), this.resourcePool);
        }

        // Metering runs on the pre-glare image, so the veil cannot feed itself
        PostChain metering = this.minecraft.getShaderManager().getPostChain(MORE_DARKNESS_METERING, LevelTargetBundle.MAIN_TARGETS);
        if (metering != null) {
            metering.process(this.minecraft.getMainRenderTarget(), this.resourcePool);
            ScreenMetering.issueReadback(metering);
        }

        float glareStops = EyeState.glareStops();
        float retinalGain = EyeState.retinalGainStops();
        if (glareStops > 0.01f || retinalGain > 0.01f) {
            PostChain glare = this.minecraft.getShaderManager().getPostChain(MORE_DARKNESS_GLARE, LevelTargetBundle.MAIN_TARGETS);
            if (glare != null) {
                float timeSeconds = (System.currentTimeMillis() % 100_000L) / 1000.0f;
                DynamicUniforms.update(glare, "GlareConfig", glareStops, GLARE_BLOOM_INTENSITY, timeSeconds, retinalGain);
                glare.process(this.minecraft.getMainRenderTarget(), this.resourcePool);
            }
        }
    }
}
