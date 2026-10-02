package com.cyberspectraa.cyberserver;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.joml.Vector3f;

public final class CyberServerEffects {
    private static final DustParticleOptions GOLD =
            new DustParticleOptions(new Vector3f(1.0F, 0.82F, 0.28F), 1.6F);
    private static final DustParticleOptions PALE_GOLD =
            new DustParticleOptions(new Vector3f(1.0F, 0.96F, 0.72F), 1.25F);

    private CyberServerEffects() {
    }

    public static void renderFallback(ServerLevel level, double x, double y, double z, int age) {
        if (age <= 34) {
            double radius = Math.max(0.65D, 2.75D - age * 0.055D);
            double rotation = age * 0.22D;
            for (int i = 0; i < 16; i++) {
                double angle = rotation + (Math.PI * 2.0D * i / 16.0D);
                double px = x + Math.cos(angle) * radius;
                double pz = z + Math.sin(angle) * radius;
                sendParticle(level, i % 2 == 0 ? GOLD : PALE_GOLD,
                        px, y + 0.08D, pz, 1, 0, 0, 0, 0);
            }
        }

        if (age <= 42) {
            for (int i = 0; i < 7; i++) {
                double py = y + 0.35D + i * 0.72D;
                double angle = age * 0.36D + i * 0.92D;
                double radius = 0.72D - Math.min(age, 35) * 0.010D;
                double dx = Math.cos(angle) * radius;
                double dz = Math.sin(angle) * radius;
                sendParticle(level, ParticleTypes.END_ROD, x + dx, py, z + dz,
                        1, 0.01F, 0.02F, 0.01F, 0.0F);
                sendParticle(level, PALE_GOLD, x - dx, py, z - dz,
                        1, 0, 0, 0, 0);
            }
        }

        if (age <= 46 && age % 3 == 0) {
            double top = level.getMaxBuildHeight() - 1.0D;
            for (double py = y + 0.5D; py <= top; py += 5.0D) {
                sendParticle(level, age % 6 == 0 ? PALE_GOLD : GOLD,
                        x, py, z, 2, 0.10F, 1.65F, 0.10F, 0.0F);
            }
        }

        if (age == 30) {
            sendParticle(level, ParticleTypes.ELECTRIC_SPARK, x, y + 1.0D, z,
                    44, 1.7F, 2.6F, 1.7F, 0.18F);
        }

        if (age == 47) {
            sendParticle(level, ParticleTypes.FLASH, x, y + 1.0D, z,
                    1, 0, 0, 0, 0);
            sendParticle(level, ParticleTypes.TOTEM_OF_UNDYING, x, y + 1.0D, z,
                    42, 1.5F, 2.3F, 1.5F, 0.12F);
        }
    }

    private static void sendParticle(ServerLevel level, ParticleOptions particle,
                                     double x, double y, double z, int count,
                                     float xDist, float yDist, float zDist, float speed) {
        ClientboundLevelParticlesPacket packet = new ClientboundLevelParticlesPacket(
                particle, true, x, y, z, xDist, yDist, zDist, speed, count
        );

        for (ServerPlayer viewer : level.players()) {
            double dx = viewer.getX() - x;
            double dz = viewer.getZ() - z;
            if ((dx * dx + dz * dz) <= (192.0D * 192.0D)) {
                viewer.connection.send(packet);
            }
        }
    }
}
