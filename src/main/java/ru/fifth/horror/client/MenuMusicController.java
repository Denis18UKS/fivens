package ru.fifth.horror.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import ru.fifth.horror.FifthMod;

public final class MenuMusicController {
    private static SoundInstance menuSound;
    private static int retryTicks;

    private MenuMusicController() {}

    public static void tick(MinecraftClient client) {
        if (client == null) return;
        if (client.world != null) {
            stop(client);
            return;
        }

        // While no world is loaded, Fifth owns the menu soundscape and vanilla menu music is suppressed.
        client.getMusicTracker().stop();
        if (retryTicks > 0) { retryTicks--; return; }

        if (menuSound == null || !client.getSoundManager().isPlaying(menuSound)) {
            try {
                menuSound = new PositionedSoundInstance(
                        FifthMod.id("menu_ambient"), SoundCategory.MUSIC, 0.58f, 1.0f,
                        SoundInstance.createRandom(), true, 0, SoundInstance.AttenuationType.NONE,
                        0.0, 0.0, 0.0, true
                );
                client.getSoundManager().play(menuSound);
            } catch (Exception ignored) {
                retryTicks = 40;
            }
        }
    }

    public static void stop(MinecraftClient client) {
        if (menuSound != null) {
            client.getSoundManager().stop(menuSound);
            menuSound = null;
        }
    }
}
