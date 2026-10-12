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
