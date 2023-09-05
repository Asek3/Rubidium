package me.jellysquid.mods.sodium.client;

import me.jellysquid.mods.sodium.client.compat.ccl.CCLCompat;
import me.jellysquid.mods.sodium.client.gui.SodiumGameOptions;
import net.minecraftforge.fml.ExtensionPoint;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.network.FMLNetworkConstants;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(SodiumClientMod.MODID)
public class SodiumClientMod {
    public static final String MODID = "rubidium";

    private static SodiumGameOptions CONFIG;
    private static final Logger LOGGER = LogManager.getLogger("Rubidium");

    private static String MOD_VERSION;
    
    public static final boolean flywheelLoaded = FMLLoader.getLoadingModList().getModFileById("flywheel") != null;
    public static final boolean cclLoaded = FMLLoader.getLoadingModList().getModFileById("codechickenlib") != null;
    
    public SodiumClientMod() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onInitializeClient);
        
        ModLoadingContext.get().registerExtensionPoint(ExtensionPoint.DISPLAYTEST, () -> Pair.of(() -> FMLNetworkConstants.IGNORESERVERONLY, (a, b) -> true));
    }
    
    public void onInitializeClient(final FMLClientSetupEvent event) {
        MOD_VERSION = ModList.get().getModContainerById(MODID).get().getModInfo().getVersion().toString();
    	
    	if(cclLoaded) {
    		CCLCompat.init();
    	}
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
        return SodiumGameOptions.load(FMLPaths.CONFIGDIR.get().resolve(MODID + "-options.json"));
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
