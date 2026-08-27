package ru.fifth.horror.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.text.Text;
import ru.fifth.horror.client.video.VideoPlaybackManager;
import ru.fifth.horror.client.video.VideoUploadClient;
import ru.fifth.horror.vhs.VhsRecordingFeature;
import ru.fifth.horror.video.VideoAssetPolicy;
import ru.fifth.horror.video.VideoFeature;

/** Client registration for real-video authoring/import/playback plus the standalone CRT diagnostic. */
public final class VhsRecordingClient implements ClientModInitializer {
    private boolean wasInWorld;

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(VideoFeature.UPLOAD_STATUS, (client, handler, buf, sender) -> {
            String phase = buf.readString(16);
            String id = buf.readString(128);
            boolean success = buf.readBoolean();
            String message = buf.readString(256);
            client.execute(() -> VideoUploadClient.handleStatus(phase, id, success, message));
        });

        ClientPlayNetworking.registerGlobalReceiver(VideoFeature.PLAYBACK_START, (client, handler, buf, sender) -> {
            var metadata = VideoFeature.readMetadata(buf);
            var tvPos = buf.readBlockPos();
            client.execute(() -> VideoPlaybackManager.start(metadata, tvPos));
        });

        ClientPlayNetworking.registerGlobalReceiver(VideoFeature.CHUNK_DATA, (client, handler, buf, sender) -> {
            String id = buf.readString(128);
            long offset = buf.readLong();
            byte[] bytes;
            try {
                bytes = buf.readByteArray(VideoAssetPolicy.CHUNK_BYTES);
            } catch (RuntimeException malformed) {
                return;
            }
            boolean eof = buf.readBoolean();
            client.execute(() -> VideoPlaybackManager.receiveChunk(id, offset, bytes, eof));
        });

        ClientPlayNetworking.registerGlobalReceiver(VideoFeature.PLAYBACK_ERROR, (client, handler, buf, sender) -> {
            var tvPos = buf.readBlockPos();
            String message = buf.readString(256);
            client.execute(() -> VideoPlaybackManager.error(tvPos, message));
        });

        // /fiven tv test deliberately stays independent from stored media and FFmpeg sessions.
        ClientPlayNetworking.registerGlobalReceiver(VhsRecordingFeature.TV_DIAGNOSTIC, (client, handler, buf, sender) -> {
            var tvPos = buf.readBlockPos();
            client.execute(() -> {
                VhsRecordedPlayback.startDiagnostic(tvPos);
                if (client.player != null) {
                    client.player.sendMessage(Text.literal("§8[§cFiven§8] §7TV TEST получен клиентом: проверяю CRT."), true);
                }
            });
        });

        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> VhsRecorderClient.captureNext(tickDelta));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            VhsRecorderClient.tick();
            VideoUploadClient.tick();
            VideoPlaybackManager.tick();
            VhsRecordedPlayback.tick(); // diagnostic session only
            boolean inWorld = client.world != null;
            if (!inWorld && wasInWorld) {
                VideoPlaybackManager.clear();
                VhsRecordedPlayback.clear();
                VhsSignalTexture.clear();
            }
            wasInWorld = inWorld;
        });
    }
}
