package ru.fifth.horror.client.video;

import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALCCapabilities;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Dedicated OpenAL stream for media audio. A separate context keeps FFmpeg PCM handling out of
 * Minecraft's private sound-engine internals while still giving the TV a true positional source.
 */
public final class PositionalVideoAudio implements AutoCloseable {
    private static final Engine ENGINE = new Engine();

    private final AtomicInteger source = new AtomicInteger();
    private volatile boolean closed;

    public PositionalVideoAudio() {
        ENGINE.submit(() -> {
            if (!ENGINE.ready || closed) return;
            int id = AL10.alGenSources();
            AL10.alSourcei(id, AL10.AL_SOURCE_RELATIVE, AL10.AL_FALSE);
            AL10.alSourcef(id, AL10.AL_REFERENCE_DISTANCE, 4.0f);
            AL10.alSourcef(id, AL10.AL_MAX_DISTANCE, 64.0f);
            AL10.alSourcef(id, AL10.AL_ROLLOFF_FACTOR, 1.0f);
            source.set(id);
        });
    }

    public void queue(short[] pcm, int channels, int sampleRate) {
        if (closed || pcm == null || pcm.length == 0 || sampleRate <= 0) return;
        final int normalizedChannels = channels <= 1 ? 1 : 2;
        ENGINE.submit(() -> {
            int id = source.get();
            if (!ENGINE.ready || id == 0 || closed) return;
            cleanupProcessed(id);
            int buffer = AL10.alGenBuffers();
            ShortBuffer samples = BufferUtils.createShortBuffer(pcm.length);
            samples.put(pcm).flip();
            int format = normalizedChannels == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16;
            AL10.alBufferData(buffer, format, samples, sampleRate);
            AL10.alSourceQueueBuffers(id, buffer);
            int state = AL10.alGetSourcei(id, AL10.AL_SOURCE_STATE);
            if (state != AL10.AL_PLAYING && AL10.alGetSourcei(id, AL10.AL_BUFFERS_QUEUED) >= 2) {
                AL10.alSourcePlay(id);
            }
        });
    }

    /** Called on the Minecraft client thread; only immutable scalar values cross into the audio thread. */
    public void update(BlockPos tvPos) {
        if (closed || tvPos == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;
        Vec3d listener = client.gameRenderer.getCamera().getPos();
        float yaw = client.gameRenderer.getCamera().getYaw();
        float pitch = client.gameRenderer.getCamera().getPitch();
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        float fx = (float) (-Math.sin(yawRad) * Math.cos(pitchRad));
        float fy = (float) (-Math.sin(pitchRad));
        float fz = (float) (Math.cos(yawRad) * Math.cos(pitchRad));
        float blocks = client.options.getSoundVolume(SoundCategory.BLOCKS);
        float master = client.options.getSoundVolume(SoundCategory.MASTER);
        float gain = Math.max(0.0f, Math.min(1.0f, blocks * master));
        float sx = tvPos.getX() + 0.5f, sy = tvPos.getY() + 0.5f, sz = tvPos.getZ() + 0.5f;
        float lx = (float) listener.x, ly = (float) listener.y, lz = (float) listener.z;

        ENGINE.submit(() -> {
            int id = source.get();
            if (!ENGINE.ready || id == 0 || closed) return;
            cleanupProcessed(id);
            AL10.alSource3f(id, AL10.AL_POSITION, sx, sy, sz);
            AL10.alSourcef(id, AL10.AL_GAIN, gain);
            AL10.alListener3f(AL10.AL_POSITION, lx, ly, lz);
            FloatBuffer orientation = BufferUtils.createFloatBuffer(6);
            orientation.put(fx).put(fy).put(fz).put(0f).put(1f).put(0f).flip();
            AL10.alListenerfv(AL10.AL_ORIENTATION, orientation);
        });
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        ENGINE.submit(() -> {
            int id = source.getAndSet(0);
            if (!ENGINE.ready || id == 0) return;
            AL10.alSourceStop(id);
            cleanupAll(id);
            AL10.alDeleteSources(id);
        });
    }

    private static void cleanupProcessed(int source) {
        int processed = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);
        while (processed-- > 0) {
            int buffer = AL10.alSourceUnqueueBuffers(source);
            if (buffer != 0) AL10.alDeleteBuffers(buffer);
        }
    }

    private static void cleanupAll(int source) {
        cleanupProcessed(source);
        int queued = AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED);
        while (queued-- > 0) {
            int buffer = AL10.alSourceUnqueueBuffers(source);
            if (buffer != 0) AL10.alDeleteBuffers(buffer);
        }
    }

    private static final class Engine implements Runnable {
        private final BlockingQueue<Runnable> commands = new LinkedBlockingQueue<>();
        private final Thread thread;
        private volatile boolean ready;

        Engine() {
            thread = new Thread(this, "Fiven-Video-Audio");
            thread.setDaemon(true);
            thread.start();
        }

        void submit(Runnable command) {
            if (command != null) commands.offer(command);
        }

        @Override
        public void run() {
            long device = 0L;
            long context = 0L;
            try {
                device = ALC10.alcOpenDevice((ByteBuffer) null);
                if (device == 0L) return;
                ALCCapabilities capabilities = ALC.createCapabilities(device);
                context = ALC10.alcCreateContext(device, (IntBuffer) null);
                if (context == 0L || !ALC10.alcMakeContextCurrent(context)) return;
                AL.createCapabilities(capabilities);
                ready = true;
                while (!Thread.currentThread().isInterrupted()) {
                    Runnable command = commands.take();
                    try { command.run(); } catch (Throwable ignored) {}
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (Throwable ignored) {
                ready = false;
            } finally {
                ready = false;
                if (context != 0L) {
                    try { ALC10.alcMakeContextCurrent(0L); } catch (Throwable ignored) {}
                    try { ALC10.alcDestroyContext(context); } catch (Throwable ignored) {}
                }
                if (device != 0L) try { ALC10.alcCloseDevice(device); } catch (Throwable ignored) {}
            }
        }
    }
}
