package ru.fifth.horror.item;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;

import java.util.function.Supplier;

/** Places a GeckoLib-backed prop/entity from an inventory item. */
public final class EntityPlacerItem extends Item {
    private final Supplier<? extends EntityType<? extends Entity>> type;

    public EntityPlacerItem(Settings settings, Supplier<? extends EntityType<? extends Entity>> type) {
        super(settings);
        this.type = type;
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        if (!(context.getWorld() instanceof ServerWorld world)) return ActionResult.SUCCESS;
        Entity entity = type.get().create(world);
        if (entity == null) return ActionResult.FAIL;

        BlockPos pos = context.getBlockPos().offset(context.getSide());
        entity.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                context.getPlayerYaw(), 0.0f);
        if (!world.spawnEntity(entity)) return ActionResult.FAIL;

        if (context.getPlayer() == null || !context.getPlayer().getAbilities().creativeMode) {
            context.getStack().decrement(1);
        }
        return ActionResult.CONSUME;
    }
}
