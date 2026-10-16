package io.github.nanoforged.fml.api.spi.coremod;

import io.github.nanoforged.fml.api.Identifier;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.NonNull;

import java.util.List;


/**
 * "CoreMod" plugin entry point.
 *
 * <p>Instances are discovered by the loader and configured during core plugin
 * processing. Plugins are invoked in ascending order of {@link #priority()}:
 * lower values run first. Ties are broken by {@link #id()} in natural order.
 * <p>
 * In FluxModLoader Fully Functional version, this may be an Obsolete compat Interface
 */
@ApiStatus.Experimental
public interface IFluxCorePlugin {

    /**
     * Minecraft style ID
     * @return like Identifier.of("namespace","path")
     */
    @NonNull
    Identifier id();

    /**
     * FMLLoadingPlugin style method
     * @return like List.of("your.package.name.FQN") // must impl
     */
    @NonNull
    List<String> transformers();

    /**
     * Mixinbooter style method
     * @return like List.of("modid.mixins.json") etc...
     */
    @NonNull
    List<String> mixinConfigs();


    /**
     * Execution priority of this plugin.
     *
     * <p>Lower values are called earlier. Plugins with the same priority are
     * ordered by {@link #id()} in natural order.
     * <p>
     * this method may be changed in FluxModLoader Fully Functional version
     * @return the priority; smaller means earlier
     */
    @ApiStatus.Experimental
    int priority();
}
