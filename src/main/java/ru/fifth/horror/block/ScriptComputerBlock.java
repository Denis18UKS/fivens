package ru.fifth.horror.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import ru.fifth.horror.network.FifthNetworking;

public class ScriptComputerBlock extends Block implements BlockEntityProvider {
    public ScriptComputerBlock(Settings settings) { super(settings); }
    @Override public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new ScriptComputerBlockEntity(pos, state); }
    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!world.isClient && player instanceof ServerPlayerEntity sp && world.getBlockEntity(pos) instanceof ScriptComputerBlockEntity be) {
            if (sp.hasPermissionLevel(2)) FifthNetworking.openComputer(sp, be);
        }
        return ActionResult.SUCCESS;
    }
}
