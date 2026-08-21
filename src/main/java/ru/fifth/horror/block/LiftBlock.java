package ru.fifth.horror.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
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
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import ru.fifth.horror.FifthMod;
import ru.fifth.horror.lift.LiftManager;
import ru.fifth.horror.network.FifthNetworking;

/**
 * The physical elevator cabin. The GeckoLib model is rendered by a BlockEntity renderer;
 * collision is built from the same dimensions as lift.geo.json and changes with the doors.
 */
public final class LiftBlock extends BlockWithEntity {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final BooleanProperty OPEN = Properties.OPEN;

    // lift.geo.json uses 16 model units per block and is centred on the origin.
    private static final VoxelShape FLOOR = Block.createCuboidShape(-16, 0, -16, 32, 2, 32);
    private static final VoxelShape CEILING = Block.createCuboidShape(-16, 34, -16, 32, 36, 32);
    private static final VoxelShape LEFT_WALL = Block.createCuboidShape(-16, 2, -16, -12, 34, 32);
    private static final VoxelShape RIGHT_WALL = Block.createCuboidShape(28, 2, -16, 32, 34, 32);
    private static final VoxelShape BACK_WALL = Block.createCuboidShape(-12, 2, 28, 28, 34, 32);
    private static final VoxelShape DOORS = Block.createCuboidShape(-12, 2, -16, 28, 34, -12);
    private static final VoxelShape CABIN_OPEN = VoxelShapes.union(FLOOR, CEILING, LEFT_WALL, RIGHT_WALL, BACK_WALL);
    private static final VoxelShape CABIN_CLOSED = VoxelShapes.union(CABIN_OPEN, DOORS);

    public LiftBlock(Settings settings) {
        super(settings.nonOpaque());
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(OPEN, false));
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite()).with(OPEN, false);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN);
    }

    @Override
    public BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(FACING, rotation.rotate(state.get(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, BlockMirror mirror) {
        return state.rotate(mirror.getRotation(state.get(FACING)));
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return DirectionalBlockShapes.rotateFromNorth(state.get(OPEN) ? CABIN_OPEN : CABIN_CLOSED, state.get(FACING));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return getOutlineShape(state, world, pos, context);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new LiftBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return world.isClient ? null : checkType(type, FifthMod.LIFT_BE, LiftBlockEntity::tickServer);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.onPlaced(world, pos, state, placer, stack);
        if (!world.isClient && world.getBlockEntity(pos) instanceof LiftBlockEntity lift) {
            LiftManager.register(lift);
        }
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock()) && world.getBlockEntity(pos) instanceof LiftBlockEntity lift) {
            LiftManager.unregister(lift);
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!(world.getBlockEntity(pos) instanceof LiftBlockEntity lift)) return ActionResult.PASS;
        ItemStack held = player.getStackInHand(hand);

        if (held.isOf(FifthMod.LIFT_EDITOR_TOOL)) {
            if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer && serverPlayer.hasPermissionLevel(2)) {
                FifthNetworking.openLiftEditor(serverPlayer, lift);
            }
            return ActionResult.success(world.isClient);
        }

        if (held.isOf(FifthMod.LIFT_PANEL_TOOL)) {
            if (!world.isClient) {
                held.getOrCreateNbt().putString("FivenLiftWorld", world.getRegistryKey().getValue().toString());
                held.getOrCreateNbt().putLong("FivenLiftPos", pos.asLong());
                player.sendMessage(Text.literal("§8[§cFiven§8] §7Лифт для панели выбран: §f" + lift.getLiftId()), true);
            }
            return ActionResult.success(world.isClient);
        }

        if (player.isSneaking()) {
            if (!world.isClient) {
                player.sendMessage(Text.literal("§8[§cFiven§8] §7Лифт §f" + lift.getLiftId() + " §7| этаж §c" + lift.getCurrentFloor() + " §7| слой от §f" + lift.getStageOrigin().toShortString()), true);
            }
            return ActionResult.success(world.isClient);
        }

        if (!world.isClient) {
            if (lift.canOpenOnFloor(lift.getCurrentFloor())) {
                lift.openDoors(80);
            } else {
                player.sendMessage(Text.literal("§8[§cFiven§8] §7Двери на этаже §c" + lift.getCurrentFloor() + " §7заблокированы."), true);
                if (world instanceof ServerWorld sw) sw.playSound(null, pos, net.minecraft.sound.SoundEvents.BLOCK_IRON_DOOR_CLOSE, net.minecraft.sound.SoundCategory.BLOCKS, 0.8f, 0.7f);
            }
        }
        return ActionResult.success(world.isClient);
    }
}
