package ru.fifth.horror.block;

import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

/** Rotates collision/outline voxel shapes around the centre of a block. */
public final class DirectionalBlockShapes {
    private DirectionalBlockShapes() {}

    public static VoxelShape rotateFromNorth(VoxelShape north, Direction facing) {
        if (facing == Direction.NORTH) return north;
        VoxelShape[] out = {VoxelShapes.empty()};
        north.forEachBox((minX, minY, minZ, maxX, maxY, maxZ) -> {
            VoxelShape part = switch (facing) {
                case SOUTH -> VoxelShapes.cuboid(1.0 - maxX, minY, 1.0 - maxZ, 1.0 - minX, maxY, 1.0 - minZ);
                case EAST -> VoxelShapes.cuboid(1.0 - maxZ, minY, minX, 1.0 - minZ, maxY, maxX);
                case WEST -> VoxelShapes.cuboid(minZ, minY, 1.0 - maxX, maxZ, maxY, 1.0 - minX);
                default -> VoxelShapes.cuboid(minX, minY, minZ, maxX, maxY, maxZ);
            };
            out[0] = VoxelShapes.union(out[0], part);
        });
        return out[0];
    }
}
