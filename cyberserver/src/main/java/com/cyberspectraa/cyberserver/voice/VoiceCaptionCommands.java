package com.cyberspectraa.cyberserver.voice;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class VoiceCaptionCommands {
    @SubscribeEvent
    public void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("voicecaptions")
                        .requires(source -> source.getEntity() instanceof ServerPlayer)
                        .executes(ctx -> status(ctx.getSource()))
                        .then(Commands.literal("status")
                                .executes(ctx -> status(ctx.getSource())))
                        .then(Commands.literal("on")
                                .executes(ctx -> set(ctx.getSource(), true)))
                        .then(Commands.literal("off")
                                .executes(ctx -> set(ctx.getSource(), false)))
                        .then(Commands.literal("pacing")
                                .executes(ctx -> showPacing(ctx.getSource()))
                                .then(Commands.literal("fast")
                                        .executes(ctx -> setPacing(ctx.getSource(),
                                                VoiceCaptionConsent.Pacing.FAST)))
                                .then(Commands.literal("normal")
                                        .executes(ctx -> setPacing(ctx.getSource(),
                                                VoiceCaptionConsent.Pacing.NORMAL)))
                                .then(Commands.literal("relaxed")
                                        .executes(ctx -> setPacing(ctx.getSource(),
                                                VoiceCaptionConsent.Pacing.RELAXED))))
                        .then(Commands.literal("debug")
                                .executes(ctx -> debug(ctx.getSource())))
                        .then(Commands.literal("test")
                                .executes(ctx -> test(ctx.getSource())))
        );
    }

    @SubscribeEvent
    public void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END
                && ServerLifecycleHooks.getCurrentServer() != null
                && ServerLifecycleHooks.getCurrentServer().getTickCount() % 10 == 0) {
            VoiceCaptionEngine.flushIdle();
        }
    }

    private int test(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        try {
            // Test only the CyberServer -> CyberNpc network/bubble path.
            // No microphone capture, AI model or private voice routing needed.
            Class<?> bridge = Class.forName(
                    "com.cyberspectraa.cybernpc.voice.CyberNpcVoiceBubbleApi");
            bridge.getMethod("broadcast", ServerPlayer.class, String.class,
                    boolean.class, double.class)
                    .invoke(null, player, "Voice captions bubble test!", true, 32.0D);
            source.sendSuccess(() -> Component.literal(
                    "Sent test bubble above your player. Use third-person view to check."), false);
            return 1;
        } catch (ReflectiveOperationException | LinkageError error) {
            source.sendFailure(Component.literal(
                    "CyberNpc voice bubble bridge unavailable: " + error.getClass().getSimpleName()));
            return 0;
        }
    }

    private int debug(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        source.sendSuccess(() -> Component.literal(
                "Captions consent=" + VoiceCaptionConsent.isEnabled(player.getUUID())
                + ", server enabled=" + VoiceCaptionConfig.ENABLED.get()
                + ", pacing=" + VoiceCaptionConsent.getPacing(player.getUUID()).name()), false);
        if (!net.minecraftforge.fml.ModList.get().isLoaded("voicechat")) {
            source.sendFailure(Component.literal("Simple Voice Chat is not installed!"));
            return 0;
        }
        try {
            source.sendSuccess(() -> Component.literal(
                    "Voice: " + CyberVoiceChatPlugin.diagnostics(player)), false);
            source.sendSuccess(() -> Component.literal(
                    "Vosk: " + VoiceCaptionEngine.diagnostics()), false);
        } catch (LinkageError error) {
            source.sendFailure(Component.literal("Voice Chat API unavailable: "
                    + error.getClass().getSimpleName()));
            return 0;
        }
        return 1;
    }

    private int showPacing(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        VoiceCaptionConsent.Pacing choice =
                VoiceCaptionConsent.getPacing(player.getUUID());
        source.sendSuccess(() -> Component.literal(
                "Voice caption pacing: "
                + choice.name().toLowerCase(java.util.Locale.ROOT)
                + " (" + choice.silenceMillis() + "ms). "
                + "Use /voicecaptions pacing fast, normal or relaxed."), false);
        return 1;
    }

    private int setPacing(CommandSourceStack source, VoiceCaptionConsent.Pacing pacing) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        VoiceCaptionConsent.setPacing(player, pacing);
        return 1;
    }

    private int status(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        source.sendSuccess(() -> Component.literal("Voice captions are "
                + (VoiceCaptionConsent.isEnabled(player.getUUID()) ? "ON" : "OFF")
                + ". Use /voicecaptions on or /voicecaptions off."), false);
        return 1;
    }

    private int set(CommandSourceStack source, boolean enabled) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        VoiceCaptionConsent.toggle(player, enabled);
        return 1;
    }
}
