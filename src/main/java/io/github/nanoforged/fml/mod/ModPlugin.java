package io.github.nanoforged.fml.mod;

import com.fs.starfarer.api.BaseModPlugin;
import io.github.nanoforged.fml.mod.relaunch.FluxLaunchConfigPlugin;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Year;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class ModPlugin extends BaseModPlugin {

    public static final String MOD_NAME = "LiteLoader";

    private static final List<String> FUNNY_MESSAGES = List.of(
            "I Hate You Alex.",
            "NanoForge Also is Forge.",
            "Did you know that Fabric can running in Starsector?",
            "Stop using ClassLoader hacks, use Mixin!",
            "File access and reflection are allowed LOL",
            "Reflection? Reflection! SecurityException! Yeah!",
            "In %s , Still using log4j 1.x and lwjgl2...".formatted(Year.now().toString())
    );


    public static final String NOTICE_MESSAGE =
            """
            %s
            =========[ %s ]=========
            Bootstrap state not current!
            %s requires a LaunchRedirector to bootstrap Loader Core.
            This may be because LR has not been installed.
            Pleas check LR install status or loader settings.
            
            LR Installation:
              Windows:  run bootstrap-agent.ps1 in the FluxModLoader mod folder,
                you can using 'bootstrap-agent.ps1 -help' to get usage.
              Manual:   add -javaagent:{You-Path}/FluxModLoader/bootstrap/LaunchRedirector.jar
                to Starsector/vmparams
            
            State:
                RFB:%b
                FML:%b
            ===========================
            """;

    public ModPlugin() {
        if (!isInstalled()) throw new MissingRuntimeEnvironmentException(NOTICE_MESSAGE.formatted(randomMessage(), MOD_NAME, MOD_NAME,detectRFBInstalled(),detectFMLInstalled()));
    }



    private static boolean isInstalled() {
        return detectRFBInstalled() && detectFMLInstalled();
    }


    private static boolean detectRFBInstalled(){
        return ClassLoader.getSystemClassLoader().getClass().getName().startsWith("com.gtnewhorizons.retrofuturabootstrap");
    }


    private static boolean detectFMLInstalled() {
        try {
            Class.forName("io.github.nanoforged.fml.FluxModLoader",false,ClassLoader.getSystemClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    static class MissingRuntimeEnvironmentException extends RuntimeException {
        public MissingRuntimeEnvironmentException(String message) {
            super(message);
        }
    }

    public static Path getModLocation() {
        try {
            return Path.of(ModPlugin.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent().getParent();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private static String randomMessage() {
        return FUNNY_MESSAGES.get(ThreadLocalRandom.current().nextInt(FUNNY_MESSAGES.size()));
    }

}
