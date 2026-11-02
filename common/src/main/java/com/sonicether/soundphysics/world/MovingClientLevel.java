package com.sonicether.soundphysics.world;

import com.sonicether.soundphysics.utils.RaycastUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.joml.Matrix4dc;
import org.joml.Vector3d;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public class MovingClientLevel implements ClientLevelProxy {

    private final ClientLevelProxy level;
    private final List<SubLevel> subLevels;

    public MovingClientLevel(ClientLevelProxy level, List<SubLevel> subLevels) {
        this.level = level;
        this.subLevels = List.copyOf(subLevels);
    }

    public BlockHitResult rayCast(Vec3 from, Vec3 to, @Nullable BlockPos ignore) {
        return rayCast(from, to, ignore, null);
    }

    public BlockHitResult rayCast(Vec3 from, Vec3 to, @Nullable BlockPos ignore, @Nullable BlockGetter ignoredLevel) {
        return rayCast(from, to, ignore, ignoredLevel, Map.of(), false);
    }

    public BlockHitResult rayCastIgnoringHits(Vec3 from, Vec3 to, Map<BlockGetter, Set<BlockPos>> ignoredHits) {
        return rayCast(from, to, null, null, ignoredHits, false);
    }

    private BlockHitResult rayCast(Vec3 from, Vec3 to, @Nullable BlockPos ignore, @Nullable BlockGetter ignoredLevel, Map<BlockGetter, Set<BlockPos>> ignoredHits, boolean terrainFirst) {
        BlockPos worldIgnore = ignoredLevel == null || ignoredLevel == this || ignoredLevel == level ? ignore : null;
        Set<BlockPos> worldHits = ignoredHits.getOrDefault(this, Set.of());
        boolean insideShip = false;
        for (SubLevel subLevel : subLevels) {
            if (!terrainFirst && subLevel.bounds.contains(from)) {
                insideShip = true;
                break;
            }
        }
        if (insideShip && hasBlocksNearStart(level, from, to, pos -> pos.equals(worldIgnore) || worldHits.contains(pos))) {
            insideShip = false;
        }
        BlockHitResult closest = insideShip ? BlockHitResult.miss(to, Direction.getNearest(from.subtract(to)), BlockPos.containing(to)) : RaycastUtils.rayCastFiltered(level, from, to, pos -> pos.equals(worldIgnore) || worldHits.contains(pos));
        double distance = from.distanceToSqr(closest.getLocation());
        for (SubLevel subLevel : subLevels) {
            if (!subLevel.bounds.contains(from) && subLevel.bounds.clip(from, to).isEmpty()) {
                continue;
            }
            Vec3 localFrom = transform(subLevel.toLocal, from);
            Vec3 localTo = transform(subLevel.toLocal, closest.getLocation());
            BlockPos localIgnore = ignoredLevel == null || ignoredLevel == subLevel.blocks ? ignore : null;
            if (ignoredLevel == null && BlockPos.containing(from).equals(ignore)) {
                localIgnore = BlockPos.containing(localFrom);
            }
            if (!subLevel.localBounds.contains(localFrom)) {
                var entry = subLevel.localBounds.clip(localFrom, localTo);
                if (entry.isEmpty()) {
                    continue;
                }
                localFrom = entry.get();
            }
            if (!subLevel.localBounds.contains(localTo)) {
                localTo = subLevel.localBounds.clip(localTo, localFrom).orElse(localTo);
            }
            BlockPos ignoredBlock = localIgnore;
            Set<BlockPos> localHits = ignoredHits.getOrDefault(subLevel.blocks, Set.of());
            if (insideShip && hasBlocksNearStart(subLevel.blocks, localFrom, localTo, pos -> pos.equals(ignoredBlock) || localHits.contains(pos))) {
                return rayCast(from, to, ignore, ignoredLevel, ignoredHits, true);
            }
            BlockHitResult hit = RaycastUtils.rayCastFiltered(subLevel.blocks, localFrom, localTo, pos -> pos.equals(ignoredBlock) || localHits.contains(pos));
            if (hit.getType() == HitResult.Type.MISS) {
                continue;
            }
            Vec3 position = transform(subLevel.toWorld, hit.getLocation());
            double hitDistance = from.distanceToSqr(position);
            if (hitDistance <= distance) {
                Vector3d normal = new Vector3d(hit.getDirection().getStepX(), hit.getDirection().getStepY(), hit.getDirection().getStepZ());
                subLevel.normals.transformDirection(normal).normalize();
                closest = new MovingHitResult(hit, position, new Vec3(normal.x, normal.y, normal.z), subLevel.blocks);
                distance = hitDistance;
            }
        }
        if (!insideShip) {
            return closest;
        }
        // Ship walls bound the terrain search, especially for long reflection rays inside a cabin.
        BlockHitResult terrain = RaycastUtils.rayCastFiltered(level, from, closest.getLocation(), pos -> pos.equals(worldIgnore) || worldHits.contains(pos));
        return terrain.getType() != HitResult.Type.MISS && (closest.getType() == HitResult.Type.MISS || from.distanceToSqr(terrain.getLocation()) < distance) ? terrain : closest;
    }

    private static boolean hasBlocksNearStart(BlockGetter blocks, Vec3 from, Vec3 to, Predicate<BlockPos> ignore) {
        // Minecraft probes 0.1% along the ray for inside-shape hits; shortening it can change those hits.
        return BlockGetter.traverseBlocks(from, from.lerp(to, 0.001D), blocks, (level, pos) -> {
            return !ignore.test(pos) && !level.getBlockState(pos).isAir() ? Boolean.TRUE : null;
        }, level -> Boolean.FALSE);
    }

    public Vec3 toWorld(Vec3 position) {
        for (SubLevel subLevel : subLevels) {
            if (subLevel.localBounds.contains(position)) {
                return transform(subLevel.toWorld, position);
            }
        }
        return position;
    }

    public FluidState getFluidStateAt(Vec3 position) {
        FluidState fluid = level.getFluidState(BlockPos.containing(position));
        if (!fluid.isEmpty()) {
            return fluid;
        }
        for (SubLevel subLevel : subLevels) {
            if (subLevel.bounds.contains(position)) {
                fluid = subLevel.blocks.getFluidState(BlockPos.containing(transform(subLevel.toLocal, position)));
                if (!fluid.isEmpty()) {
                    return fluid;
                }
            }
        }
        return fluid;
    }

    @Override
    public BlockState getBlockState(@Nonnull BlockPos position) {
        return level.getBlockState(position);
    }

    @Override
    public FluidState getFluidState(@Nonnull BlockPos position) {
        return level.getFluidState(position);
    }

    @Override
    public BlockEntity getBlockEntity(@Nonnull BlockPos position) {
        return level.getBlockEntity(position);
    }

    @Override
    public int getHeight() {
        return level.getHeight();
    }

    @Override
    public int getMinBuildHeight() {
        return level.getMinBuildHeight();
    }

    public static Vec3 transform(Matrix4dc matrix, Vec3 position) {
        Vector3d result = matrix.transformPosition(new Vector3d(position.x, position.y, position.z));
        return new Vec3(result.x, result.y, result.z);
    }

    public static class SubLevel {

        private final ClientLevelProxy blocks;
        private final AABB bounds;
        private final AABB localBounds;
        private final Matrix4d toWorld;
        private final Matrix4d toLocal;
        private final Matrix4d normals;

        public SubLevel(ClientLevelProxy blocks, AABB bounds, AABB localBounds, Matrix4dc toWorld) {
            this.blocks = blocks;
            this.bounds = bounds.inflate(0.001D);
            this.localBounds = localBounds.inflate(0.001D);
            this.toWorld = new Matrix4d(toWorld);
            // Slightly non-unit rotations make the orthonormal inverse shift distant plots by whole blocks.
            this.toLocal = this.toWorld.invertAffine(new Matrix4d());
            this.normals = this.toLocal.transpose(new Matrix4d());
        }
    }

    public static class MovingHitResult extends BlockHitResult {

        private final Vec3 localPosition;
        private final Vec3 normal;
        private final ClientLevelProxy blocks;

        public MovingHitResult(BlockHitResult hit, Vec3 position, Vec3 normal, ClientLevelProxy blocks) {
            super(position, hit.getDirection(), hit.getBlockPos(), hit.isInside());
            this.localPosition = hit.getLocation();
            this.normal = normal;
            this.blocks = blocks;
        }

        public Vec3 getLocalPosition() {
            return localPosition;
        }

        public Vec3 getNormal() {
            return normal;
        }

        public ClientLevelProxy getBlocks() {
            return blocks;
        }
    }
}
