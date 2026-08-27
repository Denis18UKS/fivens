# Trigger-zone Threshold and Visualization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add multiplayer trigger thresholds plus personal director-only 3D trigger-zone visualization, while preserving PR #6 command-zone behavior and producing a Java 17 Fabric 1.20.1 build artifact.

**Architecture:** Keep `TriggerZoneManager` server-authoritative. Extract pure occupancy/visual-selection policies that can be unit-tested without Minecraft runtime, then use a small S2C snapshot channel for only the director who opted in. A focused client renderer draws camera-relative world-space cuboids and labels; it never changes gameplay or collision state.

**Tech Stack:** Java 17, Fabric 1.20.1 / Fabric API, Brigadier, Fabric networking, Fabric WorldRenderEvents, JUnit 5 for pure policy tests, GitHub Actions Gradle build.

**Spec:** `docs/superpowers/specs/2026-08-27-trigger-zone-threshold-visualization-design.md`

## Global Constraints

- Minecraft 1.20.1, Fabric, Java 17.
- Keep PR #6 stacked on PR #5; do not merge either automatically.
- Do not modify `main` directly.
- Existing trigger zones with no `minPlayers` field must behave as `minPlayers = 1`.
- Visualization is personal/session-local and must never be broadcast to players who did not enable it.
- One-shot group activation executes the full target group before disabling the zone.
- Server is authoritative; client visualization is read-only.
- Completion requires GitHub Actions `./gradlew clean build --stacktrace --no-daemon` success and a downloadable production `.jar`.

---

### Task 1: Add testable occupancy and visualization-selection policies

**Files:**
- Modify: `build.gradle`
- Create: `src/test/java/ru/fifth/horror/trigger/TriggerOccupancyPolicyTest.java`
- Create: `src/test/java/ru/fifth/horror/trigger/TriggerVisualizationSelectionTest.java`
- Create after RED: `src/main/java/ru/fifth/horror/trigger/TriggerOccupancyPolicy.java`
- Create after RED: `src/main/java/ru/fifth/horror/trigger/TriggerVisualizationSelection.java`

**Interfaces:**
- Produces: `TriggerOccupancyPolicy.enterCrossed(int previous, int current, int minimum)`, `stayEligible(int current, int minimum)`, `exitQualified(int previous, int current, int minimum)`, `minimum(int requested)`.
- Produces: `TriggerVisualizationSelection.showAll()`, `hideAll()`, `show(String id)`, `hide(String id)`, `includes(String id)`, `isEmpty()`.

- [ ] **Step 1: Add JUnit 5 test runtime only.** Add `testImplementation 'org.junit.jupiter:junit-jupiter:5.10.2'` and `test { useJUnitPlatform() }` to `build.gradle`.
- [ ] **Step 2: Write RED occupancy tests.** Assert: `2->3` with minimum 3 fires ENTER; `3->4` does not; dropping to 2 and later `2->3` is eligible again; STAY requires `current >= minimum`; EXIT is false for a group that never met threshold and true when the previous occupancy met threshold and at least one player left; minimum values below 1 normalize to 1.
- [ ] **Step 3: Write RED visualization selection tests.** Assert all-on includes arbitrary zones, hiding one while all-on excludes only that id, showing it again restores it, all-off clears everything, and single-zone on does not reveal unrelated zones.
- [ ] **Step 4: Run CI and verify RED.** The test compilation must fail specifically because the two production policy classes do not exist yet.
- [ ] **Step 5: Implement the two minimal pure Java policy classes.** Do not add Minecraft dependencies to either class.
- [ ] **Step 6: Re-run CI and verify the policy tests pass.**

---

### Task 2: Make trigger activation threshold-aware and group-safe

**Files:**
- Modify: `src/main/java/ru/fifth/horror/trigger/TriggerZoneManager.java`
- Modify: `src/main/java/ru/fifth/horror/TriggerZoneFeature.java`

**Interfaces:**
- Consumes: `TriggerOccupancyPolicy` from Task 1.
- Produces: persisted `Zone.minPlayers`, `setMinPlayers(MinecraftServer,String,int)`, `currentCount(String)`, threshold-aware ENTER/STAY/EXIT execution.

- [ ] **Step 1: Add `minPlayers = 1` to `Zone` and sanitize old JSON.** `sanitize` must force missing/zero/negative persisted values to 1.
- [ ] **Step 2: Add `setMinPlayers`.** Validate/clamp to at least 1, save JSON, and reset the zone's occupancy/cooldown activation state so the new threshold takes effect predictably.
- [ ] **Step 3: Replace per-player ENTER firing with threshold crossing.** Build the current alive/non-spectator player list first. Fire only when `previousCount < minPlayers && currentCount >= minPlayers`; execute for every current player.
- [ ] **Step 4: Preserve STAY per-player cooldown.** Only evaluate STAY while current occupancy is at least `minPlayers`.
- [ ] **Step 5: Gate EXIT by the previous qualifying occupancy.** If one or more players left and the previous occupancy was at least `minPlayers`, execute against the online players who participated in that previous qualifying set.
- [ ] **Step 6: Make one-shot group execution atomic at the gameplay level.** Execute every target player's command first, then disable the zone once after the group finishes.
- [ ] **Step 7: Keep cooldown behavior deterministic.** Use a zone-level timestamp for grouped ENTER/EXIT activations and existing per-player timestamps for STAY.
- [ ] **Step 8: Add `/fiven trigger players <id> <count>` and `/fiven_trigger players <id> <count>`.** Show the resulting requirement in feedback.
- [ ] **Step 9: Update `list` and `info`.** Include current occupancy and minimum as `current/min`, plus mode/enabled/once/cooldown.
- [ ] **Step 10: Run Gradle tests/build through CI.**

