package io.github.nanoforged.fml;


import io.github.nanoforged.fml.pipeline.SimpleModDiscover;
import io.github.nanoforged.fml.utils.LaunchHelper;
import lombok.Getter;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixins;

import java.util.*;


public final class FluxModLoader {

    @Getter
    private static final Logger LOGGER = LogManager.getLogger("Flux/FluxModLoader");

    private FluxModLoader() {}

    private static final List<String> MIXINS = new ArrayList<>();

    private static final Set<String> REGISTERED_TRANSFORMERS = new HashSet<>();
    private static final Set<String> REGISTERING_MIXINS = new HashSet<>();

    public static void run(LaunchClassLoader launchClassLoader) {

        LaunchHelper.configureLaunch(launchClassLoader);

        var results = SimpleModDiscover.discover(launchClassLoader);

        results.forEach(result -> handleResult(launchClassLoader, result));

        MIXINS.forEach(Mixins::addConfiguration);
    }

    private static void handleResult(LaunchClassLoader launchClassLoader, SimpleModDiscover.PluginResult result) {

        for (String transformer : result.transformers()) {
            if (!REGISTERED_TRANSFORMERS.add(transformer)) {
                LOGGER.warn("duplicate transformer declaration ignored: {} (from {})", transformer, result.id());
                continue;
            }
            launchClassLoader.addTransformerExclusion(transformer);
            launchClassLoader.registerTransformer(transformer);
        }

        for (String mixin : result.mixins()) {
            if (!REGISTERING_MIXINS.add(mixin)) {
                LOGGER.warn("duplicate mixin declaration ignored: {}", mixin);
                continue;
            }
            MIXINS.add(mixin);
        }
        LOGGER.info("installed CoreMod:{} mixins:{} transformers:{}", result.id(), result.mixins(), result.transformers());
    }


    /**
     * 经 {@code ServiceLoader} 装载一组 SPI 实现。
     *
     * @param type SPI 接口
     * @param classLoader 加载 SPI 实现的类加载器
     * @param <T> SPI 类型
     * @return 实现列表，按 services 声明顺序
     */
    public static <T> List<T> load(Class<T> type, ClassLoader classLoader) {
        List<T> services = new ArrayList<>();
        for (T service : ServiceLoader.load(type, classLoader)) {
            services.add(service);
        }
        return services;
    }
}
