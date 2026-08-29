package ru.fifth.horror.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import ru.fifth.horror.CheckpointFeature;
import ru.fifth.horror.entity.MflDeathSequenceManager;

/** Client half of MFL capture: locks input and accepts authoritative checkpoint reset. */
public final class DeathSequenceClientFeature implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(MflDeathSequenceManager.CAPTURE_LOCK, (client, handler, buf, sender) -> {
            boolean locked = buf.readBoolean();
            client.execute(() -> CaptureInputLock.setLocked(locked));
        });
        ClientPlayNetworking.registerGlobalReceiver(CheckpointFeature.CLIENT_RESET, (client, handler, buf, sender) ->
                client.execute(() -> {
                    CaptureInputLock.setLocked(false);
                    CutscenePlayback.stop();
                }));
        ClientTickEvents.START_CLIENT_TICK.register(CaptureInputLock::apply);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null) CaptureInputLock.setLocked(false);
        });
    }
}
