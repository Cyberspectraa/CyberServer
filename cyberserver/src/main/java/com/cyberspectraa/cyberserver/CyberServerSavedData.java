package com.cyberspectraa.cyberserver;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class CyberServerSavedData extends SavedData {
    // Intentionally kept identical to the old standalone Arrival mod so existing
    // test-world spawn/player state carries over when that JAR is removed.
    private static final String DATA_NAME = "season2arrival_data";

    private final Set<UUID> arrivedPlayers = new HashSet<>();
    private boolean spawnConfigured;
    private int spawnX;
    private int spawnY;
    private int spawnZ;
    private float spawnYaw;

    public static CyberServerSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                CyberServerSavedData::load,
                CyberServerSavedData::new,
                DATA_NAME
        );
    }

    public static CyberServerSavedData load(CompoundTag tag) {
        CyberServerSavedData data = new CyberServerSavedData();
        data.spawnConfigured = tag.getBoolean("SpawnConfigured");
        data.spawnX = tag.getInt("SpawnX");
        data.spawnY = tag.getInt("SpawnY");
        data.spawnZ = tag.getInt("SpawnZ");
        data.spawnYaw = tag.getFloat("SpawnYaw");

        ListTag list = tag.getList("ArrivedPlayers", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            try {
                data.arrivedPlayers.add(UUID.fromString(list.getString(i)));
            } catch (IllegalArgumentException ignored) {
            }
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("SpawnConfigured", spawnConfigured);
        tag.putInt("SpawnX", spawnX);
        tag.putInt("SpawnY", spawnY);
        tag.putInt("SpawnZ", spawnZ);
        tag.putFloat("SpawnYaw", spawnYaw);

        ListTag list = new ListTag();
        for (UUID uuid : arrivedPlayers) {
            list.add(StringTag.valueOf(uuid.toString()));
        }
        tag.put("ArrivedPlayers", list);
        return tag;
    }

    public boolean isSpawnConfigured() {
        return spawnConfigured;
    }

    public BlockPos getSpawnPos() {
        return new BlockPos(spawnX, spawnY, spawnZ);
    }

    public float getSpawnYaw() {
        return spawnYaw;
    }

    public void setSpawn(BlockPos pos, float yaw) {
        spawnConfigured = true;
        spawnX = pos.getX();
        spawnY = pos.getY();
        spawnZ = pos.getZ();
        spawnYaw = yaw;
        setDirty();
    }

    public boolean hasArrived(UUID uuid) {
        return arrivedPlayers.contains(uuid);
    }

    public void markArrived(UUID uuid) {
        if (arrivedPlayers.add(uuid)) {
            setDirty();
        }
    }

    public void reset(UUID uuid) {
        if (arrivedPlayers.remove(uuid)) {
            setDirty();
        }
    }

    public int resetAll() {
        int count = arrivedPlayers.size();
        if (!arrivedPlayers.isEmpty()) {
            arrivedPlayers.clear();
            setDirty();
        }
        return count;
    }

    public int arrivedCount() {
        return arrivedPlayers.size();
    }
}
