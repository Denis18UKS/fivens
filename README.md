# Пятый: Horror Director Toolkit

Fabric 1.20.1 toolkit for building cinematic multiplayer horror maps directly in Minecraft.

## Included
- Custom horror title screen for «Пятый» with hidden director-studio access.
- Scriptable NPCs: no AI/script = statue.
- In-game NPC template editor and 64x64 skin editor with base/overlay layer filtering.
- GeckoLib model/animation resource fields and automatic animation discovery on resource reload.
- NPC path authoring tool with persistent NPC selection and indexed runtime lookup.
- FifthScript sandbox computer with PHP-like commands for NPC AI, triggers and layers.
- Cutscene camera keyframes, FOV, subtitles, HUD/control lock and optional end teleport.
- Cutscene library + VHS recording/playback with post-processing and fallback scanlines.
- Structure snapshot variants/layers with default-active, restore-on-load and replacement groups.
- Independent lift floor sets keyed by `liftId:floor`, with per-floor door permissions.
- `/fiven tools` gives all authoring tools.

## Lift floor sets
Each independent lift should have a unique ID in the in-game lift editor. Use the same ID as the floor-set ID when capturing floor layers. This allows multiple lifts to reuse floors 1–9 without overwriting each other.

Old maps that have floor data without a floor-set ID are treated as `default` and remain available as a compatibility fallback.

A blocked door floor is still a valid travel destination: the lift arrives, but the doors stay closed. Floors 2, 5 and 8 are blocked by default.

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

Every push to `main`/`fix/**` and every pull request is validated by the Java 17 Gradle build workflow.
