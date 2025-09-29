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

import baritone.api.utils.BetterBlockPos;
import net.minecraft.world.level.ChunkPos;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Caches frequently-used path segments ("highways") within chunks to dramatically
 * reduce pathfinding computation. Uses dual caches for building and non-building mobs.
 *
 * Highways are path segments that have been used multiple times and represent
 * optimal routes around obstacles, through terrain, etc.
 *
 * @author Enhanced for concurrent mob usage
 */
public class HighwayCache {

    private static final HighwayCache INSTANCE = new HighwayCache();

    // Configuration
    private static final int MIN_HIGHWAY_LENGTH = 5; // Minimum blocks for a highway
    private static final int MAX_HIGHWAY_LENGTH = 30; // Maximum blocks per highway segment
    private static final int MIN_USAGE_FOR_HIGHWAY = 3; // Times a segment must be used
    private static final int MAX_HIGHWAYS_PER_CHUNK = 50; // Limit per chunk
    private static final long HIGHWAY_EXPIRY_MS = 600000; // 10 minutes
    private static final double MAX_HIGHWAY_JOIN_DISTANCE = 5.0; // Max distance to join highway

    // Dual caches for building and non-building mobs
    private final Map<ChunkPos, ChunkHighways> buildingHighways = new ConcurrentHashMap<>();
    private final Map<ChunkPos, ChunkHighways> nonBuildingHighways = new ConcurrentHashMap<>();

    // Path segment tracking for highway detection
    private final Map<SegmentKey, AtomicInteger> buildingSegmentUsage = new ConcurrentHashMap<>();
    private final Map<SegmentKey, AtomicInteger> nonBuildingSegmentUsage = new ConcurrentHashMap<>();

    // Statistics
    private final AtomicLong highwayHits = new AtomicLong(0);
    private final AtomicLong highwayMisses = new AtomicLong(0);
    private final AtomicLong segmentsPromoted = new AtomicLong(0);

    private HighwayCache() {
        // Private constructor for singleton
    }

    public static HighwayCache getInstance() {
        return INSTANCE;
    }

    /**
     * Records a path for potential highway extraction.
     * Analyzes the path for frequently-used segments.
     */
    public void recordPath(List<BetterBlockPos> path, boolean canBuild) {
        if (path == null || path.size() < MIN_HIGHWAY_LENGTH) {
            return;
        }

        Map<SegmentKey, AtomicInteger> segmentUsage = canBuild ? buildingSegmentUsage : nonBuildingSegmentUsage;
        Map<ChunkPos, ChunkHighways> highways = canBuild ? buildingHighways : nonBuildingHighways;

        // Extract segments from the path
        for (int i = 0; i < path.size() - MIN_HIGHWAY_LENGTH; i += MIN_HIGHWAY_LENGTH / 2) {
            int endIdx = Math.min(i + MAX_HIGHWAY_LENGTH, path.size());

            // Find good segment endpoints (direction changes, chunk boundaries)
            int segmentEnd = findSegmentEnd(path, i, endIdx);
            if (segmentEnd - i < MIN_HIGHWAY_LENGTH) {
                continue;
            }

            List<BetterBlockPos> segment = path.subList(i, segmentEnd);
            SegmentKey key = new SegmentKey(segment.get(0), segment.get(segment.size() - 1), canBuild);

            // Track usage
            int usage = segmentUsage.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();

            // Promote to highway if used enough
            if (usage == MIN_USAGE_FOR_HIGHWAY) {
                promoteToHighway(segment, canBuild, highways);
            }
        }
    }

    /**
     * Finds good endpoint for a path segment (direction change, height change, chunk boundary).
     */
    private int findSegmentEnd(List<BetterBlockPos> path, int start, int maxEnd) {
        BetterBlockPos startPos = path.get(start);
        int lastGoodEnd = start + MIN_HIGHWAY_LENGTH;

        for (int i = start + MIN_HIGHWAY_LENGTH; i < maxEnd && i < path.size(); i++) {
            BetterBlockPos current = path.get(i);

            // Stop at chunk boundaries
            if ((current.x >> 4) != (startPos.x >> 4) || (current.z >> 4) != (startPos.z >> 4)) {
                return i;
            }

            // Check for significant direction or height change
            if (i > start + 1) {
                BetterBlockPos prev = path.get(i - 1);
                BetterBlockPos prev2 = path.get(i - 2);

                // Direction change detection
                int dx1 = prev.x - prev2.x;
                int dz1 = prev.z - prev2.z;
                int dx2 = current.x - prev.x;
                int dz2 = current.z - prev.z;

                if (dx1 != dx2 || dz1 != dz2) {
                    lastGoodEnd = i;
                }

                // Height change detection
                if (Math.abs(current.y - prev.y) > 1) {
                    return i;
                }
            }
        }

        return Math.min(lastGoodEnd, maxEnd - 1);
    }

