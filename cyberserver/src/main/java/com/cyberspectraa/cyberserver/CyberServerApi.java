package com.cyberspectraa.cyberserver;

import net.minecraft.server.MinecraftServer;

public final class CyberServerApi {
    private CyberServerApi() {
    }

    public static int getStoryAct(
            MinecraftServer server
    ) {
        return server == null
                ? 1
                : CyberServerSavedData.get(server)
                    .getStoryAct();
    }

    public static boolean isFlagSet(
            MinecraftServer server,
            String flag
    ) {
        return server != null
                && CyberServerSavedData.get(server)
                    .getFlag(flag);
    }

    public static boolean isRegionUnlocked(
            MinecraftServer server,
            String region
    ) {
        return server != null
                && CyberServerSavedData.get(server)
                    .isRegionUnlocked(region);
    }

    public static boolean isBossDefeated(
            MinecraftServer server,
            String boss
    ) {
        return server != null
                && CyberServerSavedData.get(server)
                    .isBossDefeated(boss);
    }

    public static double getQuestRewardMultiplier(
            MinecraftServer server
    ) {
        return server == null
                ? 1.0D
                : CyberServerSavedData.get(server)
                    .getQuestRewardMultiplier();
    }

    public static String[] getNpcQuestBindings(
            MinecraftServer server,
            String npcId
    ) {
        if (server == null) {
            return new String[0];
        }

        return CyberServerSavedData.get(server)
                .getNpcQuestBindings(npcId)
                .toArray(String[]::new);
    }

    public static int getNaturalNpcLevelCap(
            MinecraftServer server
    ) {
        return server == null
                ? 35
                : CyberServerSavedData.get(server)
                    .getNaturalNpcLevelCap();
    }

    public static boolean areAdvancedNpcClassesUnlocked(
            MinecraftServer server
    ) {
        return server == null
                || CyberServerSavedData.get(server)
                    .areAdvancedNpcClassesUnlocked();
    }

    public static boolean areEvolvedNpcRacesUnlocked(
            MinecraftServer server
    ) {
        return server == null
                || CyberServerSavedData.get(server)
                    .areEvolvedNpcRacesUnlocked();
    }
}
