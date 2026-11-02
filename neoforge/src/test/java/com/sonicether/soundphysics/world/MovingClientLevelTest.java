package com.sonicether.soundphysics.world;

import com.sonicether.soundphysics.utils.RaycastUtils;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MovingClientLevelTest {

    private static final BlockPos PLOT = new BlockPos(16000, 64, 16000);

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void translatedWallRetainsItsMaterialAndLocalHitPosition() {
        var ship = ship(Map.of(PLOT, Blocks.STONE.defaultBlockState()), pose(0D));
        var scene = new MovingClientLevel(blocks(Map.of()), List.of(ship));
        var hit = RaycastUtils.rayCast(scene, new Vec3(8, 64.5, 10.5), new Vec3(13, 64.5, 10.5), null);

        assertEquals(HitResult.Type.BLOCK, hit.getType());
        assertEquals(PLOT, hit.getBlockPos());
        assertEquals(Blocks.STONE.defaultBlockState(), RaycastUtils.getHitLevel(scene, hit).getBlockState(hit.getBlockPos()));
        assertVector(new Vec3(10, 64.5, 10.5), hit.getLocation());
        assertVector(new Vec3(16000, 64.5, 16000.5), RaycastUtils.getLocalPosition(hit));
        assertVector(new Vec3(-1, 0, 0), RaycastUtils.getNormal(hit));
    }

    @Test
    void rotatedWallHasAnExactWorldNormalAndLocalFace() {
        Matrix4d pose = pose(Math.PI / 4D);
        var scene = new MovingClientLevel(blocks(Map.of()), List.of(ship(Map.of(PLOT, Blocks.OAK_SLAB.defaultBlockState()), pose)));
        Vec3 from = MovingClientLevel.transform(pose, new Vec3(15998, 64.25, 16000.5));
        Vec3 to = MovingClientLevel.transform(pose, new Vec3(16002, 64.25, 16000.5));
        var hit = RaycastUtils.rayCast(scene, from, to, null);

        assertEquals(HitResult.Type.BLOCK, hit.getType());
        assertVector(new Vec3(-Math.sqrt(0.5), 0, Math.sqrt(0.5)), RaycastUtils.getNormal(hit));
        assertVector(new Vec3(16000, 64.25, 16000.5), RaycastUtils.getLocalPosition(hit));
        var hitLevel = RaycastUtils.getHitLevel(scene, hit);
        assertFalse(hitLevel.getBlockState(PLOT).isFaceSturdy(hitLevel, PLOT, hit.getDirection()));
        Vec3 normal = RaycastUtils.getNormal(hit);
        assertEquals(HitResult.Type.MISS, RaycastUtils.rayCast(scene, hit.getLocation().add(normal.scale(0.001)), from, null).getType());
    }

    @Test
    void closestHitWinsBetweenTerrainAndSeveralShips() {
        var near = ship(Map.of(PLOT, Blocks.WHITE_WOOL.defaultBlockState()), pose(0));
        Matrix4d farPose = new Matrix4d().translation(3, 0, 0).mul(pose(0));
        var far = ship(Map.of(PLOT, Blocks.STONE.defaultBlockState()), farPose);
        Vec3 from = new Vec3(8, 64.5, 10.5);
        Vec3 to = new Vec3(16, 64.5, 10.5);
        var scene = new MovingClientLevel(blocks(Map.of()), List.of(far, near));
        assertVector(new Vec3(10, 64.5, 10.5), RaycastUtils.rayCast(scene, from, to, null).getLocation());

        BlockPos ground = new BlockPos(9, 64, 10);
        scene = new MovingClientLevel(blocks(Map.of(ground, Blocks.STONE.defaultBlockState())), List.of(near, far));
        assertEquals(ground, RaycastUtils.rayCast(scene, from, to, null).getBlockPos());
    }

    @Test
    void shipWallStopsTerrainTraversalAtTheWall() {
        int[] reads = {0};
        var scene = new MovingClientLevel(blocks(Map.of(), reads), List.of(ship(Map.of(PLOT, Blocks.STONE.defaultBlockState()), pose(0))));
        var hit = RaycastUtils.rayCast(scene, new Vec3(8, 64.5, 10.5), new Vec3(264, 64.5, 10.5), null);

        assertEquals(PLOT, hit.getBlockPos());
        assertTrue(reads[0] <= 10, "terrain reads stop at the ship wall: " + reads[0]);
    }

    @Test
    void shipWinsAnEqualDistanceTerrainHit() {
        BlockPos ground = new BlockPos(10, 64, 10);
        var scene = new MovingClientLevel(blocks(Map.of(ground, Blocks.WHITE_WOOL.defaultBlockState())), List.of(ship(Map.of(PLOT, Blocks.STONE.defaultBlockState()), pose(0))));
        var hit = RaycastUtils.rayCast(scene, new Vec3(8, 64.5, 10.5), new Vec3(13, 64.5, 10.5), null);

        assertEquals(PLOT, hit.getBlockPos());
        assertTrue(hit instanceof MovingClientLevel.MovingHitResult);
    }

    @Test
    void unobstructedRayKeepsItsOriginalEndpoint() {
        var scene = new MovingClientLevel(blocks(Map.of()), List.of(ship(Map.of(PLOT, Blocks.STONE.defaultBlockState()), pose(0))));
        Vec3 to = new Vec3(264, 64.5, 14.5);
        var hit = RaycastUtils.rayCast(scene, new Vec3(8, 64.5, 14.5), to, null);

        assertEquals(HitResult.Type.MISS, hit.getType());
        assertVector(to, hit.getLocation());
    }

    @Test
    void startingInsideShipShapeRetainsItsOriginalHitPosition() {
        var scene = new MovingClientLevel(blocks(Map.of(new BlockPos(11, 64, 10), Blocks.STONE.defaultBlockState())), List.of(ship(Map.of(PLOT, Blocks.STONE.defaultBlockState()), pose(0))));
        var hit = RaycastUtils.rayCast(scene, new Vec3(10.5, 64.5, 10.5), new Vec3(264, 64.5, 10.5), null);

        assertEquals(PLOT, hit.getBlockPos());
        assertTrue(hit.isInside());
        assertVector(new Vec3(10.5005, 64.5, 10.5), hit.getLocation());
    }

    @Test
    void startingInsideTerrainShapeRetainsItsOriginalHitPosition() {
        BlockPos ground = new BlockPos(8, 64, 10);
        var scene = new MovingClientLevel(blocks(Map.of(ground, Blocks.STONE.defaultBlockState())), List.of(ship(Map.of(PLOT, Blocks.STONE.defaultBlockState()), pose(0))));
        var hit = RaycastUtils.rayCast(scene, new Vec3(8.5, 64.5, 10.5), new Vec3(264, 64.5, 10.5), null);

        assertEquals(ground, hit.getBlockPos());
        assertTrue(hit.isInside());
        assertVector(new Vec3(8.7555, 64.5, 10.5), hit.getLocation());
    }

    @Test
    void startingJustAboveThinShipShapeRetainsItsOriginalHitPosition() {
        var ship = new MovingClientLevel.SubLevel(blocks(Map.of(PLOT, Blocks.WHITE_CARPET.defaultBlockState())),
                new AABB(6, 0, 6, 18, 100, 18), new AABB(15998, 0, 15998, 16003, 100, 16003), pose(0));
        var scene = new MovingClientLevel(blocks(Map.of(new BlockPos(10, 63, 10), Blocks.STONE.defaultBlockState())), List.of(ship));
        var hit = RaycastUtils.rayCast(scene, new Vec3(10.5, 64.06251, 10.5), new Vec3(10.5, -191.93749, 10.5), null);

        assertEquals(PLOT, hit.getBlockPos());
        assertTrue(hit.isInside());
        assertVector(new Vec3(10.5, 64.062254, 10.5), hit.getLocation());
    }

    @Test
    void occlusionContinuesThroughConsecutiveShipBlocks() {
        var scene = new MovingClientLevel(blocks(Map.of()), List.of(ship(Map.of(PLOT, Blocks.STONE.defaultBlockState(), PLOT.east(), Blocks.WHITE_WOOL.defaultBlockState()), pose(0))));
        Vec3 end = new Vec3(14, 64.5, 10.5);
        var first = RaycastUtils.rayCast(scene, new Vec3(8, 64.5, 10.5), end, null);
        var second = RaycastUtils.rayCastFromHit(scene, first.getLocation(), end, first);
        var last = RaycastUtils.rayCastFromHit(scene, second.getLocation(), end, second);

        assertEquals(PLOT, first.getBlockPos());
        assertEquals(PLOT.east(), second.getBlockPos());
        assertEquals(Blocks.WHITE_WOOL.defaultBlockState(), RaycastUtils.getHitLevel(scene, second).getBlockState(second.getBlockPos()));
        assertEquals(HitResult.Type.MISS, last.getType());
    }

    @Test
    void aSoundInsideAShipSkipsOnlyItsSourceBlock() {
        var scene = new MovingClientLevel(blocks(Map.of()), List.of(ship(Map.of(PLOT, Blocks.STONE.defaultBlockState(), PLOT.east(), Blocks.WHITE_WOOL.defaultBlockState()), pose(0))));
        Vec3 source = new Vec3(10.5, 64.5, 10.5);
        var hit = RaycastUtils.rayCast(scene, source, new Vec3(14, 64.5, 10.5), BlockPos.containing(source));
        assertEquals(PLOT.east(), hit.getBlockPos());
        assertVector(source, scene.toWorld(PLOT.getCenter()));
        assertSame(source, scene.toWorld(source));
    }

    @Test
    void movingWaterAndSnapshotTransformsAreRetained() {
        Matrix4d pose = pose(0);
        var scene = new MovingClientLevel(blocks(Map.of()), List.of(ship(Map.of(PLOT, Blocks.WATER.defaultBlockState()), pose)));
        pose.identity();

        assertFalse(scene.getFluidStateAt(new Vec3(10.5, 64.5, 10.5)).isEmpty());
        assertTrue(scene.getFluidStateAt(new Vec3(20, 64.5, 10.5)).isEmpty());
        assertEquals(HitResult.Type.BLOCK, RaycastUtils.rayCast(scene, new Vec3(8, 64.5, 10.5), new Vec3(12, 64.5, 10.5), null).getType());
    }

    @Test
    void emptySceneKeepsVanillaHitsAndMisses() {
        ClientLevelProxy terrain = blocks(Map.of(BlockPos.ZERO, Blocks.STONE.defaultBlockState()));
        var scene = new MovingClientLevel(terrain, List.of());
        Vec3 from = new Vec3(-2, 0.5, 0.5);
        Vec3 to = new Vec3(2, 0.5, 0.5);
        var expected = RaycastUtils.rayCast(terrain, from, to, null);
        var actual = RaycastUtils.rayCast(scene, from, to, null);
        assertEquals(expected.getBlockPos(), actual.getBlockPos());
        assertVector(expected.getLocation(), actual.getLocation());
        assertVector(new Vec3(-1, 0, 0), RaycastUtils.getNormal(actual));
        assertEquals(HitResult.Type.MISS, RaycastUtils.rayCast(scene, from, to, BlockPos.ZERO).getType());
        assertEquals(HitResult.Type.MISS, RaycastUtils.rayCast(null, from, to, null).getType());
    }

    @Test
    void terrainAndShipHitsKeepSeparateMaterialsAtTheSameBlockCoordinates() {
        var terrain = blocks(Map.of(PLOT, Blocks.WHITE_WOOL.defaultBlockState()));
        var scene = new MovingClientLevel(terrain, List.of(ship(Map.of(PLOT, Blocks.STONE.defaultBlockState()), pose(0))));
        var worldHit = RaycastUtils.rayCast(scene, PLOT.getCenter().add(-2, 0, 0), PLOT.getCenter().add(2, 0, 0), null);
        var shipHit = RaycastUtils.rayCast(scene, new Vec3(8, 64.5, 10.5), new Vec3(12, 64.5, 10.5), null);
        assertEquals(Blocks.WHITE_WOOL.defaultBlockState(), RaycastUtils.getHitLevel(scene, worldHit).getBlockState(PLOT));
        assertEquals(Blocks.STONE.defaultBlockState(), RaycastUtils.getHitLevel(scene, shipHit).getBlockState(PLOT));
    }

    @Test
    void distantShipPositionsTransformEvenWithoutCachedBlocks() {
        Matrix4d pose = new Matrix4d().translation(100, 0, 0).mul(pose(0));
        var scene = new MovingClientLevel(blocks(Map.of()), List.of(ship(Map.of(), pose)));
        assertVector(new Vec3(110.5, 64.5, 10.5), scene.toWorld(PLOT.getCenter()));
    }

    @Test
    void aTerrainHitDoesNotSuppressAnOverlappingShipWall() {
        var scene = new MovingClientLevel(blocks(Map.of(new BlockPos(10, 64, 10), Blocks.STONE.defaultBlockState())),
                List.of(ship(Map.of(PLOT, Blocks.WHITE_WOOL.defaultBlockState()), pose(0))));
        Vec3 from = new Vec3(8, 64.5, 10.5);
        Vec3 to = new Vec3(13, 64.5, 10.5);
        var first = RaycastUtils.rayCast(scene, from, to, null);
        var second = RaycastUtils.rayCastFromHit(scene, first.getLocation(), to, first);
        assertEquals(HitResult.Type.BLOCK, second.getType());
        assertNotEquals(first.getBlockPos(), second.getBlockPos());

        Map<BlockGetter, Set<BlockPos>> ignored = new HashMap<>();
        ignored.computeIfAbsent(RaycastUtils.getHitLevel(scene, first), key -> new HashSet<>()).add(first.getBlockPos());
        ignored.computeIfAbsent(RaycastUtils.getHitLevel(scene, second), key -> new HashSet<>()).add(second.getBlockPos());
        assertEquals(HitResult.Type.MISS, RaycastUtils.rayCastIgnoringHits(scene, second.getLocation(), to, ignored).getType());
    }

    @Test
    void shipRaysOnlyReadBlocksInsideTheirLocalBounds() {
        int[] reads = {0};
        var shipBlocks = blocks(Map.of(), reads);
        var ship = new MovingClientLevel.SubLevel(shipBlocks, new AABB(6, 60, 6, 18, 70, 18), new AABB(PLOT).inflate(2), pose(0));
        var scene = new MovingClientLevel(blocks(Map.of()), List.of(ship));
        var hit = RaycastUtils.rayCast(scene, new Vec3(-200, 64.5, 10.5), new Vec3(200, 64.5, 10.5), null);
        assertEquals(HitResult.Type.MISS, hit.getType());
        assertTrue(reads[0] > 0 && reads[0] <= 16, "ship lookups should cover its bounds, not the entire 400-block ray: " + reads[0]);

        reads[0] = 0;
        scene = new MovingClientLevel(blocks(Map.of(new BlockPos(0, 64, 10), Blocks.STONE.defaultBlockState())), List.of(ship));
        RaycastUtils.rayCast(scene, new Vec3(-200, 64.5, 10.5), new Vec3(200, 64.5, 10.5), null);
        assertEquals(0, reads[0], "ships behind the nearest terrain hit need no block traversal");
    }

    @Test
    void roundedRotationAtDistantPlotKeepsSoundPathsAligned() {
        BlockPos plot = new BlockPos(20483077, 47, 20704258);
        var rotation = new Quaterniond().rotationY(Math.PI / 4D).mul(0.99999996D);
        Matrix4d pose = new Matrix4d().translation(-8100, 72, 4350).rotate(rotation).translate(-plot.getX(), -plot.getY(), -plot.getZ());
        var ship = new MovingClientLevel.SubLevel(blocks(Map.of(plot, Blocks.STONE.defaultBlockState())),
                new AABB(-8120, 52, 4330, -8080, 92, 4370), new AABB(plot).inflate(12), pose);
        var scene = new MovingClientLevel(blocks(Map.of()), List.of(ship));
        Vec3 from = MovingClientLevel.transform(pose, plot.getCenter().add(-2, 0, 0));
        Vec3 to = MovingClientLevel.transform(pose, plot.getCenter().add(2, 0, 0));
        var hit = RaycastUtils.rayCast(scene, from, to, null);

        assertEquals(HitResult.Type.BLOCK, hit.getType());
        assertEquals(plot, hit.getBlockPos());
        assertVector(new Vec3(plot.getX(), plot.getY() + 0.5, plot.getZ() + 0.5), RaycastUtils.getLocalPosition(hit));
        Vec3 clearFrom = MovingClientLevel.transform(pose, plot.getCenter().add(-2, 0, 2));
        Vec3 clearTo = MovingClientLevel.transform(pose, plot.getCenter().add(2, 0, 2));
        assertEquals(HitResult.Type.MISS, RaycastUtils.rayCast(scene, clearFrom, clearTo, null).getType());
    }

    private static Matrix4d pose(double angle) {
        return new Matrix4d().translation(10, 64, 10).rotateY(angle).translate(-PLOT.getX(), -PLOT.getY(), -PLOT.getZ());
    }

    private static MovingClientLevel.SubLevel ship(Map<BlockPos, BlockState> states, Matrix4d pose) {
        return new MovingClientLevel.SubLevel(blocks(states), new AABB(6, 60, 6, 18, 70, 18), new AABB(PLOT).inflate(2), pose);
    }

    private static ClientLevelProxy blocks(Map<BlockPos, BlockState> states) {
        return blocks(states, new int[]{0});
    }

    private static ClientLevelProxy blocks(Map<BlockPos, BlockState> states, int[] reads) {
        return new ClientLevelProxy() {
            @Override
            public BlockState getBlockState(BlockPos pos) {
                reads[0]++;
                return states.getOrDefault(pos, Blocks.AIR.defaultBlockState());
            }

            @Override
            public FluidState getFluidState(BlockPos pos) {
                return getBlockState(pos).getFluidState();
            }

            @Override
            public BlockEntity getBlockEntity(BlockPos pos) {
                return null;
            }

            @Override
            public int getHeight() {
                return 384;
            }

            @Override
            public int getMinBuildHeight() {
                return -64;
            }
        };
    }

    private static void assertVector(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, 1E-7);
        assertEquals(expected.y, actual.y, 1E-7);
        assertEquals(expected.z, actual.z, 1E-7);
    }
}
