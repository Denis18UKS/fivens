# Пятый: Horror Director Toolkit

Fabric 1.20.1 toolkit for building cinematic multiplayer horror maps directly in Minecraft.

## Included
- Custom horror title screen for «Пятый».
- Scriptable NPCs: no AI/script = statue.
- In-game NPC template editor and 64x64 skin editor with base/overlay layer filtering.
- GeckoLib model/animation resource fields and automatic animation discovery on resource reload.
- NPC path authoring tool.
- FifthScript sandbox computer with PHP-like commands for NPC AI, triggers and layers.
- Cutscene camera keyframes, FOV/HUD/control lock playback.
- Structure snapshot variants/layers with default-active, restore-on-load and replacement groups.
- `/fiven tools` gives all authoring tools.

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
