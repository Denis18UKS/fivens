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

import java.util.UUID;

/**
 * Runtime bridge for director-only manual locomotion and chase-test.
 * Normal authored LOGICAL AI now stays entirely inside MonsterForLiftEntity so navigation is not restarted twice
 * every tick (that restart loop was one of the causes of visual sliding/jitter).
 */
@Mixin(MonsterForLiftEntity.class)
public abstract class MonsterForLiftRuntimeMixin {
    @Unique private Vec3d fiven$manualLocomotionTarget;
    @Unique private Vec3d fiven$testLastPathTarget;
    @Unique private int fiven$testRepathCooldown;

    @Inject(method = "tick", at = @At("TAIL"))
    private void fiven$runtimeLocomotion(CallbackInfo ci) {
        MonsterForLiftEntity mfl = (MonsterForLiftEntity) (Object) this;
        if (mfl.getWorld().isClient || !(mfl.getWorld() instanceof ServerWorld world)) return;
        MonsterForLiftRuntimeAccess access = (MonsterForLiftRuntimeAccess) (Object) mfl;
        if (fiven$testRepathCooldown > 0) fiven$testRepathCooldown--;

        MflTestModeManager.State test = MflTestModeManager.state(mfl);
        if (test != null) {
            fiven$manualLocomotionTarget = null;
            tickTestChase(mfl, access, world, test);
            return;
        }

        fiven$testLastPathTarget = null;
        fiven$testRepathCooldown = 0;

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
        if (mfl.getNavigation().isIdle() || mfl.age % 10 == 0) {
            mfl.getNavigation().startMovingTo(target.x, target.y, target.z, Math.max(.1, Math.min(3.5, speed)));
        }
    }

    @Unique
    private void tickTestChase(MonsterForLiftEntity mfl, MonsterForLiftRuntimeAccess access,
                               ServerWorld world, MflTestModeManager.State test) {
        if (access.fiven$getManualAnimationTicks() > 0 && "mfl_screamer".equals(mfl.getCurrentAnimation())) return;

        ServerPlayerEntity target = findPlayer(world, test.targetUuid);
        if (target == null || !target.isAlive() || target.isSpectator()) {
            mfl.getNavigation().stop();
            access.fiven$setCurrentAnimation("idle");
            return;
        }

        // Keep authored OFF mode from cancelling director chase-test on the next entity tick.
        access.fiven$setManualAnimationTicks(2);

        boolean hidden = MflHidingManager.isHidden(target);
        boolean visible = !hidden && access.fiven$canSeeTarget(target);
        if (visible) {
            Vec3d targetPos = target.getPos();
            test.lastKnown = targetPos;
            test.searchTicks = mfl.getSearchDurationTicks();

            if (mfl.squaredDistanceTo(target) <= 1.55 * 1.55) {
                mfl.triggerScreamer(target);
                return;
            }

            if (mfl.getNavigation().isIdle() || fiven$testRepathCooldown <= 0
                    || fiven$testLastPathTarget == null || fiven$testLastPathTarget.squaredDistanceTo(targetPos) > 2.25) {
                mfl.getNavigation().startMovingTo(target, mfl.getRunSpeed());
                fiven$testLastPathTarget = targetPos;
                fiven$testRepathCooldown = 5;
            }
            access.fiven$setCurrentAnimation("run");
            return;
        }

        if (test.lastKnown != null && test.searchTicks-- > 0) {
            Vec3d p = test.lastKnown;
            if (mfl.getPos().squaredDistanceTo(p) < 1.2) {
                mfl.getNavigation().stop();
                access.fiven$setCurrentAnimation("idle");
                return;
            }
            if (mfl.getNavigation().isIdle() || fiven$testRepathCooldown <= 0) {
                mfl.getNavigation().startMovingTo(p.x, p.y, p.z, mfl.getWalkSpeed());
                fiven$testRepathCooldown = 10;
            }
            access.fiven$setCurrentAnimation("walking");
            return;
        }

        mfl.getNavigation().stop();
        access.fiven$setCurrentAnimation("idle");
    }

    @Unique
    private static ServerPlayerEntity findPlayer(ServerWorld world, UUID uuid) {
        if (uuid == null) return null;
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (uuid.equals(player.getUuid())) return player;
        }
        return null;
    }
}
