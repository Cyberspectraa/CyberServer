package com.cyberspectraa.cyberserver.voice;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Speaker-first consent. Disabled by default for every character. Only a
 * boolean is persisted; microphone audio or recognised words are never saved.
 */
public final class VoiceCaptionConsent {
    private static final String KEY = "CyberServerVoiceCaptionsOptIn";
    private static final Set<UUID> ENABLED = ConcurrentHashMap.newKeySet();

    public static boolean isEnabled(UUID player) {
        return ENABLED.contains(player);
    }

    public static boolean toggle(ServerPlayer player, boolean enabled) {
        CompoundTag persistent = player.getPersistentData()
                .getCompound(Player.PERSISTED_NBT_TAG);
        persistent.putBoolean(KEY, enabled);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persistent);
        if (enabled) ENABLED.add(player.getUUID());
        else {
            ENABLED.remove(player.getUUID());
            VoiceCaptionEngine.clearPlayer(player.getUUID());
        }
        player.sendSystemMessage(Component.literal("Voice captions: "
                + (enabled ? "ON (nearby players may read what you say)"
                        : "OFF (your microphone is no longer transcribed)")));
        return enabled;
    }

    @SubscribeEvent
    public void onJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG)
                    .getBoolean(KEY)) ENABLED.add(player.getUUID());
            else ENABLED.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public void onQuit(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ENABLED.remove(player.getUUID());
            VoiceCaptionEngine.clearPlayer(player.getUUID());
        }
    }

    @SubscribeEvent
    public void onClone(PlayerEvent.Clone event) {
        // Forge persistent NBT is explicitly carried over across death.
        if (event.isWasDeath()) {
            CompoundTag original = event.getOriginal().getPersistentData()
                    .getCompound(Player.PERSISTED_NBT_TAG);
            event.getEntity().getPersistentData()
                    .put(Player.PERSISTED_NBT_TAG, original.copy());
        }
    }
}
