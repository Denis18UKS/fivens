package ru.fifth.horror.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import ru.fifth.horror.FifthMod;
import ru.fifth.horror.block.ScriptComputerBlockEntity;
import ru.fifth.horror.cutscene.CutsceneDefinition;
import ru.fifth.horror.cutscene.CutsceneManager;
import ru.fifth.horror.effect.EntityEffectManager;
import ru.fifth.horror.entity.DirectorNpcEntity;
import ru.fifth.horror.entity.LiftEntity;
import ru.fifth.horror.entity.LiftPanelEntity;
import ru.fifth.horror.lift.LiftManager;
import ru.fifth.horror.script.FifthScriptEngine;
import ru.fifth.horror.structure.StructureLayerManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class FifthNetworking {
    private static final Gson GSON = new Gson();

    public static final Identifier OPEN_COMPUTER = FifthMod.id("open_computer");
    public static final Identifier SAVE_COMPUTER = FifthMod.id("save_computer");
    public static final Identifier RUN_COMPUTER = FifthMod.id("run_computer");
    public static final Identifier CREATE_NPC_EGG = FifthMod.id("create_npc_egg");
    public static final Identifier SAVE_NPC = FifthMod.id("save_npc");
    public static final Identifier STRUCTURE_CAPTURE = FifthMod.id("structure_capture");
    public static final Identifier STRUCTURE_ACTIVATE = FifthMod.id("structure_activate");
    public static final Identifier SAVE_CUTSCENE = FifthMod.id("save_cutscene");
    public static final Identifier PLAY_CUTSCENE = FifthMod.id("play_cutscene");
    public static final Identifier CUTSCENE_PAYLOAD = FifthMod.id("cutscene_payload");
    public static final Identifier CUTSCENE_END_TELEPORT = FifthMod.id("cutscene_end_teleport");
    public static final Identifier REQUEST_NPC_LIBRARY = FifthMod.id("request_npc_library");
    public static final Identifier NPC_LIBRARY_PAYLOAD = FifthMod.id("npc_library_payload");
    public static final Identifier PREVIEW_NPC_ANIMATION = FifthMod.id("preview_npc_animation");
    public static final Identifier NPC_CONTROL = FifthMod.id("npc_control");
    public static final Identifier MFL_CONTROL = FifthMod.id("mfl_control");
    public static final Identifier REQUEST_CUTSCENE_LIBRARY = FifthMod.id("request_cutscene_library");
    public static final Identifier CUTSCENE_LIBRARY_PAYLOAD = FifthMod.id("cutscene_library_payload");
    public static final Identifier REQUEST_CUTSCENE_EDIT = FifthMod.id("request_cutscene_edit");
    public static final Identifier CUTSCENE_EDIT_PAYLOAD = FifthMod.id("cutscene_edit_payload");
    public static final Identifier CREATE_CASSETTE = FifthMod.id("create_cassette");
    public static final Identifier VHS_PLAYBACK = FifthMod.id("vhs_playback");
    public static final Identifier SAVE_ANIMATION_CONDITION = FifthMod.id("save_animation_condition");
    public static final Identifier OPEN_LIFT_PANEL = FifthMod.id("open_lift_panel");
    public static final Identifier LIFT_PANEL_CONTROL = FifthMod.id("lift_panel_control");
    public static final Identifier LIFT_TRAVEL_START = FifthMod.id("lift_travel_start");
    public static final Identifier LIFT_TRAVEL_END = FifthMod.id("lift_travel_end");
    public static final Identifier OPEN_LIFT_EDITOR = FifthMod.id("open_lift_editor");
    public static final Identifier SAVE_LIFT_CONFIG = FifthMod.id("save_lift_config");
    public static final Identifier LIFT_PANEL_TRANSFORM = FifthMod.id("lift_panel_transform");
    public static final Identifier ENTITY_EFFECT_SAVE = FifthMod.id("entity_effect_save");
    public static final Identifier ENTITY_EFFECT_SYNC = FifthMod.id("entity_effect_sync");
    public static final Identifier SAVE_TV_CONFIG = FifthMod.id("save_tv_config");

    private FifthNetworking() {}

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(SAVE_LIFT_CONFIG, (server, player, handler, buf, responseSender) -> {
            int id = buf.readVarInt();
            int floor = buf.readVarInt();
            int mask = buf.readVarInt();
            BlockPos stage = buf.readBlockPos();
            server.execute(() -> {
                if (!player.hasPermissionLevel(2)) return;
                if (player.getWorld().getEntityById(id) instanceof LiftEntity lift) {
                    lift.setCurrentFloor(floor);
                    lift.setTargetFloor(floor);
                    for (int f = 1; f <= 9; f++) lift.setOpenOnFloor(f, (mask & (1 << (f - 1))) != 0);
                    lift.setStageOrigin(stage);
                    player.sendMessage(Text.literal("§8[§cFiven§8] §7Лифт сохранён."), true);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(LIFT_PANEL_TRANSFORM, (server, player, handler, buf, responseSender) -> {
            int id = buf.readVarInt();
            double x = buf.readDouble(), y = buf.readDouble(), z = buf.readDouble();
            float yaw = buf.readFloat();
            server.execute(() -> {
                if (!player.hasPermissionLevel(2)) return;
                if (player.getWorld().getEntityById(id) instanceof LiftPanelEntity panel) {
                    panel.setPosition(x, y, z);
                    panel.setYaw(yaw);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(ENTITY_EFFECT_SAVE, (server, player, handler, buf, responseSender) -> {
            EntityEffectManager.Config cfg = EntityEffectManager.read(buf);
            server.execute(() -> {
                if (player.hasPermissionLevel(2)) EntityEffectManager.save(server, cfg);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(SAVE_TV_CONFIG, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            int quality = buf.readVarInt();
            float noise = buf.readFloat();
            boolean monochrome = buf.readBoolean();
            server.execute(() -> {
                if (!player.hasPermissionLevel(2)) return;
                if (player.getWorld().getBlockEntity(pos) instanceof ru.fifth.horror.block.TelevisionBlockEntity tv) {
                    tv.configure(quality, noise, monochrome);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(CREATE_NPC_EGG, (server, player, handler, buf, responseSender) -> {
            String json = buf.readString(32767);
            server.execute(() -> giveNpcEgg(player, json));
        });

        ServerPlayNetworking.registerGlobalReceiver(SAVE_NPC, (server, player, handler, buf, responseSender) -> {
            int entityId = buf.readVarInt();
            String json = buf.readString(32767);
            server.execute(() -> {
                if (player.getWorld().getEntityById(entityId) instanceof DirectorNpcEntity npc && player.hasPermissionLevel(2)) {
                    npc.applyTemplateJson(json);
                    FifthScriptEngine.indexNpc(npc);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(SAVE_COMPUTER, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            String name = buf.readString(128);
            String script = buf.readString(1_000_000);
            server.execute(() -> {
                if (!player.hasPermissionLevel(2)) return;
                if (player.getWorld().getBlockEntity(pos) instanceof ScriptComputerBlockEntity be) {
                    be.setScriptName(name);
                    be.setScript(script);
                    be.markDirty();
                    FifthScriptEngine.saveScript(server, name, script);
                    player.sendMessage(Text.literal("§7Сценарий сохранён: §f" + name), false);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(RUN_COMPUTER, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            String name = buf.readString(128);
            String script = buf.readString(1_000_000);
            boolean hasNpc = buf.readBoolean();
            java.util.UUID npcUuid = hasNpc ? buf.readUuid() : null;
            String npcId = hasNpc ? buf.readString(128) : "";
            server.execute(() -> {
                if (!player.hasPermissionLevel(2)) return;
                FifthScriptEngine.saveScript(server, name, script);
                if (player.getWorld().getBlockEntity(pos) instanceof ScriptComputerBlockEntity be) {
                    be.setScriptName(name);
                    be.setScript(script);
                    be.markDirty();
                }
                if (npcUuid != null) {
                    DirectorNpcEntity npc = FifthScriptEngine.findNpc(server, npcUuid, npcId);
                    if (npc == null) {
                        player.sendMessage(Text.literal("§cВыбранный NPC больше не найден: §f" + npcId), false);
                        return;
                    }
                    FifthScriptEngine.runNamedForNpc(server, name, player, npc);
                } else {
                    FifthScriptEngine.runNamed(server, name, player);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(STRUCTURE_CAPTURE, (server, player, handler, buf, responseSender) -> {
            String build = buf.readString(128);
            String variant = buf.readString(128);
            String group = buf.readString(128);
            boolean defaultActive = buf.readBoolean();
            boolean restoreOnLoad = buf.readBoolean();
            String floorSet = buf.readString(64);
            int floor = buf.readVarInt();
            BlockPos a = buf.readBlockPos();
            BlockPos b = buf.readBlockPos();
            server.execute(() -> {
                if (!player.hasPermissionLevel(2)) return;
                StructureLayerManager.capture(server, player.getServerWorld(), build, variant, group,
                        defaultActive, restoreOnLoad, floorSet, floor, a, b);
                player.sendMessage(Text.literal("§7Слой сохранён: §f" + build + "/" + variant +
                        (floor > 0 ? " §8[§f" + floorSet + ":" + floor + "§8]" : "")), false);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(STRUCTURE_ACTIVATE, (server, player, handler, buf, responseSender) -> {
            String build = buf.readString(128);
            String variant = buf.readString(128);
            server.execute(() -> {
                if (player.hasPermissionLevel(2)) {
                    StructureLayerManager.activate(server, player.getServerWorld(), build, variant);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(LIFT_PANEL_CONTROL, (server, player, handler, buf, responseSender) -> {
            int entityId = buf.readVarInt();
            String action = buf.readString(32);
            int floor = Math.max(1, Math.min(9, buf.readVarInt()));
            int value = buf.readVarInt();
            server.execute(() -> {
                if (!(player.getWorld().getEntityById(entityId) instanceof LiftPanelEntity panel)) return;
                LiftEntity lift = panel.getLiftUuid() == null ? null : LiftManager.findLift(server, panel.getLiftUuid());

                if ("door".equals(action) || "enable".equals(action)) {
                    if (!player.hasPermissionLevel(2)) return;
                    // Keep the panel's old mask for backwards-compatible saves, but the linked lift is authoritative.
                    panel.setEnabled(floor, value != 0);
                    if (lift != null) lift.setOpenOnFloor(floor, value != 0);
                    return;
                }

                if ("press".equals(action)) {
                    if (lift == null) {
                        player.sendMessage(Text.literal("§8[§cFiven§8] §7Панель не привязана к лифту."), true);
                        return;
                    }
                    LiftManager.travel(player, lift, floor);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(SAVE_ANIMATION_CONDITION, (server, player, handler, buf, responseSender) -> {
            String id = buf.readString(64);
            String uuid = buf.readString(64);
            String anim = buf.readString(256);
            String type = buf.readString(32);
            double value = buf.readDouble();
            server.execute(() -> {
                if (player.hasPermissionLevel(2)) {
                    ru.fifth.horror.animation.AnimationConditionManager.saveRule(server,
                            new ru.fifth.horror.animation.AnimationConditionManager.Rule(id, uuid, anim, type, value));
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(REQUEST_CUTSCENE_LIBRARY, (server, player, handler, buf, responseSender) ->
                server.execute(() -> {
                    if (!player.hasPermissionLevel(2)) return;
                    var list = CutsceneManager.list(server);
                    PacketByteBuf out = PacketByteBufs.create();
                    out.writeVarInt(list.size());
                    for (var info : list) {
                        out.writeString(info.id(), 128);
                        out.writeVarInt(info.frames());
                        out.writeVarInt(info.ticks());
                        out.writeBoolean(info.teleportAtEnd());
                    }
                    ServerPlayNetworking.send(player, CUTSCENE_LIBRARY_PAYLOAD, out);
                }));

        ServerPlayNetworking.registerGlobalReceiver(REQUEST_CUTSCENE_EDIT, (server, player, handler, buf, responseSender) -> {
            String id = buf.readString(128);
            server.execute(() -> {
                if (!player.hasPermissionLevel(2)) return;
                String json = CutsceneManager.json(server, id);
                if (json.isBlank()) return;
                PacketByteBuf out = PacketByteBufs.create();
                out.writeString(json, 1_000_000);
                ServerPlayNetworking.send(player, CUTSCENE_EDIT_PAYLOAD, out);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(CREATE_CASSETTE, (server, player, handler, buf, responseSender) -> {
            String id = buf.readString(128);
            server.execute(() -> {
                if (!player.hasPermissionLevel(2) || CutsceneManager.load(server, id) == null) return;
                ItemStack stack = new ItemStack(FifthMod.VHS_CASSETTE);
                ru.fifth.horror.item.VhsCassetteItem.setRecording(stack, id);
                player.giveItemStack(stack);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(SAVE_CUTSCENE, (server, player, handler, buf, responseSender) -> {
            String json = buf.readString(1_000_000);
            server.execute(() -> {
                if (player.hasPermissionLevel(2)) CutsceneManager.save(server, GSON.fromJson(json, CutsceneDefinition.class));
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(PLAY_CUTSCENE, (server, player, handler, buf, responseSender) -> {
            String id = buf.readString(128);
            server.execute(() -> {
                if (player.hasPermissionLevel(2)) CutsceneManager.play(server, id);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(CUTSCENE_END_TELEPORT, (server, player, handler, buf, responseSender) -> {
            String id = buf.readString(128);
            server.execute(() -> {
                CutsceneDefinition scene = CutsceneManager.load(server, id);
                if (scene == null || !scene.teleportPlayerAtEnd || scene.keyframes.isEmpty()) return;
                CutsceneDefinition.Keyframe end = scene.keyframes.get(scene.keyframes.size() - 1);
                double eyeOffset = player.getEyeY() - player.getY();
                player.teleport(player.getServerWorld(), end.x, end.y - eyeOffset, end.z, end.yaw, end.pitch);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(REQUEST_NPC_LIBRARY, (server, player, handler, buf, responseSender) ->
                server.execute(() -> sendNpcLibrary(player)));

        ServerPlayNetworking.registerGlobalReceiver(MFL_CONTROL, (server, player, handler, buf, responseSender) -> {
            int entityId = buf.readVarInt();
            String action = buf.readString(64);
            String arg = "animation".equals(action) ? buf.readString(128) : "";
            server.execute(() -> {
                if (!player.hasPermissionLevel(2)) return;
                if (!(player.getWorld().getEntityById(entityId) instanceof ru.fifth.horror.entity.MonsterForLiftEntity m)) return;
                switch (action) {
                    case "toggle_route" -> {
                        if (m.isRouteRunning()) m.stopRoute();
                        else m.startRoute(true, .72);
                    }
                    case "clear_route" -> m.clearRoute();
                    case "animation" -> m.preview(arg);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(NPC_CONTROL, (server, player, handler, buf, responseSender) -> {
            int entityId = buf.readVarInt();
            String action = buf.readString(64);
            buf.readString(256); // reserved argument for protocol compatibility
            server.execute(() -> {
                if (!player.hasPermissionLevel(2)) return;
                if (!(player.getWorld().getEntityById(entityId) instanceof DirectorNpcEntity npc)) return;
                FifthScriptEngine.indexNpc(npc);
                switch (action) {
                    case "toggle_ai" -> npc.setAiEnabled(!npc.isAiEnabled());
                    case "toggle_path" -> {
                        if (npc.isRouteRunning()) npc.stopPath();
                        else {
                            npc.setAiEnabled(true);
                            npc.followPath(true, 0.25);
                        }
                    }
                    case "start_path" -> {
                        npc.setAiEnabled(true);
                        npc.followPath(true, 0.25);
                    }
                    case "stop_path" -> npc.stopPath();
                    case "statue" -> npc.setAiEnabled(false);
                    case "start" -> npc.setAiEnabled(true);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(PREVIEW_NPC_ANIMATION, (server, player, handler, buf, responseSender) -> {
            int entityId = buf.readVarInt();
            String animationFile = buf.readString(512);
            String animation = buf.readString(512);
            server.execute(() -> {
                if (!player.hasPermissionLevel(2)) return;
                if (player.getWorld().getEntityById(entityId) instanceof DirectorNpcEntity npc) {
                    FifthScriptEngine.indexNpc(npc);
                    if (!animationFile.isBlank()) npc.setAnimationResource(animationFile);
                    npc.setCurrentAnimation(animation);
                }
            });
        });
    }

    private static void sendNpcLibrary(ServerPlayerEntity player) {
        if (!player.hasPermissionLevel(2)) return;
        record Row(DirectorNpcEntity npc, String world) {}
        List<Row> rows = new ArrayList<>();
        Box all = new Box(-30_000_000, -2048, -30_000_000, 30_000_000, 4096, 30_000_000);
        var server = player.getServerWorld().getServer();
        for (var world : server.getWorlds()) {
            String worldId = world.getRegistryKey().getValue().toString();
            for (DirectorNpcEntity npc : world.getEntitiesByClass(DirectorNpcEntity.class, all, n -> !n.isRemoved())) {
                FifthScriptEngine.indexNpc(npc);
                rows.add(new Row(npc, worldId));
            }
        }
        rows.sort(Comparator.comparing((Row row) -> row.npc().getNpcId(), String.CASE_INSENSITIVE_ORDER));
        PacketByteBuf out = PacketByteBufs.create();
        out.writeVarInt(Math.min(rows.size(), 2048));
        for (int i = 0; i < rows.size() && i < 2048; i++) {
            DirectorNpcEntity npc = rows.get(i).npc();
            out.writeUuid(npc.getUuid());
            out.writeString(npc.getNpcId(), 128);
            out.writeString(npc.getCustomName() == null ? npc.getNpcId() : npc.getCustomName().getString(), 256);
            out.writeBoolean(npc.isAiEnabled());
            out.writeString(npc.getAiScript(), 512);
            out.writeVarInt(npc.getPathPoints().size());
            out.writeString(rows.get(i).world(), 256);
        }
        ServerPlayNetworking.send(player, NPC_LIBRARY_PAYLOAD, out);
    }

    private static void giveNpcEgg(ServerPlayerEntity player, String json) {
        if (!player.hasPermissionLevel(2)) return;
        JsonObject object = GSON.fromJson(json, JsonObject.class);
        ItemStack stack = new ItemStack(FifthMod.NPC_SPAWN_EGG);
        stack.getOrCreateNbt().putString("FifthNpcTemplate", json);
        if (object != null && object.has("name")) {
            stack.setCustomName(Text.literal("NPC: " + object.get("name").getAsString()));
        }
        player.giveItemStack(stack);
    }

    public static void openLiftPanel(ServerPlayerEntity player, LiftPanelEntity panel, boolean edit) {
        int mask = panel.getEnabledMask();
        if (panel.getLiftUuid() != null) {
            LiftEntity lift = LiftManager.findLift(player.getServer(), panel.getLiftUuid());
            if (lift != null) mask = lift.getOpenFloorMask();
        }
        PacketByteBuf out = PacketByteBufs.create();
        out.writeVarInt(panel.getId());
        out.writeVarInt(mask);
        out.writeBoolean(edit);
        out.writeDouble(panel.getX());
        out.writeDouble(panel.getY());
        out.writeDouble(panel.getZ());
        out.writeFloat(panel.getYaw());
        ServerPlayNetworking.send(player, OPEN_LIFT_PANEL, out);
    }

    public static void openLiftEditor(ServerPlayerEntity player, LiftEntity lift) {
        PacketByteBuf out = PacketByteBufs.create();
        out.writeVarInt(lift.getId());
        out.writeVarInt(lift.getCurrentFloor());
        out.writeVarInt(lift.getOpenFloorMask());
        out.writeBlockPos(lift.getStageOrigin());
        ServerPlayNetworking.send(player, OPEN_LIFT_EDITOR, out);
    }

    public static void openComputer(ServerPlayerEntity player, ScriptComputerBlockEntity be) {
        PacketByteBuf out = PacketByteBufs.create();
        out.writeBlockPos(be.getPos());
        out.writeString(be.getScriptName());
        out.writeString(be.getScript(), 1_000_000);
        ServerPlayNetworking.send(player, OPEN_COMPUTER, out);
    }
}
