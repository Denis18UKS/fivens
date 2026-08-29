package ru.fifth.horror.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;
import ru.fifth.horror.block.TelevisionBlockEntity;
import ru.fifth.horror.client.VhsRecordedPlayback;

/** Full-screen "inside the television" view for manually browsing immutable VHS camera frames. */
public final class VhsFrameViewerScreen extends Screen {
    private static final int BACKGROUND = 0xFF030303;
    private final BlockPos tvPos;

    public VhsFrameViewerScreen(BlockPos tvPos) {
        super(Text.literal("VHS / КАДРЫ"));
        this.tvPos = tvPos.toImmutable();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void tick() {
        if (client == null || client.world == null) {
            close();
            return;
        }
        if (!(client.world.getBlockEntity(tvPos) instanceof TelevisionBlockEntity tv) || tv.getRecording().isBlank()) {
            close();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            VhsRecordedPlayback.step(tvPos, -1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            VhsRecordedPlayback.step(tvPos, 1);
            return true;
        }
        if (client != null && client.options.sneakKey.matchesKey(keyCode, scanCode)) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, BACKGROUND);

        Identifier texture = VhsRecordedPlayback.texture(tvPos);
        int sourceWidth = VhsRecordedPlayback.width(tvPos);
        int sourceHeight = VhsRecordedPlayback.height(tvPos);
        int frameCount = VhsRecordedPlayback.frameCount(tvPos);
        int current = VhsRecordedPlayback.currentFrame(tvPos);

        int topMargin = 28;
        int bottomMargin = 38;
        int availableWidth = Math.max(1, width - 24);
        int availableHeight = Math.max(1, height - topMargin - bottomMargin);

        if (texture != null && sourceWidth > 0 && sourceHeight > 0) {
            double scale = Math.min(availableWidth / (double) sourceWidth, availableHeight / (double) sourceHeight);
            int drawWidth = Math.max(1, (int) Math.round(sourceWidth * scale));
            int drawHeight = Math.max(1, (int) Math.round(sourceHeight * scale));
            int x = (width - drawWidth) / 2;
            int y = topMargin + (availableHeight - drawHeight) / 2;
            context.drawTexture(texture, x, y, 0.0f, 0.0f, drawWidth, drawHeight, sourceWidth, sourceHeight);
        } else {
            String error = VhsRecordedPlayback.error(tvPos);
            String message = error.isBlank() ? "ЗАГРУЗКА КАДРА..." : error;
            context.drawCenteredTextWithShadow(textRenderer, message, width / 2, height / 2 - 4, 0xFFE9E9E9);
        }

        String counter = frameCount <= 0 ? "КАДР — / —" : "КАДР " + (current + 1) + " / " + frameCount;
        context.fill(0, 0, width, 22, 0xC0000000);
        context.fill(0, height - 28, width, height, 0xC0000000);
        context.drawCenteredTextWithShadow(textRenderer, counter, width / 2, 7, 0xFFF2F2F2);
        context.drawCenteredTextWithShadow(textRenderer,
                "← / → — переключить кадр     Приседание — выйти",
                width / 2, height - 19, 0xFFD8D8D8);
    }

    @Override
    public void removed() {
        VhsRecordedPlayback.closeSession(tvPos);
        super.removed();
    }
}
