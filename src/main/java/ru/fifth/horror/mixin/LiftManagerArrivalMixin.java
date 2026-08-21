package ru.fifth.horror.mixin;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ru.fifth.horror.block.LiftBlockEntity;
import ru.fifth.horror.lift.CursedLiftEventManager;
import ru.fifth.horror.lift.LiftManager;

/** Fires configured cursed-lift events only when LiftManager commits a completed ride's destination floor. */
@Mixin(value = LiftManager.class, remap = false)
public abstract class LiftManagerArrivalMixin {
    @Redirect(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lru/fifth/horror/block/LiftBlockEntity;setCurrentFloor(I)V", remap = false),
            remap = false
    )
    private static void fiven$fireCursedArrival(LiftBlockEntity lift, int floor, MinecraftServer server) {
        lift.setCurrentFloor(floor);
        CursedLiftEventManager.onArrival(server, lift, floor, null);
    }
}
