package dev.proo.precorder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Stores user preferences in config/precorder.json.
 * These settings are UI preferences only; they do not enable video capture.
 */
public final class PrecorderConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance()
            .getConfigDir().resolve("precorder.json");

    public int resolution = 1080;
    public int fps = 60;
    public int bitrateMbps = 12;
    public boolean showHud = true;

    public static PrecorderConfig load() {
        try {
            if (Files.exists(FILE)) {
                PrecorderConfig loaded = GSON.fromJson(Files.readString(FILE), PrecorderConfig.class);
                if (loaded != null) {
                    loaded.sanitize();
                    return loaded;
                }
            }
        } catch (Exception ignored) {
            // Fall back to defaults if the config is missing or invalid.
        }
        return new PrecorderConfig();
    }

    public void save() {
        sanitize();
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(this));
        } catch (IOException exception) {
            throw new RuntimeException("Could not save Precorder settings", exception);
        }
    }

    private void sanitize() {
        if (resolution != 400 && resolution != 700 && resolution != 1080) resolution = 1080;
        if (fps != 30 && fps != 60 && fps != 120) fps = 60;
        if (bitrateMbps < 2 || bitrateMbps > 50) bitrateMbps = 12;
    }
}
