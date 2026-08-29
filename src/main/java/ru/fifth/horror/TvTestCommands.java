package ru.fifth.horror;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import ru.fifth.horror.block.TelevisionBlockEntity;
import ru.fifth.horror.vhs.VhsRecordingFeature;

/** Director-only diagnostic for the physical CRT render path. */
public final class TvTestCommands implements ModInitializer {
    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                CommandManager.literal("fiven")
                        .then(CommandManager.literal("tv")
                                .requires(source -> source.hasPermissionLevel(2))
                                .then(CommandManager.literal("test")
                                        .executes(ctx -> test(ctx.getSource()))))));
    }

    private static int test(ServerCommandSource source) {
        ServerPlayerEntity player;
        try {
            player = source.getPlayerOrThrow();
        } catch (Exception e) {
            source.sendError(Text.literal("Команду нужно выполнять игроком, смотря на телевизор."));
            return 0;
        }

        HitResult hit = player.raycast(12.0, 1.0f, false);
        if (!(hit instanceof BlockHitResult blockHit)
                || !(player.getServerWorld().getBlockEntity(blockHit.getBlockPos()) instanceof TelevisionBlockEntity tv)) {
            source.sendError(Text.literal("Посмотри прямо на телевизор Fiven в радиусе 12 блоков."));
            return 0;
        }

        tv.start("__tv_diagnostic__");
        PacketByteBuf out = PacketByteBufs.create();
        out.writeBlockPos(tv.getPos());
        ServerPlayNetworking.send(player, VhsRecordingFeature.TV_DIAGNOSTIC, out);
        source.sendFeedback(() -> Text.literal("§8[§cFiven§8] §aTV TEST: §71.5 сек помех, затем тест-карта CRT."), false);
        return 1;
    }
}
