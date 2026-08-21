package ru.fifth.horror.block;

import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import ru.fifth.horror.FifthMod;

/** Television block. The current Blockbench model fills one block; facing follows the placer. */
public final class TelevisionBlock extends BlockWithEntity {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public TelevisionBlock(Settings settings){super(settings);setDefaultState(getStateManager().getDefaultState().with(FACING,Direction.NORTH));}
    @Nullable @Override public BlockState getPlacementState(ItemPlacementContext ctx){return getDefaultState().with(FACING,ctx.getHorizontalPlayerFacing().getOpposite());}
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> b){b.add(FACING);}
    @Override public BlockState rotate(BlockState s,BlockRotation r){return s.with(FACING,r.rotate(s.get(FACING)));}
    @Override public BlockState mirror(BlockState s,BlockMirror m){return s.rotate(m.getRotation(s.get(FACING)));}
    @Override public BlockEntity createBlockEntity(BlockPos p,BlockState s){return new TelevisionBlockEntity(p,s);}
    @Override public BlockRenderType getRenderType(BlockState s){return BlockRenderType.MODEL;}
    @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World w,BlockState s,BlockEntityType<T> t){return w.isClient?checkType(t,FifthMod.TELEVISION_BE,TelevisionBlockEntity::tickClient):null;}
    @Override public ActionResult onUse(BlockState state,World world,BlockPos pos,PlayerEntity player,Hand hand,BlockHitResult hit){if(!world.isClient)player.sendMessage(Text.literal("§8[§cFiven§8] §7Телевизор. Свяжи его с кассетоводом инструментом TV/VHS."),true);return ActionResult.success(world.isClient);}
}
