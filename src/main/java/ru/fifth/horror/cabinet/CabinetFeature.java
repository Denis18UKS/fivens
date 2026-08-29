package ru.fifth.horror.cabinet;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import ru.fifth.horror.FifthMod;
import ru.fifth.horror.block.CabinetBlock;
import ru.fifth.horror.block.CabinetBlockEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Player hiding cabinet: one player per cabinet and one cabinet per player. */
public final class CabinetFeature implements ModInitializer {
    public static final Block PLAYER_CABINET = net.minecraft.registry.Registry.register(net.minecraft.registry.Registries.BLOCK,
            FifthMod.id("player_cabinet"), new CabinetBlock(AbstractBlock.Settings.create().strength(2.5f).nonOpaque()));
    public static final Item PLAYER_CABINET_ITEM = net.minecraft.registry.Registry.register(net.minecraft.registry.Registries.ITEM,
            FifthMod.id("player_cabinet"), new BlockItem(PLAYER_CABINET, new Item.Settings()));
    public static final net.minecraft.block.entity.BlockEntityType<CabinetBlockEntity> CABINET_BE = net.minecraft.registry.Registry.register(
            net.minecraft.registry.Registries.BLOCK_ENTITY_TYPE, FifthMod.id("player_cabinet"),
            FabricBlockEntityTypeBuilder.create(CabinetBlockEntity::new, PLAYER_CABINET).build());

    private static final CabinetOccupancyPolicy POLICY = new CabinetOccupancyPolicy();
    private static final Map<UUID, CabinetBlockEntity> LOADED = new HashMap<>();
    private static final Map<UUID, Boolean> WAS_INVISIBLE = new HashMap<>();

