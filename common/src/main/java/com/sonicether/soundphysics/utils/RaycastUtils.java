package com.sonicether.soundphysics.utils;

import com.sonicether.soundphysics.world.MovingClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public class RaycastUtils {

    public static BlockHitResult rayCast(@Nullable BlockGetter blockGetter, Vec3 from, Vec3 to, @Nullable BlockPos ignore) {
        if (blockGetter == null) {
            return BlockHitResult.miss(to, Direction.getNearest(from.subtract(to)), BlockPos.containing(to));
        }
        if (blockGetter instanceof MovingClientLevel movingLevel) {
            return movingLevel.rayCast(from, to, ignore);
        }
        return rayCastFiltered(blockGetter, from, to, pos -> pos.equals(ignore));
    }

    public static BlockHitResult rayCastFiltered(BlockGetter blockGetter, Vec3 from, Vec3 to, Predicate<BlockPos> ignore) {
        return BlockGetter.traverseBlocks(from, to, blockGetter, (g, pos) -> {
            if (ignore.test(pos)) {
                return null;
            }
            BlockState blockState = blockGetter.getBlockState(pos);
            FluidState fluidState = blockGetter.getFluidState(pos);
            VoxelShape shape = ClipContext.Block.COLLIDER.get(blockState, blockGetter, pos, CollisionContext.empty());
            BlockHitResult blockHitResult = blockGetter.clipWithInteractionOverride(from, to, pos, shape, blockState);
            VoxelShape fluidShape = fluidState.getShape(blockGetter, pos);
            BlockHitResult fluidHitResult = fluidShape.clip(from, to, pos);
            if (fluidHitResult == null) {
                return blockHitResult;
            }
            if (blockHitResult == null) {
                return fluidHitResult;
            }
            double blockDistance = from.distanceToSqr(blockHitResult.getLocation());
            double fluidDistance = from.distanceToSqr(fluidHitResult.getLocation());
            return blockDistance <= fluidDistance ? blockHitResult : fluidHitResult;
        }, (g) -> {
            return BlockHitResult.miss(to, Direction.getNearest(from.subtract(to)), BlockPos.containing(to));
        });
    }

    public static Vec3 getNormal(BlockHitResult hit) {
        return hit instanceof MovingClientLevel.MovingHitResult movingHit ? movingHit.getNormal() : new Vec3(hit.getDirection().step());
    }

    public static BlockHitResult rayCastFromHit(BlockGetter level, Vec3 from, Vec3 to, BlockHitResult ignore) {
        if (level instanceof MovingClientLevel movingLevel) {
            return movingLevel.rayCast(from, to, ignore.getBlockPos(), getHitLevel(level, ignore));
        }
        return rayCast(level, from, to, ignore.getBlockPos());
    }

    public static BlockHitResult rayCastIgnoringHits(BlockGetter level, Vec3 from, Vec3 to, Map<BlockGetter, Set<BlockPos>> ignoredHits) {
        if (level instanceof MovingClientLevel movingLevel) {
            return movingLevel.rayCastIgnoringHits(from, to, ignoredHits);
        }
        Set<BlockPos> ignored = ignoredHits.getOrDefault(level, Set.of());
        return rayCastFiltered(level, from, to, ignored::contains);
    }

    public static Vec3 getLocalPosition(BlockHitResult hit) {
        return hit instanceof MovingClientLevel.MovingHitResult movingHit ? movingHit.getLocalPosition() : hit.getLocation();
    }

    public static BlockGetter getHitLevel(BlockGetter level, BlockHitResult hit) {
        return hit instanceof MovingClientLevel.MovingHitResult movingHit ? movingHit.getBlocks() : level;
    }

}
