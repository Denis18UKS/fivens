package ru.fifth.horror.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;

/** Independent server-authoritative lock used while MFL physically holds a victim. */
public final class CaptureInputLock {
    private static boolean locked;
    private CaptureInputLock() {}
    public static void setLocked(boolean value) { locked = value; }
    public static boolean locked() { return locked; }

    public static void apply(MinecraftClient client) {
        if (!locked || client == null || client.options == null) return;
        release(client.options.forwardKey); release(client.options.backKey); release(client.options.leftKey); release(client.options.rightKey);
        release(client.options.jumpKey); release(client.options.sneakKey); release(client.options.sprintKey);
        release(client.options.attackKey); release(client.options.useKey); release(client.options.pickItemKey);
        release(client.options.dropKey); release(client.options.swapHandsKey); release(client.options.inventoryKey);
        for (KeyBinding hotbar : client.options.hotbarKeys) release(hotbar);
        if (client.player != null && client.player.input != null) {
            client.player.input.movementForward = 0; client.player.input.movementSideways = 0;
            client.player.input.jumping = false; client.player.input.sneaking = false;
        }
    }

    private static void release(KeyBinding key) {
        if (key == null) return;
        key.setPressed(false);
        while (key.wasPressed()) { }
    }
}
