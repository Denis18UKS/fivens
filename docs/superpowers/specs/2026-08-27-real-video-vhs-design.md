# Real Video VHS Design

## Scope

Replace Fiven's current stored-PNG-frame VHS pipeline with real video assets decoded by FFmpeg, while preserving the existing physical CRT television, cassette drive, TV linking, startup interference, checkpoint, MFL, trigger-zone, lift, and layer behavior already present on the feature branch.

The change stays on `feature/mfl-death-checkpoints-recorded-vhs-2026-08-27` / PR #7 and must not merge `main` automatically.

## Goals

1. A cutscene recorded to VHS becomes one real encoded video file rather than a folder of PNG frames.
2. The television decodes that file as continuous video and renders decoded video frames to the CRT texture according to media timestamps, not Minecraft tick-indexed image files.
3. Real external video files can be imported through a separate `Загрузить видео` button.
4. Imported videos preserve their original audio and play that audio positionally from the television.
5. Startup static remains a short cassette/signal-lock effect and is not used as a replacement for missing video.
6. The server stores video media as files; clients use a hash-addressed local cache and only download media they do not already have.
7. Existing old frame-folder VHS recordings are not silently treated as real videos. They are reported as legacy/incompatible and must be re-recorded.

## Media engine

Use JavaCV/FFmpeg as the client media engine.

- `FFmpegFrameGrabber` decodes imported/recorded video files.
- `FFmpegFrameRecorder` creates MP4 recordings from Fiven cutscene camera captures.
- Bundle the JavaCV core, JavaCPP core, FFmpeg Java bindings, and Windows x86_64 native FFmpeg runtime as nested Fabric dependencies so the delivered Windows client mod remains one installable mod JAR and does not require the user to separately install `ffmpeg.exe`.
- Dedicated server code must never initialize FFmpeg native classes. Server-side responsibilities are file storage, validation, hashing, metadata, chunk transfer, and playback authorization only.

The initial supported client platform for bundled native decoding is Windows x86_64, matching the project's current target environment. Unsupported client platforms must fail with a clear `FFmpeg runtime unavailable` message rather than crash Minecraft.

## Video asset format

A stored media asset is represented by metadata plus one real media file.

Server world layout:

```text
world/
  fiven/
    videos/
      <video-id>/
        metadata.json
        media.<ext>
```

Metadata fields:

- `id`
- `fileName`
- `container`
- `width`
- `height`
- `durationMicros`
- `hasAudio`
- `audioChannels`
- `audioSampleRate`
- `byteLength`
- `sha256`
- `origin` = `CUTSCENE_RECORDING` or `IMPORTED_FILE`

Video ids continue using the existing safe-id rules.

Maximum uploaded/imported asset size for this implementation: 512 MiB. Transfer chunks are 64 KiB.

## Cutscene -> real video recording

The existing `Записать VHS` action remains tied to a saved cutscene, but it no longer uploads individual PNG files.

Recording flow:

1. Validate the cutscene and open a local temporary MP4 file.
2. Create `FFmpegFrameRecorder` for a 640x360, 30 FPS MP4 video track.
3. Advance through the saved cutscene timeline using presentation time rather than one PNG per Minecraft tick.
4. Render each required camera sample through the existing off-screen secondary camera capture path.
5. Feed each captured image directly into the recorder as a video frame.
6. Finalize the MP4.
7. Compute SHA-256 and inspect the completed file using `FFmpegFrameGrabber` to produce trustworthy metadata.
8. Upload the finished MP4 to the server as chunks.
9. The server writes into a temporary upload file, validates declared length/hash, atomically publishes the asset, then gives the player a VHS cassette that references the new video id.

The generated cutscene recording is a real MP4 media file. Internally all digital video decoding still produces frames, but the persistent source and playback timeline are the encoded video stream, not a slideshow of stored PNGs.

### Audio for generated cutscene recordings

Imported real videos retain and reproduce their existing audio track.

A saved Fiven camera cutscene currently contains camera/event data but does not contain a deterministic PCM mix of Minecraft's OpenAL output. This implementation therefore does **not** pretend to capture arbitrary live Minecraft sound into the generated MP4. Generated cutscene MP4 recordings are video-only in this iteration. This limitation is explicit in the UI. A later soundtrack attachment feature can add an authored audio track without changing the media-store/playback architecture.

## Importing a real external video

Add a separate `Загрузить видео` button to the cutscene/VHS library UI.

Use LWJGL TinyFileDialogs for the native Windows file picker. Supported selectable extensions:

- `.mp4`
- `.mov`
- `.mkv`
- `.webm`
- `.avi`
- `.m4v`

Import flow:

1. Director presses `Загрузить видео`.
2. Native file picker opens.
3. Client opens the selected media using `FFmpegFrameGrabber` and validates that at least one video stream can be decoded.
4. Read metadata and compute file SHA-256.
5. Prompt/use a safe id derived from the filename; collisions replace only after explicit confirmation in the UI, otherwise append a numeric suffix.
6. Upload the original media bytes unchanged. Do not unnecessarily transcode imported media.
7. Server verifies byte length and hash and stores the original container extension.
8. Server gives a VHS cassette referencing the imported video id.

Preserving the original imported file is important because it keeps its original video/audio timing and avoids avoidable quality loss.

## Network transfer

Replace the frame-request protocol as the primary VHS media transport with chunked asset transfer.

### Upload packets

- `VIDEO_UPLOAD_BEGIN(id, fileName, byteLength, sha256, metadata)`
- `VIDEO_UPLOAD_CHUNK(id, offset, bytes)`
- `VIDEO_UPLOAD_FINISH(id)`
- `VIDEO_UPLOAD_STATUS(id, phase, success, message)`

Rules:

