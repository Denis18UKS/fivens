package ru.fifth.horror.item;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import ru.fifth.horror.entity.DirectorNpcEntity;

/** Developer toggle: freeze an NPC into a statue or start its programmed behaviour. */
public class NpcStateToolItem extends Item {
    public NpcStateToolItem(Settings settings) { super(settings); }

    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
        if (!(entity instanceof DirectorNpcEntity npc)) return ActionResult.PASS;
        if (user.getWorld().isClient) return ActionResult.SUCCESS;

        if (npc.isAiEnabled()) {
            npc.setAiEnabled(false);
            npc.setCurrentAnimation("");
            user.sendMessage(Text.literal("§8[§cПятый§8] §f" + npc.getNpcId() + " §7→ §8СТАТУЯ §7(ИИ и движение остановлены)"), true);
        } else {
            npc.setAiEnabled(true);
            if (npc.getAiScript().isBlank() && !npc.getPathPoints().isEmpty()) npc.followPath(true, 0.25);
            String extra = npc.getAiScript().isBlank()
                    ? (npc.getPathPoints().isEmpty() ? " §8• §7нет AI-скрипта/маршрута" : " §8• §7тестовый патруль маршрута")
                    : " §8• §7AI: §f" + npc.getAiScript();
            user.sendMessage(Text.literal("§8[§cПятый§8] §f" + npc.getNpcId() + " §7→ §aЗАПУЩЕН" + extra), true);
        }
        return ActionResult.CONSUME;
    }
}
