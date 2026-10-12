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
    private volatile VoicechatServerApi serverApi;

    @Override
    public String getPluginId() {
        return "cyberserver_voice_captions";
    }

    @Override
    public void initialize(VoicechatApi api) {
        CyberServer.LOGGER.info("CyberServer voice-captions integration detected Simple Voice Chat");
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, event -> {
            serverApi = event.getVoicechat();
            VoiceCaptionEngine.start(serverApi);
        });
        registration.registerEvent(VoicechatServerStoppedEvent.class, event -> {
            serverApi = null;
            VoiceCaptionEngine.stop();
        });
        registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophone);
    }

    private void onMicrophone(MicrophonePacketEvent event) {
        VoicechatServerApi api = serverApi;
        if (api == null || !VoiceCaptionConfig.ENABLED.get()) return;
        VoicechatConnection connection = event.getSenderConnection();
        if (connection == null || !connection.isConnected() || connection.isInGroup()) return;
        java.util.UUID uuid = connection.getPlayer().getUuid();
        if (!VoiceCaptionConsent.isEnabled(uuid)) return;
        byte[] opus = event.getPacket().getOpusEncodedData();
        if (opus == null || opus.length == 0 || opus.length > 4096) return;
        // Copy before Voice Chat's packet buffer is reused/modified.
        VoiceCaptionEngine.submit(uuid, opus.clone(), event.getPacket().isWhispering());
    }
}
