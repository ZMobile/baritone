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

import baritone.Baritone;
import baritone.api.cache.IWorldData;
import baritone.api.utils.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

/**
 * Implementation of {@link IPlayerContext} that provides information about the primary player.
 *
 * @author Brady
 * @since 11/12/2018
 */
public final class BaritonePlayerContext implements IPlayerContext {
    private final Baritone baritone;
    private final MinecraftServer mcs;
    private final IPlayerController playerController;
    private final IPlayer baritonePlayer;

    // Cached world reference to avoid repeated entity().level() calls during pathfinding
    private volatile Level cachedWorld;

    public BaritonePlayerContext(Baritone baritone, MinecraftServer minecraftServer, LivingEntity livingEntity) {
        this.baritone = baritone;
        //this.mc = null;
        this.mcs = minecraftServer;
        //this.playerController = new BaritonePlayerController(mc);
        this.playerController = null;
        this.baritonePlayer = new BaritonePlayer(livingEntity);
        this.cachedWorld = livingEntity.level();
    }

    @Override
    public MinecraftServer server() {
        return this.mcs;
    }

    @Override
    public IPlayer baritonePlayer() {
        return this.baritonePlayer;
    }

    //For backwards compatibility
    /*@Override
    public LocalPlayer player() {
       return baritonePlayer.getPlayer();
    }*/

    @Override
    public LivingEntity entity() {
        return this.baritonePlayer.getEntity();
    }

    /*@Override
    public IPlayerController playerController() {
        return this.playerController;
    }*/

    @Override
    public Level world() {
        // Use cached world reference - much faster than entity().level() chain
        Level world = this.cachedWorld;
        if (world == null) {
            world = this.entity().level();
            this.cachedWorld = world;
        }
        return world;
    }

    /**
     * Refreshes the cached world reference. Call this if the entity changes dimensions.
     */
    public void refreshWorldCache() {
        this.cachedWorld = this.entity().level();
    }

    @Override
    public IWorldData worldData() {
        return this.baritone.getWorldProvider().getCurrentWorld();
    }

    @Override
    public BetterBlockPos viewerPos() {
        final Entity entity = entity();
        return entity == null ? this.playerFeet() : BetterBlockPos.from(entity.blockPosition());
    }

    /*@Override
    public Rotation playerRotations() {
        return this.baritone.getLookBehavior().getEffectiveRotation().orElseGet(IPlayerContext.super::playerRotations);
    }*/

    @Override
    public HitResult objectMouseOver() {
        return RayTraceUtils.rayTraceTowards(baritonePlayer().getEntity(), playerRotations(), baritonePlayer().getBlockReachDistance());
    }
}
