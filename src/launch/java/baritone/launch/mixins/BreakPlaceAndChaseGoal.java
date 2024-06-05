package baritone.launch.mixins;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.behavior.IPathingBehavior;
import baritone.api.pathing.calc.IPath;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.path.IPathExecutor;
import baritone.api.utils.BetterBlockPos;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BreakPlaceAndChaseGoal extends Goal {
    private final PathfinderMob mob;
    private LocalPlayer targetPlayer;
    private IPathingBehavior pathingBehavior;
    private IPath currentPath;
    private int breakingTicks;
    private BlockPos breakingPos;
    private BlockPos placingPos;
    private static final int BREAKING_TIME = 50; // Faster breaking time in ticks (2.5 seconds)
    private final Map<BlockPos, Integer> blockDamageProgress = new HashMap<>();

    public BreakPlaceAndChaseGoal(PathfinderMob mob) {
        this.mob = mob;
        BaritoneAPI.getSettings().allowBreak.value = true;
        BaritoneAPI.getSettings().allowPlace.value = true;
        BaritoneAPI.getSettings().allowParkour.value = false;
        BaritoneAPI.getSettings().allowJumpAt256.value = false;
        BaritoneAPI.getSettings().allowParkourAscend.value = false;
        BaritoneAPI.getSettings().allowParkourPlace.value = false;
        BaritoneAPI.getSettings().avoidance.value = false;
        BaritoneAPI.getSettings().assumeExternalAutoTool.value = true; // Assume tool is externally managed
        BaritoneAPI.getSettings().renderPath.value = true;
        BaritoneAPI.getSettings().renderSelectionBoxes.value = true;
        BaritoneAPI.getSettings().renderGoal.value = true;
        BaritoneAPI.getSettings().renderCachedChunks.value = true;
        BaritoneAPI.getSettings().renderSelectionCorners.value = true;
        BaritoneAPI.getSettings().renderGoalAnimated.value = true;
        BaritoneAPI.getSettings().renderPathAsLine.value = true;
        BaritoneAPI.getSettings().renderGoalXZBeacon.value = false;
    }

    @Override
    public boolean canUse() {
        if (mob.getTarget() != null && mob.getTarget() instanceof LocalPlayer) {
            targetPlayer = (LocalPlayer) mob.getTarget();
            boolean withinRange = mob.blockPosition().closerThan(targetPlayer.blockPosition(), 100)
                    && Math.abs(mob.blockPosition().getY() - targetPlayer.blockPosition().getY()) < 50;
            return !mob.isAggressive() && !mob.isPathFinding() && withinRange;
        }
        return false;
    }

    @Override
    public void start() {
        //System.out.println("#################### GOAL Triggered");
        //calculatePath();
    }

    private void calculatePath() {
        //System.out.println("Calculating path.");
        if (this.targetPlayer != null) {
            BlockPos targetPos = targetPlayer.blockPosition();
            GoalBlock goal = new GoalBlock(targetPos.getX(), targetPos.getY(), targetPos.getZ());
            //Check if block underneath player is air and if so set goal to one of the adjacent blocks thats over a solid block.
            if (mob.level().getBlockState(targetPos.below()).isAir()) {
                for (Direction direction : Direction.Plane.HORIZONTAL) {
                    BlockPos adjacentPos = targetPos.relative(direction);
                    if (mob.level().getBlockState(adjacentPos.below()).isSolidRender(mob.level(), adjacentPos.below())) {
                        goal = new GoalBlock(adjacentPos.getX(), adjacentPos.getY(), adjacentPos.getZ());
                        break;
                    }
                }
            }
            if (mob.level().getBlockState(targetPos.below()).isAir()) {
                //System.out.println("Player is standing on air. Cannot calculate path.");
                return;
            }
            IBaritone goalBaritone = BaritoneAPI.getProvider().getBaritoneForEntity(mob);
            if (goalBaritone != null) {
                pathingBehavior = goalBaritone.getPathingBehavior();
                goalBaritone.getCustomGoalProcess().setGoalAndPath(goal);
                if (goalBaritone.getPathingBehavior().getCurrent() != null) {
                    currentPath = goalBaritone.getPathingBehavior().getCurrent().getPath();
                    breakingPos = null;
                    placingPos = null;
                    findBreakingOrPlacingBlock();
                } else {
                    currentPath = null;
                    //System.out.println("Failed to calculate path.");
                }
            }
        }
    }

    private boolean isPlacementNeeded(BlockPos blockPos) {
        // Check if the block position needs a block to be placed
        return mob.level().getBlockState(blockPos).isAir();
    }

    private void findBreakingOrPlacingBlock() {
        if (currentPath != null) {
            List<BetterBlockPos> positions = currentPath.positions();
            for (int i = 0; i < positions.size(); i++) {
                BetterBlockPos pos = positions.get(i);
                //System.out.println("Checking block at: " + pos);
                BlockPos blockPos = new BlockPos(pos.x, pos.y, pos.z);
                if (isPlacementNeeded(blockPos.below()) && mob.getMainHandItem().getItem() instanceof BlockItem) {
                    placingPos = blockPos;
                    breakingPos = null; // Ensure breakingPos is null
                    mob.getNavigation().moveTo(blockPos.getX(), blockPos.getY(), blockPos.getZ(), 1.0);
                    return;
                }


                if (isBreakable(blockPos) || isBreakable(blockPos.above())) {
                    breakingPos = isBreakable(blockPos) ? blockPos : blockPos.above();
                    //System.out.println("Identified block to break at: " + breakingPos);
                    BlockPos adjacentPos;
                    if (i == 0) {
                        adjacentPos = mob.blockPosition();
                    } else {
                        adjacentPos = positions.get(i - 1);
                    }
                    if (adjacentPos != null) {
                        mob.getNavigation().moveTo(adjacentPos.getX(), adjacentPos.getY(), adjacentPos.getZ(), 1.0);
                        return;
                    }
                } else {
                    //System.out.println("Check diagonals between positions.");
                    if (i != positions.size() - 1) {
                        BetterBlockPos nextPos = positions.get(i + 1);
                        //If next block pos x and y, or y and z are different, check if the diagonal block is breakable
                        if (nextPos.x != pos.x && nextPos.z != pos.z) {
                            BlockPos diagonalBlockPos1 = new BlockPos(pos.x, pos.y, nextPos.z);
                            BlockPos diagonalBlockPos2 = new BlockPos(nextPos.x, pos.y, pos.z);
                            if ((isBreakable(diagonalBlockPos1) || isBreakable(diagonalBlockPos1.above()) && isSolidBlock(diagonalBlockPos1.below()))) {
                                if (isSolidBlock(diagonalBlockPos2.below())&& !isSolidBlock(diagonalBlockPos2) && !isSolidBlock(diagonalBlockPos2)) {
                                    //Diagonal path already exists, none needed
                                    continue;
                                }
                                breakingPos = isBreakable(diagonalBlockPos1) ? diagonalBlockPos1 : diagonalBlockPos1.above();
                                //System.out.println("Identified block to break at: " + breakingPos);
                                mob.getNavigation().moveTo(pos.getX(), pos.getY(), pos.getZ(), 1.0);
                                return;
                            }
                            if (isBreakable(diagonalBlockPos2) || isBreakable(diagonalBlockPos2.above()) && isSolidBlock(diagonalBlockPos2.below())) {
                                if (isSolidBlock(diagonalBlockPos1.below()) && !isSolidBlock(diagonalBlockPos1) && !isSolidBlock(diagonalBlockPos1)) {
                                    //Diagonal path already exists, none needed
                                    continue;
                                }
                                breakingPos = isBreakable(diagonalBlockPos2) ? diagonalBlockPos2 : diagonalBlockPos2.above();
                                //System.out.println("Identified block to break at: " + breakingPos);
                                mob.getNavigation().moveTo(pos.getX(), pos.getY(), pos.getZ(), 1.0);
                                return;
                            }
                        }
                        if (nextPos.y != pos.y) {
                            BlockPos twoBlocksUp;
                            if (nextPos.y < pos.y) {
                                twoBlocksUp = new BlockPos(nextPos.x, nextPos.y + 2, nextPos.z);
                            } else {
                                twoBlocksUp = new BlockPos(pos.x, pos.y + 2, pos.z);
                            }
                            if (isBreakable(twoBlocksUp)) {
                                breakingPos = twoBlocksUp;
                                //System.out.println("Identified block to break at: " + breakingPos);
                                mob.getNavigation().moveTo(pos.getX(), pos.getY(), pos.getZ(), 1.0);
                                return;
                            }
                        }
                    }
                }
            }
        }
        //System.out.println("No block to break found in the path.");
    }

    private boolean isBreakable(BlockPos blockPos) {
        //System.out.println("block state: " + mob.level().getBlockState(blockPos));
        if (pathingBehavior != null && pathingBehavior.getCurrent() != null) {
            IPathExecutor current = pathingBehavior.getCurrent(); // this should prevent most race conditions?
            Set<BlockPos> blocksToBreak = current.toBreak();
            //System.out.println("Blocks to break size: " + blocksToBreak.size());
            for (BlockPos pos : blocksToBreak) {
                if (pos.equals(blockPos)) {
                    //System.out.println("Blocks to break contains this block");
                    return true;
                }
                //System.out.println("Block to break: " + pos);
            }
        }
        return mob.level().getBlockState(blockPos).isSolidRender(mob.level(), blockPos);
    }

    private boolean isSolidBlock(BlockPos blockPos) {
        //System.out.println("block state: " + mob.level().getBlockState(blockPos));
        if (pathingBehavior != null && pathingBehavior.getCurrent() != null) {
            IPathExecutor current = pathingBehavior.getCurrent(); // this should prevent most race conditions?
            Set<BlockPos> blocksToBreak = current.toBreak();
            Set<BlockPos> blocksToWalkInto = current.toWalkInto();
            //System.out.println("Blocks to break size: " + blocksToBreak.size());
            for (BlockPos pos : blocksToBreak) {
                if (pos.equals(blockPos)) {
                    //System.out.println("Blocks to break contains this block");
                    return true;
                }
            }
            /*for (BlockPos pos : blocksToWalkInto) {
                if (pos.equals(blockPos)) {
                    return true;
                }
            }*/
        }
        return mob.level().getBlockState(blockPos).isSolidRender(mob.level(), blockPos);
    }

    @Override
    public void tick() {
        if (targetPlayer != null) {
            if (breakingPos == null) {
                calculatePath();
            }
            if (breakingPos != null) {
                if (mob.blockPosition().closerThan(breakingPos, 3)) {
                    //System.out.println("Block is within distance to break.");
                    continueBreakingBlock();
                } else {
                    mob.getNavigation().moveTo(breakingPos.getX(), breakingPos.getY(), breakingPos.getZ(), 1.0);
                }
                //System.out.println("Block is not within distance to break. Moving to block.");
                //System.out.println("Distance: " + mob.blockPosition().getManhattanDistance(breakingPos));
            } else if (placingPos != null) {
                if (mob.blockPosition().closerThan(placingPos, 3)) {
                    // System.out.println("Block is within distance to place.");
                    placeBlock();
                } else {
                    // System.out.println("Block is not within distance to place. Moving to block.");
                    // System.out.println("Distance: " + mob.blockPosition().getManhattanDistance(placingPos));
                    mob.getNavigation().moveTo(placingPos
                            .getX(), placingPos.getY(), placingPos.getZ(), 1.0);
                }
            }
        }
    }

    private void placeBlock() {
        if (placingPos != null && mob.getMainHandItem().getItem() instanceof BlockItem) {
            if (!hasAdjacentBlockIncludingBelow(placingPos)) {
                //System.out.println("Placing block at: " + placingPos);
                placingPos = placingPos.below();
            }
            Level world = mob.level();
            ItemStack itemStack = mob.getMainHandItem();
            BlockItem blockItem = (BlockItem) itemStack.getItem();
            world.setBlock(placingPos, blockItem.getBlock().defaultBlockState(), 1);
            //Remove item from hand
            mob.setItemInHand(mob.getUsedItemHand(), ItemStack.EMPTY);
            placingPos = null;
        }
    }

    private boolean hasAdjacentBlockIncludingBelow(BlockPos blockPos) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos adjacentPos = blockPos.relative(direction);
            if (mob.level().getBlockState(adjacentPos).isSolidRender(mob.level(), adjacentPos)) {
                return true;
            }
        }
        if (mob.level().getBlockState(blockPos.below()).isSolidRender(mob.level(), blockPos.below())) {
            return true;
        }
        return false;
    }



    private void continueBreakingBlock() {
        Level world = mob.level();
        breakingTicks++;
        int originalProgress = blockDamageProgress.getOrDefault(breakingPos, 0);
        int progress;

        // Retrieve block hardness
        float blockHardness = mob.level().getBlockState(breakingPos).getDestroySpeed(mob.level(), breakingPos);
        int adjustedBreakingTime = (int) (BREAKING_TIME * blockHardness);

        // Increase progress incrementally
        //System.out.println("Breaking ticks: " + breakingTicks);
        progress = originalProgress + (int) ((breakingTicks / (float) adjustedBreakingTime) * 10);
        //System.out.println("Progress: " + progress);
        world.destroyBlockProgress(mob.getId(), breakingPos, progress);
        blockDamageProgress.put(breakingPos, progress);

        if (progress >= 10) {
            //System.out.println("Breaking block at: " + breakingPos);
            boolean success = world.destroyBlock(breakingPos, true, mob);
            //System.out.println("Block broken: " + success);
            //System.out.println("Is air: " + world.getBlockState(breakingPos).isAir());
            if (!success) {
                world.setBlock(breakingPos, Blocks.AIR.defaultBlockState(), 3);
            }
            blockDamageProgress.remove(breakingPos);
            breakingTicks = 0;
            breakingPos = null;
        }
    }

    @Override
    public boolean canContinueToUse() {
        if (currentPath != null) {
            return areSolidBlocksSeparatingPlayerFromMob();
        }
        return true;
    }

    public boolean areSolidBlocksSeparatingPlayerFromMob() {
        List<BetterBlockPos> positions = currentPath.positions();
        for (int i = 0; i < positions.size(); i++) {
            BetterBlockPos pos = positions.get(i);
            //System.out.println("Checking block at: " + pos);
            BlockPos blockPos = new BlockPos(pos.x, pos.y, pos.z);
            if (isBreakable(blockPos) || isBreakable(blockPos.above())) {
                return true;
            } else {
                //System.out.println("Check diagonals between positions.");
                if (i != positions.size() - 1) {
                    BetterBlockPos nextPos = positions.get(i + 1);
                    if (nextPos.x != pos.x && nextPos.z != pos.z) {
                        BlockPos diagonalBlockPos1 = new BlockPos(pos.x, pos.y, nextPos.z);
                        BlockPos diagonalBlockPos2 = new BlockPos(nextPos.x, pos.y, pos.z);
                        if ((isBreakable(diagonalBlockPos1) || isBreakable(diagonalBlockPos1.above())) && isSolidBlock(diagonalBlockPos1.below())) {
                            if (isSolidBlock(diagonalBlockPos2.below()) && !isSolidBlock(diagonalBlockPos2) && !isSolidBlock(diagonalBlockPos2)) {
                                //Diagonal path already exists, none needed
                                continue;
                            }
                            return true;
                        }
                        if ((isBreakable(diagonalBlockPos2) || isBreakable(diagonalBlockPos2.above())) && isSolidBlock(diagonalBlockPos2.below())) {
                            if (isSolidBlock(diagonalBlockPos1.below()) && !isSolidBlock(diagonalBlockPos1) && !isSolidBlock(diagonalBlockPos1)) {
                                //Diagonal path already exists, none needed
                                continue;
                            }
                            return true;
                        }
                    }
                    if (nextPos.y != pos.y) {
                        BlockPos twoBlocksUp;
                        if (nextPos.y < pos.y) {
                            twoBlocksUp = new BlockPos(nextPos.x, nextPos.y + 2, nextPos.z);
                        } else {
                            twoBlocksUp = new BlockPos(pos.x, pos.y + 2, pos.z);
                        }
                        if (isBreakable(twoBlocksUp)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    @Override
    public void stop() {
        //System.out.println("#################### GOAL Stopped");
        resetGoal();
    }

    private void resetGoal() {
        currentPath = null;
        breakingTicks = 0;
        breakingPos = null;
        mob.getNavigation().stop();
    }
}
