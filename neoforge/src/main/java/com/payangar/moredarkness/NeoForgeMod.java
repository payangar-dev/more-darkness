package com.payangar.moredarkness;

import com.payangar.moredarkness.config.MoreDarknessConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = Constants.MOD_ID, dist = Dist.CLIENT)
public class NeoForgeMod {

    public NeoForgeMod(IEventBus modEventBus, ModContainer modContainer) {
        Constants.LOG.info("Initializing {} on NeoForge", Constants.MOD_NAME);
        MoreDarknessConfig.load();

        modContainer.registerExtensionPoint(
                IConfigScreenFactory.class,
                (container, parent) -> ConfigScreenBuilder.create(parent)
        );
    }
}
