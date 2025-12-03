/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.pathing.calc;

import baritone.api.pathing.goals.Goal;
import baritone.api.utils.BetterBlockPos;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A thread-safe cache for sharing intermediate pathfinding calculations
 * between nearby mobs. This caches the computed costs from positions
 * to goals, allowing mobs to benefit from previous calculations without
 * sharing actual path instances (which would break splicing).
 *
 * @author Enhanced for concurrent mob usage
 */
public final class IntermediateNodeCache {

    private static final IntermediateNodeCache INSTANCE = new IntermediateNodeCache();

    // Separate caches for building and non-building mobs
    private final Map<CacheKey, CacheEntry> buildingCache = new ConcurrentHashMap<>();
    private final Map<CacheKey, CacheEntry> nonBuildingCache = new ConcurrentHashMap<>();

    // Statistics for both caches
    private final AtomicInteger buildingHits = new AtomicInteger(0);
    private final AtomicInteger buildingMisses = new AtomicInteger(0);
    private final AtomicInteger nonBuildingHits = new AtomicInteger(0);
    private final AtomicInteger nonBuildingMisses = new AtomicInteger(0);

    // Cache configuration
    private static final int MAX_CACHE_SIZE = 5000;
    private static final long DEFAULT_CACHE_EXPIRY_MS = 10000; // 10 seconds for regular paths
    private static final long SUCCESS_CACHE_EXPIRY_MS = 30000; // 30 seconds for paths that reach the goal
    private static final int REGION_SIZE = 32; // blocks per region

    // Cleanup management
    private final AtomicBoolean cleanupInProgress = new AtomicBoolean(false);
    private volatile long lastCleanupTime = System.currentTimeMillis();

    // Cached timestamp to avoid frequent System.currentTimeMillis() calls
    // Updated every ~50ms by the first thread that notices it's stale
    private final AtomicLong cachedCurrentTime = new AtomicLong(System.currentTimeMillis());
    private static final long TIME_CACHE_REFRESH_MS = 50;

    // Sampling: only cache every Nth non-success node to reduce overhead
    private static final int CACHE_SAMPLE_RATE = 4; // Cache 1 in 4 nodes
    private final ThreadLocal<Integer> sampleCounter = ThreadLocal.withInitial(() -> 0);

    private IntermediateNodeCache() {
        // Private constructor for singleton
    }

    public static IntermediateNodeCache getInstance() {
        return INSTANCE;
    }

    /**
     * Gets a cached current time, refreshing only every TIME_CACHE_REFRESH_MS.
     * This avoids expensive System.currentTimeMillis() calls on every operation.
     */
    private long getCurrentTime() {
        long cached = cachedCurrentTime.get();
        long actual = System.currentTimeMillis();
        if (actual - cached > TIME_CACHE_REFRESH_MS) {
            // Try to update; if another thread beats us, that's fine
            cachedCurrentTime.compareAndSet(cached, actual);
            return actual;
        }
        return cached;
    }

    // Thread-local reusable CacheKey to avoid allocations
    private static final ThreadLocal<CacheKey> REUSABLE_KEY = ThreadLocal.withInitial(() -> new CacheKey(0, 0, null));

    /**
     * Gets cached cost information for a position-goal pair.
     * Optimized to minimize allocations and lock contention.
     */
    public CachedNodeData getCachedData(BetterBlockPos pos, Goal goal, boolean canBuild) {
        return getCachedData(pos.x, pos.y, pos.z, goal, canBuild);
    }

    /**
     * Gets cached cost information using primitive coordinates.
     * Optimized to minimize allocations and lock contention.
     */
    public CachedNodeData getCachedData(int x, int y, int z, Goal goal, boolean canBuild) {
        int regionX = x >> 5; // divide by 32
        int regionZ = z >> 5;

        // Reuse thread-local key to avoid allocation
        CacheKey key = REUSABLE_KEY.get();
        key.update(regionX, regionZ, goal);

        Map<CacheKey, CacheEntry> cache = canBuild ? buildingCache : nonBuildingCache;
























        CacheEntry entry = cache.get(key);
        if (entry == null) {
            return null;
        }

        // Check if cache entry is still valid - use cached time to avoid syscall
        long currentTime = getCurrentTime();
        long expiryTime = entry.isSuccessPath ? SUCCESS_CACHE_EXPIRY_MS : DEFAULT_CACHE_EXPIRY_MS;
        if (currentTime - entry.timestamp > expiryTime) {
            cache.remove(key);
            return null;
        }

        // Look for cached data at this position
        long posKey = BetterBlockPos.longHash(x, y, z);
        CachedNodeData data = entry.getNodeData(posKey);

        if (data != null) {
            // Reset timer when path is reused
            entry.timestamp = currentTime;
        }

        return data;
    }

