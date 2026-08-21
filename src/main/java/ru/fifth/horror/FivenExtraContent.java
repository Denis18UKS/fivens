package ru.fifth.horror;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import ru.fifth.horror.block.ClockArmsBlock;
import ru.fifth.horror.block.LiftBlockEntity;
import ru.fifth.horror.effect.EntityEffectManager;
import ru.fifth.horror.lift.LiftManager;
import ru.fifth.horror.network.FifthNetworking;

/** Extra director utilities kept separate from the stable core bootstrap. */
public final class FivenExtraContent implements ModInitializer {
    public static final Block CLOCK_ARMS = Registry.register(Registries.BLOCK, FifthMod.id("clock_arms"),
            new ClockArmsBlock(AbstractBlock.Settings.create().strength(1.2f).nonOpaque()));
    public static final Item CLOCK_ARMS_ITEM = Registry.register(Registries.ITEM, FifthMod.id("clock_arms"),
            new BlockItem(CLOCK_ARMS, new Item.Settings().maxCount(64)));

    @Override
    public void onInitialize() {
        ItemGroupEvents.modifyEntriesEvent(FifthMod.FIFTH_ITEM_GROUP_KEY).register(entries -> entries.add(CLOCK_ARMS_ITEM));
        registerCommands();
    }

    private static void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            var pitch = CommandManager.argument("pitch", DoubleArgumentType.doubleArg(-90.0, 90.0))
                    .executes(ctx -> teleportExact(ctx.getSource(),
                            DoubleArgumentType.getDouble(ctx, "x"),
                            DoubleArgumentType.getDouble(ctx, "y"),
                            DoubleArgumentType.getDouble(ctx, "z"),
                            (float) DoubleArgumentType.getDouble(ctx, "yaw"),
                            (float) DoubleArgumentType.getDouble(ctx, "pitch")));

            var yaw = CommandManager.argument("yaw", DoubleArgumentType.doubleArg(-180.0, 180.0))
                    .executes(ctx -> teleportExact(ctx.getSource(),
                            DoubleArgumentType.getDouble(ctx, "x"),
                            DoubleArgumentType.getDouble(ctx, "y"),
                            DoubleArgumentType.getDouble(ctx, "z"),
                            (float) DoubleArgumentType.getDouble(ctx, "yaw"), null))
                    .then(pitch);

            var z = CommandManager.argument("z", DoubleArgumentType.doubleArg(-30_000_000.0, 30_000_000.0))
                    .executes(ctx -> teleportExact(ctx.getSource(),
                            DoubleArgumentType.getDouble(ctx, "x"),
                            DoubleArgumentType.getDouble(ctx, "y"),
                            DoubleArgumentType.getDouble(ctx, "z"), null, null))
                    .then(yaw);

            var y = CommandManager.argument("y", DoubleArgumentType.doubleArg(-2048.0, 4096.0)).then(z);
            var x = CommandManager.argument("x", DoubleArgumentType.doubleArg(-30_000_000.0, 30_000_000.0)).then(y);
            dispatcher.register(CommandManager.literal("tpe").requires(source -> source.hasPermissionLevel(2)).then(x));

