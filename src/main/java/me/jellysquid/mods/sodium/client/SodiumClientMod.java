package me.jellysquid.mods.sodium.client;

import me.jellysquid.mods.sodium.client.compat.ccl.CCLCompat;
import me.jellysquid.mods.sodium.client.compat.immersive.ImmersiveConnectionRenderer;
import me.jellysquid.mods.sodium.client.gui.SodiumGameOptions;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.network.NetworkConstants;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(SodiumClientMod.MODID)
public class SodiumClientMod {
    public static final String MODID = "rubidium";

    private static SodiumGameOptions CONFIG;
    private static final Logger LOGGER = LoggerFactory.getLogger("Rubidium");

    private static String MOD_VERSION;
    
    public static boolean immersiveLoaded = FMLLoader.getLoadingModList().getModFileById("immersiveengineering") != null;
    public static boolean cclLoaded = FMLLoader.getLoadingModList().getModFileById("codechickenlib") != null;
    
    public SodiumClientMod() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        MinecraftForge.EVENT_BUS.addListener(this::registerReloadListener);
        MOD_VERSION = ModList.get().getModContainerById(MODID).get().getModInfo().getVersion().toString();

        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class, () -> new IExtensionPoint.DisplayTest(() -> NetworkConstants.IGNORESERVERONLY, (a, b) -> true));
    }
    
    public void setup(final FMLClientSetupEvent event) {
        CONFIG = loadConfig();

        if(cclLoaded) {
            CCLCompat.init();
        }
    }
    
    public void registerReloadListener(RegisterClientReloadListenersEvent ev) {
    	if(immersiveLoaded)
    		ev.registerReloadListener(new ImmersiveConnectionRenderer());
    }

    public static SodiumGameOptions options() {
        if (CONFIG == null) {
        	CONFIG = loadConfig();
        }

        return CONFIG;
    }

    public static Logger logger() {
        return LOGGER;
    }

    private static SodiumGameOptions loadConfig() {
        try {
            return SodiumGameOptions.load();
        } catch (Exception e) {
            LOGGER.error("Failed to load configuration file", e);
            LOGGER.error("Using default configuration file in read-only mode");

            var config = new SodiumGameOptions();
            config.setReadOnly();

            return config;
        }
    }

    public static void restoreDefaultOptions() {
        CONFIG = SodiumGameOptions.defaults();

        try {
            CONFIG.writeChanges();
        } catch (IOException e) {
            throw new RuntimeException("Failed to write config file", e);
        }
    }

    public static String getVersion() {
        if (MOD_VERSION == null) {
            throw new NullPointerException("Mod version hasn't been populated yet");
        }

        return MOD_VERSION;
    }

    public static boolean isDirectMemoryAccessEnabled() {
        return options().advanced.allowDirectMemoryAccess;
    }
}
