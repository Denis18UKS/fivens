package ru.fifth.horror.item;

import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import ru.fifth.horror.entity.LiftEntity;
import ru.fifth.horror.lift.LiftManager;

import java.util.UUID;

/** Uses the VANILLA stone button; this item only rebinds it to a lift/floor. */
public final class LiftButtonBinderItem extends Item {
    public LiftButtonBinderItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
        return ActionResult.PASS;
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext ctx) {
        PlayerEntity player = ctx.getPlayer();
        if (player == null) return ActionResult.PASS;
        if (!ctx.getWorld().getBlockState(ctx.getBlockPos()).isOf(Blocks.STONE_BUTTON)) return ActionResult.PASS;
        if (ctx.getWorld().isClient) return ActionResult.SUCCESS;
        if (!(ctx.getWorld() instanceof ServerWorld serverWorld)) return ActionResult.PASS;

        var nbt = ctx.getStack().getOrCreateNbt();
        if (!nbt.contains("FivenLiftUuid")) {
            player.sendMessage(Text.literal("§8[§cFiven§8] §7Сначала выбери лифт: ПКМ предметом по лифту."), true);
            return ActionResult.SUCCESS;
        }

        UUID liftUuid;
        try {
            liftUuid = UUID.fromString(nbt.getString("FivenLiftUuid"));
        } catch (IllegalArgumentException invalidUuid) {
            nbt.remove("FivenLiftUuid");
            player.sendMessage(Text.literal("§8[§cFiven§8] §cПривязка лифта повреждена. Выбери лифт заново."), true);
            return ActionResult.FAIL;
        }

        int floor = nbt.contains("FivenBindFloor") ? nbt.getInt("FivenBindFloor") : 1;
        floor = Math.max(1, Math.min(9, floor));
        LiftManager.bindButton(serverWorld.getServer(), serverWorld, ctx.getBlockPos(), liftUuid, floor);
        player.sendMessage(Text.literal("§8[§cFiven§8] §7Каменная кнопка привязана к этажу §c" + floor + "§7."), true);
        return ActionResult.SUCCESS;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient) {
            int floor = stack.getOrCreateNbt().getInt("FivenBindFloor");
            floor = floor < 1 || floor >= 9 ? 1 : floor + 1;
            stack.getOrCreateNbt().putInt("FivenBindFloor", floor);
            user.sendMessage(Text.literal("§8[§cFiven§8] §7Этаж для следующей привязки: §c" + floor), true);
        }
        return TypedActionResult.success(stack, world.isClient);
    }

    public static ActionResult selectLift(ItemStack stack, PlayerEntity player, LiftEntity lift) {
        if (!player.getWorld().isClient) {
            stack.getOrCreateNbt().putString("FivenLiftUuid", lift.getUuidAsString());
            player.sendMessage(Text.literal("§8[§cFiven§8] §7Выбран лифт: §f" + lift.getLiftId() + "§7. ПКМ в воздухе меняет этаж."), true);
        }
        return ActionResult.SUCCESS;
    }
}
