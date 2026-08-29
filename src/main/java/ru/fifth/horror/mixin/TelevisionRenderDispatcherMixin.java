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
 * TV render watchdog.
 *
 * Important: do NOT cancel BlockEntityRenderDispatcher here. The registered TelevisionRenderer must run through
 * Minecraft's normal block-entity renderer path so all dispatcher transforms/buffer setup remain intact.
 * The mixin only records that the dispatcher reached this TV, which keeps the requested mixin hook without
 * replacing vanilla renderer plumbing.
 */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class TelevisionRenderDispatcherMixin {
    @Inject(
            method = "render(Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;)V",
            at = @At("HEAD")
    )
    private void fiven$observePhysicalTelevision(BlockEntity blockEntity, float tickDelta,
                                                 MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                                 CallbackInfo ci) {
        if (blockEntity instanceof TelevisionBlockEntity tv) {
            TelevisionRenderer.noteDispatcherHit(tv);
        }
    }
}
