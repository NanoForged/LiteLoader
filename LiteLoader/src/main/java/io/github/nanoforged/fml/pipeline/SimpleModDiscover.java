package io.github.nanoforged.fml.pipeline;

import io.github.nanoforged.fml.FluxModLoader;
import io.github.nanoforged.fml.api.Identifier;
import io.github.nanoforged.fml.api.spi.coremod.IFluxCorePlugin;
import io.github.nanoforged.fml.api.spi.ext.IFluxLiteLoaderExtension;
import io.github.nanoforged.fml.utils.PathUtils;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SimpleModDiscover {
    public static final Path COREMOD_PATH = PathUtils.getModsPath().resolve("coremods");



    public static List<PluginResult> discover(LaunchClassLoader launchClassLoader) {
        appendToClasspath(launchClassLoader);

        List<IFluxCorePlugin> plugins = FluxModLoader.load(IFluxCorePlugin.class,launchClassLoader);

        return processCorePlugins(plugins,launchClassLoader);
    }



    private static void appendToClasspath(LaunchClassLoader launchClassLoader) {
        if (!COREMOD_PATH.toFile().exists()) {
            COREMOD_PATH.toFile().mkdirs();
        }

        try {
            launchClassLoader.addURL(COREMOD_PATH.toUri().toURL());
        } catch (Exception e) {
            throw new RuntimeException("Cannot append Classpath form COREMOD_PATH" + COREMOD_PATH, e);
        }
    }


    private static List<PluginResult> processCorePlugins(List<IFluxCorePlugin> plugins, LaunchClassLoader launchClassLoader) {

        var bb = Launch.blackboard;
        List<PluginResult> results = new ArrayList<>();
        var sorted = new ArrayList<>(plugins);

        sorted.forEach(p -> {
            if(p instanceof IFluxLiteLoaderExtension extension) {
                extension.configure(launchClassLoader, bb);
            }
        });


        sorted.sort(
                Comparator.comparingInt(IFluxCorePlugin::priority).thenComparing(IFluxCorePlugin::id)
        );

        for (IFluxCorePlugin plugin : sorted) {
            results.add(new PluginResult(plugin.id(), plugin.transformers(), plugin.mixinConfigs()));
        }

        return List.copyOf(results);
    }




    public static record PluginResult(Identifier id, List<String> transformers, List<String> mixins) {}
}
