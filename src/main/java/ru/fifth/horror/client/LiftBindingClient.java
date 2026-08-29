package ru.fifth.horror.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.Hand;
import ru.fifth.horror.client.gui.LiftButtonBinderScreen;
import ru.fifth.horror.lift.LiftBindingFeature;

/** Client popup for the final step of Stone Button -> lift floor binding. */
public final class LiftBindingClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(LiftBindingFeature.OPEN_BINDER, (client, handler, buf, sender) -> {
            int handIndex = buf.readVarInt();
            var buttonPos = buf.readBlockPos();
            int currentFloor = buf.readVarInt();
            String liftName = buf.readString(64);
            client.execute(() -> client.setScreen(new LiftButtonBinderScreen(
                    client.currentScreen,
                    handIndex == 1 ? Hand.OFF_HAND : Hand.MAIN_HAND,
                    currentFloor,
                    liftName,
                    buttonPos
            )));
        });
    }
}
