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

/** Uses a vanilla stone button; this tool only selects a physical lift block and binds a floor. */
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
                nbt.remove("FivenLiftUuid");
                player.sendMessage(Text.literal("§8[§cFiven§8] §7Выбран блок лифта: §f" + lift.getLiftId() + "§7. ПКМ в воздухе меняет этаж."), true);
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
        int floor = nbt.contains("FivenBindFloor") ? nbt.getInt("FivenBindFloor") : 1;
        floor = Math.max(1, Math.min(9, floor));
        if (LiftManager.findLift(serverWorld.getServer(), liftWorld, liftPos) == null) {
            player.sendMessage(Text.literal("§8[§cFiven§8] §cВыбранный блок лифта больше не найден. Выбери его заново."), true);
            return ActionResult.FAIL;
        }
        LiftManager.bindButton(serverWorld.getServer(), serverWorld, clicked, liftWorld, liftPos, floor);
        player.sendMessage(Text.literal("§8[§cFiven§8] §7Каменная кнопка теперь вызывает лифт на этаж §c" + floor + "§7 и не используется как redstone-кнопка."), true);
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

    /** Legacy migration helper for old entity lifts. Legacy selection is routed through UseEntityCallback. */
    public static ActionResult selectLift(ItemStack stack, PlayerEntity player, LiftEntity lift) {
        if (!player.getWorld().isClient) {
            stack.getOrCreateNbt().putString("FivenLiftUuid", lift.getUuidAsString());
            player.sendMessage(Text.literal("§8[§cFiven§8] §eВыбран старый Entity-лифт. Поставь новый блок лифта и перепривяжи кнопку."), true);
        }
        return ActionResult.SUCCESS;
    }
}
