package ru.fifth.horror.client.gui;

import net.minecraft.client.MinecraftClient;
import ru.fifth.horror.entity.DirectorNpcEntity;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Base64;

/** Seeds the live editor from an NPC's normal resource PNG before the Screen constructor runs. */
public final class NpcTextureEditorBootstrap {
    private NpcTextureEditorBootstrap() {}

    public static void prepare(DirectorNpcEntity npc) {
        if (npc == null || (npc.getSkinBase64() != null && !npc.getSkinBase64().isBlank())) return;
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            var resource = client.getResourceManager().getResource(npc.getTextureResource());
            if (resource.isEmpty()) return;
            BufferedImage image;
            try (InputStream in = resource.get().getInputStream()) {
                image = ImageIO.read(in);
            }
            if (image == null) return;
            if (image.getWidth() != 64 || image.getHeight() != 64) {
                BufferedImage scaled = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = scaled.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g.drawImage(image, 0, 0, 64, 64, null);
                g.dispose();
                image = scaled;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", out);
            npc.setSkinBase64(Base64.getEncoder().encodeToString(out.toByteArray()));
        } catch (Exception ignored) {}
    }
}
