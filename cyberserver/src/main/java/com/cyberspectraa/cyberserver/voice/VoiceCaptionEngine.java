package com.cyberspectraa.cyberserver.voice;

import com.cyberspectraa.cyberserver.CyberServer;
import com.google.gson.JsonParser;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Opt-in, privacy-limited, two-worker offline microphone transcription.
 * No Minecraft world access on recognition threads; bounded queues drop
 * stale frames instead of stalling the network/voice thread.
 */
public final class VoiceCaptionEngine {
    private static final int WORKER_COUNT = 2;
    private static final int MAX_ACTIVE_SPEAKERS = 2;
    private static final int MAX_CAPTION_CHARS = 180;
    private static final long PARTIAL_NANOS = TimeUnit.MILLISECONDS.toNanos(450);
    private static final long END_UTTERANCE_NANOS = TimeUnit.MILLISECONDS.toNanos(700);
    private static final long EXPIRE_SESSION_NANOS = TimeUnit.SECONDS.toNanos(5);

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    private static volatile ThreadPoolExecutor[] workers;
    private static volatile VoicechatServerApi voiceApi;

    public static synchronized void start(VoicechatServerApi api) {
        if (workers != null) stop();
        voiceApi = api;
        ThreadPoolExecutor[] created = new ThreadPoolExecutor[WORKER_COUNT];
        for (int i = 0; i < WORKER_COUNT; i++) {
            final int number = i;
            created[i] = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.SECONDS,
                    new ArrayBlockingQueue<>(180), runnable -> {
                Thread thread = new Thread(runnable, "CyberServer-Vosk-" + number);
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.DiscardPolicy());
        }
        workers = created;
        CyberServer.LOGGER.info("Voice captions enabled (opt-in, {} bounded workers)", WORKER_COUNT);
    }

    public static void submit(UUID playerId, byte[] opusData, boolean whispering) {
        ThreadPoolExecutor[] current = workers;
        if (current == null || !VoiceCaptionConsent.isEnabled(playerId)) return;
        current[index(playerId)].execute(() -> process(playerId, opusData, whispering));
    }

    private static void process(UUID playerId, byte[] opus, boolean whispering) {
        if (!VoiceCaptionConsent.isEnabled(playerId) || !VoiceCaptionConfig.ENABLED.get()) return;
        VoicechatServerApi api = voiceApi;
        if (api == null) return;
        Session session = SESSIONS.get(playerId);
        long now = System.nanoTime();
        if (session == null) {
            if (SESSIONS.size() >= MAX_ACTIVE_SPEAKERS) return;
            Model model = VoiceModelStore.get();
            if (model == null) return;
            try {
                Session created = new Session(api.createDecoder(), new Recognizer(model, 16000F));
                Session existing = SESSIONS.putIfAbsent(playerId, created);
                if (existing == null) session = created;
                else {
                    created.close();
                    session = existing;
                }
            } catch (Throwable t) {
                CyberServer.LOGGER.warn("Unable to start voice recognition session", t);
                return;
            }
        }

        try {
            short[] pcm = session.decoder.decode(opus);
            if (pcm == null || pcm.length < 3) return;
            // Opus PCM is mono 48 kHz; Vosk accepts 16 kHz.
            // Simple bounded 3:1 averaging low-pass decimation.
            short[] downsampled = new short[pcm.length / 3];
            for (int i = 0; i < downsampled.length; i++) {
                int p = i * 3;
                downsampled[i] = (short) ((pcm[p] + pcm[p + 1] + pcm[p + 2]) / 3);
            }
            session.lastPacket = now;
            session.hasAudio = true;
            session.whispering = whispering;
            if (session.recognizer.acceptWaveForm(downsampled, downsampled.length)) {
                String finished = text(session.recognizer.getResult(), "text");
                publish(playerId, finished, true, whispering);
                // Vosk automatically advances its internal utterance boundary.
                session.lastPartial = "";
            } else if (now - session.lastEmit >= PARTIAL_NANOS) {
                String partial = text(session.recognizer.getPartialResult(), "partial");
                if (!partial.isBlank() && !partial.equals(session.lastPartial)) {
                    publish(playerId, partial, false, whispering);
                    session.lastPartial = partial;
                    session.lastEmit = now;
                }
            }
        } catch (Throwable t) {
            CyberServer.LOGGER.warn("Voice recognition frame rejected", t);
            remove(playerId);
        }
    }

