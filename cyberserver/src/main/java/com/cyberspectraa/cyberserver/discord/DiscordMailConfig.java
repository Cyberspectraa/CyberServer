package com.cyberspectraa.cyberserver.discord;

import com.cyberspectraa.cyberserver.CyberServer;
import net.minecraftforge.fml.loading.FMLPaths;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Dedicated-server configuration only; never contains credentials in the JAR.
 * Environment variables take priority for hosts that manage secrets externally.
 */
public record DiscordMailConfig(String token, String guildId, String applicationId, Path spoolDir) {
    private static final String FILENAME = "cyberserver-discord-mail.properties";

    /**
     * Create the server-side config as soon as CyberServer itself loads.
     * It must NOT depend on CyberNpc's version, Discord connectivity, world
     * startup, or enabled=true; otherwise owners cannot configure the bot.
     */
    public static void ensureExists() {
        Path configDir = FMLPaths.CONFIGDIR.get();
        Path settingsFile = configDir.resolve(FILENAME);
        try {
            Files.createDirectories(configDir);
            if (!Files.exists(settingsFile)) {
                Properties defaults = new Properties();
                defaults.setProperty("enabled", "false");
                defaults.setProperty("bot_token", "");
                defaults.setProperty("guild_id", "");
                defaults.setProperty("application_id", "");
                try (Writer writer = Files.newBufferedWriter(settingsFile,
                        StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE_NEW)) {
                    defaults.store(writer,
                            "CyberServer Discord post office - server-only. Do not publish this file.");
                } catch (java.nio.file.FileAlreadyExistsException ignored) {
                    // Another server setup pass already created it.
                }
                CyberServer.LOGGER.info(
                        "Discord post office config location: {}", settingsFile.toAbsolutePath());
            }
        } catch (IOException ex) {
            CyberServer.LOGGER.error("Could not create Discord post office config at {}: {}",
                    settingsFile.toAbsolutePath(), ex.getMessage());
        }
    }

    @Nullable
    static DiscordMailConfig load() {
        ensureExists();
        Path configDir = FMLPaths.CONFIGDIR.get();
        Path settingsFile = configDir.resolve(FILENAME);
        Properties settings = new Properties();
        try (Reader reader = Files.newBufferedReader(settingsFile, StandardCharsets.UTF_8)) {
            settings.load(reader);
        } catch (IOException ex) {
            CyberServer.LOGGER.error("Could not read Discord mail configuration at {}: {}",
                    settingsFile.toAbsolutePath(), ex.getMessage());
            return null;
        }

        String enabled = setting("CYBERSERVER_DISCORD_ENABLED", settings, "enabled");
        if (!Boolean.parseBoolean(enabled)) return null;

        String token = setting("CYBERSERVER_DISCORD_TOKEN", settings, "bot_token");
        String guild = setting("CYBERSERVER_DISCORD_GUILD_ID", settings, "guild_id");
        String application = setting("CYBERSERVER_DISCORD_APPLICATION_ID",
                settings, "application_id");
        if (token.isBlank() || !guild.matches("[0-9]{15,22}")
                || !application.matches("[0-9]{15,22}")) {
            CyberServer.LOGGER.warn(
                    "Discord mail is enabled but the bot token, guild ID or application ID is missing/invalid");
            return null;
        }
        // Both mods see the same server config directory. CyberNpc handles
        // actual Drop Box, courier and letterbox delivery from this folder.
        return new DiscordMailConfig(token, guild, application,
                configDir.resolve("cybernpc-discord-mail"));
    }

    private static String setting(String env, Properties settings, String property) {
        String value = System.getenv(env);
        if (value == null || value.isBlank()) value = settings.getProperty(property, "");
        return value.trim();
    }
}