    /**
     * Caches node data for a position-goal pair.
     */
    public void cacheNodeData(BetterBlockPos pos, Goal goal, double costFromStart, double estimatedCostToGoal, boolean canBuild) {
        cacheNodeData(pos.x, pos.y, pos.z, goal, costFromStart, estimatedCostToGoal, canBuild, false);
    }

    /**
     * Caches node data for a position-goal pair with success flag.
     */
    public void cacheNodeData(BetterBlockPos pos, Goal goal, double costFromStart, double estimatedCostToGoal, boolean canBuild, boolean isSuccessPath) {
        cacheNodeData(pos.x, pos.y, pos.z, goal, costFromStart, estimatedCostToGoal, canBuild, isSuccessPath);
    }

    /**
     * Caches node data using primitive coordinates to avoid object allocation.
     */
    public void cacheNodeData(int x, int y, int z, Goal goal, double costFromStart, double estimatedCostToGoal, boolean canBuild, boolean isSuccessPath) {
        int regionX = x >> 5;
        int regionZ = z >> 5;
        CacheKey key = new CacheKey(regionX, regionZ, goal);

        Map<CacheKey, CacheEntry> cache = canBuild ? buildingCache : nonBuildingCache;
        CacheEntry entry = cache.computeIfAbsent(key, k -> new CacheEntry());

        // Mark as success path if applicable
        if (isSuccessPath) {
            entry.isSuccessPath = true;
        }

        long currentTime = getCurrentTime();

        // Clean up asynchronously if getting too large - don't block the hot path
        // Use approximate size to avoid synchronization
        if (entry.getApproximateSize() > 500 && entry.tryStartCleanup()) {
            final CacheEntry entryToClean = entry;
            CompletableFuture.runAsync(() -> {
                try {
                    entryToClean.cleanupOldData(getCurrentTime());
                } finally {
                    entryToClean.finishCleanup();
                }
            });
        }

        long posKey = BetterBlockPos.longHash(x, y, z);
        entry.putNodeData(posKey, new CachedNodeData(costFromStart, estimatedCostToGoal, currentTime));
        entry.timestamp = currentTime;

        // Global cache size management
        if (cache.size() > MAX_CACHE_SIZE && !cleanupInProgress.get()) {
            triggerAsyncCleanup(cache);
        }
    }

    /**
     * Triggers asynchronous cleanup to avoid lag spikes.
     */
    private void triggerAsyncCleanup(Map<CacheKey, CacheEntry> cache) {
        // Only allow one cleanup at a time
        if (!cleanupInProgress.compareAndSet(false, true)) {
            return;
        }

        // Don't cleanup too frequently (at most once per second)
        long now = System.currentTimeMillis();
        if (now - lastCleanupTime < 1000) {
            cleanupInProgress.set(false);
            return;
        }

        // Run cleanup asynchronously
        CompletableFuture.runAsync(() -> {
            try {
                int removed = cleanupOldEntries(cache);
                lastCleanupTime = System.currentTimeMillis();
                if (removed > 0) {
                    // Optional: log cleanup stats
                    // System.out.println("IntermediateNodeCache: Removed " + removed + " expired entries");
                }
            } finally {
                cleanupInProgress.set(false);
            }
        });
    }

    /**
     * Removes expired entries from the cache.
     * Returns the number of entries removed.
     */
    private int cleanupOldEntries(Map<CacheKey, CacheEntry> cache) {
        long currentTime = System.currentTimeMillis();
        int removed = 0;

        // Remove expired entries in small batches to reduce lock contention
        var iterator = cache.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            long expiryTime = entry.getValue().isSuccessPath ? SUCCESS_CACHE_EXPIRY_MS : DEFAULT_CACHE_EXPIRY_MS;
            if (currentTime - entry.getValue().timestamp > expiryTime) {
                iterator.remove();
                removed++;

                // Yield periodically to avoid hogging CPU
                if (removed % 10 == 0) {
                    Thread.yield();
                }
            }
        }

