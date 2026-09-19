# MC Auto Translation Tool

[简体中文](../Zh-cn/README.md) · [繁體中文](../Zh-tw/README.md) · [English](README.md) · [Repository home](../../README.md)

A charity-driven, open-source, client-only full-interface translation mod for Minecraft Java Edition.

[⬇️ Download the latest release](https://github.com/chengai77/mc-auto-translation_tool/releases) ·
[🌐 Official download page](https://github.com/wuxiangdan96-byte/mc-auto-translation-tool/releases) ·
[📚 Language directory](../README.md) · [📖 Installation and usage guide](USER_GUIDE.md)

Original author: [Bilibili creator “我小张7272635”](https://space.bilibili.com/3546631091783712).
Please retain the original author attribution and MIT License copyright notice when redistributing,
republishing, or adapting this project.

The project aims to translate visible text sent to players by servers, including chat, scoreboards,
the Tab player list, Action Bar messages, titles, Boss Bars, container titles, item names and lore,
signs, books, holograms, and custom entity names. Player names, numbers, URLs, and Minecraft style
codes are preserved by default.

## Downloads

We recommend downloading the latest version from [GitHub Releases](https://github.com/chengai77/mc-auto-translation_tool/releases).
Make sure that the file exactly matches your Minecraft version and mod loader:

| Minecraft | Loader | Download |
| --- | --- | --- |
| 1.8.9 | Forge | [Download JAR](https://github.com/chengai77/mc-auto-translation_tool/releases/download/v2026-08-25/mc-auto-translation-tool-forge-1.8.9-1.1.jar) |
| 1.12.2 | Forge | [Download JAR](https://github.com/chengai77/mc-auto-translation_tool/releases/download/v2026-08-25/mc-auto-translation-tool-forge-1.12.2-1.1.jar) |
| 1.21.11 | Fabric | [Download JAR](https://github.com/chengai77/mc-auto-translation_tool/releases/download/v2026-08-25/mc-auto-translation-tool-fabric-1.21.11-1.1.jar) |
| 1.21.10 | Fabric | Development adaptation; JAR not released yet |
| 1.16.5 | Fabric | Development adaptation; JAR not released yet |

[View all releases and release notes](https://github.com/chengai77/mc-auto-translation_tool/releases) ·
[SHA-256 checksum file](https://github.com/chengai77/mc-auto-translation_tool/releases/download/v1.1/SHA256SUMS.txt)

## Design principles

- The server does not need to install the mod.
- The default provider is an offline model running on the user's computer; no API key or project server is required.
- Offline mode binds only to `127.0.0.1`, so server text does not leave the user's computer.
- LibreTranslate, a Tencent-compatible interface, and OpenAI-compatible LLM APIs are available; API fallback after an offline failure is disabled by default.
- Optional outgoing translation translates normal chat in the background and preserves send order; commands remain unchanged.
- Translation runs in the background. If the service is unavailable, the original text is retained immediately without affecting gameplay.
- Identical text and dynamic text templates use a local cache to reduce latency and cost.
- Player names, coordinates, numbers, URLs, and formatting codes are not translated by default.
- Users can prevent private chat or other sensitive content from being sent externally on a per-server basis.

## Version modules

| Minecraft | Loader | Java |
| --- | --- | --- |
| 26.3 | Fabric | 25 |
| 26.2 | Fabric | 25 |
| 26.1 | Fabric | 25 |
| 1.21.11 | Fabric | 21 |
| 1.21.10 | Fabric | 21 |
| 1.18.1 | Fabric | 17 |
| 1.16.5 | Fabric | 8 |
| 1.12.2 | Forge | 8 |
| 1.8.9 | Forge | 8 |

Each game version produces a separate JAR while sharing the same core logic and configuration semantics.

The main branch also contains Fabric 1.16.5, 1.18.1, 1.21.10, 26.1, 26.2, and 26.3 development adaptations;
1.16.5 uses Java 8, 1.18.1 uses Java 17, 1.21.10 uses Java 21, and 26.x uses Java 25. Fabric 1.16.5 is Fabric-only, has
passed a main-menu launch check, and does not provide hologram text (TextDisplay) translation because
that API does not exist in 1.16.5; Fabric 1.18.1 is Fabric-only as well and has passed a clean build,
remapping, and the shared core self-test. Fabric 26.2 and 26.3 have passed clean builds, reobfuscation, and
shared core self-tests; they are not listed in the official download table above until real-game
startup and in-server regression are complete.

## Current status

Release 1.1 provides three separate client JARs. All three versions passed compilation,
reobfuscation, and the shared core self-tests. The real-game startup checks completed before
release 1.0 remain the compatibility baseline:

- Fabric 1.21.11;
- Forge 1.12.2;
- Forge 1.8.9.

Press `U` in game to open the settings screen. The mod is disabled by default. New installations
default to the offline provider and translated-only replacement mode, which avoids overflowing
scoreboards and container titles with bilingual text. Press `F8` to toggle translation at any time.
Both shortcuts can be changed in Minecraft's key-binding screen. After the first translatable text
appears, the mod downloads a platform engine of about 10–17 MB and the 491 MB Lite model in the
background. Original text remains visible during the download. Model downloads prefer the
ModelScope mirror for users in China, resume automatically after a failure, and fall back to the
official source. Files are used only after their size and SHA-256 checksum have been verified.
LibreTranslate and the legacy Tencent-compatible mode also remain available.
About three seconds after joining a server, the chat panel displays a local-only `U`/`F8` hint.
It does not send any chat message or packet to the server.

Verified behavior includes:

- Preserving player names, server IP addresses and domains, ports, color codes, numbers, percentages, and URLs.
- Separating protected content locally so it is never sent to the offline model or an online API.
- Normalizing dynamic scoreboard content into reusable templates.
- Caching translations and coalescing identical concurrent requests.
- Skipping network access for text that is already in the target language or contains only numbers.
- Translating only English segments in mixed Chinese-English text while preserving existing Chinese text.
- Keeping the original color by default, or applying a separately selected aqua, green, gold, or other color.
- Returning the original text when the translation service fails.
- Keeping background translation off the render thread.
- Applying saved settings without restarting the game.
- Allowing external transmission to be disabled independently for chat and other interfaces.
- Keeping outgoing translation disabled by default, with a target language separate from interface translation.
- Installing the offline Lite and Quality models on demand instead of bundling them in the mod JAR.

## Privacy notice

Offline mode does not send server text anywhere. Online API mode or “API fallback” means that
selected server text may be sent to the translation service configured by the user. The project
provides a clear master switch, separate switches for chat and other content, and a local cache.
API keys remain in the user's local configuration and must never be committed to the repository.
Remote endpoints must use HTTPS; HTTP is allowed only for exact local loopback addresses.

See the [user guide](USER_GUIDE.md) for detailed installation and usage instructions, and the
[compatibility matrix](COMPATIBILITY.md) for the verified scope and planned version order. The
website source is in `../../website/`.
