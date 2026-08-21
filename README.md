# Пятый: Horror Director Toolkit

Fabric 1.20.1 toolkit for building cinematic multiplayer horror maps directly in Minecraft.

## Included
- Custom horror title screen for «Пятый» with hidden director-studio access.
- Scriptable NPCs: no AI/script = statue; routes, start/stop state, scripts and animation control are authored in-game.
- In-game NPC editor with automatic GeckoLib animation discovery and a live 64×64 PNG skin editor: pencil, line, eraser, color picker, undo, 3D preview, PNG export and server persistence.
- FifthScript sandbox computer with PHP-like commands for NPC AI, triggers, layers and cutscene playback.
- Cutscene camera keyframes, FOV, subtitles, HUD/control lock, optional end teleport, searchable cutscene library and VHS cassette creation.
- Physical VHS workflow: cassette-drive insert/eject transitions, required TV link, static/no-signal phase and TV-only playback. During playback a separate recorded camera renders the Minecraft world into a 256×144 off-screen framebuffer; the resulting VHS-processed texture is mapped only to the television screen. A safe timeline fallback is kept for GPUs/drivers that reject secondary world rendering.
- Physical lift Block + BlockEntity with independent `liftId:floor` sets, wall floor panels, vanilla stone-button calls, per-floor door permissions, red floor travel overlay and persistent NORMAL/CURSED mode.
- Cursed-lift arrival events can bind a saved cutscene, FifthScript scenario and/or named trigger to a particular floor; manual paranormal test events are also available.
- MFL with explicit `OFF / LOGICAL / SCRIPTED` AI, no random wandering, player-only hunt/search/chase, vision settings, routes, screamers and authored hiding volumes for cupboards/closets.
- Manual MFL `walking` / `run` previews now drive real Minecraft navigation instead of playing the animation while standing still.
- Director-only MFL runtime tests provide an explicit screamer command and a reversible chase-test mode that can target Creative players without changing the saved authored AI state.
- Structure snapshot variants/layers with default-active and restore-on-load behavior.
- World-space entity horror effects with protected entries that can survive bulk clearing.
- Decorative `clock_arms` wall block and the complete director-tool creative tab.
- `/fiven tools` gives the complete authoring tool set, including MFL hiding-zone authoring and clock arms.

## GUI tools: RMB-first workflow
Every Fiven item whose main purpose is a GUI opens its interface with right click while held. Editors that need a target open a searchable nearby-target selector when right-clicked in the air; direct right-click on a concrete NPC/entity/lift/panel still opens that target immediately where applicable.

World-authoring tools such as paths, linking, MFL hiding-zone selection and stone-button binding keep their direct in-world behavior.

## Lift floor sets
New lifts are physical blocks. Give every independent lift a unique ID in the Lift Editor and use the same ID as the floor-set ID when capturing floor layers. Multiple lifts can therefore reuse floors 1–9 without overwriting each other.

Old floor data without a set ID is treated as `default` for compatibility. A blocked-door floor remains a valid destination: the lift arrives but its doors do not open. Floors 2, 5 and 8 are blocked by default.

A vanilla stone button can be linked to a physical lift with Lift Button Binder. The binder has explicit floor selection; bound buttons are handled by Fiven instead of acting as normal redstone switches.

### Cursed lift authoring
Near the physical lift:

```text
/fiven lift-type normal
/fiven lift-type cursed
/fiven lift-event bind-cutscene <1-9> <cutscene_id>
/fiven lift-event bind-script <1-9> <script_name>
/fiven lift-event bind-trigger <1-9> <trigger_name>
/fiven lift-event status <1-9>
/fiven lift-event clear <1-9>
/fiven lift-event slam
/fiven lift-event screamer
/fiven lift-event wrong-floor
```

Configured arrival events are deterministic: they run only after the requested destination layer has actually been restored and the cursed lift has completed the ride.

## MFL hiding zones
Use `MFL Hiding Zones` in-world:
- first RMB selects point A;
- second RMB selects point B and saves the hiding volume;
- Shift+RMB inside a saved zone removes it.

A survival/adventure player whose body is inside one of these volumes is ignored by MFL's LOGICAL target acquisition. Zones are stored per world/dimension.

Useful commands:

```text
/fiven hiding-zones status
/fiven hiding-zones clear
```

## MFL runtime tests
All commands use the nearest living MFL within 64 blocks of the command player.

```text
/fiven mfl screamer
/fiven mfl chase-test start
/fiven mfl chase-test start <player>
/fiven mfl chase-test status
/fiven mfl chase-test stop
```

`/fiven mfl screamer` plays the actual `mfl_screamer` animation on the MFL and shows the screamer to the command player even when the director is in Creative.

`chase-test start` temporarily pauses the MFL's authored AI state and drives a real navigation chase toward the selected player. It supports Creative targets for testing, uses the normal run/walk speeds, respects MFL hiding zones, remembers the last visible player position and switches from `run` to search/walking after line of sight is lost. `stop` restores the previous AI mode, hunt and patrol flags.

Manual animation preview behavior:
- `walking` / `walk` = MFL physically walks forward using path navigation;
- `run` / `running` = MFL physically runs forward using path navigation;
- idle/look/hand/screamer animations do not create locomotion on their own.

## Director utility commands
```text
/fiven tools
/fiven reload-scripts
/fiven restore-defaults
/fiven shader clear
/tpe <x> <y> <z> [yaw] [pitch]
/lift <1-9>
/fiven animation play <target> <animation>
/fiven animation stop <target>
/fiven animation condition <id>
/fivenanim ...
```

`/fiven shader clear` removes Fiven entity effects except entries explicitly protected in the entity-effect editor.

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

Every pull request is validated by the Java 17 Gradle build workflow. On success the built JARs are published as the `fivens-build` GitHub Actions artifact.
