package com.cyberspectraa.cyberserver;

import com.cyberspectraa.cyberserver.discord.DiscordMailConfig;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(CyberServer.MOD_ID)
public final class CyberServer {
    public static final String MOD_ID = "cyberserver";
    public static final Logger LOGGER = LoggerFactory.getLogger("CyberServer");

    public CyberServer() {
        // Always create the private settings template when the mod loads.
        // This also makes the file discoverable in local/integrated test
        // environments, even though the Discord bot runs only on dedicated.
        DiscordMailConfig.ensureExists();
        MinecraftForge.EVENT_BUS.register(new CyberServerEvents());
        MinecraftForge.EVENT_BUS.register(new CyberServerNpcPolicy());
    }
}
