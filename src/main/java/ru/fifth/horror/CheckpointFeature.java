package ru.fifth.horror;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import ru.fifth.horror.checkpoint.CheckpointManager;
import ru.fifth.horror.entity.MflDeathSequenceManager;
import ru.fifth.horror.item.CheckpointToolItem;
import ru.fifth.horror.script.FifthScriptEngine;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Shared checkpoint commands/tool, game-start state and restart visual marker. */
public final class CheckpointFeature implements ModInitializer {
    public static final Identifier CLIENT_RESET = FifthMod.id("checkpoint_client_reset");
    public static final Item CHECKPOINT_TOOL = Registry.register(Registries.ITEM, FifthMod.id("checkpoint_tool"),
            new CheckpointToolItem(new Item.Settings().maxCount(1)));
    private static final Set<UUID> VISUAL = ConcurrentHashMap.newKeySet();
    private static long visualTick;

    @Override
    public void onInitialize() {
        ItemGroupEvents.modifyEntriesEvent(FifthMod.FIFTH_ITEM_GROUP_KEY).register(entries -> entries.add(CHECKPOINT_TOOL));
        ServerLifecycleEvents.SERVER_STARTED.register(CheckpointManager::load);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            MflDeathSequenceManager.tick(server);
            tickVisualization(server);
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(startTree("fiven_start"));
            dispatcher.register(CommandManager.literal("fiven")
                    .then(startTree("start"))
                    .then(checkpointTree()));
        });
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> startTree(String literal) {
        return CommandManager.literal(literal).requires(s -> s.hasPermissionLevel(2)).executes(ctx -> {
            CheckpointManager.markGameStarted(ctx.getSource().getServer());
            FifthScriptEngine.emitTrigger(ctx.getSource().getServer(), "start");
            ctx.getSource().sendFeedback(() -> Text.literal("§8[§cFiven§8] §aИгра запущена§7; общий checkpoint будет использован при смерти MFL."), false);
            return 1;
        });
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> checkpointTree() {
        return CommandManager.literal("checkpoint").requires(s -> s.hasPermissionLevel(2))
                .then(CommandManager.literal("tool").executes(ctx -> giveTool(ctx.getSource())))
                .then(CommandManager.literal("set")
                        .then(CommandManager.argument("id", StringArgumentType.word())
                                .executes(ctx -> set(ctx.getSource(), StringArgumentType.getString(ctx, "id")))))
                .then(CommandManager.literal("activate")
                        .then(CommandManager.argument("id", StringArgumentType.word())
                                .executes(ctx -> activate(ctx.getSource(), StringArgumentType.getString(ctx, "id")))))
                .then(CommandManager.literal("current").executes(ctx -> current(ctx.getSource())))
                .then(CommandManager.literal("list").executes(ctx -> list(ctx.getSource())))
                .then(CommandManager.literal("delete")
                        .then(CommandManager.argument("id", StringArgumentType.word())
                                .executes(ctx -> delete(ctx.getSource(), StringArgumentType.getString(ctx, "id")))))
                .then(CommandManager.literal("respawn").executes(ctx -> respawn(ctx.getSource())))
                .then(CommandManager.literal("visualize")
                        .then(CommandManager.literal("on").executes(ctx -> visualize(ctx.getSource(), true)))
                        .then(CommandManager.literal("off").executes(ctx -> visualize(ctx.getSource(), false))));
    }

    private static int giveTool(ServerCommandSource source) {
        ServerPlayerEntity player = player(source); if (player == null) return 0;
        player.giveItemStack(CHECKPOINT_TOOL.getDefaultStack());
        source.sendFeedback(() -> Text.literal("§8[§cFiven§8] §7Инструмент контрольных точек выдан."), false);
        return 1;
    }

    private static int set(ServerCommandSource source, String id) {
        ServerPlayerEntity player = player(source); if (player == null) return 0;
        var cp = CheckpointManager.set(source.getServer(), id, player);
        source.sendFeedback(() -> Text.literal("§8[§cFiven§8] §7Checkpoint §f" + cp.id + " §7сохранён: §f" + cp.positionText()), false);
        return 1;
    }

    private static int activate(ServerCommandSource source, String id) {
        if (!CheckpointManager.activate(source.getServer(), id)) { source.sendError(Text.literal("Checkpoint не найден: " + id)); return 0; }
        source.sendFeedback(() -> Text.literal("§8[§cFiven§8] §aОбщий checkpoint активирован: §f" + id), false);
        return 1;
    }

    private static int current(ServerCommandSource source) {
        var cp = CheckpointManager.current(source.getServer());
        if (cp == null) { source.sendError(Text.literal("Общий checkpoint не выбран.")); return 0; }
        source.sendFeedback(() -> Text.literal("§8[§cFiven§8] §7Текущий: §f" + cp.id + " §8[§7" + cp.world + " / " + cp.positionText() + "§8]"), false);
        return 1;
    }

    private static int list(ServerCommandSource source) {
        var list = CheckpointManager.list(source.getServer());
        source.sendFeedback(() -> Text.literal("§8[§cFiven§8] §7Контрольных точек: §f" + list.size()), false);
        for (var cp : list) source.sendFeedback(() -> Text.literal(" §8• §f" + cp.id + " §8— §7" + cp.world + " / " + cp.positionText()), false);
        return Math.max(1, list.size());
    }

    private static int delete(ServerCommandSource source, String id) {
        if (!CheckpointManager.delete(source.getServer(), id)) { source.sendError(Text.literal("Checkpoint не найден: " + id)); return 0; }
        source.sendFeedback(() -> Text.literal("§8[§cFiven§8] §7Checkpoint удалён: §f" + id), false);
        return 1;
    }

    private static int respawn(ServerCommandSource source) {
        boolean ok = CheckpointManager.restart(source.getServer());
        if (!ok) { source.sendError(Text.literal("Не удалось восстановить checkpoint: выбери его через /fiven checkpoint activate <id>.")); return 0; }
        source.sendFeedback(() -> Text.literal("§8[§cFiven§8] §aСостояние Fiven восстановлено с общей контрольной точки."), false);
        return 1;
    }

    private static int visualize(ServerCommandSource source, boolean on) {
        ServerPlayerEntity player = player(source); if (player == null) return 0;
        if (on) VISUAL.add(player.getUuid()); else VISUAL.remove(player.getUuid());
        source.sendFeedback(() -> Text.literal("§8[§cFiven§8] §7Checkpoint-визуализация: " + (on ? "§aВКЛ" : "§cВЫКЛ")), false);
        return 1;
    }

    private static void tickVisualization(MinecraftServer server) {
        if (++visualTick % 20 != 0 || VISUAL.isEmpty()) return;
        for (UUID uuid : Set.copyOf(VISUAL)) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player == null) { VISUAL.remove(uuid); continue; }
            var cp = CheckpointManager.current(server); if (cp == null) continue;
            if (!player.getServerWorld().getRegistryKey().getValue().toString().equals(cp.world)) continue;
            String command = String.format(Locale.ROOT,
                    "particle minecraft:end_rod %.3f %.3f %.3f 0.25 0.45 0.25 0.01 5 force @s", cp.x, cp.y + .55, cp.z);
            try { server.getCommandManager().executeWithPrefix(player.getCommandSource().withLevel(4), command); } catch (Throwable ignored) {}
            if (visualTick % 40 == 0) player.sendMessage(Text.literal("§8[§cFiven§8] §7CHECKPOINT: §f" + cp.id), true);
        }
    }

    private static ServerPlayerEntity player(ServerCommandSource source) {
        try { return source.getPlayerOrThrow(); }
        catch (Exception e) { source.sendError(Text.literal("Команда доступна только игроку.")); return null; }
    }
}
