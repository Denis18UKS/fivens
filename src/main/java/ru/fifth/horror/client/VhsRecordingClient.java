package ru.fifth.horror.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import ru.fifth.horror.vhs.VhsRecordingFeature;
import ru.fifth.horror.vhs.VhsRecordingPolicy;

/** Registers the authoring recorder and immutable stored-frame TV playback on the client. */
public final class VhsRecordingClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(VhsRecordingFeature.RECORD_ACK, (client, handler, buf, sender) -> {
            String phase = buf.readString(16);
            String id = buf.readString(128);
            boolean success = buf.readBoolean();
            String message = buf.readString(256);
            client.execute(() -> VhsRecorderClient.handleAck(phase, id, success, message));
        });

        ClientPlayNetworking.registerGlobalReceiver(VhsRecordingFeature.PLAYBACK_START, (client, handler, buf, sender) -> {
            String id = buf.readString(128);
            int width = buf.readVarInt();
            int height = buf.readVarInt();
            int fps = buf.readVarInt();
            int frameCount = buf.readVarInt();
            int durationTicks = buf.readVarInt();
            var tvPos = buf.readBlockPos();
            client.execute(() -> VhsRecordedPlayback.start(id, width, height, fps, frameCount, durationTicks, tvPos));
        });

        ClientPlayNetworking.registerGlobalReceiver(VhsRecordingFeature.FRAME_DATA, (client, handler, buf, sender) -> {
            String id = buf.readString(128);
            int frameIndex = buf.readVarInt();
            byte[] png;
            try {
                png = buf.readByteArray(VhsRecordingPolicy.MAX_FRAME_BYTES);
            } catch (RuntimeException malformed) {
                return;
            }
            client.execute(() -> VhsRecordedPlayback.receiveFrame(id, frameIndex, png));
        });

        ClientPlayNetworking.registerGlobalReceiver(VhsRecordingFeature.PLAYBACK_ERROR, (client, handler, buf, sender) -> {
            var tvPos = buf.readBlockPos();
            String message = buf.readString(256);
            client.execute(() -> VhsRecordedPlayback.error(tvPos, message));
        });

        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> VhsRecorderClient.captureNext(tickDelta));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            VhsRecorderClient.tick();
            VhsRecordedPlayback.tick();
            if (client.world == null) VhsRecordedPlayback.clear();
        });
    }
}
