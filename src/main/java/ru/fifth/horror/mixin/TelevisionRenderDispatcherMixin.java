package ru.fifth.horror.mixin;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.fifth.horror.block.TelevisionBlockEntity;
import ru.fifth.horror.client.TelevisionRenderer;

/**
 * Hard TV hook. For Fiven televisions the mixin owns the CRT overlay render call and cancels the dispatcher's
 * normal BER path, so provider registration/order cannot make the VHS surface silently disappear.
 */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class TelevisionRenderDispatcherMixin {
    @Inject(
            method = "render(Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void fiven$renderPhysicalTelevision(BlockEntity blockEntity, float tickDelta,
                                                MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                                CallbackInfo ci) {
        if (!(blockEntity instanceof TelevisionBlockEntity tv)) return;
        TelevisionRenderer.renderScreen(tv, tickDelta, matrices, vertexConsumers);
        ci.cancel();
    }
}
