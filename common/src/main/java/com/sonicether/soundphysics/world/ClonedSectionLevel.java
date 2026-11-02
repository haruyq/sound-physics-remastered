package com.sonicether.soundphysics.world;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.material.FluidState;

import javax.annotation.Nonnull;

public class ClonedSectionLevel implements ClientLevelProxy {

    private final Long2ObjectMap<PalettedContainer<BlockState>> sections;
    private final int height;
    private final int minBuildHeight;

    public ClonedSectionLevel(LevelHeightAccessor level, Long2ObjectMap<PalettedContainer<BlockState>> sections) {
        this.height = level.getHeight();
        this.minBuildHeight = level.getMinBuildHeight();
        this.sections = new Long2ObjectOpenHashMap<>(sections.size());
        for (var entry : sections.long2ObjectEntrySet()) {
            this.sections.put(entry.getLongKey(), entry.getValue().copy());
        }
    }

    @Override
    public BlockState getBlockState(@Nonnull BlockPos pos) {
        if (isOutsideBuildHeight(pos)) {
            return Blocks.VOID_AIR.defaultBlockState();
        }
        var section = sections.get(SectionPos.asLong(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4));
        return section == null ? Blocks.VOID_AIR.defaultBlockState() : section.get(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
    }

    @Override
    public FluidState getFluidState(@Nonnull BlockPos pos) {
        return getBlockState(pos).getFluidState();
    }

    @Override
    public BlockEntity getBlockEntity(@Nonnull BlockPos pos) {
        return null;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public int getMinBuildHeight() {
        return minBuildHeight;
    }
}
