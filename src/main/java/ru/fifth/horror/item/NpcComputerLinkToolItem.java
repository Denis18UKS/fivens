package ru.fifth.horror.item;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import ru.fifth.horror.block.ScriptComputerBlockEntity;
import ru.fifth.horror.entity.DirectorNpcEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Links a placed Director NPC to a FifthScript computer. Supports NPC→computer and computer→NPC order. */
public class NpcComputerLinkToolItem extends Item {
    private record Link(UUID npcUuid, String npcId, BlockPos computerPos, String scriptName) {}
    private static final Map<UUID, Link> LINKS = new ConcurrentHashMap<>();

    public NpcComputerLinkToolItem(Settings settings) { super(settings); }

    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
        if (!(entity instanceof DirectorNpcEntity npc)) return ActionResult.PASS;
        if (!(user.getWorld() instanceof ServerWorld world)) return ActionResult.SUCCESS;

        Link old = read(stack);
        if (old == null) old = LINKS.get(user.getUuid());
        if (old != null && old.computerPos != null && world.getBlockEntity(old.computerPos) instanceof ScriptComputerBlockEntity be) {
            bind(user, npc, be);
            Link keep = new Link(npc.getUuid(), npc.getNpcId(), old.computerPos, be.getScriptName());
            remember(stack, keep); LINKS.put(user.getUuid(), keep);
            return ActionResult.CONSUME;
        }

        Link next = new Link(npc.getUuid(), npc.getNpcId(), null, "");
        remember(stack, next); remember(user.getStackInHand(hand), next); LINKS.put(user.getUuid(), next);
        user.sendMessage(Text.literal("§8[§cПятый§8] §7NPC для привязки: §f" + npc.getNpcId() + "§7. Теперь кликни по сценарному компьютеру."), true);
        return ActionResult.CONSUME;
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        if (!(context.getWorld() instanceof ServerWorld world)) return ActionResult.SUCCESS;
        PlayerEntity player = context.getPlayer();
        if (player == null) return ActionResult.PASS;
        if (!(world.getBlockEntity(context.getBlockPos()) instanceof ScriptComputerBlockEntity be)) return ActionResult.PASS;

        ItemStack stack = context.getStack();
        Link old = read(stack);
        if (old == null) old = LINKS.get(player.getUuid());
        DirectorNpcEntity npc = old == null ? null : resolve(world, old);
        if (npc != null) {
            bind(player, npc, be);
            Link keep = new Link(npc.getUuid(), npc.getNpcId(), context.getBlockPos().toImmutable(), be.getScriptName());
            remember(stack, keep); LINKS.put(player.getUuid(), keep);
            return ActionResult.CONSUME;
        }

        Link next = new Link(null, "", context.getBlockPos().toImmutable(), be.getScriptName());
        remember(stack, next); LINKS.put(player.getUuid(), next);
        player.sendMessage(Text.literal("§8[§cПятый§8] §7Компьютер выбран: §f" + be.getScriptName() + "§7. Теперь кликни по NPC."), true);
        return ActionResult.CONSUME;
    }

    private static void bind(PlayerEntity player, DirectorNpcEntity npc, ScriptComputerBlockEntity be) {
        String script = be.getScriptName();
        if (script == null || script.isBlank()) script = "main";
        npc.setAiScript(script);
        npc.linkComputer(be.getPos());
        player.sendMessage(Text.literal("§8[§cПятый§8] §f" + npc.getNpcId() + " §7привязан к компьютеру §f" + script + "§7. Запусти NPC красным переключателем или кодом."), true);
    }

    private static DirectorNpcEntity resolve(ServerWorld world, Link link) {
        if (link == null) return null;
        if (link.npcUuid != null && world.getEntity(link.npcUuid) instanceof DirectorNpcEntity npc) return npc;
        if (link.npcId == null || link.npcId.isBlank()) return null;
        Box all = new Box(-30_000_000, -2048, -30_000_000, 30_000_000, 4096, 30_000_000);
        var list = world.getEntitiesByClass(DirectorNpcEntity.class, all, n -> link.npcId.equals(n.getNpcId()));
        return list.isEmpty() ? null : list.get(0);
    }

    private static void remember(ItemStack stack, Link link) {
        if (stack == null || stack.isEmpty() || link == null) return;
        var nbt = stack.getOrCreateNbt();
        if (link.npcUuid != null) nbt.putUuid("FifthLinkNpc", link.npcUuid); else nbt.remove("FifthLinkNpc");
        nbt.putString("FifthLinkNpcId", link.npcId == null ? "" : link.npcId);
        if (link.computerPos != null) nbt.putLong("FifthLinkComputer", link.computerPos.asLong()); else nbt.remove("FifthLinkComputer");
        nbt.putString("FifthLinkScript", link.scriptName == null ? "" : link.scriptName);
    }

    private static Link read(ItemStack stack) {
        if (stack == null || !stack.hasNbt()) return null;
        var nbt = stack.getNbt();
        if (nbt == null) return null;
        UUID npc = nbt.containsUuid("FifthLinkNpc") ? nbt.getUuid("FifthLinkNpc") : null;
        String id = nbt.getString("FifthLinkNpcId");
        BlockPos pos = nbt.contains("FifthLinkComputer") ? BlockPos.fromLong(nbt.getLong("FifthLinkComputer")) : null;
        String script = nbt.getString("FifthLinkScript");
        if (npc == null && (id == null || id.isBlank()) && pos == null) return null;
        return new Link(npc, id, pos, script);
    }
}
