package com.cyberspectraa.cyberserver.voice;

import com.cyberspectraa.cyberserver.CyberServer;
import net.minecraftforge.fml.loading.FMLPaths;
import org.vosk.Model;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Unpacks the build-bundled Apache-2.0 English model locally ONCE.
 * Never contacts a download server at runtime. Loading native Vosk and
 * unzipping the model happen exclusively on recognition worker threads.
 */
final class VoiceModelStore {
    static final String MODEL_NAME = "vosk-model-small-en-us-0.15";
    private static final String RESOURCE = "/assets/cyberserver/voice/" + MODEL_NAME + ".zip";
    private static final long MAX_EXPANDED_BYTES = 240L * 1024L * 1024L;
    private static volatile Model model;
    private static volatile boolean failed;

    static synchronized Model get() {
        if (failed) return null;
        if (model != null) return model;
        try {
            Path base = FMLPaths.GAMEDIR.get().resolve("config").resolve("cyberserver")
                    .resolve("voice-model");
            Path root = base.resolve(MODEL_NAME);
            Path ready = root.resolve(".cyberserver-ready");
            if (!Files.isRegularFile(ready)) {
                unpack(base);
                Files.createDirectories(root);
                Files.createFile(ready);
            }
            model = new Model(root.toString());
            CyberServer.LOGGER.info("Offline English Vosk recognition model loaded");
            return model;
        } catch (Throwable error) {
            failed = true;
            CyberServer.LOGGER.error("Voice captions disabled: bundled Vosk model/native runtime unavailable", error);
            return null;
        }
    }

    private static void unpack(Path base) throws IOException {
        try (InputStream resource = VoiceModelStore.class.getResourceAsStream(RESOURCE)) {
            if (resource == null) throw new IOException("Missing bundled Vosk model: " + RESOURCE);
            Files.createDirectories(base);
            long bytes = 0;
            int count = 0;
            try (ZipInputStream zip = new ZipInputStream(resource)) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (++count > 3000) throw new IOException("Vosk archive contains too many entries");
                    Path target = base.resolve(entry.getName()).normalize();
                    if (!target.startsWith(base.resolve(MODEL_NAME).normalize())) {
                        throw new IOException("Unsafe model archive path");
                    }
                    if (entry.isDirectory()) {
                        Files.createDirectories(target);
                    } else {
                        Files.createDirectories(target.getParent());
                        // Bounded streaming extraction prevents zip bombs.
                        try (java.io.OutputStream out = Files.newOutputStream(target)) {
                            byte[] buffer = new byte[8192];
                            int n;
                            while ((n = zip.read(buffer)) > 0) {
                                bytes += n;
                                if (bytes > MAX_EXPANDED_BYTES) throw new IOException("Model exceeds extraction budget");
                                out.write(buffer, 0, n);
                            }
                        }
                    }
                    zip.closeEntry();
                }
            }
            if (count < 5) throw new IOException("Model archive is incomplete");
        }
    }

    private VoiceModelStore() {}
}
