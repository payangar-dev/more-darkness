package com.payangar.moredarkness.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRenderer.class)
public interface GameRendererInvoker {

    /** Vertical fov in degrees, exactly as fed to the level projection matrix. */
    @Invoker("getFov")
    double moreDarkness_getFov(Camera camera, float partialTick, boolean useFovSetting);
}