---

### Task 3: Add personal server-side visualization subscriptions and snapshots

**Files:**
- Create: `src/main/java/ru/fifth/horror/trigger/TriggerZoneVisualizationServer.java`
- Modify: `src/main/java/ru/fifth/horror/TriggerZoneFeature.java`
- Modify: `src/main/java/ru/fifth/horror/trigger/TriggerZoneManager.java`

**Interfaces:**
- Consumes: `TriggerVisualizationSelection` from Task 1 and zone/current-count data from Task 2.
- Produces: S2C payload `fiven:trigger_zone_visualization` containing clear flag and selected zone rows: id, dimension, min/max coordinates, mode, enabled, current count, minimum count.
- Produces: `showAll(ServerPlayerEntity)`, `hideAll(ServerPlayerEntity)`, `show(ServerPlayerEntity,String)`, `hide(ServerPlayerEntity,String)`, `sync(MinecraftServer)`, `clear(UUID)`.

- [ ] **Step 1: Implement per-player session selection map.** Never store it in world JSON.
- [ ] **Step 2: Implement full snapshot serialization.** Send only selected zones and no command strings.
- [ ] **Step 3: Add snapshot fingerprinting.** Do not resend if selected geometry/status/occupancy has not changed.
- [ ] **Step 4: Call visualization sync after trigger occupancy updates.** This keeps `2/3 -> 3/3` status current.
- [ ] **Step 5: Clear subscription on disconnect.** Register Fabric server disconnect cleanup.
- [ ] **Step 6: Add commands:** `/fiven trigger visualize on`, `off`, `on <id>`, `off <id>` and the same `/fiven_trigger` aliases. All remain permission-level 2.
- [ ] **Step 7: Validate unknown zone IDs before enabling/hiding a single zone.** Return a useful error instead of silently storing invalid selections.

---

### Task 4: Render selected trigger zones on only the subscribed director client

**Files:**
- Create: `src/main/java/ru/fifth/horror/client/TriggerZoneVisualizationClient.java`
- Modify: `src/main/java/ru/fifth/horror/client/FifthClient.java`

**Interfaces:**
- Consumes: S2C `fiven:trigger_zone_visualization` snapshot.
- Produces: client-only in-memory zone rows and `WorldRenderEvents.AFTER_ENTITIES` debug renderer.

- [ ] **Step 1: Register the S2C receiver in client initialization.** A clear payload empties all rows; a snapshot replaces them atomically on the client thread.
- [ ] **Step 2: Render only rows matching the client's current dimension.** Never render stale rows from another dimension.
- [ ] **Step 3: Draw a translucent world-space cuboid and strong outline.** Translate by camera position so geometry stays fixed in the world; rendering is read-only and has no collision effect.
- [ ] **Step 4: Draw a billboard label near the top center.** Format: `id | MODE | current/min`; append `OFF` for disabled zones. Use the entity-render-dispatcher camera rotation so the label faces the director.
- [ ] **Step 5: Clear client snapshot when leaving a world/server.** This prevents visualization leaking between sessions.
- [ ] **Step 6: Compile with the exact Fabric API/Yarn version in the project.** Fix API signature mismatches rather than weakening the feature.

---

### Task 5: Regression verification and production artifact

**Files:**
- Modify as needed only in files already touched above.
- Update: PR #6 body with final validation and commands.

**Interfaces:**
- Produces: passing Java 17 CI run and downloadable `Fiven-Horror-Engine-0.6.0-alpha.jar`.

- [ ] **Step 1: Run full GitHub Actions build.** Required command: `./gradlew clean build --stacktrace --no-daemon` on Java 17.
- [ ] **Step 2: Confirm JUnit tests execute, not only compile.** The workflow job must show successful `test` as part of `build`.
- [ ] **Step 3: Inspect the production artifact contents.** Confirm `TriggerOccupancyPolicy`, `TriggerVisualizationSelection`, `TriggerZoneVisualizationServer`, `TriggerZoneVisualizationClient`, updated trigger-zone classes, and the existing mixin refmap are present.
- [ ] **Step 4: Re-check PR #6 metadata.** It must remain open/unmerged and based on PR #5's feature branch.
- [ ] **Step 5: Download the successful `fivens-build` artifact and expose the production `.jar` to the user.**
- [ ] **Step 6: Report runtime caveat accurately.** CI/build proves compilation/tests/artifact composition; actual multiplayer world-space appearance still needs an in-game run, so do not claim visual runtime confirmation without that evidence.
