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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Coordinates pathfinding for multiple entities to reduce redundant calculations
 * when many mobs are pathfinding to similar locations.
 *
 * This is especially useful during bloodmoon events where hundreds of mobs
 * might be pathfinding to the same players.
 *
 * @author Enhanced for concurrent mob usage
 */
public final class BatchPathingCoordinator {

    private static final BatchPathingCoordinator INSTANCE = new BatchPathingCoordinator();

    // Groups of pathfinding requests targeting similar goals
    private final Map<GoalGroup, Set<PathingRequest>> requestGroups = new ConcurrentHashMap<>();

    // Executor for batch processing
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2,
        r -> {
            Thread t = new Thread(r, "Baritone-BatchPathing");
            t.setDaemon(true);
            return t;
        });

    // Configuration
    private static final int BATCH_DELAY_MS = 50; // Wait up to 50ms to collect requests
    private static final int MAX_BATCH_SIZE = 20; // Process at most 20 requests at once
    private static final double GOAL_SIMILARITY_THRESHOLD = 100; // blocks

    // Statistics
    private final AtomicInteger totalRequests = new AtomicInteger(0);
    private final AtomicInteger batchedRequests = new AtomicInteger(0);

    private BatchPathingCoordinator() {
        // Start the batch processor
        executor.scheduleWithFixedDelay(this::processBatches, 0, BATCH_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    public static BatchPathingCoordinator getInstance() {
        return INSTANCE;
    }

    /**
     * Submits a pathfinding request to be batched with similar requests.
     * Returns a future that will be completed when the path is calculated.
     */
    public CompletableFuture<PathingResult> submitRequest(BetterBlockPos start, Goal goal, Object requester) {
        totalRequests.incrementAndGet();

        PathingRequest request = new PathingRequest(start, goal, requester);
        GoalGroup group = findOrCreateGroup(goal);

        synchronized (requestGroups) {
            requestGroups.computeIfAbsent(group, k -> ConcurrentHashMap.newKeySet()).add(request);
        }

        return request.future;
    }

    /**
     * Processes batched pathfinding requests.
     */
    private void processBatches() {
        Map<GoalGroup, Set<PathingRequest>> toProcess;

        synchronized (requestGroups) {
            if (requestGroups.isEmpty()) {
                return;
            }
            toProcess = new HashMap<>(requestGroups);
            requestGroups.clear();
        }

        for (Map.Entry<GoalGroup, Set<PathingRequest>> entry : toProcess.entrySet()) {
            Set<PathingRequest> requests = entry.getValue();

            if (requests.size() >= 2) {
                // Multiple mobs going to similar location - batch process
                batchedRequests.addAndGet(requests.size());
                processBatchedRequests(requests);
            } else {
                // Single request - process normally
                for (PathingRequest request : requests) {
                    processIndividualRequest(request);
                }
            }
        }
    }

    /**
     * Processes a batch of similar requests together.
     */
    private void processBatchedRequests(Set<PathingRequest> requests) {
        // Find the centroid of all start positions
        double avgX = 0, avgY = 0, avgZ = 0;
        for (PathingRequest req : requests) {
            avgX += req.start.x;
            avgY += req.start.y;
            avgZ += req.start.z;
        }
        avgX /= requests.size();
        avgY /= requests.size();
        avgZ /= requests.size();

        // Pick the request closest to centroid as representative
        PathingRequest representative = null;
        double minDist = Double.MAX_VALUE;
        for (PathingRequest req : requests) {
            double dist = Math.abs(req.start.x - avgX) + Math.abs(req.start.y - avgY) + Math.abs(req.start.z - avgZ);
            if (dist < minDist) {
                minDist = dist;
                representative = req;
            }
        }

        // Check cache first
        ConcurrentPathCache cache = ConcurrentPathCache.getInstance();
        // TODO: BatchPathingCoordinator needs to be updated to track canBuild status
        // For now, default to false (non-building paths)
        var cachedPath = cache.getCachedPath(representative.start, representative.goal, false);

        if (cachedPath != null) {
            // Use cached path for all requests
            PathingResult result = new PathingResult(true, "Cached path");
            for (PathingRequest req : requests) {
                req.future.complete(result);
            }
        } else {
            // Calculate path for representative and share with others
            processIndividualRequest(representative);

            // Share result with other requests
            final PathingRequest finalRepresentative = representative;
            representative.future.thenAccept(result -> {
                for (PathingRequest req : requests) {
                    if (req != finalRepresentative) {
                        req.future.complete(result);
                    }
                }
            });
        }
    }

    /**
     * Processes a single pathfinding request.
     */
    private void processIndividualRequest(PathingRequest request) {
        // This would normally trigger the actual pathfinding
        // For now, we'll just complete with a placeholder result
        request.future.complete(new PathingResult(true, "Individual path"));
    }

    /**
     * Finds or creates a goal group for batching similar goals.
     */
    private GoalGroup findOrCreateGroup(Goal goal) {
        // For simplicity, we'll create a new group for each goal
        // In a real implementation, this would group similar goals together
        return new GoalGroup(goal);
    }

    /**
     * Gets statistics about batch processing.
     */
    public String getStatistics() {
        int total = totalRequests.get();
        int batched = batchedRequests.get();
        double batchRate = total == 0 ? 0.0 : (batched * 100.0 / total);
        return String.format("BatchPathingCoordinator - Total: %d, Batched: %d, Batch Rate: %.2f%%",
                total, batched, batchRate);
    }

    /**
     * Represents a group of similar goals.
     */
    private static class GoalGroup {
        private final Goal goal;
        private final int hashCode;

        GoalGroup(Goal goal) {
            this.goal = goal;
            this.hashCode = goal.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof GoalGroup)) return false;
            GoalGroup other = (GoalGroup) obj;
            // For now, exact goal matching
            // Could be enhanced to group similar goals
            return goal.equals(other.goal);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

    /**
     * Represents a pathfinding request.
     */
    private static class PathingRequest {
        final BetterBlockPos start;
        final Goal goal;
        final Object requester;
        final CompletableFuture<PathingResult> future;

        PathingRequest(BetterBlockPos start, Goal goal, Object requester) {
            this.start = start;
            this.goal = goal;
            this.requester = requester;
            this.future = new CompletableFuture<>();
        }
    }

    /**
     * Result of a pathfinding operation.
     */
    public static class PathingResult {
        public final boolean success;
        public final String message;

        PathingResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }
}