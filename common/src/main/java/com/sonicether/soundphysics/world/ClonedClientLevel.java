package com.sonicether.soundphysics.world;

import java.util.HashMap;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import javax.annotation.Nonnull;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/**
 * Read-only sparse clone of a client level section, holding a copy of chunks in a
 * radius around an origin point. Intended as a drop-in substitute to `ClientLevel`
 * for non-write operations.
 * <p>
 * Offers access to level chunks and height data, as well as proxied access to
 * block states, fluid states, block entities, and height data.
 * <p>
 * If gated behind an `AtomicReference<ClonedClientLevel>`, can be
 * safely accessed from any thread once built.
 *
 * @author Saint (@augustsaintfreytag)
 */
public class ClonedClientLevel implements ClientLevelProxy {

    private final ClonedLevelHeightAccessor heightAccessor;
    private final Long2ObjectMap<ClonedLevelChunk> clonedLevelChunks;
    private final BlockPos clonedLevelOrigin;
    private final long clonedLevelTick;

    public ClonedClientLevel(Level level, BlockPos origin, long tick, int range) {
        var cache = level.getChunkSource();
        var heightAccessor = new ClonedLevelHeightAccessor(level);

        var cachedLevelChunks = new Long2ObjectOpenHashMap<ClonedLevelChunk>();
        var originChunkPos = new ChunkPos(origin.getX() >> 4, origin.getZ() >> 4);

        for (int x = -range; x < range; x++) {
            for (int z = -range; z < range; z++) {
                var chunkPos = new ChunkPos(originChunkPos.x + x, originChunkPos.z + z);
                var chunk = cache.getChunk(chunkPos.x, chunkPos.z, ChunkStatus.FULL, false);

                if (chunk == null) {
                    continue;
                }

                var clonedChunk = new ClonedLevelChunk(level, chunkPos, chunk.getSections());
                cachedLevelChunks.put(chunkPos.toLong(), clonedChunk);
            }
        }

        this.heightAccessor = heightAccessor;
        this.clonedLevelOrigin = origin;
        this.clonedLevelTick = tick;
        this.clonedLevelChunks = cachedLevelChunks;
    }

    public ClonedClientLevel(Level level, BlockPos origin, long tick, HashMap<ChunkPos, ClonedLevelChunk> chunks) {
        this.heightAccessor = new ClonedLevelHeightAccessor(level);
        this.clonedLevelOrigin = origin;
        this.clonedLevelTick = tick;
        this.clonedLevelChunks = new Long2ObjectOpenHashMap<>(chunks.size());
        chunks.forEach((pos, chunk) -> this.clonedLevelChunks.put(pos.toLong(), chunk));
    }

    // Properties

    public BlockPos getOrigin() {
        return clonedLevelOrigin;
    }

    public long getTick() {
        return clonedLevelTick;
    }

    public ClonedLevelChunk getChunk(int x, int z) {
        return clonedLevelChunks.get(ChunkPos.asLong(x, z));
    }

    @Override
    public BlockState getBlockState(@Nonnull BlockPos blockPos) {
        if (isOutsideBuildHeight(blockPos)) {
            return Blocks.VOID_AIR.defaultBlockState();
        }

        var levelChunk = getChunk(blockPos.getX() >> 4, blockPos.getZ() >> 4);

        if (levelChunk == null) {
            return Blocks.VOID_AIR.defaultBlockState();
        }

        return levelChunk.getBlockState(blockPos);
    }

    @Override
    public FluidState getFluidState(@Nonnull BlockPos blockPos) {
        if (isOutsideBuildHeight(blockPos)) {
            return Fluids.EMPTY.defaultFluidState();
        }

        var levelChunk = getChunk(blockPos.getX() >> 4, blockPos.getZ() >> 4);

        if (levelChunk == null) {
            return Fluids.EMPTY.defaultFluidState();
        }

        return levelChunk.getFluidState(blockPos);
    }

    @Override
    public int getHeight() {
        return heightAccessor.getHeight();
    }

    @Override
    public int getMinBuildHeight() {
        return heightAccessor.getMinBuildHeight();
    }

    @Override
    public BlockEntity getBlockEntity(@Nonnull BlockPos blockPos) {
        var levelChunk = getChunk(blockPos.getX() >> 4, blockPos.getZ() >> 4);

        if (levelChunk == null) {
            return null;
        }

        return levelChunk.getBlockEntity(blockPos);
    }
}