            var fiven = CommandManager.literal("fiven")
                    .then(CommandManager.literal("shader")
                            .then(CommandManager.literal("clear")
                                    .requires(source -> source.hasPermissionLevel(2))
                                    .executes(ctx -> {
                                        int count = EntityEffectManager.clearUnprotected(ctx.getSource().getServer());
                                        ctx.getSource().sendFeedback(() -> Text.literal("§8[§cFiven§8] §7Очищено шейдер-эффектов: §f" + count + "§7. Защищённые сущности сохранены."), false);
                                        return Math.max(1, count);
                                    })))
                    .then(CommandManager.literal("lift-type")
                            .requires(source -> source.hasPermissionLevel(2))
                            .then(CommandManager.literal("normal").executes(ctx -> setNearestLiftType(ctx.getSource(), false)))
                            .then(CommandManager.literal("cursed").executes(ctx -> setNearestLiftType(ctx.getSource(), true))))
                    .then(CommandManager.literal("lift-event")
                            .requires(source -> source.hasPermissionLevel(2))
                            .then(CommandManager.literal("slam").executes(ctx -> runLiftEvent(ctx.getSource(), "slam")))
                            .then(CommandManager.literal("screamer").executes(ctx -> runLiftEvent(ctx.getSource(), "screamer")))
                            .then(CommandManager.literal("wrong-floor").executes(ctx -> runLiftEvent(ctx.getSource(), "wrong-floor"))));
            dispatcher.register(fiven);
        });
    }

    private static int teleportExact(ServerCommandSource source, double x, double y, double z, Float yaw, Float pitch) {
        try {
            ServerPlayerEntity player = source.getPlayerOrThrow();
            float targetYaw = yaw == null ? player.getYaw() : yaw;
            float targetPitch = pitch == null ? player.getPitch() : pitch;
            player.teleport(player.getServerWorld(), x, y, z, targetYaw, targetPitch);
            source.sendFeedback(() -> Text.literal(String.format(java.util.Locale.ROOT,
                    "§8[§cFiven§8] §7TPE: §f%.3f %.3f %.3f §8(§7yaw %.2f / pitch %.2f§8)",
                    x, y, z, targetYaw, targetPitch)), false);
            return 1;
        } catch (Exception e) {
            source.sendError(Text.literal("TPE доступен только игроку."));
            return 0;
        }
    }

    private static LiftBlockEntity nearest(ServerCommandSource source) {
        try {
            ServerPlayerEntity player = source.getPlayerOrThrow();
            return LiftManager.nearestLift(player, 48.0);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static int setNearestLiftType(ServerCommandSource source, boolean cursed) {
        LiftBlockEntity lift = nearest(source);
        if (lift == null) {
            source.sendError(Text.literal("Рядом не найден физический лифт Fiven."));
            return 0;
        }
        lift.setCursed(cursed);
        source.sendFeedback(() -> Text.literal("§8[§cFiven§8] §7Лифт §f" + lift.getLiftId() + " §7теперь: " + (cursed ? "§cПРОКЛЯТЫЙ" : "§aОБЫЧНЫЙ")), false);
        return 1;
    }

    /** Explicit director-triggered paranormal events. They never fire randomly by themselves. */
    private static int runLiftEvent(ServerCommandSource source, String event) {
        ServerPlayerEntity director;
        try {
            director = source.getPlayerOrThrow();
        } catch (Exception e) {
            source.sendError(Text.literal("Событие лифта запускается игроком рядом с лифтом."));
            return 0;
        }
        LiftBlockEntity lift = LiftManager.nearestLift(director, 48.0);
        if (lift == null || !(lift.getWorld() instanceof ServerWorld world)) {
            source.sendError(Text.literal("Рядом не найден физический лифт Fiven."));
            return 0;
        }
        if (!lift.isCursed()) {
            source.sendError(Text.literal("Этот лифт обычный. Сначала: /fiven lift-type cursed"));
            return 0;
        }

        switch (event) {
            case "slam" -> {
                lift.closeDoors();
                world.playSound(null, lift.getPos(), SoundEvents.BLOCK_IRON_DOOR_CLOSE, SoundCategory.BLOCKS, 1.2f, 0.45f);
                world.playSound(null, lift.getPos(), SoundEvents.BLOCK_PISTON_EXTEND, SoundCategory.BLOCKS, 0.9f, 0.35f);
            }
            case "screamer" -> {
                Vec3d center = Vec3d.ofCenter(lift.getPos());
                for (ServerPlayerEntity player : world.getPlayers(p -> !p.isSpectator()
                        && !p.getAbilities().creativeMode
                        && p.squaredDistanceTo(center) <= 24.0 * 24.0)) {
                    FifthNetworking.sendScreamer(player, 36, 1.2f);
                }
            }
            case "wrong-floor" -> {
                int current = lift.getCurrentFloor();
                int target = current >= 9 ? 8 : current + 1;
                if (!LiftManager.travel(director, lift, target)) return 0;
            }
            default -> { return 0; }
        }
        source.sendFeedback(() -> Text.literal("§8[§cFiven§8] §7Событие проклятого лифта: §c" + event), false);
        return 1;
    }
}
