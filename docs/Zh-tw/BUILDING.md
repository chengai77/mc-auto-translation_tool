# 建置說明

[简体中文](../Zh-cn/BUILDING.md) · [繁體中文](BUILDING.md) · [English](../en/BUILDING.md) · [返回繁中 README](README.md)

專案由一個 Java 8 通用核心、九個現代 Fabric 模組、一個 1.16.5 Fabric 模組及兩個獨立舊版 Forge 建置組成。
舊版 ForgeGradle 無法直接在現代 JDK 上執行，因此無法用一條根 Gradle 指令建置所有版本。

## 26.3 Fabric

需要 JDK 25 或更新版本：

```bash
./gradlew :platform-fabric-26.3:build
```

輸出位於 `platform-fabric-26.3/build/libs/`。該版本只有 Fabric 端，沒有對應的
Forge/NeoForge 工程。

## 26.2 Fabric

需要 JDK 25 或更新版本：

```bash
./gradlew :platform-fabric-26.2:build
```

輸出位於 `platform-fabric-26.2/build/libs/`。

## 26.1 Fabric

需要 JDK 25 或更新版本：

```bash
./gradlew :platform-fabric-26.1:build
```

輸出位於 `platform-fabric-26.1/build/libs/`。

## 1.21.11 Fabric

需要 JDK 21 或更新版本：

```bash
./gradlew :platform-fabric-1.21.11:build
```

輸出位於 `platform-fabric-1.21.11/build/libs/`。

## 1.21.10 Fabric

需要 JDK 21 或更新版本：

```bash
./gradlew :platform-fabric-1.21.10:build
```

輸出位於 `platform-fabric-1.21.10/build/libs/`。

## 1.16.5 Fabric

需要 JDK 8 編譯目標；本機實際啟動驗證使用 Java 8：

```bash
./gradlew :platform-fabric-1.16.5:build
```

輸出位於 `platform-fabric-1.16.5/build/libs/`。該版本使用舊版 Loom 與單一
`src/main` 源集（1.16.5 沒有獨立伺服器 JAR，不能用 `splitEnvironmentSourceSets()`），
而且只有 Fabric 端。注意 1.16.5 時代的 Fabric API 模組 id 是 `fabric`，不是
`fabric-api`，`fabric.mod.json` 的相依項目需要依此撰寫。

## 1.12.2 Forge

需要完整 JDK 8。進入 `legacy/forge-1.12.2/` 後執行：

```bash
./gradlew build
```

Wrapper 固定為 Gradle 4.10.3，ForgeGradle 固定為 3.0.197，Forge 固定為 14.23.5.2860。

## 1.8.9 Forge

需要完整 JDK 8。進入 `legacy/forge-1.8.9/` 後執行：

```bash
./gradlew build
```

Wrapper 固定為 Gradle 2.14.1，ForgeGradle 使用 2.1 系列，Forge 固定為
11.15.1.2318-1.8.9。

若 Gradle 本身執行於只有 `java` 而沒有 `javac` 的舊版 JRE，可以明確指定另一個
相容編譯器：`./gradlew build -PlegacyJavac=/absolute/path/to/javac`。一般完整 JDK 8
環境不需要此參數。

## 核心自我測試

根專案的 `translator-core` 不依賴 Minecraft。測試原始碼位於
`translator-core/src/test/java/`，以相容 JDK 8 的方式編譯後即可執行
`org.universaltranslator.core.CoreSelfTest`。測試涵蓋格式保護、動態範本、快取、
並行去重、失敗回退、端點安全、非阻塞彩現及雜湊持久化。

請勿提交任何本機 `config/universal-translator.properties`、API 金鑰、遊戲日誌、
Gradle 快取或 Minecraft 資源。
