package com.payangar.moredarkness;

import com.payangar.moredarkness.config.MoreDarknessConfig;
import com.payangar.moredarkness.events.FogEventHandler;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = Constants.MOD_ID, dist = Dist.CLIENT)
public class NeoForgeMod {

    public NeoForgeMod(IEventBus modEventBus, ModContainer modContainer) {
        Constants.LOG.info("Initializing {} on NeoForge", Constants.MOD_NAME);
        MoreDarknessConfig.load();

        NeoForge.EVENT_BUS.register(new FogEventHandler());

        modContainer.registerExtensionPoint(
                IConfigScreenFactory.class,
                (container, parent) -> ConfigScreenBuilder.create(parent)
        );
    }
}
