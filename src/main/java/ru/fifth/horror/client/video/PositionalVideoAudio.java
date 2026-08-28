package ru.fifth.horror.client.video;

import net.fabricmc.fabric.api.client.sound.v1.FabricSoundInstance;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.AudioStream;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundLoader;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Streams decoded VHS PCM through Minecraft's existing SoundSystem/OpenAL context.
 * A second native OpenAL device/context is deliberately never created; that was unsafe on Windows.
 */
public final class PositionalVideoAudio implements AutoCloseable {
    private VideoStream stream;
    private VideoSound sound;
    private volatile boolean closed;

    public synchronized void queue(short[] pcm, int channels, int sampleRate) {
        if (closed || pcm == null || pcm.length == 0 || sampleRate <= 0) return;
        int normalizedChannels = channels <= 1 ? 1 : 2;
        if (stream == null) {
            stream = new VideoStream(normalizedChannels, sampleRate);
            sound = new VideoSound(stream);
            MinecraftClient.getInstance().execute(() -> {
                if (!closed && sound != null) MinecraftClient.getInstance().getSoundManager().play(sound);
            });
        }
        stream.enqueue(pcm, normalizedChannels, sampleRate);
    }

    public void update(BlockPos tvPos) {
        if (closed || sound == null || tvPos == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        float volume = Math.max(0f, Math.min(1f,
                client.options.getSoundVolume(SoundCategory.BLOCKS) * client.options.getSoundVolume(SoundCategory.MASTER)));
        sound.setPosition(tvPos.getX() + .5f, tvPos.getY() + .5f, tvPos.getZ() + .5f);
        sound.setVolume(volume);
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        if (stream != null) stream.close();
        if (sound != null) MinecraftClient.getInstance().execute(sound::finish);
        stream = null;
        sound = null;
    }

    private static final class VideoSound extends MovingSoundInstance implements FabricSoundInstance {
        private final VideoStream stream;

        private VideoSound(VideoStream stream) {
            super(SoundEvents.BLOCK_NOTE_BLOCK_HARP, SoundCategory.BLOCKS, SoundInstance.createRandom());
            this.stream = stream;
            this.repeat = false;
            this.volume = 1f;
            this.pitch = 1f;
            this.attenuationType = SoundInstance.AttenuationType.LINEAR;
        }

        @Override
        public CompletableFuture<AudioStream> getAudioStream(SoundLoader loader, Identifier id, boolean repeatInstantly) {
            return CompletableFuture.completedFuture(stream);
        }

        @Override
        public void tick() {
            if (stream.isClosed()) setDone();
        }

        private void setPosition(float x, float y, float z) { this.x = x; this.y = y; this.z = z; }
        private void setVolume(float volume) { this.volume = volume; }
        private void finish() { stream.close(); setDone(); }
    }

    private static final class VideoStream implements AudioStream {
        private final int channels;
        private final int sampleRate;
        private final AudioFormat format;
        private final LinkedBlockingQueue<byte[]> chunks = new LinkedBlockingQueue<>();
        private volatile boolean closed;

        private VideoStream(int channels, int sampleRate) {
            this.channels = channels;
            this.sampleRate = sampleRate;
            this.format = new AudioFormat(sampleRate, 16, channels, true, false);
        }

        private void enqueue(short[] pcm, int channels, int sampleRate) {
            if (closed || channels != this.channels || sampleRate != this.sampleRate) return;
            byte[] bytes = new byte[pcm.length * 2];
            for (int i = 0; i < pcm.length; i++) {
                short s = pcm[i];
                bytes[i * 2] = (byte) (s & 0xFF);
                bytes[i * 2 + 1] = (byte) ((s >>> 8) & 0xFF);
            }
            chunks.offer(bytes);
        }

        @Override
        public AudioFormat getFormat() { return format; }

        @Override
        public ByteBuffer getBuffer(int size) throws IOException {
            if (closed && chunks.isEmpty()) return ByteBuffer.allocate(0);
            ByteBuffer out = ByteBuffer.allocate(Math.max(1, size));
            while (out.hasRemaining()) {
                byte[] chunk;
                try {
                    chunk = chunks.poll(250, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("VHS audio stream interrupted", e);
                }
                if (chunk == null) {
                    if (closed) break;
                    continue;
                }
                int copy = Math.min(out.remaining(), chunk.length);
                out.put(chunk, 0, copy);
                if (copy < chunk.length) {
                    byte[] rest = new byte[chunk.length - copy];
                    System.arraycopy(chunk, copy, rest, 0, rest.length);
                    chunks.offer(rest);
                }
            }
            out.flip();
            return out;
        }

        private boolean isClosed() { return closed; }

        @Override
        public void close() { closed = true; chunks.clear(); }
    }
}
