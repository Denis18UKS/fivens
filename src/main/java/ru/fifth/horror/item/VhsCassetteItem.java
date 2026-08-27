package ru.fifth.horror.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.world.World;

import java.util.List;

public final class VhsCassetteItem extends Item {
    public VhsCassetteItem(Settings settings) { super(settings.maxCount(1)); }

    public static String recording(ItemStack stack) {
        return stack.hasNbt() ? stack.getNbt().getString("FivenRecordingId") : "";
    }

    public static void setRecording(ItemStack stack, String id) {
        String value = id == null ? "" : id;
        stack.getOrCreateNbt().putString("FivenRecordingId", value);
        if (!value.isBlank()) stack.setCustomName(Text.literal("VHS: " + value));
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> lines, TooltipContext context) {
        String id = recording(stack);
        lines.add(Text.literal(id.isBlank()
                ? "§7Пустая / legacy-кассета — сначала запишите VHS"
                : "§7Записанная VHS: §f" + id));
    }
}
