package io.github.nanoforged.fml.utils;


import com.gtnewhorizons.retrofuturabootstrap.RfbSystemClassLoader;
import com.gtnewhorizons.retrofuturabootstrap.api.ExtensibleClassLoader;
import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.Mixins;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public final class LaunchHelper {
    private static final Logger LOGGER = LogManager.getLogger("Flux/Bootstrap");

    private static final List<String> GAME_PACKAGE_PREFIXES = List.of("com.fs.", "cdd.", "zzz.", "sound.");
    private static final List<String> GAME_LIBRARY_PACKAGE_PREFIXES = List.of(
            "org.json.",
            "com.jcraft.",
            "net.java.games.",
            "org.codehaus.janino.",
            "org.codehaus.commons.",
            "de.unkrig.",
            "com.thoughtworks.xstream.",
            "com.sun.xml.",
            "javax.xml.bind.",
            "com.luciad.");

    private LaunchHelper(){}

    public static void configureLaunch(LaunchClassLoader classLoader) {
        LOGGER.info("Starting configure Launch...");

        initMixin();

        //unlock lwjgl mixin on rfb
        unexclusionClass(classLoader);

        exclusionClass(classLoader);


        delegateGamePackagesToLaunchClassLoader();

        LOGGER.info("Launch Configure done.");
    }

    /**
     * 将游戏包登记进 RFB 系统类加载器的 childDelegations，使
     * {@code ClassLoader.getSystemClassLoader()} 对游戏类的解析委托给 LaunchClassLoader。
     *
     * <p>动机：游戏 jar 位于系统 classpath，RFB 系统类加载器默认能自行加载一份游戏类副本，
     * 与 LaunchClassLoader 中的游戏类形成双份。模组侧的自定义类加载器（如 AITweaks
     * CoreLoader，父加载器固定为系统类加载器）经由系统类加载器解析到第二份
     * {@code com.fs.starfarer.api.Global}，其静态状态（settings）未初始化，
     * 在标题界面 codex 初始化实例化 hullmod 脚本时抛出 NPE 并中断游戏主循环。</p>
     */
    private static void delegateGamePackagesToLaunchClassLoader() {
        if (!(ClassLoader.getSystemClassLoader() instanceof RfbSystemClassLoader rfbSystemClassLoader))  throw new  IllegalStateException("This should never happen");

        rfbSystemClassLoader.childDelegations.addAll(GAME_PACKAGE_PREFIXES);
        rfbSystemClassLoader.childDelegations.addAll(GAME_LIBRARY_PACKAGE_PREFIXES);
        LOGGER.info("已向 RFB 系统类加载器登记游戏包 child 委托: {} + {}",
                GAME_PACKAGE_PREFIXES, GAME_LIBRARY_PACKAGE_PREFIXES);
    }


    private static void unexclusionClass(LaunchClassLoader classLoader) {
        if (!(ClassLoader.getSystemClassLoader() instanceof RfbSystemClassLoader rfbSystemClassLoader)) throw new  IllegalStateException("This should never happen");

        var t = "org.lwjgl.";

        try {
            removeClassLoaderExclusion(classLoader,t);
            removeClassLoaderExclusion(rfbSystemClassLoader,t);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }


    private static void initMixin(){
        //Mixin
        LOGGER.info("Initializing Mixins...");
        MixinBootstrap.init();

        //MixinExtras (finally i init this without IMixinConfigPlugin XD)
        LOGGER.info("Initializing MixinExtras...");
        MixinExtrasBootstrap.init();
    }

    private static void exclusionClass(LaunchClassLoader classLoader){
        // transformer exclusions
        classLoader.addTransformerExclusion("io.github.nanoforged.fml");
        classLoader.addTransformerExclusion("org.spongepowered.");
        classLoader.addTransformerExclusion("LZMA.");
        classLoader.addTransformerExclusion("scala.");
        LOGGER.info("TransformerExclusion done.");

        // classloader exclusions
        classLoader.addClassLoaderExclusion("org.slf4j");
        classLoader.addClassLoaderExclusion("org.apache.logging.log4j");
        classLoader.addClassLoaderExclusion("org.apache.logging.slf4j");
        classLoader.addClassLoaderExclusion("LZMA.");
        LOGGER.info("ClassLoaderExclusion done.");
    }

    public static void removeClassLoaderExclusion(ExtensibleClassLoader loader, String prefix) throws Exception {
            modifyClassLoaderExclusion(loader, s -> s.remove(prefix));
    }

    public static void removeTransformerExclusion(LaunchClassLoader loader, String prefix) throws Exception {
            modifyTransformerExclusion(loader, s -> s.remove(prefix));
    }

    public static void modifyClassLoaderExclusion(ExtensibleClassLoader loader, Consumer<Set<String>> consumer) throws Exception {
        Field f = getCrruntCLField(loader);
        f.setAccessible(true);
        Set<String> exceptions = (Set<String>) f.get(loader);
        consumer.accept(exceptions);
    }

    public static void modifyTransformerExclusion(LaunchClassLoader loader, Consumer<Set<String>> consumer) throws Exception {
        Field f = LaunchClassLoader.class.getDeclaredField("transformerExceptions");
        f.setAccessible(true);
        Set<String> exceptions = (Set<String>) f.get(loader);
        consumer.accept(exceptions);
    }

    private static Field getCrruntCLField(ExtensibleClassLoader loader) throws NoSuchFieldException {
        if (loader instanceof RfbSystemClassLoader) {
            return RfbSystemClassLoader.class.getDeclaredField("classLoaderExceptions");
        }
        if (loader instanceof LaunchClassLoader) {
            return LaunchClassLoader.class.getDeclaredField("classLoaderExceptions");
        }
        throw  new UnsupportedOperationException("Unknown ExtensibleClassLoader");
    }
}
