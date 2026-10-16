package io.github.nanoforged.fml;


import io.github.nanoforged.fml.pipeline.SimpleModDiscover;
import io.github.nanoforged.fml.utils.LaunchHelper;
import lombok.Getter;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixins;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;


public final class FluxModLoader {

    @Getter
    private static final Logger LOGGER = LogManager.getLogger("Flux/FluxModLoader");

    private FluxModLoader() {}

    private static final List<String> MIXINS = new ArrayList<>();



    public static void run(LaunchClassLoader launchClassLoader) {

        LaunchHelper.configureLaunch(launchClassLoader);

        var results = SimpleModDiscover.discover(launchClassLoader);

        for (var result : results) {
            var transformers = result.transformers();
            var mixins = result.mixins();
            transformers.forEach(transformer -> {
                launchClassLoader.addTransformerExclusion(transformer);
                launchClassLoader.registerTransformer(transformer);
            });

            MIXINS.addAll(mixins);
            LOGGER.info("installed CoreMod:{}", result.id());
            LOGGER.info("mixins:{}", mixins);
            LOGGER.info("transformers:{}", transformers);
        }
        MIXINS.forEach(Mixins::addConfiguration);
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
