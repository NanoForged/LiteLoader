package io.github.nanoforged.fml.api.spi.ext;

import net.minecraft.launchwrapper.LaunchClassLoader;
import org.jetbrains.annotations.ApiStatus;

import java.util.Map;

@ApiStatus.Experimental
@ApiStatus.Obsolete(since = "FluxModLoader 1.0.0")
public interface IFluxLiteLoaderExtension {
    void configure(LaunchClassLoader launchClassLoader, Map<String, Object> blackboard);
}
