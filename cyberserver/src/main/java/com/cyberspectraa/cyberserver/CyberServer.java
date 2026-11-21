package com.cyberspectraa.cyberserver;

import com.cyberspectraa.cyberserver.discord.DiscordMailConfig;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(CyberServer.MOD_ID)
public final class CyberServer {
    public static final String MOD_ID = "cyberserver";
    public static final Logger LOGGER = LoggerFactory.getLogger("CyberServer");

    public CyberServer() {
        // Config creation must not wait for a completed server startup or
        // for optional CyberNpc dependencies to be installed.
        if (FMLEnvironment.dist == Dist.DEDICATED_SERVER) {
            DiscordMailConfig.ensureExists();
        }
        MinecraftForge.EVENT_BUS.register(new CyberServerEvents());
        MinecraftForge.EVENT_BUS.register(new CyberServerNpcPolicy());
    }
}
