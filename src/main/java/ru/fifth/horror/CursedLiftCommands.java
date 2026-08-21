package ru.fifth.horror;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import ru.fifth.horror.block.LiftBlockEntity;
import ru.fifth.horror.lift.CursedLiftEventManager;
import ru.fifth.horror.lift.LiftManager;

/** Authoring commands for persistent scripted/cutscene events on cursed lift arrival. */
public final class CursedLiftCommands implements ModInitializer {
    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                CommandManager.literal("fiven")
                        .then(CommandManager.literal("lift-event")
                                .requires(source -> source.hasPermissionLevel(2))
                                .then(CommandManager.literal("bind-cutscene")
                                        .then(CommandManager.argument("floor", IntegerArgumentType.integer(1, 9))
                                                .then(CommandManager.argument("id", StringArgumentType.word())
                                                        .executes(ctx -> bindCutscene(ctx.getSource(),
                                                                IntegerArgumentType.getInteger(ctx, "floor"),
                                                                StringArgumentType.getString(ctx, "id"))))))
                                .then(CommandManager.literal("bind-script")
                                        .then(CommandManager.argument("floor", IntegerArgumentType.integer(1, 9))
                                                .then(CommandManager.argument("name", StringArgumentType.word())
                                                        .executes(ctx -> bindScript(ctx.getSource(),
                                                                IntegerArgumentType.getInteger(ctx, "floor"),
                                                                StringArgumentType.getString(ctx, "name"))))))
                                .then(CommandManager.literal("bind-trigger")
                                        .then(CommandManager.argument("floor", IntegerArgumentType.integer(1, 9))
                                                .then(CommandManager.argument("name", StringArgumentType.word())
                                                        .executes(ctx -> bindTrigger(ctx.getSource(),
                                                                IntegerArgumentType.getInteger(ctx, "floor"),
                                                                StringArgumentType.getString(ctx, "name"))))))
                                .then(CommandManager.literal("clear")
                                        .then(CommandManager.argument("floor", IntegerArgumentType.integer(1, 9))
                                                .executes(ctx -> clear(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "floor")))))
                                .then(CommandManager.literal("status")
                                        .then(CommandManager.argument("floor", IntegerArgumentType.integer(1, 9))
                                                .executes(ctx -> status(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "floor"))))))));
    }

    private static LiftBlockEntity nearest(ServerCommandSource source) {
        try {
            ServerPlayerEntity player = source.getPlayerOrThrow();
            return LiftManager.nearestLift(player, 48.0);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static int bindCutscene(ServerCommandSource source, int floor, String id) {
        LiftBlockEntity lift = nearest(source);
        if (lift == null) return missing(source);
        if (!CursedLiftEventManager.bindCutscene(source.getServer(), lift, floor, id)) {
            source.sendError(Text.literal("Катсцена не найдена: " + id));
            return 0;
        }
        source.sendFeedback(() -> Text.literal("§8[§cFiven§8] §7Проклятый лифт §f" + lift.getLiftId() + "§7: этаж §c" + floor + " §7→ катсцена §f" + id), false);
        return 1;
    }

    private static int bindScript(ServerCommandSource source, int floor, String name) {
        LiftBlockEntity lift = nearest(source);
        if (lift == null) return missing(source);
        CursedLiftEventManager.bindScript(source.getServer(), lift, floor, name);
        source.sendFeedback(() -> Text.literal("§8[§cFiven§8] §7Проклятый лифт §f" + lift.getLiftId() + "§7: этаж §c" + floor + " §7→ сценарий §f" + name), false);
        return 1;
    }

    private static int bindTrigger(ServerCommandSource source, int floor, String name) {
        LiftBlockEntity lift = nearest(source);
        if (lift == null) return missing(source);
        CursedLiftEventManager.bindTrigger(source.getServer(), lift, floor, name);
        source.sendFeedback(() -> Text.literal("§8[§cFiven§8] §7Проклятый лифт §f" + lift.getLiftId() + "§7: этаж §c" + floor + " §7→ trigger §f" + name), false);
        return 1;
    }

    private static int clear(ServerCommandSource source, int floor) {
        LiftBlockEntity lift = nearest(source);
        if (lift == null) return missing(source);
        int removed = CursedLiftEventManager.clear(source.getServer(), lift, floor);
        source.sendFeedback(() -> Text.literal("§8[§cFiven§8] §7События этажа §c" + floor + (removed > 0 ? " §7удалены." : " §7не были настроены.")), false);
        return 1;
    }

    private static int status(ServerCommandSource source, int floor) {
        LiftBlockEntity lift = nearest(source);
        if (lift == null) return missing(source);
        var config = CursedLiftEventManager.get(source.getServer(), lift, floor);
        source.sendFeedback(() -> Text.literal("§8[§cFiven§8] §7Этаж §c" + floor + "§7: " + (config == null ? "событий нет" : config.describe())), false);
        return 1;
    }

    private static int missing(ServerCommandSource source) {
        source.sendError(Text.literal("Рядом не найден физический лифт Fiven."));
        return 0;
    }
}
