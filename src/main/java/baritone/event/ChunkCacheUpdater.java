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

package baritone.event;

import baritone.api.event.events.*;
import baritone.api.event.listener.IGameEventListener;
import baritone.utils.ChunkCache;
import baritone.pathing.calc.HighwayCache;
import net.minecraft.core.BlockPos;
import baritone.api.utils.Pair;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Listens for chunk and block updates to invalidate the chunk cache.
 * This ensures the cache stays synchronized with world changes.
 *
 * @author Enhanced for concurrent mob usage
 */
public class ChunkCacheUpdater implements IGameEventListener {

    private static final ChunkCacheUpdater INSTANCE = new ChunkCacheUpdater();
    private static final ChunkCache cache = ChunkCache.getInstance();

    private ChunkCacheUpdater() {
        // Private constructor for singleton
    }

    public static ChunkCacheUpdater getInstance() {
        return INSTANCE;
    }

    @Override
    public void onChunkEvent(ChunkEvent event) {
        if (event.getType() == ChunkEvent.Type.UNLOAD ||
            event.getType() == ChunkEvent.Type.POPULATE_FULL ||
            event.getType() == ChunkEvent.Type.POPULATE_PARTIAL) {

            // Invalidate the chunk in cache
            cache.invalidateChunk(event.getX(), event.getZ());
            // Also invalidate highways in this chunk
            HighwayCache.getInstance().invalidateChunk(event.getX(), event.getZ());
        }
    }

    @Override
    public void onBlockChange(BlockChangeEvent event) {
        // When blocks change, invalidate the containing chunk
        // BlockChangeEvent provides a list of changed blocks
        for (Pair<BlockPos, BlockState> change : event.getBlocks()) {
            cache.invalidateBlock(change.first());
            // Also invalidate highways in the affected chunk
            BlockPos pos = change.first();
            HighwayCache.getInstance().invalidateChunk(pos.getX() >> 4, pos.getZ() >> 4);
        }
    }

    // Implement all other required methods with empty bodies
    @Override public void onTick(TickEvent event) {}
    @Override public void onPostTick(TickEvent event) {}
    @Override public void onPlayerUpdate(PlayerUpdateEvent event) {}
    @Override public void onSendChatMessage(ChatEvent event) {}
    @Override public void onPreTabComplete(TabCompleteEvent event) {}
    @Override public void onRenderPass(RenderEvent event) {}
    @Override public void onWorldEvent(WorldEvent event) {}
    @Override public void onSendPacket(PacketEvent event) {}
    @Override public void onReceivePacket(PacketEvent event) {}
    @Override public void onPlayerRotationMove(RotationMoveEvent event) {}
    @Override public void onPlayerSprintState(SprintStateEvent event) {}
    @Override public void onBlockInteract(BlockInteractEvent event) {}
    @Override public void onPathEvent(PathEvent event) {}
    @Override public void onPlayerDeath() {}

    /**
     * Call this to register the updater with a Baritone instance.
     */
    public static void register(baritone.api.IBaritone baritone) {
        if (baritone != null && baritone.getGameEventHandler() != null) {
            baritone.getGameEventHandler().registerEventListener(INSTANCE);
        }
    }
}