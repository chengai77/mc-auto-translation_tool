# Compatibility matrix

[简体中文](../Zh-cn/COMPATIBILITY.md) · [繁體中文](../Zh-tw/COMPATIBILITY.md) · [English](COMPATIBILITY.md) · [Back to English README](README.md)

This document records only completed validation. “Builds successfully” is not reported as
“compatible.”

## Development targets

| Minecraft | Loader | Java | Build and self-test | Launched to main menu | Full manual in-server regression |
| --- | --- | --- | --- | --- | --- |
| 26.1 | Fabric Loader 0.19.3 + Fabric API 0.145.1 | 25 | Passed | Passed | Pending |
| 26.2 | Fabric Loader 0.19.3 + Fabric API 0.157.0 | 25 | Passed | Pending | Pending |
| 26.3 | Fabric Loader 0.19.5 + Fabric API 0.161.0 | 25 | Passed | Pending | Pending |
| 1.21.10 | Fabric Loader 0.18.1 + Fabric API 0.138.4 | 21 | Passed | Pending | Pending |
| 1.18.1 | Fabric Loader 0.16.10 + Fabric API 0.46.6+1.18 | 17 | Passed | Pending | Pending |
| 1.16.5 | Fabric Loader 0.16.10 + Fabric API 0.42.0+1.16 | 8 | Passed | Passed | Pending |

Fabric 1.16.5 uses legacy Loom with a single `src/main` source set (that version has no separate
server JAR) and Yarn `1.16.5+build.10`; its screens use `MatrixStack`, `Screen#onClose`/`isPauseScreen`,
`TextFieldWidget`, and `ButtonWidget.TooltipSupplier` instead of the modern equivalents. It passed a
clean build, remapping, and the shared-core self-test, and it was actually launched to the main menu
with no Mixin injection failures. Because of the 1.16.5 API limits, hologram text (TextDisplay)
translation is not provided, and the version ships Fabric only, with no Forge/NeoForge project.
Fabric 1.21.10 has a complete Fabric feature adaptation using the same 49 source files as 1.21.11,
with Yarn `1.21.10+build.3` and Fabric API `0.138.4+1.21.10`; it now compiles and remaps cleanly on
this machine, with launch and in-server regression still pending.
Fabric 1.18.1, like 1.17.1, uses legacy Loom with a single `src/main` source set, Yarn
`1.18.1+build.22`, and Fabric API `0.46.6+1.18`; it passed a clean build, remapping, and the shared
core self-test, and all 16 Mixin injection targets plus their `@Shadow` fields were checked against
the mapped 1.18.1 class files. It is Fabric-only as well and does not provide hologram text
(TextDisplay) translation because 1.18 has no TextDisplay API; launch and in-server regression are
still pending. Fabric 26.1 completed a clean build, Mixin injection checks, and an actual launch to the main menu.
Fabric 26.2 has been migrated to the new `Minecraft.gui` and `Hud` APIs, and its Mixin targets for
chat, scoreboards, the Tab list, titles, the Action Bar, Boss Bars, text drawing, and tooltips were
checked. For 26.2, only clean build, remapping, and shared-core self-test results are recorded so far.
Fabric 26.3 builds on 26.2 by adapting to the removal of GLFW in input constants and to the changed
`SignText` message list return type; it passed a clean build, remapping, a check of every Mixin
target class, and the shared-core self-test. Fabric is the only platform provided for 26.3, and no
Forge/NeoForge project exists for it yet. None of these targets is released until its manual
in-server regression is complete.

## Version 1.1 release validation

Version 1.1 received clean builds, remapping, and shared-core self-tests for its three target
versions. This update focused on grouped item-name and Lore translation, download progress,
failure messages, and API fallback status. Manual in-server compatibility still uses the 1.0
launch results as its baseline; the automated builds are not presented as new hands-on validation.

## Version 1.0 verified targets

| Minecraft | Loader | Java | Build and self-test | Launched to main menu | Full manual in-server regression |
| --- | --- | --- | --- | --- | --- |
| 1.8.9 | Forge 11.15.1.2318 | 8 | Passed | Passed | Awaiting the next hands-on confirmation round |
| 1.12.2 | Forge 14.23.5.2864 | 8 | Passed | Passed | Awaiting the next hands-on confirmation round |
| 1.21.11 | Fabric Loader 0.19.3 + Fabric API 0.141.6 | 21 | Passed | Passed | Awaiting the next hands-on confirmation round |

All three builds share the same translation core. A real Qwen2.5 0.5B offline-model probe verified
English translation, mixed Chinese/English segmentation, and local protection and reconstruction
of player names, server addresses, and numbers. Version 1.0 also confirmed that both Forge versions
installed the general tooltip and item-generation tooltip hooks, while the standard item tooltip
Mixin for Fabric 1.21.11 produced no injection errors with translation enabled. The final visual
appearance of item names and Lore remains part of the manual in-server regression.

## The principle behind “support every version”

Minecraft generations use different loaders, Java versions, rendering APIs, and text-component
structures. One JAR cannot safely cover every Java Edition version. The project adapts
representative, long-lived versions individually and keeps a separate JAR for each release line.
A target enters the verified table only after build, launch, and in-server interface regression.

Planned candidates, in order:

1. 1.12.2 Cleanroom;
2. 1.7.10 Forge;
3. 1.16.5 Fabric (completed, see the table above); the Forge side is still to be evaluated;
4. 1.18.2 Forge/Fabric;
5. 1.20.1 Forge/Fabric;
6. 1.21.1 Fabric/NeoForge;
7. later releases that retain a substantial player base.

Work on those versions will not begin until the 1.8.9, 1.12.2, 1.16.5, 1.21.10, 1.21.11, 26.1, 26.2, and 26.3 in-server regressions
all pass, avoiding the introduction of more unverified rendering differences at the same time.

## In-server regression checklist

- chat, scoreboard, Tab list, Action Bar, titles, and Boss Bar;
- chest/menu titles, item names and Lore, signs, books, entity names, and holographic text;
- text entered and sent by the player remains unchanged;
- online player names, the local player name, and server IP/domain/port remain unchanged;
- existing Chinese is not translated again, while English fragments can be translated;
- translated-only mode does not overflow with bilingual text, and color options work;
- the `U` control panel and `F8` master toggle work at different window sizes;
- the game stays responsive and keeps the original text while a model downloads or when a download or offline engine fails.
