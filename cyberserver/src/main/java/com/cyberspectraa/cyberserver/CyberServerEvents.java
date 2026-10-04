package com.cyberspectraa.cyberserver;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class CyberServerEvents {
    private static final List<ArrivalSequence> ACTIVE_SEQUENCES = new ArrayList<>();
    private static final Set<UUID> PENDING_CHARACTER_ARRIVALS = new HashSet<>();

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CyberServerCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        CyberServerSavedData data = CyberServerSavedData.get(server);
        if (!data.isSpawnConfigured() || data.hasArrived(player.getUUID())) {
            return;
        }

        if (!isCharacterReady(player)) {
            PENDING_CHARACTER_ARRIVALS.add(player.getUUID());
            return;
        }

        beginFirstArrival(player, data);
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PENDING_CHARACTER_ARRIVALS.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.isEndConquered()) {
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        CyberServerSavedData data = CyberServerSavedData.get(server);
        if (!data.isSpawnConfigured()) {
            return;
        }

        BlockPos arrivalSpawn = data.getSpawnPos();
        BlockPos personalSpawn = player.getRespawnPosition();

        // Only override vanilla's safe-spawn search when the player is supposed to
        // return to the CyberServer world/arrival spawn. Beds and respawn anchors
        // elsewhere are left completely alone.
        boolean usesWorldSpawn = personalSpawn == null;
        boolean personalSpawnIsArrival =
                personalSpawn != null
                        && player.getRespawnDimension() == net.minecraft.world.level.Level.OVERWORLD
                        && personalSpawn.equals(arrivalSpawn);

        if (!usesWorldSpawn && !personalSpawnIsArrival) {
            return;
        }

        double x = arrivalSpawn.getX() + 0.5D;
        double y = arrivalSpawn.getY();
        double z = arrivalSpawn.getZ() + 0.5D;

        player.teleportTo(server.overworld(), x, y, z, data.getSpawnYaw(), 0.0F);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            ACTIVE_SEQUENCES.clear();
            PENDING_CHARACTER_ARRIVALS.clear();
            return;
        }

        processPendingCharacterArrivals(server);

        if (ACTIVE_SEQUENCES.isEmpty()) {
            return;
        }

        Iterator<ArrivalSequence> iterator = ACTIVE_SEQUENCES.iterator();
        while (iterator.hasNext()) {
            ArrivalSequence sequence = iterator.next();
            ServerLevel level = server.getLevel(sequence.dimension);
            if (level == null) {
                iterator.remove();
                continue;
            }

            if (sequence.vanillaFallback) {
                CyberServerEffects.renderFallback(level, sequence.x, sequence.y, sequence.z, sequence.age);
            }

            playTimedSounds(level, sequence);
            sequence.age++;

            if (sequence.age > 64) {
                iterator.remove();
            }
        }
    }

    public static void startArrival(ServerLevel level, double x, double y, double z) {
        boolean photonPlayed = PhotonBridge.play(level, x, y, z);
        ACTIVE_SEQUENCES.add(new ArrivalSequence(
                level.dimension(), x, y, z, !photonPlayed
        ));

        level.playSound(null, x, y, z,
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2F, 1.25F);
        level.playSound(null, x, y, z,
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 0.8F);
    }

    public static void enforceExactWorldSpawn(MinecraftServer server, BlockPos spawn, float yaw) {
        ServerLevel overworld = server.overworld();
        overworld.setDefaultSpawnPos(spawn, yaw);
        overworld.getGameRules().getRule(GameRules.RULE_SPAWN_RADIUS).set(0, server);
    }

    private static void processPendingCharacterArrivals(MinecraftServer server) {
        if (PENDING_CHARACTER_ARRIVALS.isEmpty()) {
            return;
        }

        CyberServerSavedData data = CyberServerSavedData.get(server);
        Iterator<UUID> iterator = PENDING_CHARACTER_ARRIVALS.iterator();

        while (iterator.hasNext()) {
            UUID uuid = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);

            if (player == null) {
                continue;
            }

            if (!data.isSpawnConfigured() || data.hasArrived(uuid)) {
                iterator.remove();
                continue;
            }

            if (!isCharacterReady(player)) {
                continue;
            }

            beginFirstArrival(player, data);
            iterator.remove();
        }
    }

    private static void beginFirstArrival(ServerPlayer player, CyberServerSavedData data) {
        MinecraftServer server = player.getServer();
        if (server == null || data.hasArrived(player.getUUID())) {
            return;
        }

        // Only consume the one-time arrival after character creation is ready.
        // Mark first so a reconnect/crash during the visual sequence cannot replay it.
        data.markArrived(player.getUUID());

        ServerLevel level = server.overworld();
        BlockPos spawn = data.getSpawnPos();
        double x = spawn.getX() + 0.5D;
        double y = spawn.getY();
        double z = spawn.getZ() + 0.5D;

        player.teleportTo(level, x, y, z, data.getSpawnYaw(), 0.0F);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;

        startArrival(level, x, y, z);
    }

    private static boolean isCharacterReady(ServerPlayer player) {
        if (!ModList.get().isLoaded("cyberraces")) {
            return true;
        }

        CompoundTag root = player.getPersistentData().getCompound("CyberRaces");

        if (root.contains("CharacterCreated")) {
            return root.getBoolean("CharacterCreated");
        }

        // Compatibility with CyberRaces builds from before the creator existed:
        // a player who already has a race is treated as completed.
        return root.contains("Race");
    }

    private static void playTimedSounds(ServerLevel level, ArrivalSequence sequence) {
        int age = sequence.age;

        if (age == 8) {
            level.playSound(null, sequence.x, sequence.y, sequence.z,
                    SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.15F, 1.45F);
        }

        if (age == 17) {
            level.playSound(null, sequence.x, sequence.y, sequence.z,
                    SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.35F, 1.65F);
        }

        if (age == 27) {
            level.playSound(null, sequence.x, sequence.y, sequence.z,
                    SoundEvents.AMETHYST_CLUSTER_HIT, SoundSource.PLAYERS, 1.1F, 1.7F);
            level.playSound(null, sequence.x, sequence.y, sequence.z,
                    SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.75F, 1.45F);
        }

        if (age == 55) {
            level.playSound(null, sequence.x, sequence.y, sequence.z,
                    SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.75F, 1.55F);
        }
    }

    private static final class ArrivalSequence {
        private final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
        private final double x;
        private final double y;
        private final double z;
        private final boolean vanillaFallback;
        private int age;

        private ArrivalSequence(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
                                double x, double y, double z, boolean vanillaFallback) {
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.vanillaFallback = vanillaFallback;
        }
    }
}
