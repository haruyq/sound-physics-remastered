package com.sonicether.soundphysics.integration;

import com.sonicether.soundphysics.SoundPhysicsMod;
import com.sonicether.soundphysics.utils.LevelAccessUtils;
import com.sonicether.soundphysics.world.ClonedSectionLevel;
import com.sonicether.soundphysics.world.MovingClientLevel;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;

import java.util.ArrayList;
import java.util.List;

public class SableIntegration {

    public static List<MovingClientLevel.SubLevel> snapshot(Level level, Vec3 origin) {
        double range = SoundPhysicsMod.CONFIG.levelCloneRange.get() * 16D;
        double poseRange = Math.max(range, SoundPhysicsMod.CONFIG.maxSoundProcessingDistance.get());
        var query = new BoundingBox3d(origin.x - poseRange, origin.y - poseRange, origin.z - poseRange, origin.x + poseRange, origin.y + poseRange, origin.z + poseRange);
        var copyBounds = new AABB(origin, origin).inflate(range);
        var snapshots = new ArrayList<MovingClientLevel.SubLevel>();
        for (var subLevel : SableCompanion.INSTANCE.getAllIntersecting(level, query)) {
            var bounds = subLevel.boundingBox();
            var worldBounds = new AABB(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ());
            Matrix4d toWorld = subLevel.logicalPose().bakeIntoMatrix(new Matrix4d());
            Matrix4d toLocal = toWorld.invertAffine(new Matrix4d());
            AABB localBounds = transformBounds(worldBounds, toLocal);
            var sections = new Long2ObjectOpenHashMap<PalettedContainer<BlockState>>();
            if (worldBounds.intersects(copyBounds)) {
                AABB localCopyBounds = transformBounds(worldBounds.intersect(copyBounds), toLocal);
                int minSection = Math.max(0, level.getSectionIndex(Mth.floor(localCopyBounds.minY)));
                int maxSection = Math.min(level.getSectionsCount() - 1, level.getSectionIndex(Mth.floor(localCopyBounds.maxY)));
                for (int x = Mth.floor(localCopyBounds.minX) >> 4; x <= Mth.floor(localCopyBounds.maxX) >> 4; x++) {
                    for (int z = Mth.floor(localCopyBounds.minZ) >> 4; z <= Mth.floor(localCopyBounds.maxZ) >> 4; z++) {
                        var owner = SableCompanion.INSTANCE.getContaining(level, x, z);
                        if (owner == null || !owner.getUniqueId().equals(subLevel.getUniqueId())) {
                            continue;
                        }
                        var chunk = level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false);
                        if (chunk == null) {
                            continue;
                        }
                        for (int i = minSection; i <= maxSection; i++) {
                            var section = chunk.getSections()[i];
                            if (section != null && !section.hasOnlyAir()) {
                                sections.put(SectionPos.asLong(x, level.getSectionYFromSectionIndex(i), z), section.getStates());
                            }
                        }
                    }
                }
            }
            var blocks = new ClonedSectionLevel(level, sections);
            snapshots.add(new MovingClientLevel.SubLevel(blocks, worldBounds, localBounds, toWorld));
        }
        return List.copyOf(snapshots);
    }

    private static AABB transformBounds(AABB bounds, Matrix4d transform) {
        Vec3 min = new Vec3(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
        Vec3 max = new Vec3(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY);
        for (int x = 0; x < 2; x++) {
            for (int y = 0; y < 2; y++) {
                for (int z = 0; z < 2; z++) {
                    Vec3 corner = MovingClientLevel.transform(transform, new Vec3(x == 0 ? bounds.minX : bounds.maxX, y == 0 ? bounds.minY : bounds.maxY, z == 0 ? bounds.minZ : bounds.maxZ));
                    min = new Vec3(Math.min(min.x, corner.x), Math.min(min.y, corner.y), Math.min(min.z, corner.z));
                    max = new Vec3(Math.max(max.x, corner.x), Math.max(max.y, corner.y), Math.max(max.z, corner.z));
                }
            }
        }
        return new AABB(min, max);
    }

    public static class Client {

        public static void init() {
            LevelAccessUtils.setSubLevelProvider(level -> {
                var player = Minecraft.getInstance().player;
                return player == null ? List.of() : snapshot(level, player.position());
            });
        }
    }
}
