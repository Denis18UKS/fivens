package ru.fifth.horror.mixin;

import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exact client camera positioning for the off-screen VHS recorder renderer. */
@Mixin(Camera.class)
public interface CameraAccessor {
    @Invoker("setPos")
    void fiven$setPos(double x, double y, double z);

    @Invoker("setRotation")
    void fiven$setRotation(float yaw, float pitch);
}
