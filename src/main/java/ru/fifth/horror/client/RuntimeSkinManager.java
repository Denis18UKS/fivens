package ru.fifth.horror.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import ru.fifth.horror.FifthMod;
import ru.fifth.horror.entity.DirectorNpcEntity;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Runtime PNG texture cache plus a stable live-preview slot for the in-game texture editor. */
public final class RuntimeSkinManager {
    private static final Map<Integer, Identifier> CACHE = new ConcurrentHashMap<>();
    private static final Map<UUID, Identifier> PREVIEWS = new ConcurrentHashMap<>();

    private RuntimeSkinManager() {}

    public static Identifier textureFor(DirectorNpcEntity npc) {
        if (npc == null) return FifthMod.id("textures/entity/npc_default.png");
        Identifier preview = PREVIEWS.get(npc.getUuid());
        return preview != null ? preview : textureFor(npc.getSkinBase64(), npc.getTextureResource());
    }

    public static Identifier textureFor(String base64, Identifier fallback) {
        if (base64 == null || base64.isBlank()) return fallback;
        int hash = base64.hashCode();
        Identifier cached = CACHE.get(hash);
        if (cached != null) return cached;
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes));
            if (image.getWidth() != 64 || image.getHeight() != 64) {
                image.close();
                return fallback;
            }
            Identifier id = FifthMod.id("runtime_skin/" + Integer.toUnsignedString(hash));
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, new NativeImageBackedTexture(image));
            CACHE.put(hash, id);
            return id;
        } catch (Exception e) {
            return fallback;
        }
    }

    /** Installs a 64x64 ARGB image into the NPC's live editor preview slot. */
    public static void setPreview(UUID uuid, int[] argb) {
        if (uuid == null || argb == null || argb.length != 64 * 64) return;
        try {
            NativeImage image = new NativeImage(64, 64, true);
            for (int y = 0; y < 64; y++) {
                for (int x = 0; x < 64; x++) {
                    image.setColor(x, y, argbToAbgr(argb[y * 64 + x]));
                }
            }
            Identifier id = FifthMod.id("runtime_skin/preview_" + uuid.toString().replace("-", ""));
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, new NativeImageBackedTexture(image));
            PREVIEWS.put(uuid, id);
        } catch (Exception ignored) {}
    }

    public static void clearPreview(UUID uuid) {
        if (uuid != null) PREVIEWS.remove(uuid);
    }

    private static int argbToAbgr(int argb) {
        int a = (argb >>> 24) & 255;
        int r = (argb >>> 16) & 255;
        int g = (argb >>> 8) & 255;
        int b = argb & 255;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }
}
