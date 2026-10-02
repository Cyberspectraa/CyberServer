package com.cyberspectraa.cyberserver;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;

import java.util.Locale;

/**
 * Server-only Photon integration.
 *
 * CyberServer never references Photon classes. Instead it invokes Photon's own
 * registered server command, which uses Photon's own S2C networking to tell
 * tracking clients to play cyberserver:summoning.
 */
public final class PhotonBridge {
    public static final String EFFECT_ID = "cyberserver:summoning";

    private PhotonBridge() {
    }

    public static boolean isPhotonLoaded() {
        return ModList.get().isLoaded("photon");
    }

    public static boolean play(ServerLevel level, double x, double y, double z) {
        if (!isPhotonLoaded()) {
            return false;
        }

        MinecraftServer server = level.getServer();
        int bx = (int) Math.floor(x);
        int by = (int) Math.floor(y);
        int bz = (int) Math.floor(z);

        // BlockEffect anchors at block centre (+0.5 Y). Offset it down by 0.5
        // so the ritual floor sits exactly at the configured player's feet.
        String command = String.format(
                Locale.ROOT,
                "photon fx %s block %d %d %d 0 -0.5 0 0 0 0 1 1 1 0 false true false",
                EFFECT_ID, bx, by, bz
        );

        CommandSourceStack source = server.createCommandSourceStack()
                .withLevel(level)
                .withPosition(new Vec3(x, y, z))
                .withPermission(4)
                .withSuppressedOutput();

        try {
            return server.getCommands().performPrefixedCommand(source, command) > 0;
        } catch (Throwable throwable) {
            CyberServer.LOGGER.warn("Photon effect trigger failed; using vanilla fallback.", throwable);
            return false;
        }
    }
}
