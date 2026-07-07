package com.example.playerdropchance;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads and holds the mod's configuration from
 * {@code config/playerdropchance.json}. If the file is missing, a default
 * one (5% drop chance) is written out. If present, its value is used
 * instead, and any out-of-range value is clamped to [0, 1].
 */
public final class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("playerdropchance.json");

    /** Chance, from 0.0 to 1.0, that any given item stack drops on death. */
    public double dropChance = 0.05;

    private static volatile ModConfig instance = new ModConfig();

    private ModConfig() {
    }

    public static ModConfig get() {
        return instance;
    }

    public static synchronized void load() {
        try {
            if (Files.notExists(CONFIG_PATH)) {
                instance = new ModConfig();
                save();
                PlayerDropChance.LOGGER.info("[PlayerDropChance] No config found, created default at {}", CONFIG_PATH);
                return;
            }

            try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
                ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
                if (loaded == null) {
                    loaded = new ModConfig();
                }
                if (loaded.dropChance < 0.0 || loaded.dropChance > 1.0) {
                    PlayerDropChance.LOGGER.warn(
                            "[PlayerDropChance] Configured dropChance {} is outside [0, 1]; clamping.",
                            loaded.dropChance);
                    loaded.dropChance = Math.max(0.0, Math.min(1.0, loaded.dropChance));
                }
                instance = loaded;
                PlayerDropChance.LOGGER.info("[PlayerDropChance] Loaded config: dropChance={}", instance.dropChance);
            }
        } catch (IOException e) {
            PlayerDropChance.LOGGER.error(
                    "[PlayerDropChance] Failed to load {}, falling back to default 5% chance.", CONFIG_PATH, e);
            instance = new ModConfig();
        }
    }

    public static synchronized void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(instance, writer);
            }
        } catch (IOException e) {
            PlayerDropChance.LOGGER.error("[PlayerDropChance] Failed to save {}", CONFIG_PATH, e);
        }
    }
}
