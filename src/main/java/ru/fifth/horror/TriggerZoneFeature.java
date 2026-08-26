package ru.fifth.horror;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import ru.fifth.horror.cutscene.CutsceneManager;
import ru.fifth.horror.item.TriggerZoneToolItem;
import ru.fifth.horror.script.FifthScriptEngine;
import ru.fifth.horror.trigger.TriggerZoneManager;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** Trigger-zone director feature plus compact map-runtime commands/aliases. */
public final class TriggerZoneFeature implements ModInitializer {
    public static final Item TRIGGER_ZONE_TOOL = Registry.register(
            Registries.ITEM,
            FifthMod.id("trigger_zone_tool"),
            new TriggerZoneToolItem(new Item.Settings().maxCount(1))
    );

    @Override
    public void onInitialize() {
        ItemGroupEvents.modifyEntriesEvent(FifthMod.FIFTH_ITEM_GROUP_KEY).register(entries -> entries.add(TRIGGER_ZONE_TOOL));
        ServerLifecycleEvents.SERVER_STARTED.register(TriggerZoneManager::load);
        ServerTickEvents.END_SERVER_TICK.register(TriggerZoneManager::tick);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            // Requested standalone aliases.
            dispatcher.register(startTree("fiven_start"));
            dispatcher.register(cutsceneTree("fiven_catscene"));
            dispatcher.register(cutsceneTree("fiven_cutscene"));
            dispatcher.register(cutsceneTree("fiven_cs"));
            dispatcher.register(eventTree("fiven_event"));
            dispatcher.register(eventTree("fiven_evt"));
            dispatcher.register(scriptTree("fiven_script"));
            dispatcher.register(triggerTree("fiven_trigger"));

            // The same actions are also available under the normal /fiven namespace.
            dispatcher.register(CommandManager.literal("fiven")
                    .then(startTree("start"))
                    .then(cutsceneTree("catscene"))
                    .then(cutsceneTree("cutscene"))
                    .then(cutsceneTree("cs"))
                    .then(eventTree("event"))
                    .then(scriptTree("script"))
                    .then(triggerTree("trigger")));
        });
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> startTree(String literal) {
        return CommandManager.literal(literal)
                .requires(source -> source.hasPermissionLevel(2))
                .executes(ctx -> {
                    FifthScriptEngine.emitTrigger(ctx.getSource().getServer(), "start");
                    ctx.getSource().sendFeedback(() -> Text.literal("§8[§cFiven§8] §aСобытие start отправлено сценариям."), false);
                    return 1;
                });
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> cutsceneTree(String literal) {
        return CommandManager.literal(literal)
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.argument("name", StringArgumentType.string())
                        .executes(ctx -> playCutscene(ctx.getSource(), StringArgumentType.getString(ctx, "name"), defaultTargets(ctx.getSource())))
                        .then(CommandManager.argument("targets", EntityArgumentType.players())
                                .executes(ctx -> playCutscene(ctx.getSource(), StringArgumentType.getString(ctx, "name"), EntityArgumentType.getPlayers(ctx, "targets")))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> eventTree(String literal) {
        return CommandManager.literal(literal)
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.argument("name", StringArgumentType.word()).executes(ctx -> {
                    String name = StringArgumentType.getString(ctx, "name");
                    FifthScriptEngine.emitTrigger(ctx.getSource().getServer(), name);
                    ctx.getSource().sendFeedback(() -> Text.literal("§8[§cFiven§8] §7Событие §f" + name + " §7отправлено сценариям."), false);
                    return 1;
                }));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> scriptTree(String literal) {
        return CommandManager.literal(literal)
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.argument("name", StringArgumentType.word())
                        .executes(ctx -> {
                            ServerPlayerEntity player = sourcePlayer(ctx.getSource());
                            FifthScriptEngine.runNamed(ctx.getSource().getServer(), StringArgumentType.getString(ctx, "name"), player);
                            return 1;
                        })
                        .then(CommandManager.argument("player", EntityArgumentType.player()).executes(ctx -> {
                            ServerPlayerEntity player = EntityArgumentType.getPlayer(ctx, "player");
                            FifthScriptEngine.runNamed(ctx.getSource().getServer(), StringArgumentType.getString(ctx, "name"), player);
                            return 1;
                        })));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> triggerTree(String literal) {
        return CommandManager.literal(literal)
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("tool").executes(ctx -> giveTool(ctx.getSource())))
                .then(CommandManager.literal("list").executes(ctx -> listZones(ctx.getSource())))
                .then(CommandManager.literal("info")
                        .then(CommandManager.argument("id", StringArgumentType.word()).executes(ctx -> info(ctx.getSource(), StringArgumentType.getString(ctx, "id")))))
                .then(CommandManager.literal("create")
                        .then(CommandManager.argument("id", StringArgumentType.word())
                                .then(CommandManager.argument("command", StringArgumentType.greedyString())
                                        .executes(ctx -> createZone(ctx.getSource(), StringArgumentType.getString(ctx, "id"), StringArgumentType.getString(ctx, "command"))))))
                .then(CommandManager.literal("delete")
                        .then(CommandManager.argument("id", StringArgumentType.word()).executes(ctx -> deleteZone(ctx.getSource(), StringArgumentType.getString(ctx, "id")))))
                .then(CommandManager.literal("mode")
                        .then(CommandManager.argument("id", StringArgumentType.word())
                                .then(CommandManager.argument("mode", StringArgumentType.word())
                                        .executes(ctx -> mode(ctx.getSource(), StringArgumentType.getString(ctx, "id"), StringArgumentType.getString(ctx, "mode"))))))
                .then(CommandManager.literal("cooldown")
                        .then(CommandManager.argument("id", StringArgumentType.word())
                                .then(CommandManager.argument("ticks", IntegerArgumentType.integer(0, 72_000))
                                        .executes(ctx -> cooldown(ctx.getSource(), StringArgumentType.getString(ctx, "id"), IntegerArgumentType.getInteger(ctx, "ticks"))))))
                .then(CommandManager.literal("once")
                        .then(CommandManager.argument("id", StringArgumentType.word())
                                .then(CommandManager.argument("value", BoolArgumentType.bool())
                                        .executes(ctx -> once(ctx.getSource(), StringArgumentType.getString(ctx, "id"), BoolArgumentType.getBool(ctx, "value"))))))
                .then(CommandManager.literal("enable")
                        .then(CommandManager.argument("id", StringArgumentType.word())
                                .then(CommandManager.argument("value", BoolArgumentType.bool())
                                        .executes(ctx -> enable(ctx.getSource(), StringArgumentType.getString(ctx, "id"), BoolArgumentType.getBool(ctx, "value"))))))
                .then(CommandManager.literal("fire")
                        .then(CommandManager.argument("id", StringArgumentType.word())
                                .executes(ctx -> fire(ctx.getSource(), StringArgumentType.getString(ctx, "id"), defaultTargets(ctx.getSource())))
                                .then(CommandManager.argument("targets", EntityArgumentType.players())
                                        .executes(ctx -> fire(ctx.getSource(), StringArgumentType.getString(ctx, "id"), EntityArgumentType.getPlayers(ctx, "targets"))))));
    }

    private static int giveTool(ServerCommandSource source) {
        ServerPlayerEntity player = sourcePlayer(source);
        if (player == null) {
            source.sendError(Text.literal("Эту команду нужно выполнить игроком."));
            return 0;
        }
        player.giveItemStack(TRIGGER_ZONE_TOOL.getDefaultStack());
        source.sendFeedback(() -> Text.literal("§8[§cFiven§8] §7Инструмент триггер-зон выдан."), false);
        return 1;
    }

    private static int createZone(ServerCommandSource source, String id, String command) {
        ServerPlayerEntity player = sourcePlayer(source);
        if (player == null) {
            source.sendError(Text.literal("Зону нужно создавать игроком с инструментом в руке."));
            return 0;
        }
        ItemStack stack = selectedTool(player);
        if (stack == null) {
            source.sendError(Text.literal("Возьми «Триггер-зоны» в основную или вторую руку."));
            return 0;
        }
        BlockPos a = TriggerZoneToolItem.posA(stack);
        BlockPos b = TriggerZoneToolItem.posB(stack);
        if (a == null || b == null) {
            source.sendError(Text.literal("Сначала выбери A обычным ПКМ и B через Shift+ПКМ."));
            return 0;
        }
        String world = player.getServerWorld().getRegistryKey().getValue().toString();
        if (!world.equals(TriggerZoneToolItem.world(stack))) {
            source.sendError(Text.literal("Выделение сделано в другом измерении. Выбери A/B заново."));
            return 0;
        }

        TriggerZoneManager.Zone zone = TriggerZoneManager.put(source.getServer(), player.getServerWorld(), id, a, b,
                command, TriggerZoneManager.Mode.ENTER, 10, false);
        source.sendFeedback(() -> Text.literal("§8[§cFiven§8] §aТриггер-зона §f" + zone.id + " §aсохранена §8["
                + zone.sizeText() + "]§7. Режим ENTER, cooldown 10 ticks."), false);
        return 1;
    }

    private static int deleteZone(ServerCommandSource source, String id) {
        boolean ok = TriggerZoneManager.delete(source.getServer(), id);
        if (!ok) { source.sendError(Text.literal("Триггер-зона не найдена: " + id)); return 0; }
        source.sendFeedback(() -> Text.literal("§8[§cFiven§8] §7Триггер-зона §f" + id + " §7удалена."), false);
        return 1;
    }

    private static int mode(ServerCommandSource source, String id, String value) {
        TriggerZoneManager.Mode mode;
        try { mode = TriggerZoneManager.Mode.valueOf(value.toUpperCase(Locale.ROOT)); }
        catch (Exception e) { source.sendError(Text.literal("Режим: enter, exit или stay.")); return 0; }
        if (!TriggerZoneManager.setMode(source.getServer(), id, mode)) { source.sendError(Text.literal("Зона не найдена: " + id)); return 0; }
        source.sendFeedback(() -> Text.literal("§8[§cFiven§8] §7" + id + ": режим §f" + mode), false);
        return 1;
    }

    private static int cooldown(ServerCommandSource source, String id, int ticks) {
        if (!TriggerZoneManager.setCooldown(source.getServer(), id, ticks)) { source.sendError(Text.literal("Зона не найдена: " + id)); return 0; }
        source.sendFeedback(() -> Text.literal("§8[§cFiven§8] §7" + id + ": cooldown §f" + ticks + " ticks"), false);
        return 1;
    }

    private static int once(ServerCommandSource source, String id, boolean value) {
        if (!TriggerZoneManager.setOnce(source.getServer(), id, value)) { source.sendError(Text.literal("Зона не найдена: " + id)); return 0; }
        source.sendFeedback(() -> Text.literal("§8[§cFiven§8] §7" + id + ": одноразовая = §f" + value), false);
        return 1;
    }

    private static int enable(ServerCommandSource source, String id, boolean value) {
        if (!TriggerZoneManager.setEnabled(source.getServer(), id, value)) { source.sendError(Text.literal("Зона не найдена: " + id)); return 0; }
        source.sendFeedback(() -> Text.literal("§8[§cFiven§8] §7" + id + ": enabled = §f" + value), false);
        return 1;
    }

    private static int fire(ServerCommandSource source, String id, Collection<ServerPlayerEntity> targets) {
        if (TriggerZoneManager.get(source.getServer(), id) == null) { source.sendError(Text.literal("Зона не найдена: " + id)); return 0; }
        int count = 0;
        for (ServerPlayerEntity player : targets) if (TriggerZoneManager.fire(source.getServer(), id, player)) count++;
        final int fired = count;
        source.sendFeedback(() -> Text.literal("§8[§cFiven§8] §7Триггер §f" + id + " §7выполнен для §f" + fired + " §7игрок(ов)."), false);
        return count;
    }

    private static int listZones(ServerCommandSource source) {
        Collection<TriggerZoneManager.Zone> zones = TriggerZoneManager.list(source.getServer());
        if (zones.isEmpty()) {
            source.sendFeedback(() -> Text.literal("§8[§cFiven§8] §7Триггер-зон пока нет."), false);
            return 1;
        }
        source.sendFeedback(() -> Text.literal("§8[§cFiven§8] §7Триггер-зоны: §f" + zones.size()), false);
        for (TriggerZoneManager.Zone zone : zones) {
            source.sendFeedback(() -> Text.literal("§8• §f" + zone.id + " §7[" + zone.mode + ", " + zone.sizeText()
                    + ", " + (zone.enabled ? "ON" : "OFF") + "] §8→ §7/" + zone.command), false);
        }
        return zones.size();
    }

    private static int info(ServerCommandSource source, String id) {
        TriggerZoneManager.Zone zone = TriggerZoneManager.get(source.getServer(), id);
        if (zone == null) { source.sendError(Text.literal("Зона не найдена: " + id)); return 0; }
        source.sendFeedback(() -> Text.literal("§8[§cFiven§8] §f" + zone.id + " §7| " + zone.world + " | " + zone.sizeText()
                + " | " + zone.mode + " | cd=" + zone.cooldownTicks + " | once=" + zone.once + " | enabled=" + zone.enabled
                + " §8→ §7/" + zone.command), false);
        return 1;
    }

    private static int playCutscene(ServerCommandSource source, String name, Collection<ServerPlayerEntity> targets) {
        if (CutsceneManager.load(source.getServer(), name) == null) {
            source.sendError(Text.literal("Катсцена не найдена: " + name));
            return 0;
        }
        int count = CutsceneManager.play(source.getServer(), name, targets);
        source.sendFeedback(() -> Text.literal("§8[§cFiven§8] §7Катсцена §f" + name + " §7запущена для §f" + count + " §7игрок(ов)."), false);
        return count;
    }

    private static Collection<ServerPlayerEntity> defaultTargets(ServerCommandSource source) {
        ServerPlayerEntity player = sourcePlayer(source);
        return player == null ? List.copyOf(source.getServer().getPlayerManager().getPlayerList()) : List.of(player);
    }

    private static ServerPlayerEntity sourcePlayer(ServerCommandSource source) {
        try { return source.getPlayerOrThrow(); }
        catch (Exception ignored) { return null; }
    }

    private static ItemStack selectedTool(ServerPlayerEntity player) {
        if (player.getMainHandStack().isOf(TRIGGER_ZONE_TOOL)) return player.getMainHandStack();
        if (player.getOffHandStack().isOf(TRIGGER_ZONE_TOOL)) return player.getOffHandStack();
        return null;
    }
}
