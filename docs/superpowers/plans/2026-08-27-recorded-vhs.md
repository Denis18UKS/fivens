# Recorded VHS Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace live secondary-world rendering during tape playback with actual pre-recorded PNG frames stored under the world save and played back on the physical TV.

**Architecture:** `VhsRecordingStore` owns validated server-side metadata and frame files. `VhsRecorderClient` renders the saved cutscene once during authoring and uploads PNG frames in bounded chunks; only after a complete recording does the server issue a cassette. `VhsRecordedPlayback` receives/caches stored frames and advances a single dynamic texture by playback clock. Existing physical TV rendering continues to draw a texture on the CRT but no longer calls `WorldRenderer.render(...)` during playback.

**Tech Stack:** Java 17, Fabric 1.20.1 networking/rendering, Minecraft `NativeImage`, Gson, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-08-27-mfl-death-checkpoints-recorded-vhs-design.md`

## Global Constraints

- Default recording: 256x144, 15 FPS.
- Playback uses stored frames only; world changes after recording must not affect the tape.
- Startup static is exactly the existing 30 ticks and has analog hiss/click sound.
- Incomplete/missing tapes are rejected/ejected; no endless static fallback.
- No bundled FFmpeg/native codec dependency.
- Recording/checkpoint authoring requires permission level 2.

---

### Task 1: VHS metadata/policy and recording store

**Files:**
- Create: `src/main/java/ru/fifth/horror/vhs/VhsRecordingPolicy.java`
- Create: `src/test/java/ru/fifth/horror/vhs/VhsRecordingPolicyTest.java`
- Create: `src/main/java/ru/fifth/horror/vhs/VhsRecordingStore.java`

**Interfaces:**
- Produces `frameIndexForTick(int tick,int fps,int frameCount)`, metadata validation, `beginUpload`, `writeFrame`, `finishUpload`, `isComplete`, `metadata`, `readFrame`.

- [ ] Write tests for 15-FPS tick mapping, first/last frame clamping, invalid dimensions/fps/frame counts, and incomplete-recording rejection.
- [ ] Run focused test and confirm RED.
- [ ] Implement policy and filesystem store under `<world>/fiven/vhs/<id>/` with atomic temporary upload directory and final `metadata.json`.
- [ ] Validate max 256x144 default path, fps 1..30, frame count upper bound 18,000, per-frame PNG <= 512 KiB and total recording <= 512 MiB.
- [ ] Re-run focused tests and confirm GREEN.
- [ ] Commit.

### Task 2: Authoring recorder and upload protocol

**Files:**
- Create: `src/main/java/ru/fifth/horror/client/VhsRecorderClient.java`
- Modify: `src/main/java/ru/fifth/horror/network/FifthNetworking.java`
- Modify: `src/main/java/ru/fifth/horror/client/FifthClient.java`
- Modify: `src/main/java/ru/fifth/horror/client/gui/CameraEditorScreen.java`

**Interfaces:**
- Payloads: `vhs_record_begin`, `vhs_record_frame`, `vhs_record_finish`, `vhs_record_ack`.
- Client entry: `VhsRecorderClient.start(CutsceneDefinition,String)`.

- [ ] Change «Получить кассету» into «Записать VHS» authoring flow: save cutscene, request record begin, then close GUI and record progress.
- [ ] Render one off-screen frame for each 15-FPS sample of the saved cutscene using the existing camera/framebuffer helpers; this secondary render is allowed only during explicit authoring, never tape playback.
- [ ] Encode each captured `NativeImage` to PNG bytes and send bounded frame payloads with index.
- [ ] Server stores validated frames and on successful finish gives a cassette whose NBT points to the completed recording id.
- [ ] Show progress/errors to the director; interrupted upload remains incomplete and is never playable.
- [ ] Run full build and commit.

### Task 3: Stored-frame transfer and client cache

**Files:**
- Create: `src/main/java/ru/fifth/horror/client/VhsRecordedPlayback.java`
- Modify: `src/main/java/ru/fifth/horror/network/FifthNetworking.java`
- Modify: `src/main/java/ru/fifth/horror/client/FifthClient.java`
- Modify: `src/main/java/ru/fifth/horror/block/CassetteDriveBlockEntity.java`

**Interfaces:**
- Payloads: `vhs_playback_start`, `vhs_frame_request`, `vhs_frame_data`, `vhs_playback_error`.
- Produces `texture(BlockPos)`, `staticPhase(BlockPos)`, `recordingPhase(BlockPos)`, `error(BlockPos)`, `tick()`.

- [ ] Cassette drive validates `VhsRecordingStore.isComplete(id)` instead of only checking a cutscene JSON.
- [ ] Playback start sends recording metadata; client requests each frame at most once and caches by recording id.
- [ ] Server sends PNG frame bytes only on request and enforces per-player request bounds/rate sanity.
- [ ] Client decodes requested PNG into `NativeImage`, updates a reusable dynamic texture for the current TV frame, and advances by `VhsRecordingPolicy.frameIndexForTick`.
- [ ] Repeated playback in the same client session reuses cached decoded frame bytes/images without re-requesting.
- [ ] Transfer/decode failure sets `TAPE READ ERROR`; it must not re-enter startup static.
- [ ] Run full build and commit.

### Task 4: Remove live playback capture and wire the physical TV renderer

**Files:**
- Modify: `src/main/java/ru/fifth/horror/client/TelevisionRenderer.java`
- Modify: `src/main/java/ru/fifth/horror/client/FifthClient.java`
- Retire playback calls from: `src/main/java/ru/fifth/horror/client/VhsPlayback.java`
- Retire playback calls from: `src/main/java/ru/fifth/horror/client/VhsWorldCapture.java`
- Modify: `src/main/java/ru/fifth/horror/item/VhsCassetteItem.java`

**Interfaces:**
- Television renderer consumes only `VhsRecordedPlayback` for tape sessions.

- [ ] Keep `VhsWorldCapture` only as an authoring helper or move its reusable capture logic into `VhsRecorderClient`; remove `captureNext()` from normal HUD/world playback.
- [ ] TV render order: startup static → stored recorded frame → end/blank; error → `TAPE READ ERROR`.
- [ ] Keep startup static sound at cassette playback start.
- [ ] Update cassette tooltip to say `Записанная VHS: <id>` and distinguish incomplete/legacy cassette behavior.
- [ ] Run `./gradlew clean build --stacktrace --no-daemon`.
- [ ] Inspect production JAR and verify there is no normal playback path that calls `WorldRenderer.render(...)` from `VhsRecordedPlayback`/TV rendering.
- [ ] Commit.
