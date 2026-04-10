package com.payangar.moredarkness;

import com.payangar.moredarkness.config.MoreDarknessConfig;
import net.fabricmc.api.ClientModInitializer;

public class FabricMod implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        Constants.LOG.info("Initializing {} on Fabric", Constants.MOD_NAME);
        MoreDarknessConfig.load();
    }
}
