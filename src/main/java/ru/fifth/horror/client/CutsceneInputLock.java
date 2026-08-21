package ru.fifth.horror.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;

/** Consumes gameplay key/mouse bindings while a cutscene owns player control. */
public final class CutsceneInputLock {
    private CutsceneInputLock() {}

    public static void apply(MinecraftClient client) {
        if (client == null || client.options == null || !CutscenePlayback.lockInput()) return;

        release(client.options.forwardKey);
        release(client.options.backKey);
        release(client.options.leftKey);
        release(client.options.rightKey);
        release(client.options.jumpKey);
        release(client.options.sneakKey);
        release(client.options.sprintKey);
        release(client.options.attackKey);
        release(client.options.useKey);
        release(client.options.pickItemKey);
        release(client.options.dropKey);
        release(client.options.swapHandsKey);
        release(client.options.inventoryKey);
        for (KeyBinding hotbar : client.options.hotbarKeys) release(hotbar);

        if (client.player != null && client.player.input != null) {
            client.player.input.movementForward = 0;
            client.player.input.movementSideways = 0;
            client.player.input.jumping = false;
            client.player.input.sneaking = false;
        }
    }

    private static void release(KeyBinding key) {
        if (key == null) return;
        key.setPressed(false);
        while (key.wasPressed()) {
            // Consume queued presses so actions such as attack/use/inventory cannot leak into the cutscene.
        }
    }
}
