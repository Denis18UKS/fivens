package ru.fifth.horror.item;

import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
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
import ru.fifth.horror.lift.LiftManager;

/** Selects a physical lift and binds an explicit GUI-selected floor to a vanilla Stone Button. */
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
                if (!nbt.contains("FivenBindFloor")) nbt.putInt("FivenBindFloor", 1);
                player.sendMessage(Text.literal("§8[§cFiven§8] §7Выбран лифт §f" + lift.getLiftId() + "§7. ПКМ в воздухе → выбери этаж из списка."), true);
            }
            return ActionResult.success(world.isClient);
        }

        if (!world.getBlockState(clicked).isOf(Blocks.STONE_BUTTON)) return ActionResult.PASS;
        if (world.isClient) return ActionResult.SUCCESS;
        if (!(world instanceof ServerWorld serverWorld)) return ActionResult.PASS;

        var nbt = stack.getOrCreateNbt();
        if (!nbt.contains("FivenLiftWorld") || !nbt.contains("FivenLiftPos")) {
            player.sendMessage(Text.literal("§8[§cFiven§8] §7Сначала ПКМ этим инструментом по блоку лифта."), true);
            return ActionResult.SUCCESS;
        }
        String liftWorld = nbt.getString("FivenLiftWorld");
        BlockPos liftPos = BlockPos.fromLong(nbt.getLong("FivenLiftPos"));
        int floor = Math.max(1, Math.min(9, nbt.contains("FivenBindFloor") ? nbt.getInt("FivenBindFloor") : 1));
        if (LiftManager.findLift(serverWorld.getServer(), liftWorld, liftPos) == null) {
            player.sendMessage(Text.literal("§8[§cFiven§8] §cВыбранный блок лифта больше не найден. Выбери его заново."), true);
            return ActionResult.FAIL;
        }
        LiftManager.bindButton(serverWorld.getServer(), serverWorld, clicked, liftWorld, liftPos, floor);
        player.sendMessage(Text.literal("§8[§cFiven§8] §7Stone Button → §f" + nbt.getString("FivenLiftName") + " §7/ этаж §c" + floor), true);
        return ActionResult.SUCCESS;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        // The client opens LiftButtonBinderScreen. No more hidden floor cycling.
        return TypedActionResult.success(user.getStackInHand(hand), world.isClient);
    }

    /** Legacy migration helper for old entity lifts. */
    public static ActionResult selectLift(ItemStack stack, PlayerEntity player, LiftEntity lift) {
        if (!player.getWorld().isClient) {
            stack.getOrCreateNbt().putString("FivenLiftUuid", lift.getUuidAsString());
            player.sendMessage(Text.literal("§8[§cFiven§8] §eВыбран старый Entity-лифт. Поставь новый блок лифта и перепривяжи кнопку."), true);
        }
        return ActionResult.SUCCESS;
    }
}
