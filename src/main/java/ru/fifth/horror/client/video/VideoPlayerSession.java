package ru.fifth.horror.client.video;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import ru.fifth.horror.video.VideoAssetStore;
import ru.fifth.horror.video.VideoPlaybackPolicy;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/** One real FFmpeg playback session for one physical television. */
public final class VideoPlayerSession implements AutoCloseable {
    private static final int OUTPUT_WIDTH = 640;
    private static final int OUTPUT_HEIGHT = 360;

    private final VideoAssetStore.Metadata metadata;
    private final BlockPos tvPos;
    private final BlockingQueue<DecodedFrame> videoFrames = new ArrayBlockingQueue<>(8);
    private final PositionalVideoAudio audio;

    private volatile boolean running = true;
    private volatile boolean decoderDone;
    private volatile boolean firstVideoFrameDecoded;
    private volatile String error = "";
    private volatile VideoPlaybackPolicy.State state = VideoPlaybackPolicy.State.PREPARING;
    private volatile long firstVideoTimestampMicros = Long.MIN_VALUE;
    private volatile long decodedVideoFrames;
    private volatile long lastDecodedTimestampMicros;
    private Path media;
    private int staticTicks;
    private volatile long playStartNanos;
    private Thread decodeThread;
    private NativeImageBackedTexture texture;
    private Identifier textureId;
    private DecodedFrame pendingFrame;
    private boolean framePresented;
    private boolean firstUploadLogged;

    public VideoPlayerSession(VideoAssetStore.Metadata metadata, BlockPos tvPos) {
        this.metadata = metadata;
        this.tvPos = tvPos.toImmutable();
        this.audio = metadata.hasAudio() ? new PositionalVideoAudio() : null;
    }

    public VideoAssetStore.Metadata metadata() { return metadata; }
    public BlockPos tvPos() { return tvPos; }
    public VideoPlaybackPolicy.State state() { return state; }
    public String error() { return error; }

    /** Keep CRT interference visible while FFmpeg is still producing the first real frame. */
    public boolean staticPhase() {
        return state == VideoPlaybackPolicy.State.STATIC
                || (state == VideoPlaybackPolicy.State.PLAYING && !firstVideoFrameDecoded && error.isBlank());
    }

    public boolean playing() { return state == VideoPlaybackPolicy.State.PLAYING && firstVideoFrameDecoded; }
    public boolean finished() { return state == VideoPlaybackPolicy.State.ENDED || state == VideoPlaybackPolicy.State.ERROR; }

    public void mediaReady(Path file) {
        if (!running || state != VideoPlaybackPolicy.State.PREPARING || file == null) return;
        media = file;
        staticTicks = 0;
        state = VideoPlaybackPolicy.State.STATIC;
        log("media ready; static tracking started; file=" + file.getFileName());
    }

    public void fail(String message) {
        error = message == null || message.isBlank() ? "TAPE READ ERROR" : message;
        state = VideoPlaybackPolicy.State.ERROR;
        running = false;
        log("ERROR: " + error);
        if (audio != null) audio.close();
    }

    public void tick() {
        if (!running && state != VideoPlaybackPolicy.State.ERROR) return;
        if (state == VideoPlaybackPolicy.State.STATIC) {
            staticTicks++;
            VideoPlaybackPolicy.State next = VideoPlaybackPolicy.next(state, media != null, staticTicks, 0, metadata.durationMicros());
            if (next == VideoPlaybackPolicy.State.PLAYING) {
                state = next;
                startDecoder();
            }
            return;
        }
        if (state == VideoPlaybackPolicy.State.PLAYING) {
            if (!firstVideoFrameDecoded) {
                if (decoderDone) fail("TAPE READ ERROR: FFmpeg returned no video frames");
                return;
            }

            long elapsed = elapsedMicros();
            advanceVideo(elapsed);
            if (audio != null) audio.update(tvPos);

            if (VideoTimelinePolicy.shouldFinish(decoderDone, videoFrames.isEmpty(), pendingFrame == null, framePresented)) {
                state = VideoPlaybackPolicy.State.ENDED;
                running = false;
                log("playback ended after presenting " + decodedVideoFrames + " decoded video frames; lastTimestamp=" + lastDecodedTimestampMicros + "us");
                if (audio != null) audio.close();
            }
        }
    }

    private void advanceVideo(long dueMicros) {
        DecodedFrame newest = null;
        while (true) {
            DecodedFrame frame = videoFrames.peek();
            if (frame == null || frame.timestampMicros > dueMicros + 10_000L) break;
            newest = videoFrames.poll();
        }
        if (newest != null) pendingFrame = newest;
    }

