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
import baritone.api.pathing.movement.ActionCosts;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Object pool for PathNode instances to reduce memory allocation overhead
 * when many mobs are pathfinding concurrently.
 *
 * This is especially important for server-side usage where hundreds of mobs
 * might be pathfinding simultaneously.
 *
 * @author Enhanced for concurrent mob usage
 */
public final class PathNodePool {

    private static final PathNodePool INSTANCE = new PathNodePool();

    // Pool of available nodes
    private final ConcurrentLinkedQueue<PathNode> pool = new ConcurrentLinkedQueue<>();

    // Track pool size approximately - avoids O(n) ConcurrentLinkedQueue.size() calls
    private final AtomicInteger poolSize = new AtomicInteger(0);

    // Statistics for monitoring
    private final AtomicInteger totalCreated = new AtomicInteger(0);
    private final AtomicInteger poolHits = new AtomicInteger(0);
    private final AtomicInteger poolMisses = new AtomicInteger(0);

    // Maximum pool size to prevent unbounded growth
    private static final int MAX_POOL_SIZE = 50000;

    private PathNodePool() {
        // Private constructor for singleton
    }

    public static PathNodePool getInstance() {
        return INSTANCE;
    }

    /**
     * Obtains a PathNode from the pool or creates a new one if the pool is empty.
     * The node is reset with the provided parameters.
     */
    public PathNode obtain(int x, int y, int z, Goal goal) {
        PathNode node = pool.poll();

        if (node != null) {
            poolSize.decrementAndGet();
            poolHits.incrementAndGet();
            // Reset the node with new values
            node.reset(x, y, z, goal);
        } else {
            poolMisses.incrementAndGet();
            totalCreated.incrementAndGet();
            // Create new node with pooled constructor
            node = new PathNode(x, y, z, goal, true);
        }

        return node;
    }

    /**
     * Returns a PathNode to the pool for reuse.
     * The node should not be used after being returned to the pool.
     */
    public void recycle(PathNode node) {
        if (node == null || !node.isPooled) {
            return;
        }

        // Only add to pool if we haven't exceeded the maximum size
        // Use tracked size instead of O(n) pool.size() call
        if (poolSize.get() < MAX_POOL_SIZE) {
            // Clear references to help GC
            node.previous = null;
            node.cost = ActionCosts.COST_INF;
            node.heapPosition = -1;

            pool.offer(node);
            poolSize.incrementAndGet();
        }
    }

    /**
     * Recycles all nodes in the provided map.
     * This should be called when a pathfinding operation completes.
     */
    public void recycleAll(Iterable<PathNode> nodes) {
        if (nodes == null) {
            return;
        }

        for (PathNode node : nodes) {
            recycle(node);
        }
    }

    /**
     * Clears the pool. This should be called periodically or when memory is low.
     */
    public void clear() {
        pool.clear();
        poolSize.set(0);
    }

    /**
     * Gets statistics about pool usage for debugging/monitoring.
     */
    public String getStatistics() {
        return String.format("PathNodePool - Created: %d, Hits: %d, Misses: %d, Current Size: %d, Hit Rate: %.2f%%",
                totalCreated.get(),
                poolHits.get(),
                poolMisses.get(),
                poolSize.get(),
                getHitRate());
    }

    private double getHitRate() {
        int hits = poolHits.get();
        int total = hits + poolMisses.get();
        return total == 0 ? 0.0 : (hits * 100.0 / total);
    }
}