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

import baritone.Baritone;
import baritone.api.utils.IPlayerContext;
import baritone.cache.CachedRegion;
import baritone.cache.WorldData;
import baritone.utils.pathing.BetterWorldBorder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * Wraps get for chuck caching capability
 *
 * @author leijurv
 */
public class BlockStateInterface {

    private final ServerChunkCache provider;
    private final WorldData worldData;
    protected final Level world;
    public final BlockPos.MutableBlockPos isPassableBlockPos;
    public final BlockGetter access;
    public final BetterWorldBorder worldBorder;

    // Multi-chunk cache for better performance when crossing chunk boundaries
    // Uses a simple 2x2 grid cache which covers most pathfinding scenarios
    private static final int CHUNK_CACHE_SIZE = 4;
    private final LevelChunk[] chunkCacheArray = new LevelChunk[CHUNK_CACHE_SIZE];
    private final int[] chunkCacheX = new int[CHUNK_CACHE_SIZE];
    private final int[] chunkCacheZ = new int[CHUNK_CACHE_SIZE];
    private int chunkCacheIndex = 0;

    private LevelChunk prev = null;
    private CachedRegion prevCached = null;
    private static final ChunkCache chunkCache = ChunkCache.getInstance();

    private final boolean useTheRealWorld;

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    public BlockStateInterface(IPlayerContext ctx) {
        this(ctx, false);
    }

    public BlockStateInterface(IPlayerContext ctx, boolean copyLoadedChunks) {
        this.world = ctx.world();
        this.worldBorder = new BetterWorldBorder(world.getWorldBorder());
        this.worldData = (WorldData) ctx.worldData();
        this.provider = (ServerChunkCache) world.getChunkSource();
        this.useTheRealWorld = !Baritone.settings().pathThroughCachedOnly.value;
        this.isPassableBlockPos = new BlockPos.MutableBlockPos();
        this.access = new BlockStateInterfaceAccessWrapper(this);
    }

    public boolean worldContainsLoadedChunk(int blockX, int blockZ) {
        return provider.hasChunk(blockX >> 4, blockZ >> 4);
    }

    public static Block getBlock(IPlayerContext ctx, BlockPos pos) {
        return get(ctx, pos).getBlock();
    }

    public static BlockState get(IPlayerContext ctx, BlockPos pos) {
        return new BlockStateInterface(ctx).get0(pos.getX(), pos.getY(), pos.getZ());
    }

    public BlockState get0(BlockPos pos) {
        return get0(pos.getX(), pos.getY(), pos.getZ());
    }

    public BlockState get0(int x, int y, int z) {
        y -= world.dimensionType().minY();
        if (y < 0 || y >= world.dimensionType().height()) {
            return AIR;
        }

        int chunkX = x >> 4;
        int chunkZ = z >> 4;

        if (useTheRealWorld) {
            // Try single-chunk cache first for ultra-fast access (most common case)
            LevelChunk cached = prev;
            if (cached != null && cached.getPos().x == chunkX && cached.getPos().z == chunkZ) {
                return getFromChunk(cached, x, y, z);
            }

            // Try multi-chunk cache (for when crossing chunk boundaries)
            for (int i = 0; i < CHUNK_CACHE_SIZE; i++) {
                if (chunkCacheArray[i] != null && chunkCacheX[i] == chunkX && chunkCacheZ[i] == chunkZ) {
                    prev = chunkCacheArray[i];  // Promote to primary cache
                    return getFromChunk(chunkCacheArray[i], x, y, z);
                }
            }

            // Use shared chunk cache to avoid synchronization
            // This is NON-BLOCKING - returns null if chunk isn't loaded
            LevelChunk chunk = null;
            if (world instanceof ServerLevel) {
                chunk = chunkCache.getChunk((ServerLevel) world, chunkX, chunkZ);
            } else {
                // Fallback for non-server worlds - use getChunkNow to avoid blocking
                chunk = provider.getChunkNow(chunkX, chunkZ);
            }

            if (chunk != null) {
                // Add to multi-chunk cache (round-robin)
                chunkCacheArray[chunkCacheIndex] = chunk;
                chunkCacheX[chunkCacheIndex] = chunkX;
                chunkCacheZ[chunkCacheIndex] = chunkZ;
                chunkCacheIndex = (chunkCacheIndex + 1) & (CHUNK_CACHE_SIZE - 1);

                prev = chunk;
                return getFromChunk(chunk, x, y, z);
            }
        }
        CachedRegion cached = prevCached;
        if (cached == null || cached.getX() != x >> 9 || cached.getZ() != z >> 9) {
            if (worldData == null) {
                return AIR;
            }
            CachedRegion region = worldData.cache.getRegion(x >> 9, z >> 9);
            if (region == null) {
                return AIR;
            }
            prevCached = region;
            cached = region;
        }
        BlockState type = cached.getBlock(x & 511, y + world.dimensionType().minY(), z & 511);
        if (type == null) {
            return AIR;
        }
        return type;
    }

    public boolean isLoaded(int x, int z) {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;

        // Try single-chunk cache first (fastest path)
        LevelChunk prevChunk = prev;
        if (prevChunk != null && prevChunk.getPos().x == chunkX && prevChunk.getPos().z == chunkZ) {
            return true;
        }

        // Check shared cache without loading
        if (chunkCache.isChunkLoaded(chunkX, chunkZ)) {
            return true;
        }

        // Check with the world using NON-BLOCKING method
        if (world instanceof ServerLevel) {
            // getChunkNow returns null if not loaded, doesn't block
            LevelChunk chunk = ((ServerLevel) world).getChunkSource().getChunkNow(chunkX, chunkZ);
            if (chunk != null) {
                prev = chunk;
                return true;
            }
        } else {
            // For non-server worlds, use getChunkNow
            LevelChunk chunk = provider.getChunkNow(chunkX, chunkZ);
            if (chunk != null) {
                prev = chunk;
                return true;
            }
        }

        // Fall back to cached region
        int regionX = x >> 9;
        int regionZ = z >> 9;
        CachedRegion prevRegion = prevCached;
        if (prevRegion != null && prevRegion.getX() == regionX && prevRegion.getZ() == regionZ) {
            return prevRegion.isCached(x & 511, z & 511);
        }
        if (worldData == null) {
            return false;
        }
        prevRegion = worldData.cache.getRegion(regionX, regionZ);
        if (prevRegion == null) {
            return false;
        }
        prevCached = prevRegion;
        return prevRegion.isCached(x & 511, z & 511);
    }

    public static BlockState getFromChunk(LevelChunk chunk, int x, int y, int z) {
        LevelChunkSection section = chunk.getSections()[y >> 4];
        if (section.hasOnlyAir()) {
            return AIR;
        }
        return section.getBlockState(x & 15, y & 15, z & 15);
    }
}