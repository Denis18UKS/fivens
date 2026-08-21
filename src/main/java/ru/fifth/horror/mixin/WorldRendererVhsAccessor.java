package ru.fifth.horror.mixin;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Access needed to prepare visible chunks for the VHS secondary camera before WorldRenderer.render(). */
@Mixin(WorldRenderer.class)
public interface WorldRendererVhsAccessor {
    @Accessor("frustum")
    Frustum fiven$getFrustum();

    @Invoker("setupTerrain")
    void fiven$setupTerrain(Camera camera, Frustum frustum, boolean hasForcedFrustum, boolean spectator);
}
