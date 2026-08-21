package ru.fifth.horror.mixin;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.fifth.horror.entity.MflHidingManager;
import ru.fifth.horror.entity.MflTestModeManager;
import ru.fifth.horror.entity.MonsterForLiftEntity;

/**
 * Runtime locomotion bridge:
 * - manual walking/run animation previews now move the MFL physically using Minecraft navigation;
 * - explicit chase-test mode drives the nearest MFL toward a selected player while preserving the authored AI state.
 */
@Mixin(value = MonsterForLiftEntity.class, remap = false)
public abstract class MonsterForLiftRuntimeMixin {
    @Unique private Vec3d fiven$manualLocomotionTarget;

    @Inject(method = "tick", at = @At("TAIL"), remap = false)
    private void fiven$runtimeLocomotion(CallbackInfo ci) {
        MonsterForLiftEntity mfl = (MonsterForLiftEntity) (Object) this;
        if (mfl.getWorld().isClient || !(mfl.getWorld() instanceof ServerWorld world)) return;
        MonsterForLiftRuntimeAccess access = (MonsterForLiftRuntimeAccess) (Object) mfl;

        MflTestModeManager.State test = MflTestModeManager.state(mfl);
        if (test != null) {
            fiven$manualLocomotionTarget = null;
            tickTestChase(mfl, access, world, test);
            return;
        }

        int manualTicks = access.fiven$getManualAnimationTicks();
        String animation = mfl.getCurrentAnimation();
        boolean walking = "walking".equals(animation) || "walk".equals(animation);
        boolean running = "run".equals(animation) || "running".equals(animation);

        if (manualTicks > 0 && (walking || running)) {
            double speed = running ? mfl.getRunSpeed() : mfl.getWalkSpeed();
            tickManualForward(mfl, speed);
        } else {
            fiven$manualLocomotionTarget = null;
        }
    }

    @Unique
    private void tickManualForward(MonsterForLiftEntity mfl, double speed) {
        if (fiven$manualLocomotionTarget == null
                || mfl.getPos().squaredDistanceTo(fiven$manualLocomotionTarget) < 1.0
                || mfl.getNavigation().isIdle()) {
            Vec3d look = mfl.getRotationVec(1.0f);
            Vec3d flat = new Vec3d(look.x, 0, look.z);
            if (flat.lengthSquared() < 0.0001) {
                double yaw = Math.toRadians(mfl.getYaw());
                flat = new Vec3d(-Math.sin(yaw), 0, Math.cos(yaw));
            }
            flat = flat.normalize();
            fiven$manualLocomotionTarget = mfl.getPos().add(flat.multiply(8.0));
        }

        Vec3d target = fiven$manualLocomotionTarget;
        mfl.getNavigation().startMovingTo(target.x, target.y, target.z, Math.max(.1, Math.min(3.5, speed)));
    }

    @Unique
    private void tickTestChase(MonsterForLiftEntity mfl, MonsterForLiftRuntimeAccess access,
                               ServerWorld world, MflTestModeManager.State test) {
        // Let a manually triggered screamer finish without the chase controller immediately overriding it.
        if (access.fiven$getManualAnimationTicks() > 0 && "mfl_screamer".equals(mfl.getCurrentAnimation())) return;

        ServerPlayerEntity target = null;
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.getUuid().equals(test.targetUuid)) {
                target = player;
                break;
            }
        }

        if (target == null || !target.isAlive() || target.isSpectator()) {
            mfl.getNavigation().stop();
            access.fiven$setCurrentAnimation("idle");
            return;
        }

        // Keep original tick() in its manual branch on the next tick, so authored OFF mode cannot stop our test path.
        access.fiven$setManualAnimationTicks(2);

        boolean hidden = MflHidingManager.isHidden(target);
        boolean visible = !hidden && access.fiven$canSeeTarget(target);
        if (visible) {
            test.lastKnown = target.getPos();
            test.searchTicks = mfl.getSearchDurationTicks();
            mfl.getNavigation().startMovingTo(target, mfl.getRunSpeed());
            access.fiven$setCurrentAnimation("run");
            return;
        }

        if (test.lastKnown != null && test.searchTicks-- > 0) {
            Vec3d p = test.lastKnown;
            mfl.getNavigation().startMovingTo(p.x, p.y, p.z, mfl.getWalkSpeed());
            access.fiven$setCurrentAnimation(mfl.getNavigation().isIdle() ? "idle" : "walking");
            if (mfl.getPos().squaredDistanceTo(p) < 1.2) mfl.getNavigation().stop();
            return;
        }

        mfl.getNavigation().stop();
        access.fiven$setCurrentAnimation("idle");
    }
}
