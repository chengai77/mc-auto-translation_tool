# 构建说明

[简体中文](BUILDING.md) · [繁體中文](../Zh-tw/BUILDING.md) · [English](../en/BUILDING.md) · [返回简中 README](README.md)

项目由一个 Java 8 通用核心、九个现代 Fabric 模块、一个 1.16.5 Fabric 模块和两个独立旧 Forge 构建组成。
旧 ForgeGradle 不能在现代 JDK 上直接运行，因此不能用一条根 Gradle 命令构建全部版本。

## 26.3 Fabric

需要 JDK 25 或更高版本：

```bash
./gradlew :platform-fabric-26.3:build
```

输出位于 `platform-fabric-26.3/build/libs/`。该版本只有 Fabric 端，没有对应的
Forge/NeoForge 工程。

## 26.2 Fabric

需要 JDK 25 或更高版本：

```bash
./gradlew :platform-fabric-26.2:build
```

输出位于 `platform-fabric-26.2/build/libs/`。

## 26.1 Fabric

需要 JDK 25 或更高版本：

```bash
./gradlew :platform-fabric-26.1:build
```

输出位于 `platform-fabric-26.1/build/libs/`。

## 1.21.11 Fabric

需要 JDK 21 或更高版本：

```bash
./gradlew :platform-fabric-1.21.11:build
```

输出位于 `platform-fabric-1.21.11/build/libs/`。

## 1.21.10 Fabric

需要 JDK 21 或更高版本：

```bash
./gradlew :platform-fabric-1.21.10:build
```

输出位于 `platform-fabric-1.21.10/build/libs/`。

## 1.16.5 Fabric

需要 JDK 8 编译目标；本机实际启动验证使用 Java 8：

```bash
./gradlew :platform-fabric-1.16.5:build
```

输出位于 `platform-fabric-1.16.5/build/libs/`。该版本使用旧版 Loom 与单一
`src/main` 源集（1.16.5 没有独立服务端 JAR，不能用 `splitEnvironmentSourceSets()`），
并且只有 Fabric 端。注意 1.16.5 时代的 Fabric API 模组 id 是 `fabric`，不是
`fabric-api`，`fabric.mod.json` 的依赖项需要按此书写。

## 1.12.2 Forge

需要完整 JDK 8。进入 `legacy/forge-1.12.2/` 后运行：

```bash
./gradlew build
```

Wrapper 固定 Gradle 4.10.3，ForgeGradle 固定 3.0.197，Forge 固定 14.23.5.2860。

## 1.8.9 Forge

需要完整 JDK 8。进入 `legacy/forge-1.8.9/` 后运行：

```bash
./gradlew build
```

Wrapper 固定 Gradle 2.14.1，ForgeGradle 使用 2.1 系列，Forge 固定
11.15.1.2318-1.8.9。

若 Gradle 本身运行在只有 `java`、没有 `javac` 的旧 JRE 中，可以显式指定另一个
兼容编译器：`./gradlew build -PlegacyJavac=/absolute/path/to/javac`。正常完整 JDK 8
环境不需要这个参数。

## 构建产物统合（本机功能）

项目提供一个仅保存在本机的 PowerShell 脚本，用于把当前已有以及后续新增的
Fabric、Forge、NeoForge 和旧版 Forge 模块产物集中移动到根目录 `build/`。
脚本目录已加入 `.gitignore`，根目录的构建产物和 JAR 也不会进入 Git。

Windows 下如果 Gradle 报 `Unable to establish loopback connection`，通常是
JDK 使用当前临时目录创建本地通信 Socket 时不兼容导致。项目 Wrapper 和统合脚本
会自动使用 `C:\codex-gradle-tmp`，该目录不写入 Git。

在项目根目录执行以下命令即可先构建，再把现有版本的全部 `build/libs/` 产物移动到
根目录 `build/`：

```powershell
.\tools\local\consolidate-build.ps1 -Build -CleanOutput
```

脚本会自动扫描根目录下所有 `platform-*` 模块和 `legacy/*` 模块，仅收集可直接
导入游戏的运行时 JAR，并排除 `-sources.jar`、`-javadoc.jar`、`-dev.jar` 等辅助包。
默认移动产物；使用 `-Copy` 可保留模块原始产物。核心库不是可直接导入游戏的版本产物，
不会被统合。不同 Minecraft 版本仍需按照其 JDK 和 Gradle 要求运行。

## 核心自测

根项目的 `translator-core` 不依赖 Minecraft。测试源码位于
`translator-core/src/test/java/`，可以用 JDK 8 兼容编译后运行
`org.universaltranslator.core.CoreSelfTest`。测试覆盖格式保护、动态模板、缓存、
并发去重、失败回退、端点安全、非阻塞渲染和哈希持久化。

不要提交任何本机 `config/universal-translator.properties`、API 密钥、游戏日志、
Gradle 缓存或 Minecraft 资源。
