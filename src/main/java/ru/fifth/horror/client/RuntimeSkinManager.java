package ru.fifth.horror.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import ru.fifth.horror.FifthMod;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RuntimeSkinManager {
    private static final Map<Integer,Identifier> CACHE = new ConcurrentHashMap<>();
    private RuntimeSkinManager() {}
    public static Identifier textureFor(String base64, Identifier fallback) {
        if (base64 == null || base64.isBlank()) return fallback;
        int hash = base64.hashCode(); Identifier cached = CACHE.get(hash); if (cached != null) return cached;
        try {
            byte[] bytes = Base64.getDecoder().decode(base64); NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes));
            if (image.getWidth()!=64 || image.getHeight()!=64) { image.close(); return fallback; }
            Identifier id = FifthMod.id("runtime_skin/" + Integer.toUnsignedString(hash));
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, new NativeImageBackedTexture(image)); CACHE.put(hash,id); return id;
        } catch (Exception e) { return fallback; }
    }
}