    /** Called from the render thread by TelevisionRenderer. */
    public Identifier texture() {
        if (!playing() || !error.isBlank()) return null;

        // If the first decoded frame arrived between the client tick and this render pass,
        // consume it here so the CRT does not flash black for an extra frame.
        if (pendingFrame == null && textureId == null) advanceVideo(elapsedMicros());

        DecodedFrame selected = pendingFrame;
        if (selected != null) {
            pendingFrame = null;
            install(selected);
        }
        return textureId;
    }

    private long elapsedMicros() {
        long started = playStartNanos;
        if (started == 0L) return 0L;
        return Math.max(0L, (System.nanoTime() - started) / 1_000L);
    }

    private void startDecoder() {
        if (decodeThread != null || media == null) return;
        playStartNanos = 0L;
        decodeThread = new Thread(this::decodeLoop, "Fiven-Video-Decode-" + metadata.id());
        decodeThread.setDaemon(true);
        decodeThread.start();
        log("FFmpeg decoder thread started; waiting for first video frame before starting playback clock");
    }

    private void decodeLoop() {
        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(media.toFile());
        try {
            // COLOR mode gives JavaCV a packed image buffer without passing pixel_format as an
            // avformat input option. The latter is rejected by ordinary MP4/MKV demuxers.
            grabber.setImageMode(FrameGrabber.ImageMode.COLOR);
            grabber.setSampleMode(FrameGrabber.SampleMode.SHORT);
            if (metadata.hasAudio()) grabber.setAudioChannels(Math.min(2, Math.max(1, metadata.audioChannels())));
            grabber.start();
            log("FFmpeg opened media: " + grabber.getImageWidth() + "x" + grabber.getImageHeight()
                    + ", length=" + grabber.getLengthInTime() + "us");

            Frame frame;
            while (running && (frame = grabber.grab()) != null) {
                long rawTimestamp = Math.max(0L, frame.timestamp);
                if (frame.image != null && frame.image.length > 0) {
                    long first = firstVideoTimestampMicros;
                    if (first == Long.MIN_VALUE) {
                        first = rawTimestamp;
                        firstVideoTimestampMicros = first;
                        log("first FFmpeg video frame: rawTimestamp=" + rawTimestamp + "us, "
                                + frame.imageWidth + "x" + frame.imageHeight + ", channels=" + frame.imageChannels
                                + ", stride=" + frame.imageStride);
                    }
                    long normalizedTimestamp = VideoTimelinePolicy.normalizeTimestamp(first, rawTimestamp);
                    videoFrames.put(toDecodedFrame(frame, normalizedTimestamp));
                    lastDecodedTimestampMicros = normalizedTimestamp;
                    decodedVideoFrames++;

                    if (!firstVideoFrameDecoded) {
                        // The visible media clock starts when a real frame is actually ready, not when
                        // FFmpeg initialization begins. Slow native startup can otherwise skip the whole clip.
                        playStartNanos = System.nanoTime();
                        firstVideoFrameDecoded = true;
                        log("first video frame buffered; playback clock anchored at 0us");
                    }
                }

                // Do not start the audio stream before the first visible video frame. Audio packets that
                // precede the first image packet are intentionally skipped to avoid sound under tracking noise.
                if (audio != null && firstVideoFrameDecoded && frame.samples != null && frame.samples.length > 0) {
                    int channels = Math.min(2, Math.max(1, frame.audioChannels));
                    short[] pcm = copyPcm(frame.samples, channels);
                    if (pcm.length > 0) audio.queue(pcm, channels, Math.max(1, frame.sampleRate));
                }
            }
            decoderDone = true;
            log("FFmpeg decoder reached EOF; decodedVideoFrames=" + decodedVideoFrames
                    + ", queued=" + videoFrames.size() + ", lastTimestamp=" + lastDecodedTimestampMicros + "us");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            decoderDone = true;
        } catch (Throwable decodeError) {
            decoderDone = true;
            fail("TAPE READ ERROR: FFmpeg decode — " + concise(decodeError));
        } finally {
            try { grabber.stop(); } catch (Throwable ignored) {}
            try { grabber.release(); } catch (Throwable ignored) {}
        }
    }

