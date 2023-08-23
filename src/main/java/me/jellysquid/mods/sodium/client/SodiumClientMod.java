package me.jellysquid.mods.sodium.client;

import me.jellysquid.mods.sodium.client.compat.immersive.ImmersiveConnectionRenderer;
import me.jellysquid.mods.sodium.client.gui.SodiumGameOptions;
import net.minecraft.client.render.RenderLayer;
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
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(SodiumClientMod.MODID)
public class SodiumClientMod {
    private static SodiumGameOptions CONFIG;
    private static Logger LOGGER = LoggerFactory.getLogger("Rubidium");

    private static String MOD_VERSION;

    public static final String MODID = "rubidium";

    public static List<RenderLayer> renderLayers = RenderLayer.getBlockLayers();
    
    public static boolean flywheelLoaded = false;
    public static boolean immersiveLoaded = FMLLoader.getLoadingModList().getModFileById("immersiveengineering") != null;
    
    public SodiumClientMod() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        MinecraftForge.EVENT_BUS.addListener(this::registerReloadListener);
        MOD_VERSION = ModList.get().getModContainerById(MODID).get().getModInfo().getVersion().toString();

        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class, () -> new IExtensionPoint.DisplayTest(() -> NetworkConstants.IGNORESERVERONLY, (a, b) -> true));
    }
    
    public void setup(final FMLClientSetupEvent event) {
        CONFIG = loadConfig();
        flywheelLoaded = ModList.get().isLoaded("flywheel");
    }
    
    public void registerReloadListener(AddReloadListenerEvent ev) {
    	if(immersiveLoaded)
    		ev.addListener(new ImmersiveConnectionRenderer());
    }

    public static SodiumGameOptions options() {
        if (CONFIG == null) {
        	CONFIG = loadConfig();
        }

        return CONFIG;
    }

    public static Logger logger() {
        if (LOGGER == null) {
            throw new IllegalStateException("Logger not yet available");
        }

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