    /** Called every 10 Minecraft ticks; work is delegated to the stripes. */
    public static void flushIdle() {
        ThreadPoolExecutor[] current = workers;
        if (current == null) return;
        for (UUID playerId : SESSIONS.keySet()) {
            current[index(playerId)].execute(() -> {
                Session session = SESSIONS.get(playerId);
                if (session == null) return;
                long gap = System.nanoTime() - session.lastPacket;
                if (gap > END_UTTERANCE_NANOS && session.hasAudio) {
                    try {
                        String result = text(session.recognizer.getFinalResult(), "text");
                        if (!result.isBlank()) publish(playerId, result, true, session.whispering);
                        session.recognizer.close();
                        Model model = VoiceModelStore.get();
                        if (model == null) { remove(playerId); return; }
                        session.recognizer = new Recognizer(model, 16000F);
                        session.lastPartial = "";
                        session.hasAudio = false;
                    } catch (Throwable t) {
                        remove(playerId);
                    }
                }
                if (gap > EXPIRE_SESSION_NANOS) remove(playerId);
            });
        }
    }

    private static String text(String json, String property) {
        try {
            String value = JsonParser.parseString(json).getAsJsonObject()
                    .get(property).getAsString().replaceAll("[\\p{Cntrl}]", " ").trim();
            return value.length() > MAX_CAPTION_CHARS
                    ? value.substring(0, MAX_CAPTION_CHARS) : value;
        } catch (Exception ignored) {
            return "";
        }
    }

    private static void publish(UUID playerId, String text, boolean complete,
                                boolean whispering) {
        if (text == null || text.isBlank()) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        VoicechatServerApi api = voiceApi;
        if (server == null || api == null) return;
        server.execute(() -> {
            ServerPlayer speaker = server.getPlayerList().getPlayer(playerId);
            if (speaker == null || !VoiceCaptionConsent.isEnabled(playerId)
                    || !VoiceCaptionConfig.ENABLED.get()) return;
            VoicechatConnection connection = api.getConnectionOf(playerId);
            if (connection == null || connection.isInGroup() || !connection.isConnected()) return;
            try {
                // Optional integration: CyberServer has no binary dependency on
                // CyberNpc, but delegates rendering to its existing player bubble.
                Class<?> bridge = Class.forName(
                        "com.cyberspectraa.cybernpc.voice.CyberNpcVoiceBubbleApi");
                Method broadcast = bridge.getMethod("broadcast",
                        ServerPlayer.class, String.class, boolean.class, double.class);
                double voiceRange = Math.min(32.0D, api.getVoiceChatDistance());
                if (whispering) voiceRange = Math.min(voiceRange, 6.0D);
                broadcast.invoke(null, speaker, text, complete, voiceRange);
            } catch (ClassNotFoundException ignored) {
                // CyberNpc not installed, safely ignore.
            } catch (ReflectiveOperationException error) {
                CyberServer.LOGGER.warn("CyberNpc live caption bridge is unavailable", error);
            }
        });
    }

    private static int index(UUID id) {
        return (id.hashCode() & Integer.MAX_VALUE) % WORKER_COUNT;
    }

    private static void remove(UUID id) {
        Session session = SESSIONS.remove(id);
        if (session != null) session.close();
    }

    public static void clearPlayer(UUID playerId) {
        ThreadPoolExecutor[] current = workers;
        if (current != null) current[index(playerId)].execute(() -> remove(playerId));
    }

    public static synchronized void stop() {
        voiceApi = null;
        ThreadPoolExecutor[] current = workers;
        workers = null;
        if (current == null) return;
        for (ThreadPoolExecutor worker : current) {
            worker.execute(() -> {
                for (UUID id : SESSIONS.keySet()) {
                    if (index(id) == Integer.parseInt(Thread.currentThread()
                            .getName().substring("CyberServer-Vosk-".length()))) remove(id);
                }
            });
            worker.shutdown();
        }
    }

    private static final class Session {
        final OpusDecoder decoder;
        Recognizer recognizer;
        long lastPacket = System.nanoTime();
        long lastEmit;
        String lastPartial = "";
        boolean whispering;
        boolean hasAudio;

        Session(OpusDecoder decoder, Recognizer recognizer) {
            this.decoder = decoder;
            this.recognizer = recognizer;
        }

        void close() {
            try { recognizer.close(); } catch (Throwable ignored) {}
            try { decoder.close(); } catch (Throwable ignored) {}
        }
    }

    private VoiceCaptionEngine() {}
}
