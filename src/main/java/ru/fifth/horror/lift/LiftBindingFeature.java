package ru.fifth.horror.lift;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import ru.fifth.horror.FifthMod;

/**
 * Clear three-step lift call-button binding:
 * 1) click the lift, 2) click a vanilla stone button, 3) choose its floor in the popup.
 */
public final class LiftBindingFeature implements ModInitializer {
    public static final Identifier OPEN_BINDER = FifthMod.id("open_lift_button_binder");
    public static final Identifier BIND_FLOOR = FifthMod.id("bind_lift_button_floor");

    @Override
    public void onInitialize() {
        ServerPlayNetworking.registerGlobalReceiver(BIND_FLOOR, (server, player, handler, buf, responseSender) -> {
            int handIndex = buf.readVarInt();
            BlockPos buttonPos = buf.readBlockPos();
            int floor = Math.max(1, Math.min(9, buf.readVarInt()));
            server.execute(() -> {
                if (!player.hasPermissionLevel(2) || !(player.getWorld() instanceof ServerWorld buttonWorld)) return;
                Hand hand = handIndex == 1 ? Hand.OFF_HAND : Hand.MAIN_HAND;
                ItemStack stack = player.getStackInHand(hand);
                if (!stack.isOf(FifthMod.LIFT_BUTTON_BINDER)) {
                    player.sendMessage(Text.literal("§8[§cFiven§8] §cИнструмент привязки больше не в выбранной руке."), true);
                    return;
                }
                var nbt = stack.getOrCreateNbt();
                if (!nbt.contains("FivenLiftWorld") || !nbt.contains("FivenLiftPos")) {
                    player.sendMessage(Text.literal("§8[§cFiven§8] §cСначала выбери лифт ПКМ по его блоку."), true);
                    return;
                }
                String liftWorld = nbt.getString("FivenLiftWorld");
                BlockPos liftPos = BlockPos.fromLong(nbt.getLong("FivenLiftPos"));
                if (LiftManager.findLift(server, liftWorld, liftPos) == null) {
                    player.sendMessage(Text.literal("§8[§cFiven§8] §cВыбранный лифт больше не найден."), true);
                    return;
                }
                LiftManager.bindButton(server, buttonWorld, buttonPos, liftWorld, liftPos, floor);
                nbt.putInt("FivenBindFloor", floor);
                player.sendMessage(Text.literal("§8[§cFiven§8] §aГотово: Stone Button → §f"
                        + nbt.getString("FivenLiftName") + " §7/ этаж §c" + floor), true);
            });
        });
    }

    public static void openFloorPicker(net.minecraft.server.network.ServerPlayerEntity player, Hand hand,
                                       BlockPos buttonPos, int currentFloor, String liftName) {
        PacketByteBuf out = PacketByteBufs.create();
        out.writeVarInt(hand == Hand.OFF_HAND ? 1 : 0);
        out.writeBlockPos(buttonPos);
        out.writeVarInt(Math.max(1, Math.min(9, currentFloor)));
        out.writeString(liftName == null ? "lift" : liftName, 64);
        ServerPlayNetworking.send(player, OPEN_BINDER, out);
    }
}
