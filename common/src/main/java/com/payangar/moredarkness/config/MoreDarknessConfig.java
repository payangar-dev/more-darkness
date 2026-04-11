package com.payangar.moredarkness.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.payangar.moredarkness.Constants;
import com.payangar.moredarkness.platform.Services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Plain POJO config, serialized with GSON.
 * No YACL dependency here — screen builders live in loader modules.
 */
public class MoreDarknessConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static MoreDarknessConfig INSTANCE;

    // --- Darkness ---
    public boolean enableMod = true;
    public boolean darkOverworld = true;
    public boolean darkNether = true;
    public boolean darkEnd = true;
    public boolean moonPhaseEffect = true;
    public float minimumMoonBrightness = 0.05f;
    public float caveDarkness = 0.0f;

    // --- Compatibility ---
    public boolean disableWithShaders = true;

    // --- Access ---

    public static MoreDarknessConfig getInstance() {
        if (INSTANCE == null) {
            load();
        }
        return INSTANCE;
    }

    public static void load() {
        Path path = getConfigPath();
        if (Files.exists(path)) {
            try {
                String json = Files.readString(path);
                INSTANCE = GSON.fromJson(json, MoreDarknessConfig.class);
                if (INSTANCE == null) {
                    INSTANCE = new MoreDarknessConfig();
                }
            } catch (Exception e) {
                Constants.LOG.warn("Failed to load config, using defaults", e);
                INSTANCE = new MoreDarknessConfig();
            }
        } else {
            INSTANCE = new MoreDarknessConfig();
            save();
        }
    }

    public static void save() {
        if (INSTANCE == null) {
            INSTANCE = new MoreDarknessConfig();
        }
        Path path = getConfigPath();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(INSTANCE));
        } catch (IOException e) {
            Constants.LOG.warn("Failed to save config", e);
        }
    }

    private static Path getConfigPath() {
        return Services.PLATFORM.getConfigDir().resolve("more_darkness.json");
    }
}