    private static DecodedFrame toDecodedFrame(Frame frame, long timestamp) {
        if (frame.image == null || frame.image.length == 0 || !(frame.image[0] instanceof ByteBuffer pixels)) {
            throw new IllegalStateException("FFmpeg did not return packed COLOR bytes");
        }
        int width = frame.imageWidth, height = frame.imageHeight, stride = frame.imageStride, channels = frame.imageChannels;
        if (width <= 0 || height <= 0 || stride <= 0 || (channels != 1 && channels != 3 && channels != 4)) {
            throw new IllegalStateException("Unexpected FFmpeg image layout: " + width + "x" + height + " channels=" + channels + " stride=" + stride);
        }
        int rowBytes = Math.multiplyExact(width, channels);
        if (stride < rowBytes) throw new IllegalStateException("FFmpeg image stride is too small");
        int required = Math.addExact(Math.multiplyExact(height - 1, stride), rowBytes);
        ByteBuffer copy = pixels.duplicate();
        copy.rewind();
        if (copy.remaining() < required) throw new IllegalStateException("FFmpeg image buffer is truncated: " + copy.remaining() + " < " + required);
        byte[] bytes = new byte[required];
        copy.get(bytes);

        int[] sourceArgb;
        if (channels == 3) {
            sourceArgb = VideoFramePixels.bgrToArgb(bytes, width, height, stride);
        } else if (channels == 4) {
            sourceArgb = VideoFramePixels.rgbaToArgb(bytes, width, height, stride);
        } else {
            sourceArgb = grayscaleToArgb(bytes, width, height, stride);
        }
        return new DecodedFrame(timestamp, VideoFramePixels.letterboxNearest(sourceArgb, width, height, OUTPUT_WIDTH, OUTPUT_HEIGHT));
    }

    private static int[] grayscaleToArgb(byte[] gray, int width, int height, int stride) {
        int[] argb = new int[Math.multiplyExact(width, height)];
        int out = 0;
        for (int y = 0; y < height; y++) {
            int row = y * stride;
            for (int x = 0; x < width; x++) {
                int value = gray[row + x] & 0xFF;
                argb[out++] = 0xFF000000 | (value << 16) | (value << 8) | value;
            }
        }
        return argb;
    }

    private static short[] copyPcm(Buffer[] buffers, int channels) {
        if (buffers.length == 1 && buffers[0] instanceof ShortBuffer packed) {
            ShortBuffer copy = packed.duplicate();
            short[] pcm = new short[copy.remaining()];
            copy.get(pcm);
            return pcm;
        }
        if (channels > 1 && buffers.length >= channels) {
            ShortBuffer[] planes = new ShortBuffer[channels];
            int samples = Integer.MAX_VALUE;
            for (int ch = 0; ch < channels; ch++) {
                if (!(buffers[ch] instanceof ShortBuffer shortBuffer)) return new short[0];
                planes[ch] = shortBuffer.duplicate();
                samples = Math.min(samples, planes[ch].remaining());
            }
            if (samples <= 0 || samples == Integer.MAX_VALUE) return new short[0];
            short[] pcm = new short[samples * channels];
            for (int i = 0; i < samples; i++) for (int ch = 0; ch < channels; ch++) pcm[i * channels + ch] = planes[ch].get();
            return pcm;
        }
        return new short[0];
    }

    private void install(DecodedFrame frame) {
        MinecraftClient client = MinecraftClient.getInstance();
        try {
            NativeImage image;
            if (texture == null) {
                image = new NativeImage(NativeImage.Format.RGBA, OUTPUT_WIDTH, OUTPUT_HEIGHT, false);
                texture = new NativeImageBackedTexture(image);
                textureId = client.getTextureManager().registerDynamicTexture(
                        "fiven_video_" + Long.toUnsignedString(tvPos.asLong()), texture);
            } else {
                image = texture.getImage();
                if (image == null) throw new IllegalStateException("Dynamic VHS texture lost its NativeImage");
            }

            int index = 0;
            for (int y = 0; y < OUTPUT_HEIGHT; y++) {
                for (int x = 0; x < OUTPUT_WIDTH; x++) {
                    image.setColor(x, y, argbToNativeAbgr(frame.argb[index++]));
                }
            }
            texture.upload();
            framePresented = true;
            if (!firstUploadLogged) {
                firstUploadLogged = true;
                log("first video texture uploaded to GPU; timestamp=" + frame.timestampMicros + "us, texture=" + textureId);
            }
        } catch (Throwable uploadError) {
            fail("TAPE READ ERROR: GPU upload — " + concise(uploadError));
        }
    }

    /** NativeImage#setColor uses the ABGR integer representation for RGBA pixel storage. */
    private static int argbToNativeAbgr(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    @Override
    public void close() {
        running = false;
        if (decodeThread != null) decodeThread.interrupt();
        videoFrames.clear();
        pendingFrame = null;
        if (audio != null) audio.close();
        if (texture != null) {
            try { texture.close(); } catch (Throwable ignored) {}
            texture = null;
            textureId = null;
        }
    }

    private void log(String message) {
        System.out.println("[Fiven/VHS " + metadata.id() + "] " + message);
    }

    private static String concise(Throwable error) {
        return error.getMessage() == null || error.getMessage().isBlank() ? error.getClass().getSimpleName() : error.getMessage();
    }

    private record DecodedFrame(long timestampMicros, int[] argb) {}
}
