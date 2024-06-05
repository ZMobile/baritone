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

package baritone.pathing.movement;

import baritone.api.utils.BetterBlockPos;
import baritone.pathing.calc.PathNode;
import baritone.pathing.movement.movements.*;
import baritone.utils.pathing.MutableMoveResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * An enum of all possible movements attached to all possible directions they could be taken in
 *
 * @author leijurv
 */

import baritone.api.utils.BetterBlockPos;
import baritone.pathing.movement.movements.*;
import baritone.utils.pathing.MutableMoveResult;
import net.minecraft.core.Direction;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public enum Moves {
    DOWNWARD(0, -1, 0) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src, PathNode previousNode) {
            return new MovementDownward(context.getBaritone(), src, src.below());
        }

        @Override
        public double cost(CalculationContext context, int x, int y, int z, PathNode previousNode) {
            return MovementDownward.cost(context, x, y, z);
        }
    },

    PILLAR(0, +1, 0) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src, PathNode previousNode) {
            return new MovementPillar(context.getBaritone(), src, src.above());
        }

        @Override
        public double cost(CalculationContext context, int x, int y, int z, PathNode previousNode) {
            return MovementPillar.cost(context, x, y, z);
        }
    },

    TRAVERSE_NORTH(0, 0, -1) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src, PathNode previousNode) {
            return new MovementTraverse(context.getBaritone(), src, src.north());
        }

        @Override
        public double cost(CalculationContext context, int x, int y, int z, PathNode previousNode) {
            return MovementTraverse.cost(context, x, y, z, x, z - 1);
        }
    },

    TRAVERSE_SOUTH(0, 0, +1) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src, PathNode previousNode) {
            return new MovementTraverse(context.getBaritone(), src, src.south());
        }

        @Override
        public double cost(CalculationContext context, int x, int y, int z, PathNode previousNode) {
            return MovementTraverse.cost(context, x, y, z, x, z + 1);
        }
    },

    TRAVERSE_EAST(+1, 0, 0) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src, PathNode previousNode) {
            return new MovementTraverse(context.getBaritone(), src, src.east());
        }

        @Override
        public double cost(CalculationContext context, int x, int y, int z, PathNode previousNode) {
            return MovementTraverse.cost(context, x, y, z, x + 1, z);
        }
    },

    TRAVERSE_WEST(-1, 0, 0) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src, PathNode previousNode) {
            return new MovementTraverse(context.getBaritone(), src, src.west());
        }

        @Override
        public double cost(CalculationContext context, int x, int y, int z, PathNode previousNode) {
            return MovementTraverse.cost(context, x, y, z, x - 1, z);
        }
    },

    ASCEND_NORTH(0, +1, -1) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src, PathNode previousNode) {
            return new MovementAscend(context.getBaritone(), src, new BetterBlockPos(src.x, src.y + 1, src.z - 1));
        }

        @Override
        public double cost(CalculationContext context, int x, int y, int z, PathNode previousNode) {
            List<BlockPos> previousPositions = new ArrayList<>();
            int i = 0;
            PathNode iteratingNode = previousNode;
            while (iteratingNode != null && i < 10) {
                previousPositions.add(new BlockPos(iteratingNode.x, iteratingNode.y, iteratingNode.z));
                iteratingNode = iteratingNode.previous;
                i++;
            }
            return MovementAscend.cost(context, x, y, z, x, z - 1, previousPositions);
        }
    },

    ASCEND_SOUTH(0, +1, +1) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src, PathNode previousNode) {
            return new MovementAscend(context.getBaritone(), src, new BetterBlockPos(src.x, src.y + 1, src.z + 1));
        }

        @Override
        public double cost(CalculationContext context, int x, int y, int z, PathNode previousNode) {
            List<BlockPos> previousPositions = new ArrayList<>();
            int i = 0;
            PathNode iteratingNode = previousNode;
            while (iteratingNode != null && i < 10) {
                previousPositions.add(new BlockPos(iteratingNode.x, iteratingNode.y, iteratingNode.z));
                iteratingNode = iteratingNode.previous;
                i++;
            }
            return MovementAscend.cost(context, x, y, z, x, z + 1, previousPositions);
        }
    },

    ASCEND_EAST(+1, +1, 0) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src, PathNode previousNode) {
            return new MovementAscend(context.getBaritone(), src, new BetterBlockPos(src.x + 1, src.y + 1, src.z));
        }

        @Override
        public double cost(CalculationContext context, int x, int y, int z, PathNode previousNode) {
            List<BlockPos> previousPositions = new ArrayList<>();
            int i = 0;
            PathNode iteratingNode = previousNode;
            while (iteratingNode != null && i < 10) {
                previousPositions.add(new BlockPos(iteratingNode.x, iteratingNode.y, iteratingNode.z));
                iteratingNode = iteratingNode.previous;
                i++;
            }
            return MovementAscend.cost(context, x, y, z, x + 1, z, previousPositions);
        }
    },

    ASCEND_WEST(-1, +1, 0) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src, PathNode previousNode) {
            return new MovementAscend(context.getBaritone(), src, new BetterBlockPos(src.x - 1, src.y + 1, src.z));
        }

        @Override
        public double cost(CalculationContext context, int x, int y, int z, PathNode previousNode) {
            List<BlockPos> previousPositions = new ArrayList<>();
            int i = 0;
            PathNode iteratingNode = previousNode;
            while (iteratingNode != null && i < 10) {
                previousPositions.add(new BlockPos(iteratingNode.x, iteratingNode.y, iteratingNode.z));
                iteratingNode = iteratingNode.previous;
                i++;
            }
            return MovementAscend.cost(context, x, y, z, x - 1, z, previousPositions);
        }
    },

    DESCEND_EAST(+1, -1, 0, false, true) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src, PathNode previousNode) {
            MutableMoveResult res = new MutableMoveResult();
            apply(context, src.x, src.y, src.z, previousNode, res);
            if (res.y == src.y - 1) {
                return new MovementDescend(context.getBaritone(), src, new BetterBlockPos(res.x, res.y, res.z));
            } else {
                return new MovementFall(context.getBaritone(), src, new BetterBlockPos(res.x, res.y, res.z));
            }
        }

        @Override
        public void apply(CalculationContext context, int x, int y, int z, PathNode previousNode, MutableMoveResult result) {
            MovementDescend.cost(context, x, y, z, x + 1, z, result);
        }

        @Override
        public double cost(CalculationContext context, int x, int y, int z, PathNode previousNode) {
            throw new UnsupportedOperationException();
        }
    },

    DESCEND_WEST(-1, -1, 0, false, true) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src, PathNode previousNode) {
            MutableMoveResult res = new MutableMoveResult();
            apply(context, src.x, src.y, src.z, previousNode, res);
            if (res.y == src.y - 1) {
                return new MovementDescend(context.getBaritone(), src, new BetterBlockPos(res.x, res.y, res.z));
            } else {
                return new MovementFall(context.getBaritone(), src, new BetterBlockPos(res.x, res.y, res.z));
            }
        }

        @Override
        public void apply(CalculationContext context, int x, int y, int z, PathNode previousNode, MutableMoveResult result) {
            MovementDescend.cost(context, x, y, z, x - 1, z, result);
        }

        @Override
        public double cost(CalculationContext context, int x, int y, int z, PathNode previousNode) {
            throw new UnsupportedOperationException();
        }
    },

    DESCEND_NORTH(0, -1, -1, false, true) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src, PathNode previousNode) {
            MutableMoveResult res = new MutableMoveResult();
            apply(context, src.x, src.y, src.z, previousNode, res);
            if (res.y == src.y - 1) {
                return new MovementDescend(context.getBaritone(), src, new BetterBlockPos(res.x, res.y, res.z));
            } else {
                return new MovementFall(context.getBaritone(), src, new BetterBlockPos(res.x, res.y, res.z));
            }
        }

        @Override
        public void apply(CalculationContext context, int x, int y, int z, PathNode previousNode, MutableMoveResult result) {
            MovementDescend.cost(context, x, y, z, x, z - 1, result);
        }

        @Override
        public double cost(CalculationContext context, int x, int y, int z, PathNode previousNode) {
            throw new UnsupportedOperationException();
        }
    },

    DESCEND_SOUTH(0, -1, +1, false, true) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src, PathNode previousNode) {
            MutableMoveResult res = new MutableMoveResult();
            apply(context, src.x, src.y, src.z, previousNode, res);
            if (res.y == src.y - 1) {
                return new MovementDescend(context.getBaritone(), src, new BetterBlockPos(res.x, res.y, res.z));
            } else {
                return new MovementFall(context.getBaritone(), src, new BetterBlockPos(res.x, res.y, res.z));
            }
        }

        @Override
        public void apply(CalculationContext context, int x, int y, int z, PathNode previousNode, MutableMoveResult result) {
            MovementDescend.cost(context, x, y, z, x, z + 1, result);
        }

        @Override
        public double cost(CalculationContext context, int x, int y, int z, PathNode previousNode) {
            throw new UnsupportedOperationException();
        }
    },

    DIAGONAL_NORTHEAST(+1, 0, -1, false, true) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src, PathNode previousNode) {
            MutableMoveResult res = new MutableMoveResult();
            apply(context, src.x, src.y, src.z, previousNode, res);
            return new MovementDiagonal(context.getBaritone(), src, Direction.NORTH, Direction.EAST, res.y - src.y);
        }

        @Override
        public void apply(CalculationContext context, int x, int y, int z, PathNode previousNode, MutableMoveResult result) {
            MovementDiagonal.cost(context, x, y, z, x + 1, z - 1, result);
        }

        @Override
        public double cost(CalculationContext context, int x, int y, int z, PathNode previousNode) {
            throw new UnsupportedOperationException();
        }
    },

    DIAGONAL_NORTHWEST(-1, 0, -1, false, true) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src, PathNode previousNode) {
            MutableMoveResult res = new MutableMoveResult();
            apply(context, src.x, src.y, src.z, previousNode, res);
            return new MovementDiagonal(context.getBaritone(), src, Direction.NORTH, Direction.WEST, res.y - src.y);
        }

        @Override
        public void apply(CalculationContext context, int x, int y, int z, PathNode previousNode, MutableMoveResult result) {
            MovementDiagonal.cost(context, x, y, z, x - 1, z - 1, result);
        }

        @Override
        public double cost(CalculationContext context, int x, int y, int z, PathNode previousNode) {
            throw new UnsupportedOperationException();
        }
    },

    DIAGONAL_SOUTHEAST(+1, 0, +1, false, true) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src, PathNode previousNode) {
            MutableMoveResult res = new MutableMoveResult();
            apply(context, src.x, src.y, src.z, previousNode, res);
            return new MovementDiagonal(context.getBaritone(), src, Direction.SOUTH, Direction.EAST, res.y - src.y);
        }

        @Override
        public void apply(CalculationContext context, int x, int y, int z, PathNode previousNode, MutableMoveResult result) {
            MovementDiagonal.cost(context, x, y, z, x + 1, z + 1, result);
        }

        @Override
        public double cost(CalculationContext context, int x, int y, int z, PathNode previousNode) {
            throw new UnsupportedOperationException();
        }
    },

    DIAGONAL_SOUTHWEST(-1, 0, +1, false, true) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src, PathNode previousNode) {
            MutableMoveResult res = new MutableMoveResult();
            apply(context, src.x, src.y, src.z, previousNode, res);
            return new MovementDiagonal(context.getBaritone(), src, Direction.SOUTH, Direction.WEST, res.y - src.y);
        }

        @Override
        public void apply(CalculationContext context, int x, int y, int z, PathNode previousNode, MutableMoveResult result) {
            MovementDiagonal.cost(context, x, y, z, x - 1, z + 1, result);
        }

        @Override
        public double cost(CalculationContext context, int x, int y, int z, PathNode previousNode) {
            throw new UnsupportedOperationException();
        }
    },

    PARKOUR_NORTH(0, 0, -4, true, true) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src, PathNode previousNode) {
            return MovementParkour.cost(context, src, Direction.NORTH);
        }

        @Override
        public void apply(CalculationContext context, int x, int y, int z, PathNode previousNode, MutableMoveResult result) {
            MovementParkour.cost(context, x, y, z, Direction.NORTH, result);
        }

        @Override
        public double cost(CalculationContext context, int x, int y, int z, PathNode previousNode) {
            throw new UnsupportedOperationException();
        }
    },

    PARKOUR_SOUTH(0, 0, +4, true, true) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src, PathNode previousNode) {
            return MovementParkour.cost(context, src, Direction.SOUTH);
        }

        @Override
        public void apply(CalculationContext context, int x, int y, int z, PathNode previousNode, MutableMoveResult result) {
            MovementParkour.cost(context, x, y, z, Direction.SOUTH, result);
        }

        @Override
        public double cost(CalculationContext context, int x, int y, int z, PathNode previousNode) {
            throw new UnsupportedOperationException();
        }
    },

    PARKOUR_EAST(+4, 0, 0, true, true) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src, PathNode previousNode) {
            return MovementParkour.cost(context, src, Direction.EAST);
        }

        @Override
        public void apply(CalculationContext context, int x, int y, int z, PathNode previousNode, MutableMoveResult result) {
            MovementParkour.cost(context, x, y, z, Direction.EAST, result);
        }

        @Override
        public double cost(CalculationContext context, int x, int y, int z, PathNode previousNode) {
            throw new UnsupportedOperationException();
        }
    },

    PARKOUR_WEST(-4, 0, 0, true, true) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src, PathNode previousNode) {
            return MovementParkour.cost(context, src, Direction.WEST);
        }

        @Override
        public void apply(CalculationContext context, int x, int y, int z, PathNode previousNode, MutableMoveResult result) {
            MovementParkour.cost(context, x, y, z, Direction.WEST, result);
        }

        @Override
        public double cost(CalculationContext context, int x, int y, int z, PathNode previousNode) {
            throw new UnsupportedOperationException();
        }
    };

    public final boolean dynamicXZ;
    public final boolean dynamicY;

    public final int xOffset;
    public final int yOffset;
    public final int zOffset;

    Moves(int x, int y, int z, boolean dynamicXZ, boolean dynamicY) {
        this.xOffset = x;
        this.yOffset = y;
        this.zOffset = z;
        this.dynamicXZ = dynamicXZ;
        this.dynamicY = dynamicY;
    }

    Moves(int x, int y, int z) {
        this(x, y, z, false, false);
    }

    public abstract Movement apply0(CalculationContext context, BetterBlockPos src, PathNode previousNode);

    public void apply(CalculationContext context, int x, int y, int z, PathNode previousNode, MutableMoveResult result) {
        if (dynamicXZ || dynamicY) {
            throw new UnsupportedOperationException();
        }
        result.x = x + xOffset;
        result.y = y + yOffset;
        result.z = z + zOffset;
        result.cost = cost(context, x, y, z, previousNode);
    }

    public double cost(CalculationContext context, int x, int y, int z, PathNode previousNode) {
        throw new UnsupportedOperationException();
    }
}
