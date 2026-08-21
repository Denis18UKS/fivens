package ru.fifth.horror.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import ru.fifth.horror.entity.MflHidingManager;

/** In-world two-point selector for cupboard/closet hiding volumes used by MFL. */
public final class MflHidingToolItem extends Item {
    private static final String KEY_WORLD = "FivenHideWorld";
    private static final String KEY_A = "FivenHideA";

    public MflHidingToolItem(Settings settings) { super(settings); }

    @Override
    public ActionResult useOnBlock(ItemUsageContext ctx) {
        if (!(ctx.getPlayer() instanceof ServerPlayerEntity player)) return ActionResult.success(ctx.getWorld().isClient);
        if (!player.hasPermissionLevel(2)) {
            player.sendMessage(Text.literal("§cНужны права оператора для редактирования зон укрытия."), true);
            return ActionResult.FAIL;
        }
        if (!(ctx.getWorld() instanceof ServerWorld world)) return ActionResult.PASS;

        BlockPos selected = ctx.getBlockPos().offset(ctx.getSide());
        ItemStack stack = ctx.getStack();
        NbtCompound nbt = stack.getOrCreateNbt();

        if (player.isSneaking()) {
            int removed = MflHidingManager.removeAt(world, selected);
            player.sendMessage(Text.literal("§8[§cFiven§8] §7Удалено зон укрытия: §f" + removed), true);
            return ActionResult.SUCCESS;
        }

        String dimension = world.getRegistryKey().getValue().toString();
        if (!nbt.contains(KEY_A) || !dimension.equals(nbt.getString(KEY_WORLD))) {
            nbt.putString(KEY_WORLD, dimension);
            nbt.putLong(KEY_A, selected.asLong());
            player.sendMessage(Text.literal("§8[§cFiven§8] §7Укрытие MFL: точка A = §f" + selected.toShortString() + "§7. Выбери точку B."), true);
            return ActionResult.SUCCESS;
        }

        BlockPos a = BlockPos.fromLong(nbt.getLong(KEY_A));
        MflHidingManager.add(world, a, selected);
        nbt.remove(KEY_WORLD);
        nbt.remove(KEY_A);
        player.sendMessage(Text.literal("§8[§cFiven§8] §aЗона укрытия сохранена§7: §f" + a.toShortString() + " §8→ §f" + selected.toShortString()), true);
        return ActionResult.SUCCESS;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient && user instanceof ServerPlayerEntity player) {
            NbtCompound nbt = stack.getOrCreateNbt();
            if (nbt.contains(KEY_A)) {
                nbt.remove(KEY_WORLD);
                nbt.remove(KEY_A);
                player.sendMessage(Text.literal("§8[§cFiven§8] §7Выбор зоны укрытия сброшен."), true);
            } else {
                player.sendMessage(Text.literal("§8[§cFiven§8] §7ПКМ по границе укрытия: точка A → точка B. Shift+ПКМ внутри зоны — удалить её."), true);
            }
        }
        return TypedActionResult.success(stack, world.isClient);
    }
}
