package ru.fifth.horror;

import com.google.gson.Gson;
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
import ru.fifth.horror.cutscene.CutsceneDefinition;
import ru.fifth.horror.network.FifthNetworking;

/** Director-only TV diagnostic that bypasses cassette/link logic and tests the physical CRT render path directly. */
public final class TvTestCommands implements ModInitializer {
    private static final Gson GSON = new Gson();

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

        CutsceneDefinition scene = new CutsceneDefinition();
        scene.id = "__tv_render_test__";
        scene.hideHud = false;
        scene.lockInput = false;
        scene.teleportPlayerAtEnd = false;
        CutsceneDefinition.Keyframe frame = new CutsceneDefinition.Keyframe(
                player.getX(), player.getEyeY(), player.getZ(),
                player.getYaw(), player.getPitch(), 70.0, 120);
        frame.subtitle = "TV MIXIN TEST";
        scene.keyframes.add(frame);

        tv.start(scene.id);
        PacketByteBuf out = PacketByteBufs.create();
        out.writeString(GSON.toJson(scene), 1_000_000);
        out.writeVarInt(1);
        out.writeBlockPos(tv.getPos());
        ServerPlayNetworking.send(player, FifthNetworking.VHS_PLAYBACK, out);
        source.sendFeedback(() -> Text.literal("§8[§cFiven§8] §aTV TEST запущен§7: 4 секунды шума, затем камера игрока на физическом экране TV."), false);
        return 1;
    }
}
