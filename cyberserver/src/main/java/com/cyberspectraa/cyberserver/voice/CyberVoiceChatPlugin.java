package com.cyberspectraa.cyberserver.voice;

import com.cyberspectraa.cyberserver.CyberServer;
import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent;
import de.maxhenkel.voicechat.api.VoicechatConnection;

/**
 * Voice Chat owns audio transport. Only normal proximity microphone packets
 * from explicitly opted-in speakers enter the bounded recognition pipeline.
 * Group-chat audio is NEVER transcribed or routed to proximity bubbles.
 */
@ForgeVoicechatPlugin
public final class CyberVoiceChatPlugin implements VoicechatPlugin {

    private static volatile CyberVoiceChatPlugin instance;
    private static final java.util.concurrent.atomic.AtomicLong allPackets =
            new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong acceptedPackets =
            new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.ConcurrentMap<java.util.UUID, Long> lastPlayerPacket =
            new java.util.concurrent.ConcurrentHashMap<>();
    private volatile VoicechatServerApi serverApi;
    private volatile boolean eventsRegistered;
    private volatile boolean serverStarted;

    public static String diagnostics(net.minecraft.server.level.ServerPlayer player) {
        CyberVoiceChatPlugin plugin = instance;
        if (plugin == null) return "voice plugin not initialised";
        if (!plugin.eventsRegistered) return "voice plugin has not registered events";
        VoicechatServerApi api = plugin.serverApi;
        if (!plugin.serverStarted || api == null) return "Simple Voice Chat server not started";
        String link;
        try {
            VoicechatConnection c = api.getConnectionOf(player.getUUID());
            if (c == null) link = "missing";
            else if (!c.isConnected()) link = "not connected";
            else if (c.isInGroup()) link = "in a voice group (captions disabled)";
            else link = "connected (proximity)";
        } catch (Exception e) {
            link = "connection lookup failed";
        }
        Long last = lastPlayerPacket.get(player.getUUID());
        String seen = last == null ? "none"
                : java.util.concurrent.TimeUnit.NANOSECONDS.toSeconds(
                        Math.max(0L, System.nanoTime() - last)) + "s ago";
        return "plugin=ready, voice=" + link
                + ", mic packets=" + allPackets.get()
                + ", eligible=" + acceptedPackets.get()
                + ", last player mic=" + seen;
    }

    @Override
    public String getPluginId() {
        return "cyberserver_voice_captions";
    }

    @Override
    public void initialize(VoicechatApi api) {
        instance = this;
        CyberServer.LOGGER.info("CyberServer voice-captions integration detected Simple Voice Chat");
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        eventsRegistered = true;
        registration.registerEvent(VoicechatServerStartedEvent.class, event -> {
            serverApi = event.getVoicechat();
            serverStarted = true;
            VoiceCaptionEngine.start(serverApi);
        });
        registration.registerEvent(VoicechatServerStoppedEvent.class, event -> {
            serverApi = null;
            serverStarted = false;
            VoiceCaptionEngine.stop();
            lastPlayerPacket.clear();
        });
        registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophone);
    }

    private void onMicrophone(MicrophonePacketEvent event) {
        allPackets.incrementAndGet();
        VoicechatConnection incoming = event.getSenderConnection();
        if (incoming != null) {
            lastPlayerPacket.put(incoming.getPlayer().getUuid(), System.nanoTime());
        }
        VoicechatServerApi api = serverApi;
        if (api == null || !VoiceCaptionConfig.ENABLED.get()) return;
        VoicechatConnection connection = event.getSenderConnection();
        if (connection == null || !connection.isConnected() || connection.isInGroup()) return;
        java.util.UUID uuid = connection.getPlayer().getUuid();
        if (!VoiceCaptionConsent.isEnabled(uuid)) return;
        byte[] opus = event.getPacket().getOpusEncodedData();
        if (opus == null || opus.length == 0 || opus.length > 4096) return;
        // Copy before Voice Chat's packet buffer is reused/modified.
        acceptedPackets.incrementAndGet();
        VoiceCaptionEngine.submit(uuid, opus.clone(), event.getPacket().isWhispering());
    }
}
