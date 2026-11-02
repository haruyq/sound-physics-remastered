package com.sonicether.soundphysics.world;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClonedSectionLevelTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void sparseSectionsAreImmutableAndPreserveNegativeCoordinatesAndFluids() {
        var states = new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY, Blocks.AIR.defaultBlockState(), PalettedContainer.Strategy.SECTION_STATES);
        states.set(15, 15, 15, Blocks.WATER.defaultBlockState());
        var sections = new Long2ObjectOpenHashMap<PalettedContainer<BlockState>>();
        sections.put(SectionPos.asLong(-1000000, -1, 1000000), states);
        var clone = new ClonedSectionLevel(LevelHeightAccessor.create(-64, 384), sections);
        BlockPos pos = new BlockPos(-16000000 + 15, -1, 16000000 + 15);
        states.set(15, 15, 15, Blocks.STONE.defaultBlockState());
        sections.clear();

        assertEquals(Blocks.WATER.defaultBlockState(), clone.getBlockState(pos));
        assertEquals(Fluids.WATER, clone.getFluidState(pos).getType());
        assertTrue(clone.getBlockState(pos.east()).isAir());
        assertTrue(clone.getBlockState(pos.below(64)).isAir());
        assertTrue(clone.getFluidState(BlockPos.ZERO).isEmpty());
        assertNull(clone.getBlockEntity(pos));
        assertEquals(-64, clone.getMinBuildHeight());
        assertEquals(384, clone.getHeight());
    }
}
