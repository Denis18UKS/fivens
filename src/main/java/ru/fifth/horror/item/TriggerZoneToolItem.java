package ru.fifth.horror.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;

/**
 * Director tool for defining trigger-zone corners.
 * RMB block = point A, Shift+RMB block = point B. The selected box is then bound with /fiven trigger create.
 */
public final class TriggerZoneToolItem extends Item {
    public static final String POS_A = "FivenTriggerPosA";
    public static final String POS_B = "FivenTriggerPosB";
    public static final String WORLD = "FivenTriggerWorld";

    public TriggerZoneToolItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        if (context.getWorld().isClient) return ActionResult.SUCCESS;
        if (!(context.getPlayer() instanceof ServerPlayerEntity player)) return ActionResult.PASS;
        if (!player.hasPermissionLevel(2)) {
            player.sendMessage(Text.literal("§8[§cFiven§8] §cИнструмент триггер-зон доступен только режиссёру/OP."), true);
            return ActionResult.FAIL;
        }

        ItemStack stack = context.getStack();
        NbtCompound nbt = stack.getOrCreateNbt();
        String world = context.getWorld().getRegistryKey().getValue().toString();
        BlockPos pos = context.getBlockPos().toImmutable();

        if (!player.isSneaking()) {
            nbt.putLong(POS_A, pos.asLong());
            nbt.remove(POS_B);
            nbt.putString(WORLD, world);
            player.sendMessage(Text.literal("§8[§cFiven§8] §7Триггер-зона: точка §aA §7= §f" + pos.toShortString()
                    + "§7. Теперь Shift+ПКМ по противоположному углу."), true);
            return ActionResult.SUCCESS;
        }

        if (!nbt.contains(POS_A)) {
            player.sendMessage(Text.literal("§8[§cFiven§8] §7Сначала поставь точку §aA §7обычным ПКМ."), true);
            return ActionResult.FAIL;
        }
        if (!world.equals(nbt.getString(WORLD))) {
            player.sendMessage(Text.literal("§8[§cFiven§8] §cA и B должны находиться в одном измерении."), true);
            return ActionResult.FAIL;
        }

        nbt.putLong(POS_B, pos.asLong());
        BlockPos a = BlockPos.fromLong(nbt.getLong(POS_A));
        int sx = Math.abs(pos.getX() - a.getX()) + 1;
        int sy = Math.abs(pos.getY() - a.getY()) + 1;
        int sz = Math.abs(pos.getZ() - a.getZ()) + 1;
        player.sendMessage(Text.literal("§8[§cFiven§8] §7Триггер-зона: точка §cB §7= §f" + pos.toShortString()
                + " §8(" + sx + "x" + sy + "x" + sz + ")§7. Сохрани: §f/fiven trigger create <id> <команда>"), false);
        return ActionResult.SUCCESS;
    }

    public static BlockPos posA(ItemStack stack) {
        return stack.hasNbt() && stack.getNbt().contains(POS_A) ? BlockPos.fromLong(stack.getNbt().getLong(POS_A)) : null;
    }

    public static BlockPos posB(ItemStack stack) {
        return stack.hasNbt() && stack.getNbt().contains(POS_B) ? BlockPos.fromLong(stack.getNbt().getLong(POS_B)) : null;
    }

    public static String world(ItemStack stack) {
        return stack.hasNbt() ? stack.getNbt().getString(WORLD) : "";
    }
}
