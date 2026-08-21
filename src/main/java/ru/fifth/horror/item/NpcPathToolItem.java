package ru.fifth.horror.item;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.Vec3d;
import ru.fifth.horror.entity.DirectorNpcEntity;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Route authoring tool. The current NPC is stored both in the stack NBT and in a server-side per-player cache.
 * The cache fixes creative/inventory synchronisation cases where the client replaces the held stack and used to lose the selected NPC.
 */
public class NpcPathToolItem extends Item {
    private record Selection(UUID uuid, String npcId) {}
    private static final Map<UUID, Selection> SELECTIONS = new ConcurrentHashMap<>();

    public NpcPathToolItem(Settings settings) { super(settings); }

    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
        if (!(entity instanceof DirectorNpcEntity npc)) return ActionResult.PASS;
        if (user.getWorld().isClient) return ActionResult.SUCCESS;

        Selection selection = new Selection(npc.getUuid(), npc.getNpcId());
        SELECTIONS.put(user.getUuid(), selection);
        remember(stack, selection);
        remember(user.getStackInHand(hand), selection);
        user.sendMessage(Text.literal("§8[§cПятый§8] §7NPC маршрута: §f" + npc.getNpcId() + " §8(выбор сохранён)"), true);
        return ActionResult.CONSUME;
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        if (!(context.getWorld() instanceof ServerWorld world)) return ActionResult.SUCCESS;
        PlayerEntity player = context.getPlayer();
        if (player == null) return ActionResult.PASS;
        ItemStack stack = context.getStack();

        Selection selection = read(stack);
        if (selection == null) selection = SELECTIONS.get(player.getUuid());
        if (selection == null) {
            player.sendMessage(Text.literal("§cСначала кликни маршрутным инструментом по NPC."), true);
            return ActionResult.FAIL;
        }

        remember(stack, selection);
        SELECTIONS.put(player.getUuid(), selection);

        DirectorNpcEntity npc = resolve(world, selection);
        if (npc == null) {
            player.sendMessage(Text.literal("§cВыбранный NPC сейчас не найден в этом измерении: §f" + selection.npcId), true);
            return ActionResult.FAIL;
        }

        if (player.isSneaking()) {
            npc.clearPath();
            player.sendMessage(Text.literal("§7Маршрут §f" + npc.getNpcId() + " §7очищен."), true);
        } else {
            Vec3d p = Vec3d.ofBottomCenter(context.getBlockPos().offset(context.getSide()));
            npc.addPathPoint(p);
            player.sendMessage(Text.literal("§7" + npc.getNpcId() + " §8• §7точка §f#" + npc.getPathPoints().size() + " §8• §7" + fmt(p)), true);
        }
        return ActionResult.CONSUME;
    }

    private static void remember(ItemStack stack, Selection selection) {
        if (stack == null || stack.isEmpty()) return;
        var nbt = stack.getOrCreateNbt();
        nbt.putUuid("FifthNpc", selection.uuid);
        nbt.putString("FifthNpcId", selection.npcId == null ? "" : selection.npcId);
    }

    private static Selection read(ItemStack stack) {
        if (stack == null || !stack.hasNbt()) return null;
        var nbt = stack.getNbt();
        if (nbt == null || !nbt.containsUuid("FifthNpc")) return null;
        String id = nbt.contains("FifthNpcId") ? nbt.getString("FifthNpcId") : "";
        return new Selection(nbt.getUuid("FifthNpc"), id);
    }

    private static DirectorNpcEntity resolve(ServerWorld world, Selection selection) {
        if (world.getEntity(selection.uuid) instanceof DirectorNpcEntity npc) return npc;
        if (selection.npcId == null || selection.npcId.isBlank()) return null;
        TypeFilter<Entity,DirectorNpcEntity> filter=TypeFilter.instanceOf(DirectorNpcEntity.class);
        var result=new ArrayList<DirectorNpcEntity>(1);
        world.collectEntitiesByType(filter,n -> selection.npcId.equals(n.getNpcId()),result,1);
        return result.isEmpty()?null:result.get(0);
    }

    private static String fmt(Vec3d p) {
        return String.format(java.util.Locale.ROOT, "%.1f, %.1f, %.1f", p.x, p.y, p.z);
    }
}