    @Override public void onInitialize() {
        ItemGroupEvents.modifyEntriesEvent(FifthMod.FIFTH_ITEM_GROUP_KEY).register(entries -> entries.add(PLAYER_CABINET_ITEM));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> clearAll(server));
        ServerTickEvents.END_SERVER_TICK.register(CabinetFeature::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> server.execute(() -> releasePlayer(server, handler.player.getUuid())));
    }

    public static boolean interact(ServerPlayerEntity player, CabinetBlockEntity cabinet) {
        String id = id(cabinet);
        UUID current = POLICY.ownerOf(id);
        if (current != null && current.equals(player.getUuid())) {
            exit(player, cabinet);
            return true;
        }
        if (current != null) {
            ServerPlayerEntity owner = player.getServer().getPlayerManager().getPlayer(current);
            String name = owner == null ? current.toString() : owner.getGameProfile().getName();
            player.sendMessage(Text.literal("Данный шкаф занят " + declineInstrumental(name) + "."), true);
            return false;
        }
        if (POLICY.cabinetOf(player.getUuid()) != null) {
            player.sendMessage(Text.literal("Вы уже спрятались в другом шкафу."), true);
            return false;
        }
        if (!POLICY.claim(player.getUuid(), id)) return false;

        cabinet.setOccupant(player.getUuid());
        LOADED.put(player.getUuid(), cabinet);
        WAS_INVISIBLE.put(player.getUuid(), player.isInvisible());
        player.setInvisible(true);
        keepPlayerInside(cabinet, player);
        cabinet.playDoorSequence(player);
        return true;
    }

    private static void exit(ServerPlayerEntity player, CabinetBlockEntity cabinet) {
        UUID uuid = player.getUuid();
        POLICY.release(uuid, id(cabinet));
        LOADED.remove(uuid);
        cabinet.setOccupant(null);
        player.setInvisible(WAS_INVISIBLE.getOrDefault(uuid, false));
        WAS_INVISIBLE.remove(uuid);
        Direction facing = cabinet.getCachedState().get(CabinetBlock.FACING);
        BlockPos exitPos = cabinet.getPos().offset(facing, 1);
        player.teleport(player.getServerWorld(), exitPos.getX() + .5, exitPos.getY(), exitPos.getZ() + .5, player.getYaw(), player.getPitch());
        cabinet.playDoorSequence(player);
    }

    public static void keepPlayerInside(CabinetBlockEntity cabinet, ServerPlayerEntity player) {
        Direction facing = cabinet.getCachedState().get(CabinetBlock.FACING);
        double x = cabinet.getPos().getX() + .5 - facing.getOffsetX() * .05;
        double y = cabinet.getPos().getY() + .05;
        double z = cabinet.getPos().getZ() + .5 - facing.getOffsetZ() * .05;
        player.teleport(player.getServerWorld(), x, y, z, facing.asRotation() + 180f, 0f);
        player.setVelocity(0, 0, 0);
        player.fallDistance = 0;
    }

    public static void forceRelease(CabinetBlockEntity cabinet, ServerPlayerEntity player) {
        UUID uuid = cabinet.getOccupant();
        if (uuid == null) return;
        POLICY.release(uuid, id(cabinet));
        LOADED.remove(uuid);
        cabinet.setOccupant(null);
        if (player != null) {
            player.setInvisible(WAS_INVISIBLE.getOrDefault(uuid, false));
            WAS_INVISIBLE.remove(uuid);
        }
    }

    public static void registerLoaded(CabinetBlockEntity cabinet) {
        UUID uuid = cabinet.getOccupant();
        if (uuid == null) return;
        String id = id(cabinet);
        UUID existing = POLICY.ownerOf(id);
        if (existing == null && POLICY.claim(uuid, id)) LOADED.put(uuid, cabinet);
    }

    public static void unregister(CabinetBlockEntity cabinet) {
        UUID uuid = cabinet.getOccupant();
        if (uuid != null) {
            POLICY.release(uuid, id(cabinet));
            LOADED.remove(uuid);
        }
    }

    public static boolean isHidden(net.minecraft.entity.player.PlayerEntity player) {
        return player != null && POLICY.cabinetOf(player.getUuid()) != null;
    }

    private static void tick(net.minecraft.server.MinecraftServer server) {
        for (var entry : Map.copyOf(LOADED).entrySet()) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            CabinetBlockEntity cabinet = entry.getValue();
            if (player == null || !player.isAlive() || cabinet.isRemoved()) {
                forceRelease(cabinet, player);
                continue;
            }
            keepPlayerInside(cabinet, player);
        }
    }

    private static void releasePlayer(net.minecraft.server.MinecraftServer server, UUID uuid) {
        CabinetBlockEntity cabinet = LOADED.get(uuid);
        if (cabinet == null) return;
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
        forceRelease(cabinet, player);
    }

    private static void clearAll(net.minecraft.server.MinecraftServer server) {
        for (UUID uuid : Map.copyOf(LOADED).keySet()) {
            CabinetBlockEntity cabinet = LOADED.get(uuid);
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (cabinet != null) forceRelease(cabinet, player);
        }
        LOADED.clear();
        WAS_INVISIBLE.clear();
    }

    private static String id(CabinetBlockEntity cabinet) {
        return cabinet.getWorld().getRegistryKey().getValue() + ":" + cabinet.getPos().asLong();
    }

    private static String declineInstrumental(String name) {
        if (name == null || name.isBlank()) return "игроком";
        String s = name;
        if (!s.matches(".*[А-Яа-яЁё]$")) return "игроком «" + s + "»";
        char last = Character.toLowerCase(s.charAt(s.length() - 1));
        return switch (last) {
            case 'а' -> s.substring(0, s.length() - 1) + "ой";
            case 'я' -> s.substring(0, s.length() - 1) + "ей";
            case 'ь' -> s.substring(0, s.length() - 1) + "ью";
            case 'й' -> s.substring(0, s.length() - 1) + "ем";
            case 'о' -> s + "м";
            default -> s + ("бвгджзклмнпрстфхцчшщ".indexOf(last) >= 0 ? "ом" : "ем");
        };
    }
}
