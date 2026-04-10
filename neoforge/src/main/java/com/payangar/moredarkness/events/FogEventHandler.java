package com.payangar.moredarkness.events;

import com.payangar.moredarkness.fog.DepthFogCalculator;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

public class FogEventHandler {

    @SubscribeEvent
    public void onRenderFog(ViewportEvent.RenderFog event) {
        Minecraft client = Minecraft.getInstance();
        float endMultiplier = DepthFogCalculator.getFogEndMultiplier(client);
        float startMultiplier = DepthFogCalculator.getFogStartMultiplier(client);

        if (endMultiplier < 1.0f || startMultiplier < 1.0f) {
            event.setFarPlaneDistance(event.getFarPlaneDistance() * endMultiplier);
            event.setNearPlaneDistance(event.getNearPlaneDistance() * startMultiplier);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onFogColor(ViewportEvent.ComputeFogColor event) {
        Minecraft client = Minecraft.getInstance();
        float colorFactor = DepthFogCalculator.getFogColorFactor(client);

        if (colorFactor < 1.0f) {
            event.setRed(event.getRed() * colorFactor);
            event.setGreen(event.getGreen() * colorFactor);
            event.setBlue(event.getBlue() * colorFactor);
        }
    }
}
