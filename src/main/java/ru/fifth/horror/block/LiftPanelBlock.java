package ru.fifth.horror.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import ru.fifth.horror.FifthMod;
import ru.fifth.horror.network.FifthNetworking;

/** A real wall-mounted 9-floor panel block. */
public final class LiftPanelBlock extends BlockWithEntity {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    private static final VoxelShape NORTH = Block.createCuboidShape(2, 1, 0, 14, 15, 1.5);

    public LiftPanelBlock(Settings settings) {
        super(settings.nonOpaque());
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        Direction side = ctx.getSide();
        Direction facing = side.getAxis().isHorizontal() ? side : ctx.getHorizontalPlayerFacing().getOpposite();
        return getDefaultState().with(FACING, facing);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(FACING); }

    @Override
    public BlockState rotate(BlockState state, BlockRotation rotation) { return state.with(FACING, rotation.rotate(state.get(FACING))); }

    @Override
    public BlockState mirror(BlockState state, BlockMirror mirror) { return state.rotate(mirror.getRotation(state.get(FACING))); }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return DirectionalBlockShapes.rotateFromNorth(NORTH, state.get(FACING));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return getOutlineShape(state, world, pos, context);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) { return BlockRenderType.MODEL; }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new LiftPanelBlockEntity(pos, state); }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!(world.getBlockEntity(pos) instanceof LiftPanelBlockEntity panel)) return ActionResult.PASS;
        ItemStack held = player.getStackInHand(hand);
        boolean edit = held.isOf(FifthMod.LIFT_PANEL_TOOL);

        if (edit && !world.isClient) {
            var nbt = held.getOrCreateNbt();
            if (nbt.contains("FivenLiftWorld") && nbt.contains("FivenLiftPos")) {
                panel.setLiftReference(nbt.getString("FivenLiftWorld"), BlockPos.fromLong(nbt.getLong("FivenLiftPos")));
                player.sendMessage(Text.literal("§8[§cFiven§8] §7Панель привязана к выбранному лифту."), true);
            }
        }

        if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer) {
            FifthNetworking.openLiftPanel(serverPlayer, panel, edit && serverPlayer.hasPermissionLevel(2));
        }
        return ActionResult.success(world.isClient);
    }
}
