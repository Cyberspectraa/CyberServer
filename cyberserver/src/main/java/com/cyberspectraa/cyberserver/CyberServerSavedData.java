package com.cyberspectraa.cyberserver;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CyberServerSavedData extends SavedData {
    /*
     * Keep the old standalone Arrival data name so existing worlds preserve
     * church spawn and first-arrival state after the server-director upgrade.
     */
    private static final String DATA_NAME = "season2arrival_data";
    public static final int DATA_VERSION = 2;

    private final Set<UUID> arrivedPlayers = new HashSet<>();

    private boolean spawnConfigured;
    private int spawnX;
    private int spawnY;
    private int spawnZ;
    private float spawnYaw;

    private int storyAct = 1;
    private final Map<String, Boolean> storyFlags = new HashMap<>();
    private final Set<String> unlockedRegions = new HashSet<>();
    private final Set<String> defeatedBosses = new HashSet<>();
    private final Map<String, Set<String>> specialNpcQuestBindings =
            new HashMap<>();

    private int naturalNpcLevelCap = 35;
    private int npcWorldDaysPerLevel = 10;
    private int npcWorldBonusCap = 20;
    private int npcLocalDifficultyBonusCap = 8;
    private double npcVeteranChance = 0.04D;
    private boolean advancedNpcClassesUnlocked = true;
    private boolean evolvedNpcRacesUnlocked = true;

    private double questRewardMultiplier = 1.0D;

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

        ListTag arrivals = tag.getList(
                "ArrivedPlayers",
                Tag.TAG_STRING
        );

        for (int i = 0; i < arrivals.size(); i++) {
            try {
                data.arrivedPlayers.add(
                        UUID.fromString(arrivals.getString(i))
                );
            } catch (IllegalArgumentException ignored) {
            }
        }

        data.storyAct = Math.max(
                1,
                tag.contains("StoryAct")
                        ? tag.getInt("StoryAct")
                        : 1
        );

        if (tag.contains("StoryFlags", Tag.TAG_COMPOUND)) {
            CompoundTag flags = tag.getCompound("StoryFlags");

            for (String key : flags.getAllKeys()) {
                data.storyFlags.put(
                        normalizeKey(key),
                        flags.getBoolean(key)
                );
            }
        }

        loadStringSet(
                tag,
                "UnlockedRegions",
                data.unlockedRegions
        );
        loadStringSet(
                tag,
                "DefeatedBosses",
                data.defeatedBosses
        );

        if (tag.contains(
                "SpecialNpcQuestBindings",
                Tag.TAG_COMPOUND
        )) {
            CompoundTag bindings = tag.getCompound(
                    "SpecialNpcQuestBindings"
            );

            for (String npcKey : bindings.getAllKeys()) {
                Set<String> quests = new HashSet<>();
                ListTag list = bindings.getList(
                        npcKey,
                        Tag.TAG_STRING
                );

                for (int i = 0; i < list.size(); i++) {
                    String questId = list.getString(i).trim();

                    if (!questId.isBlank()) {
                        quests.add(questId);
                    }
                }

                if (!quests.isEmpty()) {
                    data.specialNpcQuestBindings.put(
                            normalizeKey(npcKey),
                            quests
                    );
                }
            }
        }

        if (tag.contains("NaturalNpcLevelCap")) {
            data.naturalNpcLevelCap = clamp(
                    tag.getInt("NaturalNpcLevelCap"),
                    1,
                    1000
            );
        }

        if (tag.contains("NpcWorldDaysPerLevel")) {
            data.npcWorldDaysPerLevel = clamp(
                    tag.getInt("NpcWorldDaysPerLevel"),
                    1,
                    3650
            );
        }

        if (tag.contains("NpcWorldBonusCap")) {
            data.npcWorldBonusCap = clamp(
                    tag.getInt("NpcWorldBonusCap"),
                    0,
                    1000
            );
        }

        if (tag.contains("NpcLocalDifficultyBonusCap")) {
            data.npcLocalDifficultyBonusCap = clamp(
                    tag.getInt("NpcLocalDifficultyBonusCap"),
                    0,
                    100
            );
        }

        if (tag.contains("NpcVeteranChance")) {
            data.npcVeteranChance = clamp(
                    tag.getDouble("NpcVeteranChance"),
                    0.0D,
                    1.0D
            );
        }

        if (tag.contains("AdvancedNpcClassesUnlocked")) {
            data.advancedNpcClassesUnlocked =
                    tag.getBoolean("AdvancedNpcClassesUnlocked");
        }

        if (tag.contains("EvolvedNpcRacesUnlocked")) {
            data.evolvedNpcRacesUnlocked =
                    tag.getBoolean("EvolvedNpcRacesUnlocked");
        }

        if (tag.contains("QuestRewardMultiplier")) {
            data.questRewardMultiplier = clamp(
                    tag.getDouble("QuestRewardMultiplier"),
                    0.0D,
                    100.0D
            );
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("DataVersion", DATA_VERSION);

        tag.putBoolean("SpawnConfigured", spawnConfigured);
        tag.putInt("SpawnX", spawnX);
        tag.putInt("SpawnY", spawnY);
        tag.putInt("SpawnZ", spawnZ);
        tag.putFloat("SpawnYaw", spawnYaw);

        ListTag arrivals = new ListTag();
        for (UUID uuid : arrivedPlayers) {
            arrivals.add(StringTag.valueOf(uuid.toString()));
        }
        tag.put("ArrivedPlayers", arrivals);

        tag.putInt("StoryAct", storyAct);

        CompoundTag flags = new CompoundTag();
        for (Map.Entry<String, Boolean> entry
                : storyFlags.entrySet()) {
            flags.putBoolean(
                    entry.getKey(),
                    entry.getValue()
            );
        }
        tag.put("StoryFlags", flags);

        tag.put(
                "UnlockedRegions",
                saveStringSet(unlockedRegions)
        );
        tag.put(
                "DefeatedBosses",
                saveStringSet(defeatedBosses)
        );

        CompoundTag npcBindings = new CompoundTag();

        for (Map.Entry<String, Set<String>> entry
                : specialNpcQuestBindings.entrySet()) {
            npcBindings.put(
                    entry.getKey(),
                    saveStringSet(entry.getValue())
            );
        }

        tag.put(
                "SpecialNpcQuestBindings",
                npcBindings
        );

        tag.putInt(
                "NaturalNpcLevelCap",
                naturalNpcLevelCap
        );
        tag.putInt(
                "NpcWorldDaysPerLevel",
                npcWorldDaysPerLevel
        );
        tag.putInt(
                "NpcWorldBonusCap",
                npcWorldBonusCap
        );
        tag.putInt(
                "NpcLocalDifficultyBonusCap",
                npcLocalDifficultyBonusCap
        );
        tag.putDouble(
                "NpcVeteranChance",
                npcVeteranChance
        );
        tag.putBoolean(
                "AdvancedNpcClassesUnlocked",
                advancedNpcClassesUnlocked
        );
        tag.putBoolean(
                "EvolvedNpcRacesUnlocked",
                evolvedNpcRacesUnlocked
        );

        tag.putDouble(
                "QuestRewardMultiplier",
                questRewardMultiplier
        );

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

    public int getStoryAct() {
        return storyAct;
    }

    public void setStoryAct(int value) {
        int safe = Math.max(1, value);

        if (storyAct != safe) {
            storyAct = safe;
            setDirty();
        }
    }

    public boolean getFlag(String key) {
        return storyFlags.getOrDefault(
                normalizeKey(key),
                false
        );
    }

    public void setFlag(String key, boolean value) {
        String normalized = normalizeKey(key);

        if (normalized.isBlank()) {
            return;
        }

        Boolean previous = storyFlags.put(
                normalized,
                value
        );

        if (previous == null
                || previous.booleanValue() != value) {
            setDirty();
        }
    }

    public Set<String> getEnabledFlags() {
        Set<String> result = new HashSet<>();

        for (Map.Entry<String, Boolean> entry
                : storyFlags.entrySet()) {
            if (entry.getValue()) {
                result.add(entry.getKey());
            }
        }

        return Set.copyOf(result);
    }

    public boolean isRegionUnlocked(String id) {
        return unlockedRegions.contains(
                normalizeKey(id)
        );
    }

    public void setRegionUnlocked(
            String id,
            boolean unlocked
    ) {
        String normalized = normalizeKey(id);

        if (normalized.isBlank()) {
            return;
        }

        boolean changed = unlocked
                ? unlockedRegions.add(normalized)
                : unlockedRegions.remove(normalized);

        if (changed) {
            setDirty();
        }
    }

    public Set<String> getUnlockedRegions() {
        return Set.copyOf(unlockedRegions);
    }

    public boolean isBossDefeated(String id) {
        return defeatedBosses.contains(
                normalizeKey(id)
        );
    }

    public void setBossDefeated(
            String id,
            boolean defeated
    ) {
        String normalized = normalizeKey(id);

        if (normalized.isBlank()) {
            return;
        }

        boolean changed = defeated
                ? defeatedBosses.add(normalized)
                : defeatedBosses.remove(normalized);

        if (changed) {
            setDirty();
        }
    }

    public Set<String> getDefeatedBosses() {
        return Set.copyOf(defeatedBosses);
    }

    public Set<String> getNpcQuestBindings(
            String npcId
    ) {
        Set<String> values =
                specialNpcQuestBindings.get(
                    normalizeKey(npcId)
                );

        return values == null
                ? Set.of()
                : Set.copyOf(values);
    }

    public boolean bindNpcQuest(
            String npcId,
            String questId
    ) {
        String npc = normalizeKey(npcId);
        String quest = questId == null
                ? ""
                : questId.trim();

        if (npc.isBlank() || quest.isBlank()) {
            return false;
        }

        boolean changed =
                specialNpcQuestBindings
                    .computeIfAbsent(
                        npc,
                        key -> new HashSet<>()
                    )
                    .add(quest);

        if (changed) {
            setDirty();
        }

        return changed;
    }

    public boolean unbindNpcQuest(
            String npcId,
            String questId
    ) {
        String npc = normalizeKey(npcId);
        String quest = questId == null
                ? ""
                : questId.trim();

        Set<String> values =
                specialNpcQuestBindings.get(npc);

        if (values == null
                || !values.remove(quest)) {
            return false;
        }

        if (values.isEmpty()) {
            specialNpcQuestBindings.remove(npc);
        }

        setDirty();
        return true;
    }

    public int getNaturalNpcLevelCap() {
        return naturalNpcLevelCap;
    }

    public void setNaturalNpcLevelCap(int value) {
        int safe = clamp(value, 1, 1000);

        if (naturalNpcLevelCap != safe) {
            naturalNpcLevelCap = safe;
            setDirty();
        }
    }

    public int getNpcWorldDaysPerLevel() {
        return npcWorldDaysPerLevel;
    }

    public int getNpcWorldBonusCap() {
        return npcWorldBonusCap;
    }

    public int getNpcLocalDifficultyBonusCap() {
        return npcLocalDifficultyBonusCap;
    }

    public double getNpcVeteranChance() {
        return npcVeteranChance;
    }

    public boolean areAdvancedNpcClassesUnlocked() {
        return advancedNpcClassesUnlocked;
    }

    public void setAdvancedNpcClassesUnlocked(
            boolean value
    ) {
        if (advancedNpcClassesUnlocked != value) {
            advancedNpcClassesUnlocked = value;
            setDirty();
        }
    }

    public boolean areEvolvedNpcRacesUnlocked() {
        return evolvedNpcRacesUnlocked;
    }

    public void setEvolvedNpcRacesUnlocked(
            boolean value
    ) {
        if (evolvedNpcRacesUnlocked != value) {
            evolvedNpcRacesUnlocked = value;
            setDirty();
        }
    }

    public double getQuestRewardMultiplier() {
        return questRewardMultiplier;
    }

    public void setQuestRewardMultiplier(double value) {
        double safe = clamp(value, 0.0D, 100.0D);

        if (Math.abs(
                questRewardMultiplier - safe
        ) > 0.000001D) {
            questRewardMultiplier = safe;
            setDirty();
        }
    }

    private static void loadStringSet(
            CompoundTag tag,
            String key,
            Set<String> target
    ) {
        ListTag list = tag.getList(
                key,
                Tag.TAG_STRING
        );

        for (int i = 0; i < list.size(); i++) {
            String value = normalizeKey(
                    list.getString(i)
            );

            if (!value.isBlank()) {
                target.add(value);
            }
        }
    }

    private static ListTag saveStringSet(
            Set<String> values
    ) {
        ListTag list = new ListTag();

        for (String value : values) {
            list.add(StringTag.valueOf(value));
        }

        return list;
    }

    public static String normalizeKey(String value) {
        if (value == null) {
            return "";
        }

        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replace(' ', '_');
    }

    private static int clamp(
            int value,
            int min,
            int max
    ) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(
            double value,
            double min,
            double max
    ) {
        return Math.max(min, Math.min(max, value));
    }
}
