package ru.fifth.horror.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import ru.fifth.horror.FifthMod;
import ru.fifth.horror.entity.DirectorNpcEntity;

public class NpcSpawnEggItem extends Item {
    public NpcSpawnEggItem(Settings settings) { super(settings); }
    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        if (!(context.getWorld() instanceof ServerWorld world)) return ActionResult.SUCCESS;
        PlayerEntity player = context.getPlayer();
        if (player == null) return ActionResult.PASS;
        ItemStack stack = context.getStack();
        DirectorNpcEntity npc = FifthMod.DIRECTOR_NPC.create(world);
        if (npc == null) return ActionResult.FAIL;
        BlockPos pos = context.getBlockPos().offset(context.getSide());
        npc.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYaw(), 0);
        if (stack.hasNbt() && stack.getNbt().contains("FifthNpcTemplate")) npc.applyTemplateJson(stack.getNbt().getString("FifthNpcTemplate"));
        world.spawnEntity(npc);
        if (!player.getAbilities().creativeMode) stack.decrement(1);
        return ActionResult.CONSUME;
    }
}
