# MFL Death, Shared Checkpoints, and Recorded VHS Design

## Scope

This change adds three connected runtime systems to Fiven for Fabric 1.20.1 / Java 17:

1. A real MFL capture/death sequence instead of a short screamer pulse.
2. One shared checkpoint for the whole running Fiven game session, with a controlled Fiven-state restart after death.
3. VHS playback that uses frames captured earlier and stored on disk instead of re-rendering the current world while the tape is playing.

The work is stacked on `feature/trigger-zones-commands-mfl-2026-08-27`. It must not merge `main` automatically.

## MFL capture/death sequence

### State machine

MFL gains an explicit capture state separate from normal hunt/path navigation.

- `CHASE`: existing logical chase/search behavior.
- `CAPTURED`: navigation is stopped, one target UUID is held by the MFL, ordinary AI/path updates are suspended, and the victim cannot move/interact.
- `DEATH_SCENE`: the configured death cutscene is playing for the captured victim.
- `RESTART_PENDING`: the server waits for the authoritative scene duration, then resolves death/restart exactly once.

A second capture cannot start while an MFL already has a captured target.

### Animation

`mfl_screamer` is not looped. It is played once and held on its last pose until the death sequence resolves. The GeckoLib action animation therefore uses play-and-hold semantics rather than a loop.

The normal `main` locomotion controller continues to own `idle`, `walking`, and `run`; capture owns the separate `action` controller. During capture the MFL navigation is stopped and locomotion is forced to idle so movement cannot fight the death action.

### Victim handling

When MFL catches a valid survival/adventure player:

1. Stop MFL navigation and enter capture state.
2. Freeze the victim server-side at the capture position and send a client input-lock state.
3. Play `mfl_screamer` once-and-hold and the existing screamer audiovisual effect.
4. Start the configured death cutscene for only that victim. Default cutscene id: `mfl_death`. A per-MFL death cutscene id can override it.
5. Use the server-side cutscene duration as the authoritative timer. Client completion is not trusted for game-state changes.
6. At the end, resolve the death once. If a Fiven game session is running and a shared checkpoint exists, perform a checkpoint restart. Otherwise kill the captured player normally.
7. Clear capture/input-lock state and release the MFL only after death/restart resolution.

Creative/spectator players remain invalid normal hunt targets. The existing director chase-test may still target Creative for navigation testing, but death resolution is suppressed for a Creative test target.

## Shared checkpoint system

### Checkpoint definition

A checkpoint stores:

- id;
- dimension id;
- player anchor position;
- yaw and pitch;
- the Fiven runtime snapshot captured when it becomes active.

There is exactly one active checkpoint for the whole running game session, shared by all players.

Checkpoint definitions and the current active id are persisted under `world/fiven/checkpoints.json`.

### Director tool

Add a one-stack item `checkpoint_tool` to the Fiven director tab and `/fiven tools` set.

- Normal RMB on a block creates/updates a checkpoint at the hit location using a generated/default id when none is selected.
- Sneak+RMB activates the clicked/nearest saved checkpoint as the shared checkpoint.
- Item tooltip explains the workflow.

The command API is the authoritative management surface:

- `/fiven checkpoint set <id>` — save/update at the director's current position and orientation.
- `/fiven checkpoint activate <id>` — make it the single shared checkpoint and capture a Fiven-state snapshot.
- `/fiven checkpoint current` — show active shared checkpoint.
- `/fiven checkpoint list` — list definitions.
- `/fiven checkpoint delete <id>` — delete a definition; clears active id if needed.
- `/fiven checkpoint respawn` — manually run the same restart path used by MFL death.
- `/fiven checkpoint visualize on|off` — personal director visualization only.

### Fiven-state snapshot (variant B)

Checkpoint restart is intentionally not a full world backup. It restores Fiven-owned authored/runtime state only:

- all non-spectator players: teleport to the shared checkpoint anchor, reset velocity, fire/fall state, health, hunger, active screen/cutscene/input locks;
- all MFL entities: AI mode/hunt/patrol, route runtime state, chase/capture state, current animation reset to idle;
- all Director NPCs: AI/path runtime state and position/orientation snapshot;
- trigger zones: enabled/one-shot runtime state and armed/cooldown runtime state;
- lifts: current/target floor, door/open state and selected floor-layer state;
- Fiven cutscene/screamer/playback runtime: stopped/cleared;
- Fiven script runtime variables that are owned by the engine and can be serialized through an explicit snapshot API;
- active StructureLayer selections for Fiven-managed layer groups.

