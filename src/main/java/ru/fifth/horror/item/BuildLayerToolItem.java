package ru.fifth.horror.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;

public class BuildLayerToolItem extends Item {
    public BuildLayerToolItem(Settings settings) { super(settings); }
    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        PlayerEntity player = context.getPlayer(); if (player == null) return ActionResult.PASS;
        ItemStack stack = context.getStack(); BlockPos p = context.getBlockPos();
        if (!context.getWorld().isClient) {
            if (player.isSneaking()) { stack.getOrCreateNbt().putLong("FifthPosB", p.asLong()); player.sendMessage(Text.literal("§7Точка B: §f" + p.toShortString()), true); }
            else { stack.getOrCreateNbt().putLong("FifthPosA", p.asLong()); player.sendMessage(Text.literal("§7Точка A: §f" + p.toShortString()), true); }
        }
        return ActionResult.SUCCESS;
    }
}
