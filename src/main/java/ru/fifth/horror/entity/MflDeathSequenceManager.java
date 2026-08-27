package ru.fifth.horror.entity;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import ru.fifth.horror.FifthMod;
import ru.fifth.horror.checkpoint.CheckpointManager;
import ru.fifth.horror.cutscene.CutsceneDefinition;
import ru.fifth.horror.cutscene.CutsceneManager;
import ru.fifth.horror.mixin.MonsterForLiftRuntimeAccess;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Owns the real catch -> one-shot screamer -> death scene -> checkpoint restart sequence. */
public final class MflDeathSequenceManager {
    public static final Identifier CAPTURE_LOCK = FifthMod.id("mfl_capture_lock");
    private static final String DEFAULT_DEATH_SCENE = "mfl_death";
    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private MflDeathSequenceManager() {}

    public static boolean begin(MonsterForLiftEntity mfl, ServerPlayerEntity victim) {
        if (mfl == null || victim == null || victim.isCreative() || victim.isSpectator() || !victim.isAlive()) return false;
        if (!(mfl.getWorld() instanceof ServerWorld world) || victim.getServerWorld() != world) return false;
        if (SESSIONS.containsKey(mfl.getUuid())) return true;
        for (Session s : SESSIONS.values()) if (victim.getUuid().equals(s.victimId)) return true;

        MinecraftServer server = world.getServer();
        CutsceneDefinition scene = CutsceneManager.load(server, DEFAULT_DEATH_SCENE);
        int duration = scene == null ? 30 : sceneTicks(scene);
        MflCapturePolicy policy = new MflCapturePolicy();
        if (!policy.capture(victim.getUuid(), duration)) return false;

        Session session = new Session(mfl.getUuid(), victim.getUuid(), victim.getPos(), victim.getYaw(), victim.getPitch(), policy, scene != null);
        SESSIONS.put(mfl.getUuid(), session);

        mfl.getNavigation().stop();
        mfl.setVelocity(Vec3d.ZERO);
        mfl.triggerScreamer(victim); // one-shot GeckoLib action + audiovisual screamer
        lock(victim, true);
        if (scene != null) CutsceneManager.play(server, DEFAULT_DEATH_SCENE, victim);
        return true;
    }

    public static void tick(MinecraftServer server) {
        if (SESSIONS.isEmpty()) return;
        for (Session session : List.copyOf(SESSIONS.values())) {
            MonsterForLiftEntity mfl = findMfl(server, session.mflId);
            ServerPlayerEntity victim = server.getPlayerManager().getPlayer(session.victimId);
            if (mfl == null || victim == null || !victim.isAlive()) {
                if (victim != null) lock(victim, false);
                if (mfl != null) releaseMfl(mfl);
                SESSIONS.remove(session.mflId);
                continue;
            }

            // Victim remains physically caught until the authoritative death timer resolves.
            victim.teleport(victim.getServerWorld(), session.capturePos.x, session.capturePos.y, session.capturePos.z,
                    session.captureYaw, session.capturePitch);
            victim.setVelocity(Vec3d.ZERO);
            victim.fallDistance = 0;

            MonsterForLiftRuntimeAccess access = (MonsterForLiftRuntimeAccess) (Object) mfl;
            mfl.getNavigation().stop();
            mfl.setVelocity(Vec3d.ZERO);
            access.fiven$setManualAnimationTicks(2); // keeps authored LOGICAL/SCRIPTED AI suspended
            session.elapsed++;
            if (session.elapsed >= 10) access.fiven$setCurrentAnimation("mfl_screamer_hold");

            session.policy.tick();
            if (!session.policy.shouldResolve() || !session.policy.resolve()) continue;

            lock(victim, false);
            releaseMfl(mfl);
            SESSIONS.remove(session.mflId);

            if (CheckpointManager.isGameRunning(server) && CheckpointManager.current(server) != null) {
                CheckpointManager.restart(server);
            } else {
                victim.damage(victim.getDamageSources().generic(), Float.MAX_VALUE);
            }
        }
    }

    public static void resetAll(MinecraftServer server) {
        for (Session session : List.copyOf(SESSIONS.values())) {
            ServerPlayerEntity victim = server.getPlayerManager().getPlayer(session.victimId);
            if (victim != null) lock(victim, false);
            MonsterForLiftEntity mfl = findMfl(server, session.mflId);
            if (mfl != null) releaseMfl(mfl);
        }
        SESSIONS.clear();
    }

    public static boolean isActive(MonsterForLiftEntity mfl) { return mfl != null && SESSIONS.containsKey(mfl.getUuid()); }

    private static void releaseMfl(MonsterForLiftEntity mfl) {
        try {
            MonsterForLiftRuntimeAccess access = (MonsterForLiftRuntimeAccess) (Object) mfl;
            access.fiven$setManualAnimationTicks(0);
            access.fiven$setCurrentAnimation("idle");
            mfl.getNavigation().stop();
            mfl.setVelocity(Vec3d.ZERO);
        } catch (Throwable ignored) {}
    }

    private static void lock(ServerPlayerEntity player, boolean value) {
        PacketByteBuf out = PacketByteBufs.create();
        out.writeBoolean(value);
        ServerPlayNetworking.send(player, CAPTURE_LOCK, out);
    }

    private static MonsterForLiftEntity findMfl(MinecraftServer server, UUID id) {
        for (ServerWorld world : server.getWorlds()) {
            if (world.getEntity(id) instanceof MonsterForLiftEntity mfl) return mfl;
        }
        return null;
    }

    private static int sceneTicks(CutsceneDefinition scene) {
        int total = 0;
        if (scene != null && scene.keyframes != null) for (var k : scene.keyframes) total += Math.max(1, k.durationTicks);
        return Math.max(1, total);
    }

    private static final class Session {
        final UUID mflId, victimId;
        final Vec3d capturePos;
        final float captureYaw, capturePitch;
        final MflCapturePolicy policy;
        final boolean hasScene;
        int elapsed;
        Session(UUID mflId, UUID victimId, Vec3d capturePos, float captureYaw, float capturePitch, MflCapturePolicy policy, boolean hasScene) {
            this.mflId = mflId; this.victimId = victimId; this.capturePos = capturePos;
            this.captureYaw = captureYaw; this.capturePitch = capturePitch; this.policy = policy; this.hasScene = hasScene;
        }
    }
}
