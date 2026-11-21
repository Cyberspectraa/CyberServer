package com.cyberspectraa.cyberserver.discord;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/**
 * Discord bot -> CyberNpc handoff; no Minecraft or third-party bot dependencies.
 * Only the authenticated bot writes requests. CyberNpc consumes them on its
 * normal server tick and registers ordinary physical Drop Box mail.
 */
final class DiscordMailSpool {
    private static final int MAX_OUTSTANDING = 128;
    private final Path base;

    DiscordMailSpool(Path base) {
        this.base = base;
    }

    enum QueueResult {
        QUEUED, ALREADY_SUBMITTED, BUSY
    }

    QueueResult submit(UUID requestId, String discordId, String sender,
                       String recipient, String message) throws IOException {
        Path incoming = base.resolve("incoming");
        Path archive = base.resolve("archive");
        Path rejected = base.resolve("rejected");
        Path results = base.resolve("results");
        Files.createDirectories(incoming);
        Files.createDirectories(archive);
        Files.createDirectories(rejected);
        Files.createDirectories(results);

        String filename = requestId + ".json";
        if (Files.exists(incoming.resolve(filename), LinkOption.NOFOLLOW_LINKS)
                || Files.exists(archive.resolve(filename), LinkOption.NOFOLLOW_LINKS)
                || Files.exists(rejected.resolve(filename), LinkOption.NOFOLLOW_LINKS)
                || Files.exists(results.resolve(filename), LinkOption.NOFOLLOW_LINKS)) {
            return QueueResult.ALREADY_SUBMITTED;
        }

        int pending = 0;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(incoming, "*.json")) {
            for (Path ignored : entries) {
                if (++pending >= MAX_OUTSTANDING) return QueueResult.BUSY;
            }
        }

        JsonObject request = new JsonObject();
        request.addProperty("requestId", requestId.toString());
        request.addProperty("discordUserId", discordId);
        request.addProperty("senderName", sender);
        request.addProperty("recipientName", recipient);
        request.addProperty("message", message);

        // Same-directory atomic rename prevents the mod reading half-written
        // JSON. No token/credentials, personal Minecraft data or arbitrary
        // paths are placed in the request.
        Path temporary = incoming.resolve(requestId + "." + UUID.randomUUID() + ".tmp");
        Path destination = incoming.resolve(filename);
        try {
            Files.writeString(temporary, request.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, destination);
            }
            return QueueResult.QUEUED;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
