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
 * Runtime locomotion / hunt bridge implemented as a mixin:
 * - manual walking/run previews physically navigate forward;
 * - chase-test follows the selected player and performs a real screamer on catch;
 * - authored LOGICAL+Hunt forcibly uses the full run animation while the player is visible;
 * - when authored Hunt reaches the player it invokes the entity's normal catch path: mfl_screamer + screen screamer.
 *
 * tick() is a Minecraft override, so the injection deliberately uses normal Loom/refmap remapping.
 */
@Mixin(MonsterForLiftEntity.class)
public abstract class MonsterForLiftRuntimeMixin {
    @Unique private Vec3d fiven$manualLocomotionTarget;

    @Inject(method = "tick", at = @At("TAIL"))
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

        // Reinforce the authored Hunt state after the entity's own logical AI tick.
        // This prevents any other animation/controller from replacing RUN while MFL can actually see its target.
        if (tickAuthoredHunt(mfl, access, world)) {
            fiven$manualLocomotionTarget = null;
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

    /** Returns true only while an authored Hunt target is currently visible/being caught. */
    @Unique
    private boolean tickAuthoredHunt(MonsterForLiftEntity mfl, MonsterForLiftRuntimeAccess access, ServerWorld world) {
        if (mfl.getAiMode() != MonsterForLiftEntity.AiMode.LOGICAL || !mfl.isHuntEnabled()) return false;
        if (access.fiven$getManualAnimationTicks() > 0 && "mfl_screamer".equals(mfl.getCurrentAnimation())) return true;

        UUID targetId = access.fiven$getChaseTarget();
        if (targetId == null) return false;
        ServerPlayerEntity target = findPlayer(world, targetId);
        if (target == null || !target.isAlive() || target.isCreative() || target.isSpectator()) return false;
        if (MflHidingManager.isHidden(target) || !access.fiven$canSeeTarget(target)) return false;

        // Same catch distance as the authored entity logic. Invoke the real catch method so it also clears chase,
        // starts mfl_screamer and sends the actual screamer packet rather than only changing the animation string.
        if (mfl.squaredDistanceTo(target) <= 1.55 * 1.55) {
            access.fiven$catchPlayer(target);
            return true;
        }

        mfl.getNavigation().startMovingTo(target, mfl.getRunSpeed());
        access.fiven$setCurrentAnimation("run");
        return true;
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
        // Let a screamer finish without the chase controller overriding the screamer animation.
        if (access.fiven$getManualAnimationTicks() > 0 && "mfl_screamer".equals(mfl.getCurrentAnimation())) return;

        ServerPlayerEntity target = findPlayer(world, test.targetUuid);
        if (target == null || !target.isAlive() || target.isSpectator()) {
            mfl.getNavigation().stop();
            access.fiven$setCurrentAnimation("idle");
            return;
        }

        // Keep the original entity tick in its manual branch on the next tick so authored OFF mode cannot stop test navigation.
        access.fiven$setManualAnimationTicks(2);

        boolean hidden = MflHidingManager.isHidden(target);
        boolean visible = !hidden && access.fiven$canSeeTarget(target);
        if (visible) {
            test.lastKnown = target.getPos();
            test.searchTicks = mfl.getSearchDurationTicks();

            if (mfl.squaredDistanceTo(target) <= 1.55 * 1.55) {
                // Test mode intentionally supports Creative directors too, so use the explicit screamer method here.
                mfl.triggerScreamer(target);
                return;
            }

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

    @Unique
    private static ServerPlayerEntity findPlayer(ServerWorld world, UUID uuid) {
        if (uuid == null) return null;
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (uuid.equals(player.getUuid())) return player;
        }
        return null;
    }
}
