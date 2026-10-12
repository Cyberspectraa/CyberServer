package com.cyberspectraa.cyberserver.voice;

import net.minecraftforge.common.ForgeConfigSpec;

public final class VoiceCaptionConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> WORD_REPLACEMENTS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Offline voice-to-bubble captions, Simple Voice Chat and CyberNpc required.")
                .push("voice_captions");
        ENABLED = b.comment("Master switch. Each player must additionally opt in with /voicecaptions on.")
                .define("enabled", true);
        WORD_REPLACEMENTS = b.comment(
                "Optional literal caption word/phrase corrections: 'heard words=>intended words'.",
                "Example: 'cyber spectra=>CyberSpectra'. Empty by default.",
                "This only corrects an EXACT recognized phrase; it does not train Vosk",
                "or infer what a player meant. Up to 16 entries are applied.")
                .defineList("word_replacements", java.util.List.of(),
                        value -> value instanceof String entry
                                && entry.length() <= 96 && entry.contains("=>"));
        b.pop();
        SPEC = b.build();
    }
    private VoiceCaptionConfig() {}
}
