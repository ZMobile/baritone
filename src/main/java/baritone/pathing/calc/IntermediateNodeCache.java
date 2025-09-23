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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

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

    private IntermediateNodeCache() {
        // Private constructor for singleton
    }

    public static IntermediateNodeCache getInstance() {
        return INSTANCE;
    }

    /**
     * Gets cached cost information for a position-goal pair.
     */
    public CachedNodeData getCachedData(BetterBlockPos pos, Goal goal, boolean canBuild) {
        int regionX = pos.x >> 5; // divide by 32
        int regionZ = pos.z >> 5;
        CacheKey key = new CacheKey(regionX, regionZ, goal);

        Map<CacheKey, CacheEntry> cache = canBuild ? buildingCache : nonBuildingCache;
        AtomicInteger hits = canBuild ? buildingHits : nonBuildingHits;
        AtomicInteger misses = canBuild ? buildingMisses : nonBuildingMisses;

        CacheEntry entry = cache.get(key);
        if (entry == null) {
            misses.incrementAndGet();
            return null;
        }

        // Check if cache entry is still valid
        long expiryTime = entry.isSuccessPath ? SUCCESS_CACHE_EXPIRY_MS : DEFAULT_CACHE_EXPIRY_MS;
        if (System.currentTimeMillis() - entry.timestamp > expiryTime) {
            cache.remove(key);
            misses.incrementAndGet();
            return null;
        }

        // Look for cached data near this position
        long posKey = BetterBlockPos.longHash(pos);
        CachedNodeData data = entry.nodeData.get(posKey);

        if (data != null) {
            hits.incrementAndGet();
            // Reset timer when path is reused
            entry.timestamp = System.currentTimeMillis();
        } else {
            misses.incrementAndGet();
        }

        return data;
    }

    /**
     * Caches node data for a position-goal pair.
     */
    public void cacheNodeData(BetterBlockPos pos, Goal goal, double costFromStart, double estimatedCostToGoal, boolean canBuild) {
        cacheNodeData(pos, goal, costFromStart, estimatedCostToGoal, canBuild, false);
    }

    /**
     * Caches node data for a position-goal pair with success flag.
     */
    public void cacheNodeData(BetterBlockPos pos, Goal goal, double costFromStart, double estimatedCostToGoal, boolean canBuild, boolean isSuccessPath) {
        int regionX = pos.x >> 5;
        int regionZ = pos.z >> 5;
        CacheKey key = new CacheKey(regionX, regionZ, goal);

        Map<CacheKey, CacheEntry> cache = canBuild ? buildingCache : nonBuildingCache;
        CacheEntry entry = cache.computeIfAbsent(key, k -> new CacheEntry());

        // Mark as success path if applicable
        if (isSuccessPath) {
            entry.isSuccessPath = true;
        }

        // Clean up if getting too large
        if (entry.nodeData.size() > 100) {
            entry.cleanupOldData();
        }

        long posKey = BetterBlockPos.longHash(pos);
        entry.nodeData.put(posKey, new CachedNodeData(costFromStart, estimatedCostToGoal, System.currentTimeMillis()));
        entry.timestamp = System.currentTimeMillis();

        // Global cache size management
        if (cache.size() > MAX_CACHE_SIZE) {
            cleanupOldEntries(cache);
        }
    }

    /**
     * Removes expired entries from the cache.
     */
    private void cleanupOldEntries(Map<CacheKey, CacheEntry> cache) {
        long currentTime = System.currentTimeMillis();
        cache.entrySet().removeIf(entry -> {
            long expiryTime = entry.getValue().isSuccessPath ? SUCCESS_CACHE_EXPIRY_MS : DEFAULT_CACHE_EXPIRY_MS;
            return currentTime - entry.getValue().timestamp > expiryTime;
        });
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
     */
    private static class CacheKey {
        private final int regionX;
        private final int regionZ;
        private final Goal goal;
        private final int hashCode;

        CacheKey(int regionX, int regionZ, Goal goal) {
            this.regionX = regionX;
            this.regionZ = regionZ;
            this.goal = goal;
            this.hashCode = 31 * (31 * regionX + regionZ) + goal.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof CacheKey)) return false;

            CacheKey other = (CacheKey) obj;
            return regionX == other.regionX &&
                   regionZ == other.regionZ &&
                   goal.equals(other.goal);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

    /**
     * Cache entry containing node data for a region.
     */
    private static class CacheEntry {
        final Map<Long, CachedNodeData> nodeData = new ConcurrentHashMap<>();
        volatile long timestamp;
        volatile boolean isSuccessPath = false;

        CacheEntry() {
            this.timestamp = System.currentTimeMillis();
        }

        void cleanupOldData() {
            long cutoff = System.currentTimeMillis() - 5000; // 5 seconds
            nodeData.entrySet().removeIf(e -> e.getValue().timestamp < cutoff);
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