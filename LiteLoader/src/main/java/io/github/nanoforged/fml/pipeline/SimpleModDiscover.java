package io.github.nanoforged.fml.pipeline;

import io.github.nanoforged.fml.FluxModLoader;
import io.github.nanoforged.fml.api.Identifier;
import io.github.nanoforged.fml.api.spi.coremod.IFluxCorePlugin;
import io.github.nanoforged.fml.api.spi.ext.IFluxLiteLoaderExtension;
import io.github.nanoforged.fml.utils.PathUtils;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class SimpleModDiscover {

    private static final Logger LOGGER = LogManager.getLogger("Flux/Discover");
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

        var files = List.of(Objects.requireNonNull(COREMOD_PATH.toFile().listFiles()));

        files.forEach(file -> {
            try {
                launchClassLoader.addURL(file.toPath().toUri().toURL());
            } catch (Exception e) {
               LOGGER.warn("Cannot append Classpath for :{}",file, e);
            }
        });

    }


    private static List<PluginResult> processCorePlugins(List<IFluxCorePlugin> plugins, LaunchClassLoader launchClassLoader) {


        List<PluginResult> results = new ArrayList<>();
        var sorted = new ArrayList<>(plugins);



        sorted.sort(
                Comparator.comparingInt(IFluxCorePlugin::priority).thenComparing(IFluxCorePlugin::id)
        );

        for (IFluxCorePlugin plugin : sorted) {
            results.add(new PluginResult(plugin.id(), plugin.transformers(), plugin.mixinConfigs()));
        }

        handleConfigure(results,sorted,launchClassLoader);

        return List.copyOf(results);
    }


    private static void handleConfigure(List<PluginResult> results, List<IFluxCorePlugin> plugins, LaunchClassLoader launchClassLoader) {
        var bb = Launch.blackboard;
        var ids = results.stream().map(PluginResult::id).toList();
        bb.put("flux.lite.plugins", ids);

        var mixins = new ArrayList<>();
        results.forEach(result -> {
            mixins.addAll(result.mixins);
        });
        bb.put("flux.lite.plugins.mixin", mixins);

        var transformers = new ArrayList<>();
        results.forEach(result -> {
            transformers.addAll(result.transformers);
        });
        bb.put("flux.lite.plugins.transformers", transformers);

        plugins.forEach(p -> {
            if(p instanceof IFluxLiteLoaderExtension extension) {
                extension.configure(launchClassLoader, bb);
            }
        });
    }

    public static record PluginResult(Identifier id, List<String> transformers, List<String> mixins) {}
}