        return removed;
    }

    /**
     * Clears the entire cache.
     */
    public void clear() {
        buildingCache.clear();
        nonBuildingCache.clear();
        buildingHits.set(0);
        buildingMisses.set(0);
        nonBuildingHits.set(0);
        nonBuildingMisses.set(0);
    }

    /**
     * Gets cache statistics for monitoring.
     */
    public String getStatistics() {
        int buildingTotal = buildingHits.get() + buildingMisses.get();
        double buildingHitRate = buildingTotal == 0 ? 0.0 : (buildingHits.get() * 100.0 / buildingTotal);

        int nonBuildingTotal = nonBuildingHits.get() + nonBuildingMisses.get();
        double nonBuildingHitRate = nonBuildingTotal == 0 ? 0.0 : (nonBuildingHits.get() * 100.0 / nonBuildingTotal);

        return String.format("IntermediateNodeCache - Building[Size: %d, Hits: %d, Misses: %d, Hit Rate: %.2f%%] NonBuilding[Size: %d, Hits: %d, Misses: %d, Hit Rate: %.2f%%]",
                buildingCache.size(), buildingHits.get(), buildingMisses.get(), buildingHitRate,
                nonBuildingCache.size(), nonBuildingHits.get(), nonBuildingMisses.get(), nonBuildingHitRate);
    }

    /**
     * Cache key that combines region and goal.
     * Mutable for reuse via thread-local to avoid allocations.
     */
    private static class CacheKey {
        private int regionX;
        private int regionZ;
        private Goal goal;
        private int hashCode;

        CacheKey(int regionX, int regionZ, Goal goal) {
            update(regionX, regionZ, goal);
        }

        void update(int regionX, int regionZ, Goal goal) {
            this.regionX = regionX;
            this.regionZ = regionZ;
            this.goal = goal;
            this.hashCode = 31 * (31 * regionX + regionZ) + (goal != null ? goal.hashCode() : 0);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof CacheKey)) return false;

            CacheKey other = (CacheKey) obj;
            return regionX == other.regionX &&
                   regionZ == other.regionZ &&
                   (goal == other.goal || (goal != null && goal.equals(other.goal)));
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

    /**
     * Cache entry containing node data for a region.
     * Uses primitive long keys to avoid autoboxing overhead.
     * Uses ReadWriteLock to allow concurrent reads (the common case).
     */
    private static class CacheEntry {
        final Long2ObjectOpenHashMap<CachedNodeData> nodeData = new Long2ObjectOpenHashMap<>(128);
        volatile long timestamp;
        volatile boolean isSuccessPath = false;
        private final AtomicBoolean cleanupInProgress = new AtomicBoolean(false);

        // ReadWriteLock allows multiple concurrent readers
        private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

        // Track approximate size to avoid expensive size() calls
        private final AtomicInteger approximateSize = new AtomicInteger(0);

        CacheEntry() {
            this.timestamp = System.currentTimeMillis();
        }

        /**
         * Try to start cleanup. Returns true if this thread should do cleanup.
         */
        boolean tryStartCleanup() {
            return cleanupInProgress.compareAndSet(false, true);
        }

        /**
         * Mark cleanup as finished.
         */
        void finishCleanup() {
            cleanupInProgress.set(false);
        }

        /**
         * Gets approximate size without synchronization.
         */
        int getApproximateSize() {
            return approximateSize.get();
        }

        /**
         * Thread-safe put that tracks size. Uses write lock.
         */
        void putNodeData(long key, CachedNodeData data) {
            rwLock.writeLock().lock();
            try {
                CachedNodeData old = nodeData.put(key, data);
                if (old == null) {
                    approximateSize.incrementAndGet();
                }
            } finally {
                rwLock.writeLock().unlock();
            }
        }

        /**
         * Thread-safe get. Uses read lock for concurrent access.
         */
        CachedNodeData getNodeData(long key) {
            rwLock.readLock().lock();
            try {
                return nodeData.get(key);
            } finally {
                rwLock.readLock().unlock();
            }
        }

        /**
         * Clean up old data. Uses write lock.
         */
        void cleanupOldData(long currentTime) {
            long cutoff = currentTime - 5000; // 5 seconds

            // Collect keys to remove first, then remove them
            LongArrayList keysToRemove = new LongArrayList();

            rwLock.writeLock().lock();
            try {
                var iterator = nodeData.long2ObjectEntrySet().fastIterator();
                while (iterator.hasNext()) {
                    var entry = iterator.next();
                    if (entry.getValue().timestamp < cutoff) {
                        keysToRemove.add(entry.getLongKey());
                    }
                }

                // Remove collected keys
                for (int i = 0; i < keysToRemove.size(); i++) {
                    nodeData.remove(keysToRemove.getLong(i));
                }
                approximateSize.addAndGet(-keysToRemove.size());
            } finally {
                rwLock.writeLock().unlock();
            }
        }
    }

    /**
     * Cached data for a specific node.
     */
    public static class CachedNodeData {
        public final double costFromStart;
        public final double estimatedCostToGoal;
        public final long timestamp;

        CachedNodeData(double costFromStart, double estimatedCostToGoal, long timestamp) {
            this.costFromStart = costFromStart;
            this.estimatedCostToGoal = estimatedCostToGoal;
            this.timestamp = timestamp;
        }
    }
}