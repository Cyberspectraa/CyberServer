package com.cyberspectraa.cyberserver;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(CyberServer.MOD_ID)
public final class CyberServer {
    public static final String MOD_ID = "cyberserver";
    public static final Logger LOGGER = LoggerFactory.getLogger("CyberServer");

    public CyberServer() {
        MinecraftForge.EVENT_BUS.register(new CyberServerEvents());
    }
}
