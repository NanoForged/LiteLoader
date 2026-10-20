# FluxModLoader Lite (LiteLoader)

> A Simple CoreMod Loader Implement of Starsector  
> Based on RFB and LaunchRedirector

[![Release](https://img.shields.io/github/v/release/NanoForged/LiteLoader?label=release)](https://github.com/NanoForged/LiteLoader/releases)
[![License](https://img.shields.io/github/license/NanoForged/LiteLoader)](LICENSE)

## 概述

本项目`LiteLoder`,下称`LL`  
是一个FluxModLoader的最小可行实现，用于在Starsector中提供基于类转换的动态修改能力。  

LL使用了与MinecraftForge相同的技术栈，并将其中CoreMod系统这一概念剥离后重新实现。

LL的本质是对RetroFuturaBootstrap（即FOSS版本的LaunchWrapper）的简单包装。  
其被用于简化对特定平台的引导流程，并在一定程度上减轻`NanoForge`对特定平台的依赖。

### For User
> TLDR: 详见 `安装`

按照传统定义，本模组是一个依赖库模组，本模组允许开发者通过编写Java代码来修改游戏与其他模组。  
为了正确的进行初始化，本模组使用了JavaAgent技术，这意味着可能会与其他使用了类似技术的模组不兼容。  

潜在的不兼容有：
1. AITweak - 使用Agent、自定义类加载器
2. MixinLib - 使用Agent、且除模组Mixin加载外的绝大数功能已被包含

总而言之，如果你是用户，请不要盲目的安装本模组，本模组存在一些潜在的不兼容性。  
请确保你知道你在做什么，我们建议仅在有需求使用CoreMod或有模组明确声明依赖该模组时安装使用。  

注意，请不要通过直接删除模组文件夹卸载本模组，如果您是windows系统，请先运行bootstrap脚本中的卸载选项。  
否则您可能面临游戏无法启动等问题。

### For Dev
> TLDR: 详见 `For CoreMod Developer`

LiteLoader是一个简单的CoreMod加载器，如果您有过1.12.x MinecraftForge的CoreMod开发经验，您应该很快能够上手LiteLoder。

LiteLoader目前提供了类似`FMLLoadingPlugin`和`MixinBooter`风格的ASMTransformer与Mixin注册。  

但由于我们目前并未实现独立的Mixin引导范围，Mixin使用默认的LW兼容服务被引导。  
因此，您编写的ASMTransformer必须是幂等的，Mixin为了获取最终字节码，其处理器迫使转换器链重新从原始字节码开始处理，而这意味着每个类您的ASMTransformer都会被调用两次。

同时，这也意味着，您编写的ASMTransformer永远会作用在Mixin之前，请确保您的大部分需求可以使用Mixin解决，而非ASMTransformer，以防出现潜在的兼容性问题。


## 工作原理

```
vmparams (-javaagent)
  └─ LaunchRedirector               //relaunch game with custom JvmArgs
       └─ RFB (RetroFuturaBootstrap)
            └─ FluxBootstrap        //ITweaker invoked
                 └─ FluxModLoader.run()
                      ├─ LaunchHelper.configureLaunch()   init Mixin / MixinExtras
                      └─ SimpleModDiscover.discover()     mods/coremods → ServiceLoader → regster
```

关键点：

- 引导依赖 **LaunchRedirector**，它以 `-javaagent` 形式挂在存在于Launcher JVM上；
- 游戏类与 LaunchClassLoader 之间的委派关系由 `LaunchHelper` 显式登记（RFB 的 childDelegations），
  避免同一个游戏类被加载出两份副本；
- 插件按 `priority()` 升序执行，同优先级按 `id()` 自然序。


## 安装

### Windows

1. 从 [Releases](https://github.com/NanoForged/LiteLoader/releases) 下载 `FluxModLoader-<version>.zip`；
2. 解压到 Starsector 的 `mods/` 目录，得到 `mods/FluxModLoader/`；
3. 在该目录下执行：

   ```powershell
   .\bootstrap-agent.ps1 install
   ```

4. 用 `status` 确认写入结果：

   ```powershell
   .\bootstrap-agent.ps1 status
   ```

脚本只改动 `starsector-core` 上一级的 `vmparams`，首次修改前会备份成 `vmparams.bak`。

### 卸载

```powershell
.\bootstrap-agent.ps1 uninstall             # 摘掉所有 LaunchRedirector agent
.\bootstrap-agent.ps1 uninstall -Restore    # 或直接还原 vmparams.bak
```

### Linux / macOS

官方分发在 Unix 平台是 shell 启动脚本，`vmparams` 不参与，需要手动在启动脚本中添加：

```
-javaagent:/path/to/mods/FluxModLoader/bootstrap/LaunchRedirector.jar
```

## For CoreMod Developer

### 放置位置

CoreMod 的 jar 放进 `mods/coremods/`，加载器会把该目录下所有条目加入 LaunchClassLoader 的 classpath。

### 最小实现

```java
public final class ExampleCorePlugin implements IFluxCorePlugin {

    @Override
    public Identifier id() {
        return Identifier.of("example", "core");
    }

    @Override
    public List<String> transformers() {
        return List.of("example.asm.ExampleTransformer");
    }

    @Override
    public List<String> mixinConfigs() {
        return List.of("example.mixins.json");
    }

    @Override
    public int priority() {
        return 1000;   // 越小越早
    }
}
```

### 注册

在 jar 内提供 `META-INF/services/io.github.nanoforged.fml.api.spi.coremod.IFluxCorePlugin`，
内容为实现类的全限定名。加载器通过 `ServiceLoader` 发现实现。

### 执行顺序与 Blackboard

处理完成后，加载器会把汇总结果写进 `Launch.blackboard`，供扩展读取：

| Key | 内容 |
| :--- | :--- |
| `flux.lite.plugins` | 已装载插件的 `Identifier` 列表 |
| `flux.lite.plugins.mixin` | 汇总后的 mixin 配置名 |
| `flux.lite.plugins.transformers` | 汇总后的 transformer 类名 |

需要更早介入的插件可以实现 `IFluxLiteLoaderExtension`，在 `configure` 中直接拿到
`LaunchClassLoader` 与 blackboard。

> `IFluxCorePlugin` 与 `IFluxLiteLoaderExtension` 目前都标记为 `@ApiStatus.Experimental`，
> 跨版本不保证二进制兼容。  
> 我们可能会在将来的Loader演进中废弃/更改这些接口

## 从源码构建

```properties
# gradle.properties
starsector_path=D:/Starsector
```

```bash
./gradlew releaseZip    # build/dist/<mod_id>-<mod_version>.zip
./gradlew toGame        # 同步至 <starsector_path>/mods/FluxModLoader
./gradlew deployMod     # clean + toGame
```

构建期只依赖游戏安装里的 `starsector-core/starfarer.api.jar`（编译期），运行时 jar 来自 `runtimeOnly`。

## 项目结构

| 路径 | 作用 |
| :--- | :--- |
| `src/main/java/.../mod/` | 模组入口 `ModPlugin`（Starsector `BaseModPlugin`） |
| `LiteLoader/` | 加载器核心子项目：`FluxBootstrap`、`FluxModLoader`、SPI、发现管线 |
| `assets/` | 随包分发的资源，`bootstrap-agent.ps1` 在此 |
| `gradle/libs.versions.toml` | 依赖版本集中声明（含 LaunchRedirector 的 ref） |

## 发布

推送形如 `v1.0.0` 的 tag 会触发 `.github/workflows/release.yml`：构建 `releaseZip`，
把 `FluxModLoader-<version>.jar` 与 `FluxModLoader-<version>.zip` 挂到 GitHub Release。
带 `-` 的版本（如 `v1.0.0-beta.1`）会标为 prerelease。

## 已知限制

- 引导依赖 LaunchRedirector，缺失时 `ModPlugin` 会抛出提示信息并中止；
- Windows 安装脚本只处理 `vmparams`，Unix 启动脚本需手工维护；

## 许可证

[MIT](LICENSE)。

## 致谢

- [RetroFuturaBootstrap](https://github.com/GTNewHorizons/RetroFuturaBootstrap)
- [SpongePowered Mixin](https://github.com/SpongePowered/Mixin) 与 [MixinExtras](https://github.com/LlamaLad7/MixinExtras)
- [LaunchRedirector](https://github.com/NanoForged/LaunchRedirector)
