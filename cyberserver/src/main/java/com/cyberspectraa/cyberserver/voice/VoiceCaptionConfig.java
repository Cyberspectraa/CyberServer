package com.cyberspectraa.cyberserver.voice;

import net.minecraftforge.common.ForgeConfigSpec;

public final class VoiceCaptionConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Offline voice-to-bubble captions, Simple Voice Chat and CyberNpc required.")
                .push("voice_captions");
        ENABLED = b.comment("Master switch. Each player must additionally opt in with /voicecaptions on.")
                .define("enabled", true);
        b.pop();
        SPEC = b.build();
    }
    private VoiceCaptionConfig() {}
}
