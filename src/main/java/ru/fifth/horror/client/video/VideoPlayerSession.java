package ru.fifth.horror.client.video;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.Java2DFrameConverter;
import ru.fifth.horror.video.VideoAssetStore;
import ru.fifth.horror.video.VideoPlaybackPolicy;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.Buffer;
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
    private volatile String error = "";
    private volatile VideoPlaybackPolicy.State state = VideoPlaybackPolicy.State.PREPARING;
    private Path media;
    private int staticTicks;
    private long playStartNanos;
    private Thread decodeThread;
    private NativeImageBackedTexture texture;
    private Identifier textureId;
    /** Latest media-time frame selected on the client tick; uploaded only if/when the TV is rendered. */
    private DecodedFrame pendingFrame;

    public VideoPlayerSession(VideoAssetStore.Metadata metadata, BlockPos tvPos) {
        this.metadata = metadata;
        this.tvPos = tvPos.toImmutable();
        this.audio = metadata.hasAudio() ? new PositionalVideoAudio() : null;
    }

    public VideoAssetStore.Metadata metadata() { return metadata; }
    public BlockPos tvPos() { return tvPos; }
    public VideoPlaybackPolicy.State state() { return state; }
    public String error() { return error; }
    public boolean staticPhase() { return state == VideoPlaybackPolicy.State.STATIC; }
    public boolean playing() { return state == VideoPlaybackPolicy.State.PLAYING; }
    public boolean finished() { return state == VideoPlaybackPolicy.State.ENDED || state == VideoPlaybackPolicy.State.ERROR; }

    public void mediaReady(Path file) {
        if (!running || state != VideoPlaybackPolicy.State.PREPARING || file == null) return;
        this.media = file;
        this.staticTicks = 0;
        this.state = VideoPlaybackPolicy.State.STATIC;
    }

    public void fail(String message) {
        error = message == null || message.isBlank() ? "TAPE READ ERROR" : message;
        state = VideoPlaybackPolicy.State.ERROR;
        running = false;
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
            long elapsed = elapsedMicros();
            advanceVideo(elapsed);
            if (audio != null) audio.update(tvPos);
            if (elapsed >= metadata.durationMicros() && decoderDone) {
                state = VideoPlaybackPolicy.State.ENDED;
                running = false;
                if (audio != null) audio.close();
            }
        }
    }

    /**
     * Advances decode consumption even when the TV is outside the camera frustum. This prevents the
     * bounded video queue from blocking FFmpeg (and therefore positional audio) just because the
     * player looked away from the screen.
     */
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
        if (state != VideoPlaybackPolicy.State.PLAYING || !error.isBlank()) return null;
        DecodedFrame selected = pendingFrame;
        if (selected != null) {
            pendingFrame = null;
            install(selected);
        }
        return textureId;
    }

    private long elapsedMicros() {
        if (playStartNanos == 0L) return 0L;
        return Math.max(0L, (System.nanoTime() - playStartNanos) / 1_000L);
    }

    private void startDecoder() {
        if (decodeThread != null || media == null) return;
        playStartNanos = System.nanoTime();
        decodeThread = new Thread(this::decodeLoop, "Fiven-Video-Decode-" + metadata.id());
        decodeThread.setDaemon(true);
        decodeThread.start();
    }

    private void decodeLoop() {
        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(media.toFile());
        Java2DFrameConverter converter = new Java2DFrameConverter();
        try {
            grabber.setSampleMode(FrameGrabber.SampleMode.SHORT);
            if (metadata.hasAudio()) grabber.setAudioChannels(Math.min(2, Math.max(1, metadata.audioChannels())));
            grabber.start();
            Frame frame;
            while (running && (frame = grabber.grab()) != null) {
                long timestamp = Math.max(0L, frame.timestamp);
                if (frame.image != null) {
                    BufferedImage source = converter.convert(frame);
                    if (source != null) videoFrames.put(toDecodedFrame(source, timestamp));
                }
                if (audio != null && frame.samples != null && frame.samples.length > 0) {
                    short[] pcm = copyPcm(frame.samples, Math.min(2, Math.max(1, frame.audioChannels)));
                    if (pcm.length > 0) audio.queue(pcm, Math.min(2, Math.max(1, frame.audioChannels)), Math.max(1, frame.sampleRate));
                }
            }
            decoderDone = true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            decoderDone = true;
        } catch (Throwable decodeError) {
            decoderDone = true;
            fail("TAPE READ ERROR: FFmpeg decode — " + concise(decodeError));
        } finally {
            try { grabber.stop(); } catch (Throwable ignored) {}
            try { grabber.release(); } catch (Throwable ignored) {}
            try { converter.close(); } catch (Throwable ignored) {}
        }
    }

    private static DecodedFrame toDecodedFrame(BufferedImage source, long timestamp) {
        BufferedImage scaled = new BufferedImage(OUTPUT_WIDTH, OUTPUT_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setColor(java.awt.Color.BLACK);
            graphics.fillRect(0, 0, OUTPUT_WIDTH, OUTPUT_HEIGHT);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            double scale = Math.min(OUTPUT_WIDTH / (double) Math.max(1, source.getWidth()), OUTPUT_HEIGHT / (double) Math.max(1, source.getHeight()));
            int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
            int x = (OUTPUT_WIDTH - width) / 2;
            int y = (OUTPUT_HEIGHT - height) / 2;
            graphics.drawImage(source, x, y, width, height, null);
        } finally {
            graphics.dispose();
        }
        int[] argb = scaled.getRGB(0, 0, OUTPUT_WIDTH, OUTPUT_HEIGHT, null, 0, OUTPUT_WIDTH);
        return new DecodedFrame(timestamp, argb);
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
            if (texture == null) {
                texture = new NativeImageBackedTexture(OUTPUT_WIDTH, OUTPUT_HEIGHT, false);
                textureId = client.getTextureManager().registerDynamicTexture(
                        "fiven_video_" + Long.toUnsignedString(tvPos.asLong()), texture);
            }
            NativeImage image = texture.getImage();
            if (image == null) {
                fail("TAPE READ ERROR: dynamic texture unavailable");
                return;
            }
            for (int y = 0, index = 0; y < OUTPUT_HEIGHT; y++) {
                for (int x = 0; x < OUTPUT_WIDTH; x++, index++) {
                    int argb = frame.argb[index];
                    int a = (argb >>> 24) & 0xFF;
                    int r = (argb >>> 16) & 0xFF;
                    int g = (argb >>> 8) & 0xFF;
                    int b = argb & 0xFF;
                    image.setColor(x, y, (a << 24) | (b << 16) | (g << 8) | r);
                }
            }
            texture.upload();
        } catch (Throwable uploadError) {
            fail("TAPE READ ERROR: GPU upload — " + concise(uploadError));
        }
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

    private static String concise(Throwable error) {
        return error.getMessage() == null || error.getMessage().isBlank() ? error.getClass().getSimpleName() : error.getMessage();
    }

    private record DecodedFrame(long timestampMicros, int[] argb) {}
}
