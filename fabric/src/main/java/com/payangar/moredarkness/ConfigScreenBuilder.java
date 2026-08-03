package com.payangar.moredarkness;

import com.payangar.moredarkness.config.MoreDarknessConfig;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.FloatSliderControllerBuilder;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ConfigScreenBuilder {

    private ConfigScreenBuilder() {}

    public static Screen create(Screen parent) {
        MoreDarknessConfig defaults = new MoreDarknessConfig();
        MoreDarknessConfig config = MoreDarknessConfig.getInstance();

        return YetAnotherConfigLib.createBuilder()
                .title(Component.translatable("more_darkness.config.title"))
                .save(MoreDarknessConfig::save)
                .category(darknessCategory(defaults, config))
                .category(compatCategory(defaults, config))
                .build()
                .generateScreen(parent);
    }

    private static ConfigCategory darknessCategory(MoreDarknessConfig defaults, MoreDarknessConfig config) {
        return ConfigCategory.createBuilder()
                .name(Component.translatable("more_darkness.config.category.darkness"))
                .option(boolOption("enable_mod", defaults.enableMod, () -> config.enableMod, v -> config.enableMod = v))
                .option(boolOption("dark_overworld", defaults.darkOverworld, () -> config.darkOverworld, v -> config.darkOverworld = v))
                .option(boolOption("dark_nether", defaults.darkNether, () -> config.darkNether, v -> config.darkNether = v))
                .option(boolOption("dark_end", defaults.darkEnd, () -> config.darkEnd, v -> config.darkEnd = v))
                .option(boolOption("moon_phase_effect", defaults.moonPhaseEffect, () -> config.moonPhaseEffect, v -> config.moonPhaseEffect = v))
                .option(floatOption("minimum_moon_brightness", 0.0f, 0.5f, 0.01f, defaults.minimumMoonBrightness, () -> config.minimumMoonBrightness, v -> config.minimumMoonBrightness = v))
                .option(floatOption("cave_darkness", 0.0f, 1.0f, 0.01f, defaults.caveDarkness, () -> config.caveDarkness, v -> config.caveDarkness = v))
                .option(boolOption("eye_adaptation", defaults.eyeAdaptation, () -> config.eyeAdaptation, v -> config.eyeAdaptation = v))
                .option(boolOption("darker_water", defaults.darkerWater, () -> config.darkerWater, v -> config.darkerWater = v))
                .build();
    }

    private static ConfigCategory compatCategory(MoreDarknessConfig defaults, MoreDarknessConfig config) {
        return ConfigCategory.createBuilder()
                .name(Component.translatable("more_darkness.config.category.compatibility"))
                .option(boolOption("disable_with_shaders", defaults.disableWithShaders, () -> config.disableWithShaders, v -> config.disableWithShaders = v))
                .build();
    }

    private static Option<Boolean> boolOption(String key, boolean def, java.util.function.Supplier<Boolean> getter, java.util.function.Consumer<Boolean> setter) {
        return Option.<Boolean>createBuilder()
                .name(Component.translatable("more_darkness.config.option." + key))
                .description(OptionDescription.of(Component.translatable("more_darkness.config.option." + key + ".desc")))
                .binding(def, getter, setter)
                .controller(opt -> BooleanControllerBuilder.create(opt).coloured(true))
                .build();
    }

    private static Option<Float> floatOption(String key, float min, float max, float step, float def, java.util.function.Supplier<Float> getter, java.util.function.Consumer<Float> setter) {
        return Option.<Float>createBuilder()
                .name(Component.translatable("more_darkness.config.option." + key))
                .description(OptionDescription.of(Component.translatable("more_darkness.config.option." + key + ".desc")))
                .binding(def, getter, setter)
                .controller(opt -> FloatSliderControllerBuilder.create(opt).range(min, max).step(step))
                .build();
    }
}
