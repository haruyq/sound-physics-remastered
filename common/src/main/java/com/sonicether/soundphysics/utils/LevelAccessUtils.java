package com.sonicether.soundphysics.utils;

import com.sonicether.soundphysics.Loggers;
import com.sonicether.soundphysics.SoundPhysicsMod;
import com.sonicether.soundphysics.profiling.TaskProfiler;
import com.sonicether.soundphysics.world.CachingClientLevel;
import com.sonicether.soundphysics.world.ClientLevelProxy;
import com.sonicether.soundphysics.world.ClonedClientLevel;
import com.sonicether.soundphysics.world.MovingClientLevel;
import com.sonicether.soundphysics.world.UnsafeClientLevel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Function;

/**
 * Utility module to manage creation, invalidation, and updating of client level clones.
 * <p>
 * Level clones are created on a client tick basis and retained for some time.
 * Any module on any thread may access the cached level clone for read-only world operations.
 *
 * @author Saint (@augustsaintfreytag)
 */
public class LevelAccessUtils {

    private static final TaskProfiler PROFILER = new TaskProfiler("Level Caching");
    private static final Minecraft MC = Minecraft.getInstance();
    private static volatile Function<ClientLevel, List<MovingClientLevel.SubLevel>> subLevelProvider;
    private static volatile MovingLevelCache movingLevelCache;

    public static void setSubLevelProvider(Function<ClientLevel, List<MovingClientLevel.SubLevel>> provider) {
        subLevelProvider = provider;
    }

    public static boolean hasMovingLevelSupport() {
        return subLevelProvider != null;
    }

    // Cache Write

    public static void onLoadLevel(ClientLevel clientLevel) {
        updateMovingLevelCache(clientLevel);
        Loggers.logDebug("Creating initial level cache");
        updateLevelCache(clientLevel, levelOriginFromPlayer(), clientLevel.getGameTime());

        SoundRateManager.onLoadLevel(clientLevel);
    }

    public static void onUnloadLevel(ClientLevel clientLevel) {
        movingLevelCache = null;
        Loggers.logDebug("Removing level cache due to level unload");
        ((CachingClientLevel) clientLevel).sound_physics_remastered$setCachedClone(null);

        SoundRateManager.onUnloadLevel(clientLevel);
    }

    public static void tickLevelCache(ClientLevel clientLevel) {
        if (!SoundPhysicsMod.CONFIG.enabled.get()) {
            movingLevelCache = null;
            return;
        }
        updateMovingLevelCache(clientLevel);
        if (SoundPhysicsMod.CONFIG.unsafeLevelAccess.get()) {
            // Disable all level cloning, use direct unsafe main thread access (original behavior).
            return;
        }

        var currentTick = clientLevel.getGameTime();
        var origin = levelOriginFromPlayer();

        // Cast client level reference to interface to access injected level cache property.
        var cachingClientLevel = (CachingClientLevel) clientLevel;
        var clientLevelClone = cachingClientLevel.sound_physics_remastered$getCachedClone();

        if (clientLevelClone == null) {
            // No cache exists, cache first level clone.

            Loggers.logDebug("Creating new level cache, no existing level clone found in client cache.");
            updateLevelCache(clientLevel, origin, SoundPhysicsMod.CONFIG.levelCloneMaxRetainTicks.get());
            return;
        }

        var ticksSinceLastClone = currentTick - clientLevelClone.getTick();
        var distanceSinceLastClone = origin.distSqr(clientLevelClone.getOrigin());

        if (ticksSinceLastClone >= SoundPhysicsMod.CONFIG.levelCloneMaxRetainTicks.get() || distanceSinceLastClone >= SoundPhysicsMod.CONFIG.levelCloneMaxRetainBlockDistance.get()) {
            // Cache expired or player travelled too far from last clone origin point, update cache.

            Loggers.logDebug(
                    "Updating level cache, cache expired ({}/{} ticks) or player moved too far ({}/{} block(s)) from last clone origin.",
                    ticksSinceLastClone, SoundPhysicsMod.CONFIG.levelCloneMaxRetainTicks.get(), distanceSinceLastClone, SoundPhysicsMod.CONFIG.levelCloneMaxRetainBlockDistance.get()
            );

            updateLevelCache(clientLevel, origin, currentTick);
        }
    }

    private static void updateLevelCache(ClientLevel clientLevel, BlockPos origin, long tick) {
        Loggers.logDebug("Updating level cache, creating new level clone with origin {} on tick {}.", origin.toShortString(), tick);

        var profile = PROFILER.profile();
        var cachingClientLevel = (CachingClientLevel) clientLevel;
        var clientLevelClone = new ClonedClientLevel(clientLevel, origin, tick, SoundPhysicsMod.CONFIG.levelCloneRange.get());

        cachingClientLevel.sound_physics_remastered$setCachedClone(clientLevelClone);

        profile.finish();

        Loggers.logProfiling("Updated client level clone in cache in {} ms", profile.getDuration());
        PROFILER.onTally(PROFILER::logResults);
    }

    // Cache Read

    private static void updateMovingLevelCache(ClientLevel level) {
        if (subLevelProvider != null) {
            movingLevelCache = new MovingLevelCache(level, subLevelProvider.apply(level));
        }
    }

    private static ClientLevelProxy withSubLevels(ClientLevel level, ClientLevelProxy proxy) {
        var cache = movingLevelCache;
        if (cache == null || cache.level != level || cache.subLevels.isEmpty()) {
            return proxy;
        }
        return new MovingClientLevel(proxy, cache.subLevels);
    }

    private record MovingLevelCache(ClientLevel level, List<MovingClientLevel.SubLevel> subLevels) {
    }

    @Nullable
    public static ClientLevelProxy getClientLevelProxy(Minecraft client) {
        var clientLevel = client.level;

        if (clientLevel == null) {
            Loggers.warn("Can not return client level proxy, client level does not exist.");
            return null;
        }

        if (SoundPhysicsMod.CONFIG.unsafeLevelAccess.get()) {
            return withSubLevels(clientLevel, new UnsafeClientLevel(clientLevel));
        }

        var cachingClientLevel = (CachingClientLevel) clientLevel;
        var clientLevelClone = cachingClientLevel.sound_physics_remastered$getCachedClone();

        if (clientLevelClone == null) {
            Loggers.warn("Can not return client level proxy, client level clone has not been cached. This might only occur once on load.");
            return null;
        }

        return withSubLevels(clientLevel, clientLevelClone);
    }

    // Utilities

    private static BlockPos levelOriginFromPlayer() {
        var playerPos = MC.player.position();
        return BlockPos.containing(playerPos);
    }
}
