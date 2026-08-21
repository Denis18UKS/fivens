# Пятый: Horror Director Toolkit

Fabric 1.20.1 toolkit for building cinematic multiplayer horror maps directly in Minecraft.

## Included
- Custom horror title screen for «Пятый» with hidden director-studio access.
- Scriptable NPCs: no AI/script = statue.
- In-game NPC template editor and 64x64 skin editor with base/overlay layer filtering.
- GeckoLib model/animation resource fields and automatic animation discovery on resource reload.
- NPC path authoring tool with persistent NPC selection and indexed runtime lookup.
- FifthScript sandbox computer with PHP-like commands for NPC AI, triggers and layers.
- Cutscene camera keyframes, FOV, subtitles, HUD/control lock, optional end teleport and VHS cassette creation.
- Cutscene library + VHS recording/playback with post-processing and fallback scanlines.
- Structure snapshot variants/layers with default-active, restore-on-load and replacement groups.
- Physical lift `Block + BlockEntity`, wall-mounted floor panels, independent floor sets keyed by `liftId:floor`, and per-floor door permissions.
- World-space entity horror effects, MFL animation editor, animation conditions and screamer actions.
- `/fiven tools` gives all authoring tools.

## GUI tools: RMB-first workflow
Every Fiven item whose main purpose is a GUI opens its interface with right click while held.

Editors that need a target (NPC, MFL, animation-condition entity, lift, lift panel or TV) open a searchable nearby-target selector when right-clicked in the air. Direct right-click on a concrete NPC/entity/lift/panel still opens that target immediately where applicable.

Tools whose purpose is direct world editing rather than a GUI (path points, linking, stone-button binding, etc.) keep their world interaction behavior.

## Lift floor sets
New lifts are physical blocks. Each independent lift should have a unique ID in the in-game Lift Editor. Use the same ID as the floor-set ID when capturing floor layers. This allows multiple lifts to reuse floors 1–9 without overwriting each other.

Old maps that have floor data without a floor-set ID are treated as `default` and remain available as a compatibility fallback. Legacy entity lift/panel registrations are retained only so older worlds can load and be migrated.

A blocked-door floor is still a valid travel destination: the lift arrives, but the doors stay closed. Floors 2, 5 and 8 are blocked by default.

A vanilla stone button can be bound to a physical lift with Lift Button Binder. Bound buttons are handled by Fiven instead of acting as ordinary redstone switches.

## FifthScript examples
```php
npc("silvi")->startAi();
npc("silvi")->followPath(true, 0.27);
npc("silvi")->animation("animation.npc.walk");
scene("elevator_intro")->play();
layer("house", "destroyed")->activate();
setFlag("power_off", true);
```

Scripts are sandboxed: they can call only the map API exposed by the mod.

## Build
Requires Java 17.

```bash
./gradlew clean build
```

Windows:

```bat
gradlew.bat clean build
```

Every pull request is validated by the Java 17 Gradle build workflow and publishes the built JARs as the `fivens-build` workflow artifact after a successful build.
