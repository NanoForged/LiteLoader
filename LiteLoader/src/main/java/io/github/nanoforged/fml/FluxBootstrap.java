package io.github.nanoforged.fml;

import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.List;

public final class FluxBootstrap implements ITweaker {
    private static final Logger LOGGER = LogManager.getLogger("Flux/Tweaker");

    public static final String MAIN_CLASS = "com.fs.starfarer.StarfarerLauncher";


    @Override
    public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {

    }

    @Override
    public void injectIntoClassLoader(LaunchClassLoader classLoader) {
        LOGGER.info("Tweaker Installed! FluxModLoader Bootstrapping...");
        FluxModLoader.run(classLoader);
        LOGGER.info("Done. FluxModLoader Bootstrapped!");
    }


    @Override
    public String getLaunchTarget() {
        return MAIN_CLASS;
    }

    @Override
    public String[] getLaunchArguments() {
        return new String[0];
    }


}
