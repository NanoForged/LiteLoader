package io.github.nanoforged.fml.mod.relaunch;

import com.google.auto.service.AutoService;
import io.github.nanoforged.launchredirector.api.ILaunchConfigPlugin;
import io.github.nanoforged.launchredirector.api.LaunchContext;
import io.github.nanoforged.launchredirector.api.MutableLaunchContext;

import java.nio.file.Path;
import java.util.Set;

import static io.github.nanoforged.fml.mod.ModPlugin.getModLocation;

@AutoService(ILaunchConfigPlugin.class)
@ILaunchConfigPlugin.Order(value = 1337)
public class FluxLaunchConfigPlugin implements ILaunchConfigPlugin {
    @Override
    public LaunchContext process(MutableLaunchContext context) {
        return context
                .modifyClasspath(FluxLaunchConfigPlugin::handleClassPath)
                .addSystemProperty("java.system.class.loader","com.gtnewhorizons.retrofuturabootstrap.RfbSystemClassLoader")
                .setMainClass("com.gtnewhorizons.retrofuturabootstrap.Main")
                .addProgramArgs("--tweakClass", "io.github.nanoforged.fml.FluxBootstrap")
                .build();
    }


    private static void handleClassPath(Set<Path> cp) {
        // remove log4j1
        cp.removeIf(p -> p.getFileName().toString().contains("log4j-1"));
        // append Libs to ClassPath
        cp.add(getLibs());
    }

    private static Path getLibs() {
        return getModLocation().resolve("libs");
    }




}