The system does not copy arbitrary vanilla blocks or unrelated entities. Map areas that need block restoration must continue to use StructureLayer variants; the checkpoint snapshot records which Fiven-managed layer variant was active and re-activates it on restart.

If a subsystem has no serializable runtime state today, the checkpoint manager exposes a focused snapshot hook rather than reaching into private internals. Missing optional authored entities are skipped with a warning rather than crashing restart.

### Restart ordering

Restart executes server-side in this order:

1. Guard against concurrent/reentrant restart.
2. Stop all MFL captures/chases and client death/cutscene locks.
3. Stop Fiven cutscene/screamer/VHS runtime sessions.
4. Restore StructureLayer active selections.
5. Restore lift/NPC/MFL/trigger/script snapshots.
6. Teleport/reset all non-spectator players to the shared checkpoint anchor.
7. Emit a `checkpoint_restart` FifthScript event after the restored state is stable.
8. Release restart guard.

## Recorded VHS

### Current behavior to remove from playback

The current `VhsPlayback` stores a cutscene timeline and `VhsWorldCapture` renders the current world from that timeline during tape playback. That is not a recording and is the source of the current failure mode where only static/fallback appears.

Playback must not invoke a secondary `WorldRenderer.render(...)` at all.

### Recording format

A VHS recording is stored under:

`world/fiven/vhs/<recording-id>/`

with:

- `metadata.json` — id, width, height, fps, frame count, duration ticks, optional subtitles;
- `frame_00000.png`, `frame_00001.png`, ... — actual captured image frames.

Default format is 256x144 at 15 FPS. Frames are captured ahead of playback while the author explicitly records the saved cutscene. PNG is used to avoid bundling FFmpeg/native codecs and to keep the mod self-contained.

### Recording workflow

Creating a cassette from the camera/cutscene editor becomes a two-stage authoring operation:

1. Save the cutscene definition.
2. Record/render the cutscene once on the director client into the normal off-screen capture framebuffer and upload/store each PNG frame to the server/world save through bounded chunk packets.
3. After the server has a complete validated frame set and metadata, issue the VHS cassette referring to that recording id.

The editor reports recording progress and does not give a 'recorded' cassette until the server confirms completion. Interrupted/incomplete recordings are marked incomplete and cannot play.

For local integrated-server play, the same network protocol is used so multiplayer and single-player behavior remain identical.

### Playback workflow

When a cassette is inserted:

1. Validate linked physical TV and complete recording metadata.
2. Play the existing ~1.5 second startup static with analog hiss/click sound.
3. Server announces recording id, TV position, fps, and frame count to nearby clients.
4. Client requests/receives cached PNG frame data for that recording and uploads one reusable dynamic texture as frames advance.
5. Television renderer draws only the stored frame texture on the CRT surface.
6. World changes after recording have no effect on the tape.
7. At end, the last frame may hold briefly, then playback clears.

Frame transfer is chunked and bounded. Clients cache a recording for the session by recording id so repeated playback does not resend all frames.

### Failure behavior

- Missing/incomplete recording: cassette drive rejects/ejects the tape with a clear message/sound.
- Transfer failure: TV shows `TAPE READ ERROR`, never endless startup static.
- Startup static is only the first 30 ticks and always has the corresponding analog sound.
- Playback errors are logged under `Fiven/VHS` with recording id and TV position.

## Networking

Add explicit payloads for:

- capture/input lock state;
- checkpoint visualization snapshot and clear;
- checkpoint restart client reset;
- VHS recording begin/frame chunk/finish/ack;
- VHS playback metadata/frame request/frame chunk/error.

All authoring/upload/checkpoint management actions require permission level 2. Server validates recording ids, frame indexes, dimensions, maximum PNG size, total frame count, and total recording bytes.

## Testing and validation

Pure policy/state classes must have JUnit 5 tests for:

- MFL capture state transitions and exactly-once death resolution;
- shared checkpoint selection and restart reentrancy guard;
- VHS metadata validation, frame indexing and incomplete-recording rejection;
- playback clock mapping from Minecraft ticks to 15 FPS frame indexes.

Full CI command: `./gradlew clean build --stacktrace --no-daemon` on Java 17.

Production JAR must be inspected to confirm new classes/resources/network entrypoints are present after remapping. CI/build success does not count as visual Minecraft runtime confirmation; actual in-game death sequence, checkpoint restoration and TV frame rendering still require an in-game test.