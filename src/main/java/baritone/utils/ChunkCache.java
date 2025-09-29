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

import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CompletableFuture;

/**
 * High-performance chunk cache to avoid synchronization bottlenecks when multiple
 * mobs are pathfinding simultaneously. This cache dramatically reduces calls to
 * ServerChunkManager.getChunk() which was causing thread contention.
 *
 * This is a singleton shared across all Baritone instances to maximize cache hits.
 *
 * @author Enhanced for concurrent mob usage
 */
public class ChunkCache {

    // Maximum number of chunks to cache (16x16 area = 256 chunks)
    private static final int MAX_CACHE_SIZE = 256;

    // Cache expiry time (5 seconds - chunks shouldn't change that often)
    private static final long CACHE_EXPIRY_MS = 5000;

    // The actual cache storage
    private final Map<Long, CacheEntry> cache = new ConcurrentHashMap<>();

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

    public static ChunkCache getInstance() {
        return INSTANCE;
    }

    /**
     * Gets a chunk from cache or loads it if not cached.
     * This method is thread-safe and lock-free for cache hits.
     */
    public LevelChunk getChunk(ServerLevel world, int chunkX, int chunkZ) {
        long key = ChunkPos.asLong(chunkX, chunkZ);

        // Try to get from cache first
        CacheEntry entry = cache.get(key);
        if (entry != null && !entry.isExpired()) {
            hits.incrementAndGet();
            return entry.chunk;
        }

        // Cache miss - need to load chunk
        misses.incrementAndGet();

        // Load the chunk (this is where the synchronization happens)
        ChunkAccess chunkAccess = world.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
        if (!(chunkAccess instanceof LevelChunk)) {
            return null;
        }

        LevelChunk chunk = (LevelChunk) chunkAccess;

        // Cache it for future use
        cache.put(key, new CacheEntry(chunk));

        // Trigger async cleanup if needed
        if (cache.size() > MAX_CACHE_SIZE && !cleanupInProgress.get()) {
            triggerAsyncCleanup();
        }

        return chunk;
    }

    /**
     * Invalidates a specific chunk in the cache.
     * Called when a chunk is modified.
     */
    public void invalidateChunk(int chunkX, int chunkZ) {
        long key = ChunkPos.asLong(chunkX, chunkZ);
        cache.remove(key);
    }

    /**
     * Invalidates a chunk containing the given block position.
     */
    public void invalidateBlock(BlockPos pos) {
        invalidateChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }

    /**
     * Checks if a chunk is loaded without actually loading it.
     * This is much faster than getChunk() for existence checks.
     */
    public boolean isChunkLoaded(ServerLevel world, int chunkX, int chunkZ) {
        long key = ChunkPos.asLong(chunkX, chunkZ);

        // Check cache first
        CacheEntry entry = cache.get(key);
        if (entry != null && !entry.isExpired()) {
            hits.incrementAndGet();
            return true;
        }

        // Check if chunk exists without loading
        misses.incrementAndGet();
        return world.getChunkSource().hasChunk(chunkX, chunkZ);
    }

    /**
     * Simple cache-only check without world parameter.
     * Returns true only if chunk is in cache.
     */
    public boolean isChunkLoaded(int chunkX, int chunkZ) {
        long key = ChunkPos.asLong(chunkX, chunkZ);
        CacheEntry entry = cache.get(key);
        return entry != null && !entry.isExpired();
    }

    /**
     * Clears the entire cache.
     */
    public void clear() {
        cache.clear();
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
     * Removes expired entries from the cache.
     * Returns the number of entries removed.
     */
    private int cleanupOldEntries() {
        long now = System.currentTimeMillis();
        long expiry = now - CACHE_EXPIRY_MS;
        int removed = 0;

        // Remove expired entries in small batches to reduce lock contention
        var iterator = cache.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue().timestamp < expiry) {
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
     * Gets cache statistics for monitoring.
     */
    public String getStatistics() {
        long totalHits = hits.get();
        long totalMisses = misses.get();
        long total = totalHits + totalMisses;
        double hitRate = total == 0 ? 0.0 : (totalHits * 100.0 / total);

        return String.format("ChunkCache [Size: %d, Hits: %d, Misses: %d, Hit Rate: %.2f%%]",
                cache.size(), totalHits, totalMisses, hitRate);
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

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_EXPIRY_MS;
        }
    }
}