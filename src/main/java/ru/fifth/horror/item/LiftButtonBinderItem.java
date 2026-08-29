package ru.fifth.horror.item;

import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import ru.fifth.horror.FifthMod;
import ru.fifth.horror.block.LiftBlockEntity;
import ru.fifth.horror.entity.LiftEntity;
import ru.fifth.horror.lift.LiftBindingFeature;
import ru.fifth.horror.lift.LiftManager;

/**
 * Human-readable call-button binding flow:
 * lift block -> vanilla Stone Button -> choose floor popup. No hidden air-RMB floor cycling.
 */
public final class LiftButtonBinderItem extends Item {
    public LiftButtonBinderItem(Settings settings) { super(settings); }

    @Override
    public ActionResult useOnBlock(ItemUsageContext ctx) {
        PlayerEntity player = ctx.getPlayer();
        if (player == null) return ActionResult.PASS;
        World world = ctx.getWorld();
        BlockPos clicked = ctx.getBlockPos();
        ItemStack stack = ctx.getStack();

        if (world.getBlockState(clicked).isOf(FifthMod.LIFT_BLOCK)) {
            if (!world.isClient && world.getBlockEntity(clicked) instanceof LiftBlockEntity lift) {
                var nbt = stack.getOrCreateNbt();
                nbt.putString("FivenLiftWorld", world.getRegistryKey().getValue().toString());
                nbt.putLong("FivenLiftPos", clicked.asLong());
                nbt.putString("FivenLiftName", lift.getLiftId());
                nbt.remove("FivenLiftUuid");
                player.sendMessage(Text.literal("§8[§cFiven§8] §aШаг 1/3: лифт выбран — §f" + lift.getLiftId()
                        + "§a. Теперь ПКМ по обычной Stone Button."), true);
            }
            return ActionResult.success(world.isClient);
        }

        if (!world.getBlockState(clicked).isOf(Blocks.STONE_BUTTON)) return ActionResult.PASS;
        if (world.isClient) return ActionResult.SUCCESS;
        if (!(world instanceof ServerWorld serverWorld) || !(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;

        var nbt = stack.getOrCreateNbt();
        if (!nbt.contains("FivenLiftWorld") || !nbt.contains("FivenLiftPos")) {
            player.sendMessage(Text.literal("§8[§cFiven§8] §cШаг 1: сначала ПКМ инструментом по самому блоку лифта."), true);
            return ActionResult.SUCCESS;
        }

        String liftWorld = nbt.getString("FivenLiftWorld");
        BlockPos liftPos = BlockPos.fromLong(nbt.getLong("FivenLiftPos"));
        LiftBlockEntity lift = LiftManager.findLift(serverWorld.getServer(), liftWorld, liftPos);
        if (lift == null) {
            player.sendMessage(Text.literal("§8[§cFiven§8] §cВыбранный лифт больше не найден. Выбери его заново."), true);
            return ActionResult.FAIL;
        }

        int currentFloor = nbt.contains("FivenBindFloor") ? nbt.getInt("FivenBindFloor") : 1;
        LiftManager.ButtonBinding old = LiftManager.getBinding(serverWorld.getServer(), serverWorld, clicked);
        if (old != null) currentFloor = old.floor;

        LiftBindingFeature.openFloorPicker(serverPlayer, ctx.getHand(), clicked, currentFloor, lift.getLiftId());
        player.sendMessage(Text.literal("§8[§cFiven§8] §eШаг 2/3: кнопка выбрана. Теперь нажми нужный этаж в окне."), true);
        return ActionResult.SUCCESS;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (!world.isClient) {
            user.sendMessage(Text.literal("§8[§cFiven§8] §7Привязка: §f1) ПКМ по лифту §7→ §f2) ПКМ по Stone Button §7→ §f3) выбери этаж."), true);
        }
        return TypedActionResult.success(user.getStackInHand(hand), world.isClient);
    }

    /** Legacy migration helper for old entity lifts. */
    public static ActionResult selectLift(ItemStack stack, PlayerEntity player, LiftEntity lift) {
        if (!player.getWorld().isClient) {
            stack.getOrCreateNbt().putString("FivenLiftUuid", lift.getUuidAsString());
            player.sendMessage(Text.literal("§8[§cFiven§8] §eЭто старый Entity-лифт. Поставь новый блок лифта и выбери его инструментом."), true);
        }
        return ActionResult.SUCCESS;
    }
}