- permission level 2 required;
- maximum 512 MiB;
- 64 KiB chunks;
- offsets must be exact/sequential for the active upload;
- write to `.upload-<id>` temporary files;
- publish only after full SHA-256 verification;
- abort temporary uploads on disconnect/server stop.

### Playback/download packets

- `VIDEO_PLAYBACK_START(tvPos, metadata)`
- `VIDEO_CACHE_STATUS(id, sha256, present)`
- `VIDEO_CHUNK_REQUEST(id, offset)`
- `VIDEO_CHUNK_DATA(id, offset, bytes, eof)`
- `VIDEO_PLAYBACK_ERROR(tvPos, message)`

When playback starts, the client first checks `.minecraft/fiven/video-cache/<sha256>.<ext>`. If its hash matches, no media download occurs. Otherwise the client downloads sequential chunks, verifies SHA-256, atomically publishes the local cache file, then starts the local player session.

## Playback architecture

Create one client playback session per active physical television.

Session states:

- `PREPARING` — locating/verifying/downloading cached media;
- `STATIC` — 1.5 seconds of real CRT noise with the existing analogue startup sound;
- `PLAYING` — FFmpeg media timeline active;
- `ENDED` — television returns to black/off state;
- `ERROR` — physical TV shows `TAPE READ ERROR`, with a concrete log reason.

The static phase starts only after the media is locally ready. Therefore slow first-time downloads do not consume the startup static and then leave an unexplained black screen.

### Video rendering

`FFmpegFrameGrabber` runs on a dedicated decode worker, never the render thread.

The decoder produces a small bounded queue of decoded video frames with their FFmpeg timestamps. The Minecraft render thread uploads only the newest due frame into one reusable `NativeImageBackedTexture` for that TV. Playback clock is based on monotonic wall time / media timestamps, not frame-number-per-tick assumptions.

The existing physical `TelevisionRenderer` remains responsible for the screen quad. It asks the video player for the current dynamic texture exactly as it currently asks the VHS layer for a texture.

This preserves the corrected CRT plane and avoids reintroducing live-world rendering during tape playback.

## Positional audio

If the media has audio, decode audio frames with FFmpeg and stream PCM through a dedicated OpenAL source positioned at the television center.

Requirements:

- source position updates from the TV block position;
- attenuation is spatial/positional;
- gain respects Minecraft master volume and `BLOCKS` category volume;
- stop/cleanup source and queued buffers when playback ends, world unloads, TV disappears, or session errors;
- video clock is authoritative; audio is queued against the same media timestamps so long videos do not drift progressively;
- mono/stereo PCM supported; unsupported layouts are converted by FFmpeg to 16-bit stereo before queueing.

Imported video's original audio is therefore heard from the physical television rather than globally/fullscreen.

## Cassette behavior

The VHS cassette NBT continues to store a recording/media id, but validation now resolves that id against the real `VideoAssetStore`.

Cassette insertion:

1. validate TV link;
2. validate complete media asset;
3. notify nearby clients of media metadata;
4. each client prepares/verifies its cache;
5. once locally ready, that client runs 1.5 seconds of static + analogue sound;
6. continuous video + positional audio starts;
7. end of media returns TV to black.

Old PNG-folder recordings are legacy and rejected with a clear message asking the director to re-record the VHS.

## UI

### Camera editor

Keep:

- `Записать VHS`

Change status text to clearly say:

- `Записываю MP4...`
- progress based on media timeline;
- `MP4 готов, загружаю на сервер...`;
- final `VHS создана: <id>`.

Also show a small note that generated cutscene MP4 is video-only in this iteration.

### Cutscene/VHS library

Add a separate visible button:

- `🎬 Загрузить видео`

Existing selected-cutscene action becomes:

- `📼 Записать катсцену в видео`

Imported or recorded asset completion automatically gives a VHS cassette.

## Diagnostics

`/fiven tv test` remains a CRT-only test and must not depend on a stored asset.

Add director commands:

```text
/fiven video list
/fiven video info <id>
/fiven video cassette <id>
/fiven video delete <id>
```

`info` reports filename/container, duration, dimensions, audio presence, bytes, and SHA-256 prefix.

Logs use `Fiven/Video` and include failures from:

- FFmpeg native initialization;
- decode/open failure;
- unsupported media;
- upload/download hash mismatch;
- OpenAL streaming errors;
- local cache corruption.

## Compatibility and migration

- Do not delete legacy `world/fiven/vhs/` frame recordings automatically.
- New real media lives under `world/fiven/videos/`.
- Old cassettes whose id resolves only to legacy frame data are rejected with `LEGACY VHS: перезапишите кассету`.
- No changes to checkpoints, trigger zones, MFL death state, simplified layer workflow, lift bindings, or unrelated project systems.

## Testing

Unit/filesystem tests cover:

- safe ids;
- upload size/chunk ordering;
- atomic publish only after correct SHA-256;
- cache path/hash verification;
- legacy-id rejection;
- media metadata serialization;
- playback state transitions (`PREPARING -> STATIC -> PLAYING -> ENDED`);
- 1.5-second static begins only after local media is ready.

CI build must compile Java 17, run all JUnit tests, remap the mod JAR, and verify the expected JavaCV/FFmpeg nested dependencies are present in the production JAR.

Runtime validation checklist on Windows Minecraft 1.20.1:

1. `/fiven tv test` shows CRT signal/test card.
2. Record a short cutscene; confirm one MP4 asset exists on the server and no PNG frames are created for the new recording.
3. Insert its cassette; confirm continuous motion after static.
4. Import an MP4 with audio; insert cassette; confirm continuous video and positional sound attenuating with distance.
5. Reinsert the same cassette; confirm local media cache avoids a second full transfer.
6. Try a legacy PNG cassette; confirm explicit legacy rejection rather than black TV/static loop.
