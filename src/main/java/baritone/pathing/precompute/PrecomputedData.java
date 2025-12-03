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

package baritone.pathing.precompute;

import baritone.pathing.movement.MovementHelper;
import baritone.utils.BlockStateInterface;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import static baritone.pathing.precompute.Ternary.MAYBE;
import static baritone.pathing.precompute.Ternary.YES;

public class PrecomputedData {

    private final int[] data = new int[Block.BLOCK_STATE_REGISTRY.size()];

    private static final int COMPLETED_MASK = 1 << 0;
    private static final int CAN_WALK_ON_MASK = 1 << 1;
    private static final int CAN_WALK_ON_SPECIAL_MASK = 1 << 2;
    private static final int CAN_WALK_THROUGH_MASK = 1 << 3;
    private static final int CAN_WALK_THROUGH_SPECIAL_MASK = 1 << 4;
    private static final int FULLY_PASSABLE_MASK = 1 << 5;
    private static final int FULLY_PASSABLE_SPECIAL_MASK = 1 << 6;
    private static final int CAN_PLACE_AGAINST_MASK = 1 << 7;
    private static final int HAS_FLUID_MASK = 1 << 8;  // Cache FluidState.isEmpty() result

    private int fillData(int id, BlockState state) {
        int blockData = 0;

        Ternary canWalkOnState = MovementHelper.canWalkOnBlockState(state);
        if (canWalkOnState == YES) {
            blockData |= CAN_WALK_ON_MASK;
        }
        if (canWalkOnState == MAYBE) {
            blockData |= CAN_WALK_ON_SPECIAL_MASK;
        }

        Ternary canWalkThroughState = MovementHelper.canWalkThroughBlockState(state);
        if (canWalkThroughState == YES) {
            blockData |= CAN_WALK_THROUGH_MASK;
        }
        if (canWalkThroughState == MAYBE) {
            blockData |= CAN_WALK_THROUGH_SPECIAL_MASK;
        }

        Ternary fullyPassableState = MovementHelper.fullyPassableBlockState(state);
        if (fullyPassableState == YES) {
            blockData |= FULLY_PASSABLE_MASK;
        }
        if (fullyPassableState == MAYBE) {
            blockData |= FULLY_PASSABLE_SPECIAL_MASK;
        }

        // Precompute canPlaceAgainst - this is expensive due to isBlockNormalCube calling Block.isShapeFullBlock
        if (MovementHelper.canPlaceAgainstBlockState(state)) {
            blockData |= CAN_PLACE_AGAINST_MASK;
        }

        // Cache FluidState.isEmpty() - this was taking 13.8% of CPU time
        if (!state.getFluidState().isEmpty()) {
            blockData |= HAS_FLUID_MASK;
        }

        blockData |= COMPLETED_MASK;

        data[id] = blockData; // in theory, this is thread "safe" because every thread should compute the exact same int to write?
        return blockData;
    }

    public boolean canWalkOn(BlockStateInterface bsi, int x, int y, int z, BlockState state) {
        int id = Block.BLOCK_STATE_REGISTRY.getId(state);
        int blockData = data[id];

        if ((blockData & COMPLETED_MASK) == 0) { // we need to fill in the data
            blockData = fillData(id, state);
        }

        if ((blockData & CAN_WALK_ON_SPECIAL_MASK) != 0) {
            return MovementHelper.canWalkOnPosition(bsi, x, y, z, state);
        } else {
            return (blockData & CAN_WALK_ON_MASK) != 0;
        }
    }

    public boolean canWalkThrough(BlockStateInterface bsi, int x, int y, int z, BlockState state) {
        int id = Block.BLOCK_STATE_REGISTRY.getId(state);
        int blockData = data[id];

        if ((blockData & COMPLETED_MASK) == 0) { // we need to fill in the data
            blockData = fillData(id, state);
        }

        if ((blockData & CAN_WALK_THROUGH_SPECIAL_MASK) != 0) {
            return MovementHelper.canWalkThroughPosition(bsi, x, y, z, state);
        } else {
            return (blockData & CAN_WALK_THROUGH_MASK) != 0;
        }
    }

    public boolean fullyPassable(BlockStateInterface bsi, int x, int y, int z, BlockState state) {
        int id = Block.BLOCK_STATE_REGISTRY.getId(state);
        int blockData = data[id];

        if ((blockData & COMPLETED_MASK) == 0) { // we need to fill in the data
            blockData = fillData(id, state);
        }

        if ((blockData & FULLY_PASSABLE_SPECIAL_MASK) != 0) {
            return MovementHelper.fullyPassablePosition(bsi, x, y, z, state);
        } else {
            return (blockData & FULLY_PASSABLE_MASK) != 0;
        }
    }

    /**
     * Cached version of canPlaceAgainst that avoids repeated isBlockNormalCube calls.
     * This is a major optimization as isBlockNormalCube calls Block.isShapeFullBlock which
     * was taking ~8.6% of CPU time in profiling.
     */
    public boolean canPlaceAgainst(BlockState state) {
        int id = Block.BLOCK_STATE_REGISTRY.getId(state);
        int blockData = data[id];

        if ((blockData & COMPLETED_MASK) == 0) { // we need to fill in the data
            blockData = fillData(id, state);
        }

        return (blockData & CAN_PLACE_AGAINST_MASK) != 0;
    }

    /**
     * Cached version of !FluidState.isEmpty().
     * This was taking 13.8% of CPU time in profiling.
     */
    public boolean hasFluid(BlockState state) {
        int id = Block.BLOCK_STATE_REGISTRY.getId(state);
        int blockData = data[id];

        if ((blockData & COMPLETED_MASK) == 0) {
            blockData = fillData(id, state);
        }

        return (blockData & HAS_FLUID_MASK) != 0;
    }

    /**
     * Get raw precomputed data for a block state.
     * Returns the bitmask directly for callers that need multiple checks.
     * This avoids repeated Block.BLOCK_STATE_REGISTRY.getId() calls which were taking 15.8% CPU.
     */
    public int getData(BlockState state) {
        int id = Block.BLOCK_STATE_REGISTRY.getId(state);
        int blockData = data[id];

        if ((blockData & COMPLETED_MASK) == 0) {
            blockData = fillData(id, state);
        }

        return blockData;
    }

    // Direct mask accessors for use with getData()
    public static boolean canWalkOnFromData(int blockData) {
        return (blockData & CAN_WALK_ON_MASK) != 0;
    }

    public static boolean canWalkOnSpecialFromData(int blockData) {
        return (blockData & CAN_WALK_ON_SPECIAL_MASK) != 0;
    }

    public static boolean canWalkThroughFromData(int blockData) {
        return (blockData & CAN_WALK_THROUGH_MASK) != 0;
    }

    public static boolean canWalkThroughSpecialFromData(int blockData) {
        return (blockData & CAN_WALK_THROUGH_SPECIAL_MASK) != 0;
    }

    public static boolean fullyPassableFromData(int blockData) {
        return (blockData & FULLY_PASSABLE_MASK) != 0;
    }

    public static boolean fullyPassableSpecialFromData(int blockData) {
        return (blockData & FULLY_PASSABLE_SPECIAL_MASK) != 0;
    }

    public static boolean canPlaceAgainstFromData(int blockData) {
        return (blockData & CAN_PLACE_AGAINST_MASK) != 0;
    }

    public static boolean hasFluidFromData(int blockData) {
        return (blockData & HAS_FLUID_MASK) != 0;
    }
}
