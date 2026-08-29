# Shared Checkpoints and Restart Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add one shared Fiven checkpoint for the whole game session and restore Fiven-owned runtime state for all players after a restart.

**Architecture:** `CheckpointManager` owns persisted checkpoint definitions, the single active checkpoint, a reentrancy guard, and a serialized runtime snapshot. Small snapshot hooks are added to existing Fiven managers so the checkpoint code does not reach into their private fields. `CheckpointFeature` registers the director item, commands, server tick hooks, and personal visualization.

**Tech Stack:** Java 17, Fabric 1.20.1, Fabric command/network/event APIs, Gson, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-08-27-mfl-death-checkpoints-recorded-vhs-design.md`

## Global Constraints

- Fabric 1.20.1 / Java 17.
- Exactly one active checkpoint shared by the entire running Fiven game.
- Restart restores Fiven-owned state, not an arbitrary full-world backup.
- Checkpoint management requires permission level 2.
- Visualization is personal to the director who enabled it.
- Do not merge `main` automatically.

---

### Task 1: Restart policy and persisted checkpoint model

**Files:**
- Create: `src/main/java/ru/fifth/horror/checkpoint/CheckpointRestartPolicy.java`
- Create: `src/test/java/ru/fifth/horror/checkpoint/CheckpointRestartPolicyTest.java`
- Create: `src/main/java/ru/fifth/horror/checkpoint/CheckpointData.java`

**Interfaces:**
- Produces: `CheckpointRestartPolicy.tryBegin()`, `finish()`, `isRestarting()` and serializable `CheckpointData`/`CheckpointData.Checkpoint`/`CheckpointData.RuntimeSnapshot` DTOs.

- [ ] Write JUnit tests proving the restart guard admits one restart, rejects a concurrent restart, and re-arms after `finish()`.
- [ ] Run `./gradlew test --tests ru.fifth.horror.checkpoint.CheckpointRestartPolicyTest --no-daemon` and confirm RED before the policy class exists.
- [ ] Implement the minimal synchronized restart guard and DTOs.
- [ ] Re-run the focused test and confirm GREEN.
- [ ] Commit the task.

### Task 2: Snapshot hooks for Fiven-owned managers

**Files:**
- Modify: `src/main/java/ru/fifth/horror/trigger/TriggerZoneManager.java`
- Modify: `src/main/java/ru/fifth/horror/structure/StructureLayerManager.java`
- Modify: `src/main/java/ru/fifth/horror/script/FifthScriptEngine.java`

**Interfaces:**
- Produces: `TriggerZoneManager.snapshotRuntime()/restoreRuntime(...)`, `StructureLayerManager.snapshotActive()/restoreActive(...)`, `FifthScriptEngine.snapshotFlags()/restoreFlags(...)`.

- [ ] Add immutable/copying snapshot methods for trigger enabled/runtime occupancy/cooldown state.
- [ ] Add active StructureLayer map snapshot and restore; restore must re-activate the recorded variants where metadata can resolve the activation key, and always rewrite `active.json` consistently.
- [ ] Add script flag snapshot/restore based on copies of the engine `FLAGS` map.
- [ ] Run `./gradlew test --no-daemon` and confirm existing trigger tests remain green.
- [ ] Commit the task.

### Task 3: Shared checkpoint manager and Fiven-state restart

**Files:**
- Create: `src/main/java/ru/fifth/horror/checkpoint/CheckpointManager.java`
- Modify: `src/main/java/ru/fifth/horror/entity/MonsterForLiftEntity.java`

**Interfaces:**
- Consumes the snapshot hooks from Task 2.
- Produces: `set(...)`, `activate(...)`, `current(...)`, `list(...)`, `delete(...)`, `isGameRunning()`, `markGameStarted(...)`, `restart(...)`, `clearTransientClientState(...)`.

- [ ] Persist definitions and active id to `<world>/fiven/checkpoints.json`.
- [ ] On activation, capture all loaded Fiven MFL/NPC/Lift block-entity NBT plus trigger/script/layer snapshots.
- [ ] Add an MFL method `resetRuntimeAfterCheckpoint()` that stops capture/chase/navigation and returns the animation/controller state to idle without deleting authored configuration.
- [ ] Restart in the specified order: guard, transient clears, layer restore, NBT restore for existing Fiven entities/BEs, trigger/script restore, player teleport/reset, emit `checkpoint_restart`, release guard.
- [ ] Ensure missing authored entities are skipped with a warning instead of throwing.
- [ ] Run `./gradlew clean build --stacktrace --no-daemon`.
- [ ] Commit the task.

### Task 4: Checkpoint director tool, commands, and visualization

**Files:**
- Create: `src/main/java/ru/fifth/horror/item/CheckpointToolItem.java`
- Create: `src/main/java/ru/fifth/horror/CheckpointFeature.java`
- Modify: `src/main/java/ru/fifth/horror/FifthMod.java`
- Modify: `src/main/resources/assets/fiven/lang/ru_ru.json`
- Modify: `src/main/resources/assets/fiven/lang/en_us.json`
- Create: `src/main/resources/assets/fiven/models/item/checkpoint_tool.json`

**Interfaces:**
- Commands: `/fiven checkpoint set|activate|current|list|delete|respawn|visualize`.
- Tool: normal RMB creates/updates a generated checkpoint at the hit position; sneak+RMB activates the nearest checkpoint.

- [ ] Register `checkpoint_tool` in the Fiven item group and `/fiven tools` set.
- [ ] Implement all permission-2 checkpoint commands.
- [ ] Implement session-local personal visualization using particles sent only to subscribed directors; active checkpoint is visually distinct by label/message and denser marker pattern.
- [ ] Wire `/fiven_start`/`/fiven start` to mark the Fiven game session running before emitting the existing `start` trigger.
- [ ] Add tooltips/translations and a generated item model that reuses the camera/checkpoint-like vanilla compass texture if no custom texture is supplied.
- [ ] Run `./gradlew clean build --stacktrace --no-daemon`.
- [ ] Commit the task.
