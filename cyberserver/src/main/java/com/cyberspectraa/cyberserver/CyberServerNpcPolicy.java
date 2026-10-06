package com.cyberspectraa.cyberserver;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;

public final class CyberServerNpcPolicy {
    private static final String NATURAL_SPAWN_TAG =
            "CyberNpcNaturalSpawn";
    private static final String POLICY_APPLIED_TAG =
            "CyberServerNaturalProgressionApplied";
    private static final String CLASS_ADVANCEMENT_LOCK_TAG =
            "CyberNpcClassAdvancementLocked";
    private static final String RACE_EVOLUTION_LOCK_TAG =
            "CyberRacesEvolutionLocked";

    private static final String PROGRESSION_MANAGER =
            "com.cyberspectraa.cyberraces.progression.ProgressionManager";

    private static boolean progressionResolved;
    private static Method setLevelMethod;

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity()
                    instanceof LivingEntity living)
                || !isNaturalWildCyberNpc(living)
                || living.getPersistentData()
                    .getBoolean(POLICY_APPLIED_TAG)) {
            return;
        }

        var server = level.getServer();
        CyberServerSavedData state =
                CyberServerSavedData.get(server);

        living.getPersistentData().putBoolean(
                CLASS_ADVANCEMENT_LOCK_TAG,
                !state.areAdvancedNpcClassesUnlocked()
        );
        living.getPersistentData().putBoolean(
                RACE_EVOLUTION_LOCK_TAG,
                !state.areEvolvedNpcRacesUnlocked()
        );

        int levelValue = calculateNaturalLevel(
                level,
                living,
                state
        );

        setCyberLevel(living, levelValue);

        if (state.areAdvancedNpcClassesUnlocked()) {
            invokeNoArg(
                    living,
                    "ensureWildClassAdvancement"
            );
        }

        living.getPersistentData().putBoolean(
                POLICY_APPLIED_TAG,
                true
        );
    }

    public static int calculateNaturalLevel(
            ServerLevel level,
            LivingEntity npc,
            CyberServerSavedData state
    ) {
        long worldDays = Math.max(
                0L,
                level.getGameTime() / 24_000L
        );

        int worldBonus = (int) Math.min(
                state.getNpcWorldBonusCap(),
                worldDays
                    / Math.max(
                        1,
                        state.getNpcWorldDaysPerLevel()
                    )
        );

        double effectiveDifficulty =
                level.getCurrentDifficultyAt(
                    npc.blockPosition()
                ).getEffectiveDifficulty();

        int localBonus = clamp(
                (int) Math.floor(
                    Math.max(
                        0.0D,
                        effectiveDifficulty - 1.0D
                    ) * 1.5D
                ),
                0,
                state.getNpcLocalDifficultyBonusCap()
        );

        int generated = 1
                + worldBonus
                + localBonus
                + npc.getRandom().nextInt(4);

        if (npc.getRandom().nextDouble()
                < state.getNpcVeteranChance()) {
            generated += 3
                    + npc.getRandom().nextInt(6);
        }

        return clamp(
                generated,
                1,
                state.getNaturalNpcLevelCap()
        );
    }

    private static boolean isNaturalWildCyberNpc(
            Entity entity
    ) {
        if (!entity.getPersistentData()
                .getBoolean(NATURAL_SPAWN_TAG)) {
            return false;
        }

        ResourceLocation id =
                ForgeRegistries.ENTITY_TYPES
                    .getKey(entity.getType());

        return id != null
                && "cybernpc".equals(id.getNamespace())
                && "cyber_npc".equals(id.getPath());
    }

    private static void setCyberLevel(
            LivingEntity entity,
            int value
    ) {
        resolveProgression();

        if (setLevelMethod == null) {
            return;
        }

        try {
            setLevelMethod.invoke(
                    null,
                    entity,
                    Math.max(1, value)
            );
        } catch (ReflectiveOperationException
                | RuntimeException exception) {
            CyberServer.LOGGER.debug(
                    "Could not apply CyberServer natural NPC level to {}",
                    entity.getUUID(),
                    exception
            );
        }
    }

    private static void resolveProgression() {
        if (progressionResolved) {
            return;
        }

        progressionResolved = true;

        try {
            Class<?> manager =
                    Class.forName(PROGRESSION_MANAGER);

            setLevelMethod = manager.getMethod(
                    "setLevel",
                    LivingEntity.class,
                    int.class
            );
        } catch (ReflectiveOperationException
                | LinkageError ignored) {
            setLevelMethod = null;
        }
    }

    private static void invokeNoArg(
            Object target,
            String name
    ) {
        if (target == null) {
            return;
        }

        try {
            Method method = target.getClass()
                    .getMethod(name);
            method.invoke(target);
        } catch (ReflectiveOperationException
                | RuntimeException ignored) {
        }
    }

    private static int clamp(
            int value,
            int min,
            int max
    ) {
        return Math.max(min, Math.min(max, value));
    }
}
