package com.sonicether.soundphysics;

import com.sonicether.soundphysics.integration.SableIntegration;
import com.sonicether.soundphysics.utils.RaycastUtils;
import com.sonicether.soundphysics.world.ClonedClientLevel;
import com.sonicether.soundphysics.world.MovingClientLevel;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.lang.management.ManagementFactory;

@GameTestHolder(SoundPhysicsMod.MODID)
@PrefixGameTestTemplate(false)
public class SableGameTests {

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void soundRaysUseRotatedSableBlocksAndImmutableSnapshots(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 3, 2));
        level.setBlockAndUpdate(origin, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(origin.east(), Blocks.WHITE_WOOL.defaultBlockState());
        var ship = SubLevelAssemblyHelper.assembleBlocks(level, origin, List.of(origin, origin.east()),
                new BoundingBox3i(origin.getX(), origin.getY(), origin.getZ(), origin.getX() + 1, origin.getY(), origin.getZ()));
        helper.runAfterDelay(3, () -> {
            ship.logicalPose().orientation().rotationY(Math.PI / 4D);
            ship.updateBoundingBox();
            BlockPos plot = ship.getPlot().getCenterBlock();
            Vec3 from = ship.logicalPose().transformPosition(plot.getCenter().add(-2, 0, 0));
            Vec3 to = ship.logicalPose().transformPosition(plot.getCenter().add(3, 0, 0));
            var snapshots = SableIntegration.snapshot(level, from);
            helper.assertTrue(!snapshots.isEmpty(), "Sable companion exposes the assembled ship");
            var terrain = new ClonedClientLevel(level, origin, level.getGameTime(), new HashMap<>());
            var scene = new MovingClientLevel(terrain, snapshots);
            var first = RaycastUtils.rayCast(scene, from, to, null);
            helper.assertTrue(first.getType() == HitResult.Type.BLOCK && first.getBlockPos().equals(plot), "the ray hits the ship's stone block");
            helper.assertTrue(RaycastUtils.getHitLevel(scene, first).getBlockState(plot).is(Blocks.STONE), "the hit retains its ship material");
            helper.assertTrue(RaycastUtils.getNormal(first).distanceTo(new Vec3(-Math.sqrt(0.5), 0, Math.sqrt(0.5))) < 1E-5, "the surface normal follows the ship's rotation: " + RaycastUtils.getNormal(first));
            var second = RaycastUtils.rayCastFromHit(scene, first.getLocation(), to, first);
            helper.assertTrue(second.getBlockPos().equals(plot.east()), "occlusion continues to the adjacent wool block");

            if (Boolean.getBoolean("soundphysics.benchmark")) {
                benchmark("snapshot", () -> SableIntegration.snapshot(level, from));
                Vec3 rayEnd = ship.logicalPose().transformPosition(plot.getCenter().add(256, 0, 0));
                var ignored = Map.of(RaycastUtils.getHitLevel(scene, first), Set.of(plot, plot.east()));
                benchmark("ray", () -> RaycastUtils.rayCastIgnoringHits(scene, from, rayEnd, ignored));
            }

            level.setBlockAndUpdate(plot, Blocks.AIR.defaultBlockState());
            helper.assertTrue(RaycastUtils.getHitLevel(scene, first).getBlockState(plot).is(Blocks.STONE), "changing the live ship does not mutate the sound-thread snapshot");
            var updated = new MovingClientLevel(terrain, SableIntegration.snapshot(level, from));
            helper.assertTrue(RaycastUtils.rayCast(updated, from, to, null).getBlockPos().equals(plot.east()), "a fresh snapshot observes the changed ship");

            Vec3 distantListener = from.add(100, 0, 0);
            var distant = new MovingClientLevel(terrain, SableIntegration.snapshot(level, distantListener));
            helper.assertTrue(distant.toWorld(plot.east().getCenter()).distanceTo(ship.logicalPose().transformPosition(plot.east().getCenter())) < 1E-5, "distant sound positions transform outside the block clone range");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void roundedRotationKeepsSnapshotBoundsAndSoundPathsAligned(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 3, 2));
        level.setBlockAndUpdate(origin, Blocks.STONE.defaultBlockState());
        var ship = SubLevelAssemblyHelper.assembleBlocks(level, origin, List.of(origin),
                new BoundingBox3i(origin.getX(), origin.getY(), origin.getZ(), origin.getX(), origin.getY(), origin.getZ()));
        helper.runAfterDelay(3, () -> {
            ship.logicalPose().orientation().rotationY(Math.PI / 4D).mul(0.99999996D);
            ship.updateBoundingBox();
            BlockPos plot = ship.getPlot().getCenterBlock();
            Vec3 from = ship.logicalPose().transformPosition(plot.getCenter().add(-2, 0, 0));
            Vec3 to = ship.logicalPose().transformPosition(plot.getCenter().add(2, 0, 0));
            var terrain = new ClonedClientLevel(level, origin, level.getGameTime(), new HashMap<>());
            var scene = new MovingClientLevel(terrain, SableIntegration.snapshot(level, from));
            var hit = RaycastUtils.rayCast(scene, from, to, null);

            helper.assertTrue(hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(plot), "rounded rotation still hits the actual ship wall");
            helper.assertTrue(RaycastUtils.getLocalPosition(hit).distanceTo(new Vec3(plot.getX(), plot.getY() + 0.5, plot.getZ() + 0.5)) < 1E-5, "the hit stays on the wall's local face");
            Vec3 clearFrom = ship.logicalPose().transformPosition(plot.getCenter().add(-2, 0, 2));
            Vec3 clearTo = ship.logicalPose().transformPosition(plot.getCenter().add(2, 0, 2));
            helper.assertTrue(RaycastUtils.rayCast(scene, clearFrom, clearTo, null).getType() == HitResult.Type.MISS, "a clear sound path does not hit a shifted wall");
            helper.succeed();
        });
    }

    private static void benchmark(String name, Runnable task) {
        for (int i = 0; i < 200; i++) {
            task.run();
        }
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long thread = Thread.currentThread().threadId();
        long bytes = bean.getThreadAllocatedBytes(thread);
        long start = System.nanoTime();
        for (int i = 0; i < 2000; i++) {
            task.run();
        }
        double micros = (System.nanoTime() - start) / 2_000_000D;
        long allocated = (bean.getThreadAllocatedBytes(thread) - bytes) / 2000;
        Loggers.log("Sable benchmark {}: {} us/op, {} bytes/op", name, micros, allocated);
    }
}
