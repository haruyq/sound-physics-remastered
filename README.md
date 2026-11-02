# Sound Physics Remastered

This is an unofficial Sable compatibility build for Minecraft 1.21.1 / NeoForge, based on [henkelmax's Sound Physics Remastered](https://github.com/henkelmax/sound-physics-remastered/tree/1.21.1).

Replace the original Sound Physics Remastered jar with this build in the client's `mods` folder. Do not install both jars. Sable is optional; its supported version range is declared in `neoforge/src/main/resources/META-INF/neoforge.mods.toml`.

Sable structures participate in sound occlusion, material absorption, and rotated reflections. Block snapshots follow `level_clone_range`; ship poses and sparse block snapshots update every client tick. Sound processing respects `update_moving_sounds`: when disabled, sounds are evaluated when they start, and music discs retain the upstream bypass. Enable it to refresh already-playing sounds at `sound_update_interval` as walls move; frequent updates increase CPU usage. Integrations that explicitly call `processSound` can still update their own streams.

Build and run the focused tests with `gradlew :neoforge:build --configure-on-demand`. Run the Sable assembly/rotation integration test with `gradlew :neoforge:runGameTestServer -PsableTests --configure-on-demand`. Run the client startup check with `gradlew :neoforge:runClient -PclientSmokeTest --configure-on-demand`, adding `-PsableTests` to load Sable; the client exits after reaching its initial screen. The upstream build plugins require JDK 25 to run Gradle; the mod itself targets Java 21. Build the distributable without either test property to exclude test classes and resources.

Add `-PsableBenchmark` to the Sable integration test to measure snapshot copying and a 256-block ray through a two-block rotated ship. It reports time and thread allocations per operation; these measurements do not predict in-game FPS.

> This is a fork of [Sound Physics Fabric](https://github.com/vlad2305m/Sound-Physics-Fabric) by [vlad2305m](https://github.com/vlad2305m) which is a fork of [Sound Physics](https://github.com/sonicether/Sound-Physics) by [Sonic Ether](https://github.com/sonicether).

A Minecraft mod that provides realistic sound attenuation, reverberation, and absorption through blocks.

Optimized for the use with [Simple Voice Chat](https://www.curseforge.com/minecraft/mc-mods/simple-voice-chat) 2.X.X.


### Requirements
This mod does not require any dependencies, but it is recommended to use the following:

**Fabric**

- [ModMenu](https://modrinth.com/mod/modmenu)
- [ClothConfig](https://modrinth.com/mod/cloth-config)

**Forge**

- [ClothConfig](https://modrinth.com/mod/cloth-config)

---

### Changes to the Original Mod

**Improvements**

- Improved sound processing performance by a factor of 10
- Optimized for [Simple Voice Chat](https://modrinth.com/mod/simple-voice-chat)
- Ported the mod to Forge
- Improved configuration UI
- Made Cloth Config optional
- Tweaked default config values
- Added reflectivity to the config GUI
- Added occlusion variation
- Added block occlusion factor config
- Added debug sound bounce rendering
- Added debug sound occlusion rendering

**Bugfixes**

- Fixed sounds not bouncing more than once
- Fixed sound bouncing using an excessive amount of performance, despite not working at all
- Fixed direction evaluation not taking sound source and primary bounces into account
- Fixed sound allowance not working for `/playsound` command
- Fixed unmapped field names in the reflectivity config
- Fixed sound processing using player position instead of camera position
