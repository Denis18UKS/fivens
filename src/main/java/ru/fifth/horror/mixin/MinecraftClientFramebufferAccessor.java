package ru.fifth.horror.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Lets the VHS secondary world pass temporarily replace MinecraftClient's render target.
 * WorldRenderer contains code paths that query client.getFramebuffer(); without this swap,
 * those passes can leak the recorded camera into the player's real world framebuffer.
 */
@Mixin(MinecraftClient.class)
public interface MinecraftClientFramebufferAccessor {
    @Accessor("framebuffer")
    Framebuffer fiven$getFramebuffer();

    @Mutable
    @Accessor("framebuffer")
    void fiven$setFramebuffer(Framebuffer framebuffer);
}