    /**
     * Promotes a path segment to a highway.
     */
    private void promoteToHighway(List<BetterBlockPos> segment, boolean canBuild, Map<ChunkPos, ChunkHighways> highways) {
        BetterBlockPos start = segment.get(0);
        ChunkPos chunkPos = new ChunkPos(start.x >> 4, start.z >> 4);

        ChunkHighways chunkHighways = highways.computeIfAbsent(chunkPos, k -> new ChunkHighways());

        // Simplify path to waypoints
        List<BetterBlockPos> waypoints = simplifyPath(segment);
        Highway highway = new Highway(waypoints, canBuild);

        chunkHighways.addHighway(highway);
        segmentsPromoted.incrementAndGet();
    }

    /**
     * Simplifies a path to key waypoints.
     */
    private List<BetterBlockPos> simplifyPath(List<BetterBlockPos> path) {
        if (path.size() <= 3) {
            return new ArrayList<>(path);
        }

        List<BetterBlockPos> waypoints = new ArrayList<>();
        waypoints.add(path.get(0));

        for (int i = 1; i < path.size() - 1; i++) {
            BetterBlockPos prev = path.get(i - 1);
            BetterBlockPos current = path.get(i);
            BetterBlockPos next = path.get(i + 1);

            // Keep points where direction changes
            int dx1 = current.x - prev.x;
            int dy1 = current.y - prev.y;
            int dz1 = current.z - prev.z;
            int dx2 = next.x - current.x;
            int dy2 = next.y - current.y;
            int dz2 = next.z - current.z;

            if (dx1 != dx2 || dy1 != dy2 || dz1 != dz2) {
                waypoints.add(current);
            }
        }

        waypoints.add(path.get(path.size() - 1));
        return waypoints;
    }

