package ru.fifth.horror.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import ru.fifth.horror.network.FifthNetworking;

/** Scenario computer with horizontal facing so its front follows placement direction. */
public final class ScriptComputerBlock extends Block implements BlockEntityProvider {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public ScriptComputerBlock(Settings settings){super(settings);setDefaultState(getStateManager().getDefaultState().with(FACING,Direction.NORTH));}
    @Nullable @Override public BlockState getPlacementState(ItemPlacementContext ctx){return getDefaultState().with(FACING,ctx.getHorizontalPlayerFacing().getOpposite());}
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> b){b.add(FACING);}
    @Override public BlockState rotate(BlockState s,BlockRotation r){return s.with(FACING,r.rotate(s.get(FACING)));}
    @Override public BlockState mirror(BlockState s,BlockMirror m){return s.rotate(m.getRotation(s.get(FACING)));}
    @Override public BlockEntity createBlockEntity(BlockPos pos,BlockState state){return new ScriptComputerBlockEntity(pos,state);}
    @Override public ActionResult onUse(BlockState state,World world,BlockPos pos,PlayerEntity player,Hand hand,BlockHitResult hit){
        if(!world.isClient&&player instanceof ServerPlayerEntity sp&&world.getBlockEntity(pos) instanceof ScriptComputerBlockEntity be&&sp.hasPermissionLevel(2))FifthNetworking.openComputer(sp,be);
        return ActionResult.SUCCESS;
    }
}
