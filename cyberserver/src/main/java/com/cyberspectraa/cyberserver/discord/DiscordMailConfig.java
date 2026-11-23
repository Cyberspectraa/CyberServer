package com.cyberspectraa.cyberserver.discord;

import com.cyberspectraa.cyberserver.CyberServer;
import net.minecraftforge.fml.loading.FMLPaths;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Properties;

/**
 * Server-owned configuration, separate from Minecraft world save data and
 * never embedded in a JAR. Environment variables can override file values.
 *
 * A dedicated CyberServer directory makes this discoverable in hosting
 * panels. Settings from the older flat file are migrated without overwriting
 * any settings a server owner has already configured.
 */
public record DiscordMailConfig(String token, String guildId, String applicationId, Path spoolDir) {
    private static final String FOLDER = "cyberserver";
    private static final String FILENAME = "discord-mail.properties";
    private static final String LEGACY_FILENAME = "cyberserver-discord-mail.properties";

    public static Path configPath() {
        return FMLPaths.CONFIGDIR.get().resolve(FOLDER).resolve(FILENAME).toAbsolutePath();
    }

    public static Path legacyConfigPath() {
        return FMLPaths.CONFIGDIR.get().resolve(LEGACY_FILENAME).toAbsolutePath();
    }

    /**
     * Create the config when the mod loads, even if the bot is disabled or
     * CyberNpc is absent. Can also be called from the in-game setup command.
     * Returns false for a real filesystem problem, so /setup never claims
     * success when the file cannot be written.
     */
    public static boolean ensureExists() {
        Path file = configPath();
        try {
            Files.createDirectories(file.getParent());
            if (Files.isRegularFile(file)) return true;
            if (Files.exists(file)) {
                CyberServer.LOGGER.error(
                        "Discord configuration path exists but is not a file: {}", file);
                return false;
            }

            Path legacy = legacyConfigPath();
            if (Files.isRegularFile(legacy)) {
                // A non-overwriting MOVE preserves real tokens/IDs and avoids
                // leaving a second plaintext token file behind. Prefer atomic
                // move if supported; the paths live under the same config dir.
                try {
                    Files.move(legacy, file, StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                    Files.move(legacy, file);
                } catch (FileAlreadyExistsException ex) {
                    // Another setup pass created the new file first.
                }
                if (Files.isRegularFile(file)) {
                    CyberServer.LOGGER.info(
                            "Migrated the existing Discord config into {}", file);
                    return true;
                }
            }

            Properties defaults = new Properties();
            defaults.setProperty("enabled", "false");
            defaults.setProperty("bot_token", "");
            defaults.setProperty("guild_id", "");
            defaults.setProperty("application_id", "");
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                defaults.store(writer, "CyberServer Discord Post Office - private server settings");
            } catch (FileAlreadyExistsException ignored) {
                // Another startup or /setup event created the same file.
            }

            CyberServer.LOGGER.info("Discord Post Office config is available at {}", file);
            return Files.isRegularFile(file);
        } catch (IOException ex) {
            CyberServer.LOGGER.error(
                    "Could not create Discord Post Office config at {}: {}", file, ex.getMessage());
            return false;
        }
    }

    /**
     * Safe, token-free diagnostic for OPs. Does NOT log or send token values,
     * recipient details or any stored private mail.
     */
    public static String setupSummary() {
        if (!ensureExists()) {
            return "Discord configuration could not be created. Check the server log and "
                    + "filesystem permissions: " + configPath();
        }

        Properties settings = readProperties();
        if (settings == null) {
            return "Discord config exists but could not be read: " + configPath();
        }

        boolean enabled = Boolean.parseBoolean(
                setting("CYBERSERVER_DISCORD_ENABLED", settings, "enabled"));
        boolean token = !setting("CYBERSERVER_DISCORD_TOKEN", settings, "bot_token").isBlank();
        boolean guild = setting("CYBERSERVER_DISCORD_GUILD_ID", settings, "guild_id")
                .matches("[0-9]{15,22}");
        boolean app = setting("CYBERSERVER_DISCORD_APPLICATION_ID",
                settings, "application_id").matches("[0-9]{15,22}");

        return "Discord config: " + configPath() + " | enabled=" + enabled
                + " | token=" + (token ? "set" : "missing")
                + " | guild ID=" + (guild ? "set" : "missing/invalid")
                + " | application ID=" + (app ? "set" : "missing/invalid")
                + ". Restart the dedicated server after changing settings.";
    }

    @Nullable
    private static Properties readProperties() {
        Path file = configPath();
        Properties settings = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            settings.load(reader);
            return settings;
        } catch (IOException | IllegalArgumentException ex) {
            CyberServer.LOGGER.error(
                    "Could not read Discord Post Office config at {}: {}", file, ex.getMessage());
            return null;
        }
    }

    @Nullable
    static DiscordMailConfig load() {
        if (!ensureExists()) return null;
        Properties settings = readProperties();
        if (settings == null) return null;

        if (!Boolean.parseBoolean(setting("CYBERSERVER_DISCORD_ENABLED", settings, "enabled"))) {
            CyberServer.LOGGER.info(
                    "Discord Post Office is disabled; edit {} to enable it", configPath());
            return null;
        }

        String token = setting("CYBERSERVER_DISCORD_TOKEN", settings, "bot_token");
        String guild = setting("CYBERSERVER_DISCORD_GUILD_ID", settings, "guild_id");
        String application = setting("CYBERSERVER_DISCORD_APPLICATION_ID",
                settings, "application_id");

        if (token.isBlank() || !guild.matches("[0-9]{15,22}")
                || !application.matches("[0-9]{15,22}")) {
            CyberServer.LOGGER.warn(
                    "Discord Post Office is enabled but bot token/guild ID/application ID "
                            + "are missing or invalid; edit {}", configPath());
            return null;
        }

        // Keep the existing physical mail queue location unchanged. CyberNpc
        // reads the identical directory; changing this would strand letters.
        return new DiscordMailConfig(token, guild, application,
                FMLPaths.CONFIGDIR.get().resolve("cybernpc-discord-mail"));
    }

    private static String setting(String env, Properties settings, String property) {
        String value = System.getenv(env);
        if (value == null || value.isBlank()) value = settings.getProperty(property, "");
        return value.trim();
    }
}
