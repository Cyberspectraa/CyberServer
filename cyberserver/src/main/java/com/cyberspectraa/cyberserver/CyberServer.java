package com.cyberspectraa.cyberserver;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import com.cyberspectraa.cyberserver.voice.VoiceCaptionConfig;
import com.cyberspectraa.cyberserver.voice.VoiceCaptionCommands;
import com.cyberspectraa.cyberserver.voice.VoiceCaptionConsent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(CyberServer.MOD_ID)
public final class CyberServer {
    public static final String MOD_ID = "cyberserver";
    public static final Logger LOGGER = LoggerFactory.getLogger("CyberServer");

    public CyberServer() {
        MinecraftForge.EVENT_BUS.register(new CyberServerEvents());
        MinecraftForge.EVENT_BUS.register(new CyberServerNpcPolicy());
        MinecraftForge.EVENT_BUS.register(new VoiceCaptionConsent());
        MinecraftForge.EVENT_BUS.register(new VoiceCaptionCommands());
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, VoiceCaptionConfig.SPEC);
    }
}
