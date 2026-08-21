package ru.fifth.horror.block;

import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import ru.fifth.horror.FifthMod;

/** Physical VHS drive, 16x4x16 model with horizontal placement rotation. */
public final class CassetteDriveBlock extends BlockWithEntity {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    private static final VoxelShape SHAPE = Block.createCuboidShape(0, 0, 0, 16, 4, 16);

    public CassetteDriveBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
    }
    @Nullable @Override public BlockState getPlacementState(ItemPlacementContext ctx){return getDefaultState().with(FACING,ctx.getHorizontalPlayerFacing().getOpposite());}
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> b){b.add(FACING);}
    @Override public BlockState rotate(BlockState s,BlockRotation r){return s.with(FACING,r.rotate(s.get(FACING)));}
    @Override public BlockState mirror(BlockState s,BlockMirror m){return s.rotate(m.getRotation(s.get(FACING)));}
    @Override public VoxelShape getOutlineShape(BlockState s,BlockView w,BlockPos p,ShapeContext c){return SHAPE;}
    @Override public VoxelShape getCollisionShape(BlockState s,BlockView w,BlockPos p,ShapeContext c){return SHAPE;}
    @Override public BlockEntity createBlockEntity(BlockPos pos,BlockState state){return new CassetteDriveBlockEntity(pos,state);}
    @Override public BlockRenderType getRenderType(BlockState state){return BlockRenderType.MODEL;}
    @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World w,BlockState s,BlockEntityType<T> t){return w.isClient?checkType(t,FifthMod.CASSETTE_DRIVE_BE,CassetteDriveBlockEntity::tickClient):checkType(t,FifthMod.CASSETTE_DRIVE_BE,CassetteDriveBlockEntity::tick);}
    @Override public ActionResult onUse(BlockState state,World world,BlockPos pos,PlayerEntity player,Hand hand,BlockHitResult hit){
        BlockEntity be=world.getBlockEntity(pos);if(!(be instanceof CassetteDriveBlockEntity drive))return ActionResult.PASS;
        ItemStack held=player.getStackInHand(hand);
        if(held.isOf(FifthMod.VHS_CASSETTE)){
            if(!world.isClient){if(drive.hasCassette())player.sendMessage(Text.literal("§7В кассетоводе уже есть кассета."),true);else{ItemStack one=held.copy();one.setCount(1);drive.insert(one);if(!player.getAbilities().creativeMode)held.decrement(1);}}
            return ActionResult.success(world.isClient);
        }
        if(player.isSneaking()&&!world.isClient){ItemStack out=drive.ejectNow();if(!out.isEmpty())player.giveItemStack(out);return ActionResult.SUCCESS;}
        return ActionResult.PASS;
    }
}
