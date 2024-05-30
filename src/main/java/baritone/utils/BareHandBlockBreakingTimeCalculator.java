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

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class BareHandBlockBreakingTimeCalculator {
    public static double getBreakSpeedWithHand(BlockState blockState, BlockPos blockPos) {
        Block block = blockState.getBlock();

        // Check if the block is air
        if (block == Blocks.AIR) {
            return 0.0;
        }

        // Fetch the block destroy speed
        float destroySpeed = blockState.getDestroySpeed(null, blockPos); // Pass null for World parameter as it is not used

        // Calculate the base break time
        float breakTime = destroySpeed * 1.5f;

        // For a hand, we assume no tool effectiveness, so multiply by 5
        breakTime *= 5.0f;

        // Calculate the break speed
        double breakSpeed = (breakTime != 0) ? 1.0 / breakTime : 0.0;

        return breakSpeed;
    }
}
