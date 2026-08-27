package ru.fifth.horror.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import ru.fifth.horror.checkpoint.CheckpointManager;

import java.util.List;

/** Simple authoring tool: click to save a checkpoint, sneak-click to activate the selected one. */
public final class CheckpointToolItem extends Item {
    public CheckpointToolItem(Settings settings) { super(settings.maxCount(1)); }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        PlayerEntity player = context.getPlayer();
        if (player == null) return ActionResult.PASS;
        if (context.getWorld().isClient) return ActionResult.SUCCESS;
        if (!(player instanceof ServerPlayerEntity sp) || !(context.getWorld() instanceof ServerWorld world)) return ActionResult.PASS;
        if (!sp.hasPermissionLevel(2)) return ActionResult.FAIL;

        ItemStack stack = context.getStack();
        if (sp.isSneaking()) {
            String selected = stack.hasNbt() ? stack.getNbt().getString("FivenCheckpointId") : "";
            CheckpointManager.Checkpoint cp = selected.isBlank() ? CheckpointManager.nearest(sp.getServer(), sp, 12.0)
                    : CheckpointManager.list(sp.getServer()).stream().filter(c -> c.id.equals(selected)).findFirst().orElse(null);
            if (cp == null || !CheckpointManager.activate(sp.getServer(), cp.id)) {
                sp.sendMessage(Text.literal("§8[§cFiven§8] §cКонтрольная точка не найдена. Сначала поставь её обычным ПКМ."), true);
                return ActionResult.FAIL;
            }
            stack.getOrCreateNbt().putString("FivenCheckpointId", cp.id);
            sp.sendMessage(Text.literal("§8[§cFiven§8] §aОбщая контрольная точка активирована: §f" + cp.id), true);
            return ActionResult.SUCCESS;
        }

        BlockPos p = context.getBlockPos();
        String id = "cp_" + p.getX() + "_" + p.getY() + "_" + p.getZ();
        CheckpointManager.Checkpoint cp = CheckpointManager.set(sp.getServer(), id, world,
                p.getX() + .5, p.getY() + 1.01, p.getZ() + .5, sp.getYaw(), sp.getPitch());
        stack.getOrCreateNbt().putString("FivenCheckpointId", cp.id);
        sp.sendMessage(Text.literal("§8[§cFiven§8] §7Контрольная точка сохранена: §f" + cp.id + "§7. Shift+ПКМ — сделать общей активной."), true);
        return ActionResult.SUCCESS;
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> lines, TooltipContext context) {
        lines.add(Text.literal("§7ПКМ по блоку: сохранить checkpoint"));
        lines.add(Text.literal("§7Shift+ПКМ: активировать её для всей команды"));
        if (stack.hasNbt() && !stack.getNbt().getString("FivenCheckpointId").isBlank())
            lines.add(Text.literal("§8Выбрано: §f" + stack.getNbt().getString("FivenCheckpointId")));
    }
}
