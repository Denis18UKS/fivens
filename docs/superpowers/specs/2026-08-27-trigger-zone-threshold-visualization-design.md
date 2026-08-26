# Trigger-zone threshold and visualization design

## Scope

Extend the command trigger-zone subsystem already introduced in PR #6. This design does not replace the existing trigger-zone commands or persistence format; it adds multiplayer activation thresholds and director-only client visualization while preserving current ENTER / EXIT / STAY behavior, cooldowns, one-shot zones, enable/disable state, placeholders, multi-command `;;` execution, and per-player command context.

## Player threshold semantics

Each persisted trigger zone gains `minPlayers`, defaulting to `1` for backward compatibility.

Command:

```mcfunction
/fiven trigger players <id> <count>
```

Alias:

```mcfunction
/fiven_trigger players <id> <count>
```

`count` is clamped/validated to at least 1.

Activation uses the number of alive, non-spectator players physically inside the zone in the zone's dimension.

### ENTER

The zone fires when occupancy crosses from below `minPlayers` to at least `minPlayers`.

Example: `minPlayers = 3`: occupancy `2 -> 3` fires once; `3 -> 4` does not fire again. The zone becomes eligible for another ENTER activation only after occupancy first drops below 3 and later reaches 3 or more again.

When the threshold is reached, the zone command is executed for **every player currently inside the zone**, not only for the last player who entered. Therefore a targeted command such as `fiven_cs "intro" @s` starts for the full qualifying group.

### STAY

The zone may fire while occupancy is at least `minPlayers`. Existing per-player cooldown behavior remains in effect. Commands execute for the players currently inside the zone.

### EXIT

EXIT remains an exit event, but it is threshold-gated. The threshold state is derived from the occupancy immediately before/after the exit so the zone does not emit an EXIT activation from a group that never satisfied its configured multiplayer requirement. Commands are executed for the relevant players participating in that qualifying zone state.

### One-shot zones

For a one-shot zone, a successful threshold activation executes the command for the complete target group and only then disables the zone. It must not disable after the first player in the group and skip the others.

## Persistence and compatibility

`minPlayers` is serialized to the existing `fiven/trigger_zones.json` data. Existing worlds with no field load as `minPlayers = 1`.

The existing `info` and `list` output must include the player requirement and current occupancy where meaningful, for example `2/3`.

## Director-only visualization

Visualization is **personal per director/player**, never global. Ordinary players do not receive or render trigger-zone geometry unless they explicitly enable visualization themselves and have permission to use the director command.

Commands:

```mcfunction
/fiven trigger visualize on
/fiven trigger visualize off
/fiven trigger visualize on <id>
/fiven trigger visualize off <id>
```

The same subcommands are available through `/fiven_trigger`.

Semantics:

- `visualize on` shows all trigger zones to the issuing player.
- `visualize off` hides all trigger zones for the issuing player.
- `visualize on <id>` enables visualization of one zone without exposing unrelated zones.
- `visualize off <id>` hides one zone while leaving other selected/all visualization state intact.
- Visualization state is session-local for the director and does not need to be persisted across game restarts.

## Networking

The server is authoritative for zone definitions and occupancy. A dedicated S2C payload sends only the data required by the requesting director:

- zone id;
- dimension id;
- min/max block coordinates;
- mode;
- enabled state;
- current player count;
- minimum player count.

The server sends a full visualization snapshot when visualization is enabled or changed. While visualization is active, it sends lightweight updates when occupancy or zone metadata changes. Disabling visualization sends a clear/reset payload.

No zone command string needs to be exposed to the client renderer.

## Client rendering

A focused client component stores the latest visualization snapshot and renders it during world rendering.

For zones in the player's current dimension:

- render a translucent 3D cuboid outline around the exact trigger volume;
- render the zone id and compact status near the top/center of the volume;
- status text includes mode and occupancy, e.g. `intro | ENTER | 2/3`;
- disabled zones remain distinguishable in the status text and are not mistaken for active gameplay areas;
- rendering is editor/debug-only and must not alter collisions, block rendering, or gameplay state.

The renderer must use world-space coordinates and camera-relative matrices so boxes stay anchored to the zone instead of following the camera.

## Command and UX behavior

Existing creation flow remains:

1. hold `Триггер-зоны команд`;
2. RMB block = A;
3. Shift+RMB block = B;
4. `/fiven trigger create <id> <command>`.

Typical multiplayer setup:

```mcfunction
/fiven trigger create intro fiven_cs "intro_scene" @s
/fiven trigger players intro 3
/fiven trigger visualize on intro
```

When the third qualifying player enters, `intro_scene` starts for all three players currently in the zone.

## Reliability constraints

- Minecraft 1.20.1, Fabric, Java 17.
- Keep PR #6 stacked on PR #5; do not merge either automatically.
- Do not modify `main` directly.
- Existing trigger zones must keep working without migration commands.
- Visualization must never be broadcast to players who did not enable it.
- One-shot group execution must run for the entire group before disabling.
- Server remains authoritative; client visualization is read-only.

## Validation

Before claiming completion:

1. `./gradlew clean build --stacktrace --no-daemon` must pass on Java 17 in GitHub Actions.
2. Verify production JAR contains the new client visualization class, networking payload registration, and updated trigger-zone classes.
3. Test logic cases in code/build where feasible: 1-player compatibility, `2 -> 3` ENTER threshold, no retrigger at `3 -> 4`, re-arm after dropping below threshold, grouped one-shot execution, STAY cooldown, visualization all/single/off state.
4. Runtime Minecraft verification remains required for the actual world-space appearance and multiplayer behavior; CI success alone is not considered visual/runtime proof.
