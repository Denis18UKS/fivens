package ru.fifth.horror.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import ru.fifth.horror.FifthMod;
import ru.fifth.horror.block.CassetteDriveBlockEntity;
import ru.fifth.horror.block.TelevisionBlockEntity;

/** Links a physical cassette drive to one physical television. Playback is always TV-only. */
public final class TvLinkToolItem extends Item {
    private static final String TV_POS = "FivenTvPos";
    private static final String TV_DIM = "FivenTvDimension";

    public TvLinkToolItem(Settings settings) { super(settings); }

    @Override
    public ActionResult useOnBlock(ItemUsageContext ctx) {
        PlayerEntity player = ctx.getPlayer();
        if (player == null) return ActionResult.PASS;
        var world = ctx.getWorld();
        var pos = ctx.getBlockPos();
        var state = world.getBlockState(pos);

        if (world.isClient) {
            return (state.isOf(FifthMod.TELEVISION) || state.isOf(FifthMod.CASSETTE_DRIVE))
                    ? ActionResult.SUCCESS : ActionResult.PASS;
        }

        var nbt = ctx.getStack().getOrCreateNbt();
        String dimension = world.getRegistryKey().getValue().toString();

        if (state.isOf(FifthMod.TELEVISION) && world.getBlockEntity(pos) instanceof TelevisionBlockEntity) {
            nbt.putLong(TV_POS, pos.asLong());
            nbt.putString(TV_DIM, dimension);
            player.sendMessage(Text.literal("§8[§cFiven§8] §7Телевизор выбран: §f" + pos.toShortString()
                    + "§7. Теперь ПКМ этим инструментом по кассетоводу."), true);
            return ActionResult.SUCCESS;
        }

        if (state.isOf(FifthMod.CASSETTE_DRIVE) && world.getBlockEntity(pos) instanceof CassetteDriveBlockEntity drive) {
            if (!nbt.contains(TV_POS)) {
                player.sendMessage(Text.literal("§cСначала выбери телевизор: ПКМ инструментом по TV."), true);
                return ActionResult.SUCCESS;
            }
            if (nbt.contains(TV_DIM) && !dimension.equals(nbt.getString(TV_DIM))) {
                player.sendMessage(Text.literal("§cТелевизор и кассетовод должны быть в одном измерении."), true);
                return ActionResult.SUCCESS;
            }

            BlockPos tvPos = BlockPos.fromLong(nbt.getLong(TV_POS));
            if (!(world.getBlockEntity(tvPos) instanceof TelevisionBlockEntity)) {
                player.sendMessage(Text.literal("§cВыбранный телевизор больше не существует. Выбери TV заново."), true);
                nbt.remove(TV_POS);
                nbt.remove(TV_DIM);
                return ActionResult.SUCCESS;
            }

            drive.linkTv(tvPos);
            drive.setPlaybackMode(1);
            player.sendMessage(Text.literal("§8[§cFiven§8] §aКассетовод связан с телевизором§7. VHS всегда воспроизводится только на физическом экране TV."), true);
            return ActionResult.SUCCESS;
        }

        return ActionResult.PASS;
    }
}
