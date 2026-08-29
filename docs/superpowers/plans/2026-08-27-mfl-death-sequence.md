# MFL Death Sequence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn MFL contact into a deterministic capture → one-shot held screamer → death cutscene → shared-checkpoint restart sequence.

**Architecture:** A pure `MflCapturePolicy` models capture lifecycle and exactly-once resolution. `MflDeathSequenceManager` owns active captures server-side, freezes victims, starts targeted cutscenes, and resolves after the authoritative server-side cutscene duration. `MonsterForLiftEntity` delegates capture to that manager and uses a one-shot-and-hold GeckoLib action animation.

**Tech Stack:** Java 17, Fabric 1.20.1, GeckoLib 4, Fabric networking, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-08-27-mfl-death-checkpoints-recorded-vhs-design.md`

## Global Constraints

- `mfl_screamer` must not loop.
- MFL remains in capture state until death/restart resolution.
- Normal survival/adventure captures restart at the shared checkpoint when a Fiven game and active checkpoint exist.
- Creative/spectator players are not normal hunt victims.
- Director chase-test may move toward Creative, but must not kill/restart a Creative target.
- Server-side cutscene duration is authoritative.

---

### Task 1: Capture lifecycle policy

**Files:**
- Create: `src/main/java/ru/fifth/horror/entity/MflCapturePolicy.java`
- Create: `src/test/java/ru/fifth/horror/entity/MflCapturePolicyTest.java`

**Interfaces:**
- Produces states `IDLE`, `CAPTURED`, `DEATH_SCENE`, `RESOLVED`; methods `capture(UUID,int)`, `tick()`, `shouldResolve()`, `resolve()`, `reset()`.

- [ ] Write tests for one capture at a time, exact duration countdown, exactly-once resolve, and reset/re-arm.
- [ ] Run focused test and confirm RED.
- [ ] Implement the policy.
- [ ] Re-run focused test and confirm GREEN.
- [ ] Commit.

### Task 2: Server death sequence manager and client capture lock

**Files:**
- Create: `src/main/java/ru/fifth/horror/entity/MflDeathSequenceManager.java`
- Create: `src/main/java/ru/fifth/horror/client/CaptureInputLock.java`
- Modify: `src/main/java/ru/fifth/horror/network/FifthNetworking.java`
- Modify: `src/main/java/ru/fifth/horror/client/FifthClient.java`
- Modify: `src/main/java/ru/fifth/horror/client/CutsceneInputLock.java`

**Interfaces:**
- Produces `begin(MonsterForLiftEntity,ServerPlayerEntity)`, `tick(MinecraftServer)`, `cancelForMfl(UUID)`, `resetAll(MinecraftServer)` and payload `fiven:capture_lock`.

- [ ] Add capture-lock payload carrying enabled flag and optional MFL entity id.
- [ ] Client input lock must combine ordinary cutscene lock and capture lock without leaving stuck keys after unlock.
- [ ] Manager freezes the target position/velocity every server tick while active.
- [ ] Resolve at the configured death cutscene total ticks; if scene missing, use a short fallback screamer window and still resolve safely.
- [ ] On resolve, call `CheckpointManager.restart(...)` when game+checkpoint are active; otherwise apply lethal generic damage to the victim.
- [ ] Ensure disconnect, removed MFL, dead victim, or checkpoint restart clears the capture exactly once.
- [ ] Run full tests/build.
- [ ] Commit.

### Task 3: MFL entity integration and death-cutscene configuration

**Files:**
- Modify: `src/main/java/ru/fifth/horror/entity/MonsterForLiftEntity.java`
- Modify: `src/main/java/ru/fifth/horror/network/FifthNetworking.java`
- Modify: `src/main/java/ru/fifth/horror/client/gui/MflAiScreen.java`
- Modify: `src/main/java/ru/fifth/horror/client/gui/MflEditorScreen.java`

**Interfaces:**
- Produces `getDeathCutsceneId()/setDeathCutsceneId(String)`, `isCaptureActive()`, `beginCapture(ServerPlayerEntity)`, `finishCapture()`.

- [ ] Persist per-MFL death cutscene id; default `mfl_death`.
- [ ] Replace direct short `catchPlayer()` screamer behavior with `MflDeathSequenceManager.begin(...)`.
- [ ] While capture is active, suspend normal logical/scripted navigation updates and force locomotion idle.
- [ ] Register screamer action with play-and-hold semantics; release/reset action controller after sequence resolution.
- [ ] Add death-cutscene id to the MFL editor/config network packet while remaining backward-safe inside the same mod build.
- [ ] Keep manual `/fiven mfl screamer` as a nonlethal visual test.
- [ ] Run `./gradlew clean build --stacktrace --no-daemon` and inspect remapped JAR for MFL classes/refmap.
- [ ] Commit.
