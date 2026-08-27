# Real Video VHS + Lift Overlay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace PNG-frame VHS playback with real encoded video assets using bundled JavaCV/FFmpeg, add external video import with positional audio, and make lift travel render as a full-screen, F1-proof cutscene overlay.

**Architecture:** Server-side `VideoAssetStore` persists validated media files and metadata under `world/fiven/videos/<id>/`, handles sequential chunk uploads/downloads, SHA-256 verification, and legacy VHS rejection without loading native FFmpeg. Client-side JavaCV records Fiven cutscenes to MP4, validates/imports user media, keeps a hash-addressed cache, decodes video/audio off-thread, and exposes one dynamic CRT texture plus positional OpenAL audio per TV session. Lift travel leaves the ordinary HUD callback and is rendered from `InGameHudMixin` after vanilla HUD processing with a reset GUI matrix/scissor, so the overlay fully covers the world and remains visible when F1 hides vanilla HUD.

**Tech Stack:** Java 17, Fabric 1.20.1, Fabric Networking, JavaCV/JavaCPP FFmpeg Windows x86_64 natives, LWJGL TinyFileDialogs/OpenAL, Gson, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-08-27-real-video-vhs-design.md`

## Global Constraints

- Stay on `feature/mfl-death-checkpoints-recorded-vhs-2026-08-27` / PR #7; never merge or modify `main`.
- Server code must not initialize FFmpeg native classes.
- New cutscene VHS: one MP4 file, 640x360, 30 FPS, video-only in this iteration.
- Imported `.mp4/.mov/.mkv/.webm/.avi/.m4v` keeps its original media bytes and audio.
- Asset size limit: 512 MiB; network chunks: 64 KiB; sequential offsets only; SHA-256 verified before publish/cache use.
- Startup static: 30 ticks, only after local media is ready.
- Legacy PNG-folder VHS remains on disk but is rejected with `LEGACY VHS: перезапишите кассету`.
- `/fiven tv test` stays independent of stored media.
- Lift travel must show only the lift cutscene/overlay over the complete screen and must remain visible with F1.

---

### Task 1: Pure policies and RED tests

**Files:**
- Create: `src/main/java/ru/fifth/horror/video/VideoAssetPolicy.java`
- Create: `src/main/java/ru/fifth/horror/video/VideoPlaybackPolicy.java`
- Create: `src/main/java/ru/fifth/horror/client/LiftTravelHudPolicy.java`
- Create: `src/test/java/ru/fifth/horror/video/VideoAssetPolicyTest.java`
- Create: `src/test/java/ru/fifth/horror/video/VideoPlaybackPolicyTest.java`
- Create: `src/test/java/ru/fifth/horror/client/LiftTravelHudPolicyTest.java`

**Interfaces:**
- `VideoAssetPolicy.safeId(String)`, `allowedExtension(String)`, `validDeclaredSize(long)`, `validChunk(int)`, `nextOffset(long,int)`, `sha256(Path)`.
- `VideoPlaybackPolicy.State { PREPARING, STATIC, PLAYING, ENDED, ERROR }` plus pure transition helpers.
- `LiftTravelHudPolicy.cancelVanillaHud(boolean cutsceneHideHud, boolean liftActive)`.

- [ ] Write tests first for safe ids/extensions/size/chunk bounds, playback `PREPARING -> STATIC -> PLAYING -> ENDED`, and lift-active overriding HUD cancellation.
- [ ] Push RED tests and confirm CI fails because production classes do not exist.
- [ ] Implement only enough pure code to make those tests GREEN.
- [ ] Re-run CI and confirm tests pass.

### Task 2: Server `VideoAssetStore` and chunk protocol

**Files:**
- Create: `src/main/java/ru/fifth/horror/video/VideoAssetStore.java`
- Create: `src/main/java/ru/fifth/horror/video/VideoFeature.java`
- Create: `src/test/java/ru/fifth/horror/video/VideoAssetStoreTest.java`
- Modify: `src/main/java/ru/fifth/horror/FifthMod.java`

**Interfaces:**
- Metadata: `id,fileName,container,width,height,durationMicros,hasAudio,audioChannels,audioSampleRate,byteLength,sha256,origin`.
- Upload: `beginUpload(metadata)`, `writeChunk(id,offset,bytes)`, `finishUpload(id)`, `abortUpload(id)`, `metadata(id)`, `mediaPath(id)`, `isComplete(id)`, `isLegacyOnly(id)`.
- Packets: `video_upload_begin/chunk/finish/status`, `video_playback_start`, `video_cache_status`, `video_chunk_request/data`, `video_playback_error`.

- [ ] Add filesystem tests: out-of-order chunk rejection, size cap, bad hash rejection, atomic publish on exact hash, legacy-id detection.
- [ ] Implement temporary `.upload-<id>` file and atomic publish into `world/fiven/videos/<id>/`.
- [ ] Enforce permission 2 on upload/admin commands and clean temporary uploads on disconnect/server stop.
- [ ] Add `/fiven video list|info|cassette|delete` commands.
- [ ] Run CI.

### Task 3: Bundle JavaCV/FFmpeg runtime and client media inspection

**Files:**
- Modify: `build.gradle`
- Create: `src/main/java/ru/fifth/horror/client/video/VideoNativeRuntime.java`
- Create: `src/main/java/ru/fifth/horror/client/video/VideoInspector.java`

**Interfaces:**
- `VideoNativeRuntime.available()/failureMessage()` initializes JavaCV only on the physical client.
- `VideoInspector.inspect(Path, Origin)` returns server-compatible metadata and validates at least one decodable video stream.

- [ ] Add nested dependencies for JavaCV/JavaCPP + FFmpeg Windows x86_64 while keeping dedicated-server class loading free of native references.
- [ ] Validate dimensions, duration, audio metadata, byte length and SHA-256 after opening media.
- [ ] Fail unsupported runtime/media with a concrete user message instead of crashing.
- [ ] Run CI and inspect remapped JAR for nested FFmpeg/JavaCPP jars.

### Task 4: Cutscene -> real MP4 recorder

**Files:**
- Replace behavior in: `src/main/java/ru/fifth/horror/client/VhsRecorderClient.java`
- Keep authoring helper only: `src/main/java/ru/fifth/horror/client/VhsWorldCapture.java`
- Modify: `src/main/java/ru/fifth/horror/client/gui/CameraEditorScreen.java`
- Modify: `src/main/java/ru/fifth/horror/client/gui/CutsceneLibraryScreen.java`

**Interfaces:**
- `VhsRecorderClient.start(CutsceneDefinition)` creates a local temporary 640x360/30 FPS H.264 MP4 via `FFmpegFrameRecorder`.
- Recorder captures each media-time camera sample off-screen, writes it directly into the MP4, finalizes, inspects, hashes and uploads the single file through `VideoFeature`.

- [ ] Remove persistent PNG upload from the new recording path.
- [ ] Show `Записываю MP4...`, percentage, `MP4 готов, загружаю на сервер...`, and the generated-video-is-video-only note.
- [ ] Give cassette only after verified server publish.
- [ ] Run CI.

### Task 5: External real-video import

**Files:**
- Create: `src/main/java/ru/fifth/horror/client/video/VideoImportClient.java`
- Modify: `src/main/java/ru/fifth/horror/client/gui/CutsceneLibraryScreen.java`

**Interfaces:**
- `VideoImportClient.openPickerAndUpload()` uses TinyFileDialogs and accepts `.mp4,.mov,.mkv,.webm,.avi,.m4v`.

- [ ] Add visible `🎬 Загрузить видео` button and rename selected cutscene action to `📼 Записать катсцену в видео`.
- [ ] Inspect selected file before upload; preserve original bytes/container/audio.
- [ ] Derive a safe id and suffix collisions unless replacement was explicitly confirmed.
- [ ] Upload in 64 KiB chunks with progress and final cassette creation.
- [ ] Run CI.

### Task 6: Client cache, FFmpeg playback and positional audio

**Files:**
- Create: `src/main/java/ru/fifth/horror/client/video/VideoCache.java`
- Create: `src/main/java/ru/fifth/horror/client/video/VideoPlayerSession.java`
- Create: `src/main/java/ru/fifth/horror/client/video/VideoPlaybackManager.java`
- Create: `src/main/java/ru/fifth/horror/client/video/PositionalVideoAudio.java`
- Modify: `src/main/java/ru/fifth/horror/client/VhsRecordingClient.java`
- Modify: `src/main/java/ru/fifth/horror/client/TelevisionRenderer.java`

**Interfaces:**
- Cache path: `.minecraft/fiven/video-cache/<sha256>.<ext>`; use cached file only after SHA-256 verification.
- One player session per TV: `PREPARING/STATIC/PLAYING/ENDED/ERROR`.
- Decoder thread uses `FFmpegFrameGrabber`; render thread uploads newest due image into one reusable `NativeImageBackedTexture`.
- Audio decoder queues 16-bit mono/stereo PCM to a dedicated OpenAL source at TV center, respecting master + BLOCKS volume.

- [ ] Download only missing/corrupt media; sequential requests and atomic cache publish.
- [ ] Start 30-tick static only after cache is ready.
- [ ] Drive video from FFmpeg timestamps/monotonic time, not Minecraft frame indexes.
- [ ] Stream positional audio and clean AL resources on end/error/world unload/TV removal.
- [ ] Make `TelevisionRenderer` consume only the real-video playback manager for new cassettes while retaining `/fiven tv test` diagnostic texture.
- [ ] Run CI.

### Task 7: Cassette migration and legacy rejection

**Files:**
- Modify: `src/main/java/ru/fifth/horror/block/CassetteDriveBlockEntity.java`
- Modify: `src/main/java/ru/fifth/horror/item/VhsCassetteItem.java`
- Retire primary runtime usage of: `src/main/java/ru/fifth/horror/vhs/VhsRecordingFeature.java`, `VhsRecordingStore.java`, `VhsRecordingPolicy.java`, `src/main/java/ru/fifth/horror/client/VhsRecordedPlayback.java`.

- [ ] Resolve cassette ids against `VideoAssetStore` first.
- [ ] If only legacy `world/fiven/vhs/<id>` exists, reject with `LEGACY VHS: перезапишите кассету` rather than static/black playback.
- [ ] Update tooltips/descriptions to real media wording.
- [ ] Keep old legacy files untouched.
- [ ] Run CI.

### Task 8: Full-screen F1-proof lift travel overlay

**Files:**
- Modify: `src/main/java/ru/fifth/horror/mixin/InGameHudMixin.java`
- Modify: `src/main/java/ru/fifth/horror/client/LiftTravelOverlay.java`
- Modify: `src/main/java/ru/fifth/horror/client/FifthClient.java`
- Test: `src/test/java/ru/fifth/horror/client/LiftTravelHudPolicyTest.java`

- [ ] RED test: when lift travel is active, ordinary cutscene HUD cancellation must not prevent the lift render path.
- [ ] Stop drawing lift travel from `HudRenderCallback`.
- [ ] Render it at `InGameHud.render` tail; do not cancel HUD at HEAD while lift is active.
- [ ] In overlay rendering, push + load identity GUI matrix and disable inherited scissor before filling `0..scaledWidth x 0..scaledHeight`, then draw only the lift floor/shaft animation.
- [ ] Because the injection is after vanilla HUD internals, F1 may hide vanilla HUD but must not hide this final lift overlay.
- [ ] Run CI.

### Task 9: Final verification

- [ ] Run complete GitHub Actions Java 17 build: tests, `check`, `remapJar`, `build`.
- [ ] Inspect production JAR for new `video/*` classes, JavaCV/FFmpeg nested dependencies and production refmap.
- [ ] Verify source has no new-recording path writing `frame_*.png` and no normal real-video playback path calling `WorldRenderer.render(...)`.
- [ ] Download the successful production artifact and provide the final `.jar`/CI archive.
- [ ] Leave PR #7 open and unmerged; leave `main` untouched.
