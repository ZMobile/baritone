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

package baritone.utils;

import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CompletableFuture;

/**
 * High-performance LOCK-FREE chunk cache for maximum throughput during pathfinding.
 * Uses ConcurrentHashMap for thread-safe access without blocking.
 *
 * Designed for blood moon scenarios with many mobs pathfinding simultaneously.
 *
 * @author Enhanced for concurrent mob usage
 */
public class ChunkCache {

    // Maximum number of chunks to cache (increased for blood moon scenarios)
    private static final int MAX_CACHE_SIZE = 512;

    // Cache expiry time (30 seconds - chunks don't change during pathfinding, cleanup handles staleness)
    private static final long CACHE_EXPIRY_MS = 30000;

    // LOCK-FREE cache using ConcurrentHashMap with Long keys
    // The overhead of Long boxing is worth it for lock-free access
    private final ConcurrentHashMap<Long, CacheEntry> cache = new ConcurrentHashMap<>(MAX_CACHE_SIZE);

    // Approximate size tracking
    private final AtomicInteger approximateSize = new AtomicInteger(0);

    // Statistics for monitoring
    private final AtomicLong hits = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);

    // Cleanup management
    private final AtomicBoolean cleanupInProgress = new AtomicBoolean(false);
    private volatile long lastCleanupTime = System.currentTimeMillis();

    // Singleton instance
    private static final ChunkCache INSTANCE = new ChunkCache();

    private ChunkCache() {
        // Private constructor for singleton
    }

    /**
     * Computes chunk key as primitive long.
     */
    private static long chunkKeyLong(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    public static ChunkCache getInstance() {
        return INSTANCE;
    }

    /**
     * Gets a chunk from cache or loads it if not cached.
     * LOCK-FREE - maximum throughput for pathfinding.
     */
    public LevelChunk getChunk(ServerLevel world, int chunkX, int chunkZ) {
        long key = chunkKeyLong(chunkX, chunkZ);

        // Try to get from cache first - LOCK-FREE
        CacheEntry entry = cache.get(key);
        if (entry != null) {
            // Don't check expiry on every access - too expensive
            // Cleanup will handle expired entries
            hits.incrementAndGet();
            return entry.chunk;
        }

        // Cache miss - check if chunk is loaded WITHOUT blocking
        misses.incrementAndGet();

        // Use getChunkNow which doesn't block - returns null if not loaded
        LevelChunk chunk = world.getChunkSource().getChunkNow(chunkX, chunkZ);
        if (chunk == null) {
            return null;
        }

        // Cache it for future use - LOCK-FREE
        CacheEntry oldEntry = cache.put(key, new CacheEntry(chunk));
        if (oldEntry == null) {
            approximateSize.incrementAndGet();
        }

        // Trigger async cleanup if needed
        if (approximateSize.get() > MAX_CACHE_SIZE && !cleanupInProgress.get()) {
            triggerAsyncCleanup();
        }

        return chunk;
    }

    /**
     * Invalidates a specific chunk in the cache.
     * Called when a chunk is modified.
     */
    public void invalidateChunk(int chunkX, int chunkZ) {
        long key = chunkKeyLong(chunkX, chunkZ);
        if (cache.remove(key) != null) {
            approximateSize.decrementAndGet();
        }
    }

    /**
     * Invalidates a chunk containing the given block position.
     */
    public void invalidateBlock(BlockPos pos) {
        invalidateChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }

    /**
     * Checks if a chunk is loaded. LOCK-FREE.
     */
    public boolean isChunkLoaded(ServerLevel world, int chunkX, int chunkZ) {
        long key = chunkKeyLong(chunkX, chunkZ);

        // Check cache first - LOCK-FREE
        if (cache.containsKey(key)) {
            return true;
        }

        // Check if chunk exists without loading
        return world.getChunkSource().hasChunk(chunkX, chunkZ);
    }

    /**
     * Simple cache-only check without world parameter. LOCK-FREE.
     */
    public boolean isChunkLoaded(int chunkX, int chunkZ) {
        long key = chunkKeyLong(chunkX, chunkZ);
        return cache.containsKey(key);
    }

    /**
     * Clears the entire cache.
     */
    public void clear() {
        cache.clear();
        approximateSize.set(0);
        hits.set(0);
        misses.set(0);
    }

    /**
     * Triggers asynchronous cleanup to avoid lag spikes.
     */
    private void triggerAsyncCleanup() {
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
                int removed = cleanupOldEntries();
                lastCleanupTime = System.currentTimeMillis();
                if (removed > 0) {
                    // Optional: log cleanup stats
                    // System.out.println("ChunkCache: Removed " + removed + " expired entries");
                }
            } finally {
                cleanupInProgress.set(false);
            }
        });
    }

    /**
     * Removes expired entries from the cache. LOCK-FREE.
     */
    private int cleanupOldEntries() {
        long now = System.currentTimeMillis();
        long expiry = now - CACHE_EXPIRY_MS;
        int removed = 0;

        // Iterate and remove expired entries - ConcurrentHashMap handles this safely
        var iterator = cache.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue().timestamp < expiry) {
                iterator.remove();
                removed++;
            }
        }
        approximateSize.addAndGet(-removed);

        return removed;
    }

    /**
     * Gets cache statistics for monitoring.
     */
    public String getStatistics() {
        long totalHits = hits.get();
        long totalMisses = misses.get();
        long total = totalHits + totalMisses;
        double hitRate = total == 0 ? 0.0 : (totalHits * 100.0 / total);

        return String.format("ChunkCache [Size: %d, Hits: %d, Misses: %d, Hit Rate: %.2f%%]",
                approximateSize.get(), totalHits, totalMisses, hitRate);
    }

    /**
     * Cache entry holding a chunk and its timestamp.
     */
    private static class CacheEntry {
        final LevelChunk chunk;
        final long timestamp;

        CacheEntry(LevelChunk chunk) {
            this.chunk = chunk;
            this.timestamp = System.currentTimeMillis();
        }
    }
}