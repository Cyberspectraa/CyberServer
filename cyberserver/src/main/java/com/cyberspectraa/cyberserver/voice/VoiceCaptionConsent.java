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

    // Per-speaker caption timing. Only the chosen mode (not audio/text) is
    // stored in player NBT; worker threads read the concurrent map.
    private static final String PACING_KEY = "CyberServerVoiceCaptionPacing";
    private static final java.util.concurrent.ConcurrentMap<UUID, Pacing> PACING =
            new ConcurrentHashMap<>();

    public enum Pacing {
        FAST(750),
        NORMAL(1350),
        RELAXED(2300);

        private final int silenceMillis;
        Pacing(int silenceMillis) { this.silenceMillis = silenceMillis; }
        public int silenceMillis() { return silenceMillis; }

        static Pacing fromSaved(String value) {
            try { return Pacing.valueOf(value); }
            catch (IllegalArgumentException | NullPointerException ignored) { return NORMAL; }
        }
    }

    public static Pacing getPacing(UUID player) {
        return PACING.getOrDefault(player, Pacing.NORMAL);
    }

    public static void setPacing(ServerPlayer player, Pacing pacing) {
        CompoundTag persistent = player.getPersistentData()
                .getCompound(Player.PERSISTED_NBT_TAG);
        persistent.putString(PACING_KEY, pacing.name());
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persistent);
        PACING.put(player.getUUID(), pacing);
        player.sendSystemMessage(Component.literal("Voice caption pacing: "
                + pacing.name().toLowerCase(java.util.Locale.ROOT)
                + " (" + pacing.silenceMillis() + "ms pause before a final sentence)."));
    }

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
            PACING.remove(player.getUUID());
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
            CompoundTag persisted = player.getPersistentData()
                    .getCompound(Player.PERSISTED_NBT_TAG);
            if (persisted.getBoolean(KEY)) ENABLED.add(player.getUUID());
            else ENABLED.remove(player.getUUID());
            PACING.put(player.getUUID(),
                    Pacing.fromSaved(persisted.getString(PACING_KEY)));
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
