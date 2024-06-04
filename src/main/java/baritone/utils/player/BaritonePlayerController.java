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

package baritone.utils.player;

import baritone.api.utils.IPlayerController;
import baritone.utils.accessor.IPlayerControllerMP;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Implementation of {@link IPlayerController} that chains to the primary player controller's methods
 * for server-side usage.
 *
 * @since 12/14/2018
 */
public final class BaritonePlayerController implements IPlayerController {

    /*private final ServerPlayer player;

    public BaritonePlayerController(ServerPlayer player) {
        this.player = player;
    }

    @Override
    public void syncHeldItem() {
        // Synchronize the held item on the server
        player.connection.send(player.getInventory().getCarried());
    }

    @Override
    public boolean hasBrokenBlock() {
        return !((IPlayerControllerMP) player.gameMode).isHittingBlock();
    }

    @Override
    public boolean onPlayerDamageBlock(BlockPos pos, Direction side) {
        return player.gameMode.handleBlockBreakAction(pos, ServerPlayerInteractionManager.Action.START_DESTROY_BLOCK, side, player.level.getBlockState(pos), player.level);
    }

    @Override
    public void resetBlockRemoving() {
        player.gameMode.breakingBlock = false;
    }

    @Override
    public void windowClick(int windowId, int slotId, int mouseButton, ClickType type, Player player) {
        this.player.connection.handleContainerClick(new ServerboundContainerClickPacket(windowId, slotId, mouseButton, type, player.getInventory().getCarried(), player.containerMenu.getStateId()));
    }

    @Override
    public GameType getGameType() {
        return player.gameMode.getGameModeForPlayer();
    }

    @Override
    public InteractionResult processRightClickBlock(ServerPlayer player, ServerLevel world, InteractionHand hand, BlockHitResult result) {
        return player.gameMode.useItemOn(player, world, hand, result);
    }

    @Override
    public InteractionResult processRightClick(ServerPlayer player, ServerLevel world, InteractionHand hand) {
        return player.gameMode.useItem(player, world, hand);
    }

    @Override
    public boolean clickBlock(BlockPos loc, Direction face) {
        return player.gameMode.handleBlockBreakAction(loc, ServerPlayerInteractionManager.Action.START_DESTROY_BLOCK, face, player.level.getBlockState(loc), player.level);
    }

    @Override
    public void setHittingBlock(boolean hittingBlock) {
        ((IPlayerControllerMP) player.gameMode).setIsHittingBlock(hittingBlock);
    }*/
}
