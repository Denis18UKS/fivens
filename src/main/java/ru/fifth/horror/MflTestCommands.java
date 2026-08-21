package ru.fifth.horror;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import ru.fifth.horror.entity.MflTestModeManager;
import ru.fifth.horror.entity.MonsterForLiftEntity;

import java.util.Comparator;

/** Director-only runtime commands for testing MFL horror behavior without changing map scripting. */
public final class MflTestCommands implements ModInitializer {
    private static final double SEARCH_RADIUS = 64.0;

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            var start = CommandManager.literal("start")
                    .executes(ctx -> startChase(ctx.getSource(), null))
                    .then(CommandManager.argument("player", EntityArgumentType.player())
                            .executes(ctx -> startChase(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"))));

            var chaseTest = CommandManager.literal("chase-test")
                    .then(start)
                    .then(CommandManager.literal("stop")
                            .executes(ctx -> stopChase(ctx.getSource())))
                    .then(CommandManager.literal("status")
                            .executes(ctx -> status(ctx.getSource())));

            var mfl = CommandManager.literal("mfl")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(CommandManager.literal("screamer")
                            .executes(ctx -> screamer(ctx.getSource())))
                    .then(chaseTest);

            dispatcher.register(CommandManager.literal("fiven").then(mfl));
        });
    }

    private static int screamer(ServerCommandSource source) {
        ServerPlayerEntity viewer = sourcePlayer(source);
        if (viewer == null) return 0;
        MonsterForLiftEntity mfl = nearest(viewer);
        if (mfl == null) return missing(source);

        mfl.triggerScreamer(viewer);
        source.sendFeedback(() -> Text.literal("§8[§cFiven§8] §7Тестовый MFL-скример запущен у §f" + shortId(mfl) + "§7."), false);
        return 1;
    }

    private static int startChase(ServerCommandSource source, ServerPlayerEntity explicitTarget) {
        ServerPlayerEntity director = sourcePlayer(source);
        if (director == null) return 0;
        MonsterForLiftEntity mfl = nearest(director);
        if (mfl == null) return missing(source);

        ServerPlayerEntity target = explicitTarget == null ? director : explicitTarget;
        if (target.getServerWorld() != mfl.getWorld()) {
            source.sendError(Text.literal("MFL и тестовый игрок должны находиться в одном измерении."));
            return 0;
        }

        MflTestModeManager.start(mfl, target);
        source.sendFeedback(() -> Text.literal("§8[§cFiven§8] §aCHASE TEST включён§7: MFL §f" + shortId(mfl)
                + " §7преследует §f" + target.getGameProfile().getName()
                + "§7. Creative разрешён; укрытия и потеря видимости учитываются."), false);
        return 1;
    }

    private static int stopChase(ServerCommandSource source) {
        ServerPlayerEntity director = sourcePlayer(source);
        if (director == null) return 0;
        MonsterForLiftEntity mfl = nearest(director);
        if (mfl == null) return missing(source);

        if (!MflTestModeManager.stop(mfl)) {
            source.sendError(Text.literal("У ближайшего MFL тест преследования не запущен."));
            return 0;
        }
        source.sendFeedback(() -> Text.literal("§8[§cFiven§8] §7CHASE TEST выключен; прежний режим MFL восстановлен."), false);
        return 1;
    }

    private static int status(ServerCommandSource source) {
        ServerPlayerEntity director = sourcePlayer(source);
        if (director == null) return 0;
        MonsterForLiftEntity mfl = nearest(director);
        if (mfl == null) return missing(source);

        MflTestModeManager.State state = MflTestModeManager.state(mfl);
        if (state == null) {
            source.sendFeedback(() -> Text.literal("§8[§cFiven§8] §7CHASE TEST: §cВЫКЛ§7 для ближайшего MFL."), false);
        } else {
            source.sendFeedback(() -> Text.literal("§8[§cFiven§8] §7CHASE TEST: §aВКЛ§7, target UUID: §f" + state.targetUuid), false);
        }
        return 1;
    }

    private static ServerPlayerEntity sourcePlayer(ServerCommandSource source) {
        try {
            return source.getPlayerOrThrow();
        } catch (Exception e) {
            source.sendError(Text.literal("Команда должна выполняться игроком рядом с MFL."));
            return null;
        }
    }

    private static MonsterForLiftEntity nearest(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld world)) return null;
        Box box = player.getBoundingBox().expand(SEARCH_RADIUS);
        return world.getEntitiesByClass(MonsterForLiftEntity.class, box, Entity::isAlive).stream()
                .min(Comparator.comparingDouble(player::squaredDistanceTo))
                .orElse(null);
    }

    private static int missing(ServerCommandSource source) {
        source.sendError(Text.literal("В радиусе 64 блоков не найден MFL."));
        return 0;
    }

    private static String shortId(MonsterForLiftEntity mfl) {
        String id = mfl.getUuidAsString();
        return id.substring(0, Math.min(8, id.length()));
    }
}