    /**
     * Gets nearby highways that could help with pathfinding.
     * Returns highways within the current and adjacent chunks.
     */
    public List<Highway> getNearbyHighways(BetterBlockPos pos, boolean canBuild, double maxDistance) {
        List<Highway> nearbyHighways = new ArrayList<>();
        Map<ChunkPos, ChunkHighways> highways = canBuild ? buildingHighways : nonBuildingHighways;

        // Check current and adjacent chunks
        int chunkX = pos.x >> 4;
        int chunkZ = pos.z >> 4;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                ChunkPos chunkPos = new ChunkPos(chunkX + dx, chunkZ + dz);
                ChunkHighways chunkHighways = highways.get(chunkPos);

                if (chunkHighways != null) {
                    for (Highway highway : chunkHighways.getHighways()) {
                        if (highway.isNear(pos, maxDistance)) {
                            nearbyHighways.add(highway);
                        }
                    }
                }
            }
        }

        return nearbyHighways;
    }

    /**
     * Checks if we can join a highway from current position.
     */
    public Highway findJoinableHighway(BetterBlockPos pos, BetterBlockPos goal, boolean canBuild) {
        List<Highway> nearby = getNearbyHighways(pos, canBuild, MAX_HIGHWAY_JOIN_DISTANCE);

        Highway bestHighway = null;
        double bestScore = Double.MAX_VALUE;

        for (Highway highway : nearby) {
            // Check if highway moves us toward goal
            if (highway.isTowardGoal(pos, goal)) {
                double joinDistance = highway.getJoinDistance(pos);
                double exitDistance = highway.exit.distanceSq(goal);
                double score = joinDistance + exitDistance;

                if (score < bestScore) {
                    bestScore = score;
                    bestHighway = highway;
                }
            }
        }

        if (bestHighway != null) {
            highwayHits.incrementAndGet();
        } else {
            highwayMisses.incrementAndGet();
        }

        return bestHighway;
    }

    /**
     * Invalidates highways in a chunk when blocks change.
     */
    public void invalidateChunk(int chunkX, int chunkZ) {
        ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
        buildingHighways.remove(chunkPos);
        nonBuildingHighways.remove(chunkPos);
    }

    /**
     * Gets statistics for monitoring.
     */
    public String getStatistics() {
        int totalBuildingHighways = buildingHighways.values().stream()
                .mapToInt(ch -> ch.highways.size()).sum();
        int totalNonBuildingHighways = nonBuildingHighways.values().stream()
                .mapToInt(ch -> ch.highways.size()).sum();

        long hits = highwayHits.get();
        long misses = highwayMisses.get();
        double hitRate = (hits + misses) == 0 ? 0 : (100.0 * hits / (hits + misses));

        return String.format("HighwayCache [Building: %d, NonBuilding: %d, Promoted: %d, Hit Rate: %.2f%%]",
                totalBuildingHighways, totalNonBuildingHighways, segmentsPromoted.get(), hitRate);
    }

    /**
     * Container for highways within a chunk.
     */
    private static class ChunkHighways {
        private final List<Highway> highways = new CopyOnWriteArrayList<>();
        private volatile long lastCleanup = System.currentTimeMillis();

        void addHighway(Highway highway) {
            // Clean up old highways periodically
            if (System.currentTimeMillis() - lastCleanup > 60000) {
                cleanup();
            }

            // Limit highways per chunk
            if (highways.size() >= MAX_HIGHWAYS_PER_CHUNK) {
                // Remove least used
                highways.stream()
                        .min(Comparator.comparingInt(h -> h.usageCount.get()))
                        .ifPresent(highways::remove);
            }

            highways.add(highway);
        }

        List<Highway> getHighways() {
            return highways;
        }

        void cleanup() {
            long now = System.currentTimeMillis();
            highways.removeIf(h -> now - h.lastUsed > HIGHWAY_EXPIRY_MS);
            lastCleanup = now;
        }
    }

    /**
     * Represents a highway - a frequently used path segment.
     */
    public static class Highway {
        public final BetterBlockPos entry;
        public final BetterBlockPos exit;
        public final List<BetterBlockPos> waypoints;
        public final double cost;
        public final boolean requiresBuilding;
        public final AtomicInteger usageCount = new AtomicInteger(0);
        public volatile long lastUsed = System.currentTimeMillis();

        Highway(List<BetterBlockPos> waypoints, boolean requiresBuilding) {
            this.waypoints = Collections.unmodifiableList(waypoints);
            this.entry = waypoints.get(0);
            this.exit = waypoints.get(waypoints.size() - 1);
            this.requiresBuilding = requiresBuilding;

            // Calculate total cost
            double totalCost = 0;
            for (int i = 1; i < waypoints.size(); i++) {
                totalCost += waypoints.get(i - 1).distanceSq(waypoints.get(i));
            }
            this.cost = Math.sqrt(totalCost);
        }

        public boolean isNear(BetterBlockPos pos, double maxDistance) {
            double distSq = maxDistance * maxDistance;
            return entry.distanceSq(pos) <= distSq || exit.distanceSq(pos) <= distSq;
        }

        public boolean isTowardGoal(BetterBlockPos current, BetterBlockPos goal) {
            double currentDist = current.distanceSq(goal);
            double exitDist = exit.distanceSq(goal);
            return exitDist < currentDist - 25; // Must move us at least 5 blocks closer
        }

        public double getJoinDistance(BetterBlockPos pos) {
            return Math.sqrt(entry.distanceSq(pos));
        }

        public void recordUsage() {
            usageCount.incrementAndGet();
            lastUsed = System.currentTimeMillis();
        }
    }

    /**
     * Key for tracking path segments.
     */
    private static class SegmentKey {
        final BetterBlockPos start;
        final BetterBlockPos end;
        final boolean canBuild;
        final int hashCode;

        SegmentKey(BetterBlockPos start, BetterBlockPos end, boolean canBuild) {
            this.start = start;
            this.end = end;
            this.canBuild = canBuild;
            this.hashCode = Objects.hash(start, end, canBuild);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof SegmentKey)) return false;
            SegmentKey other = (SegmentKey) obj;
            return start.equals(other.start) && end.equals(other.end) && canBuild == other.canBuild;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }
}