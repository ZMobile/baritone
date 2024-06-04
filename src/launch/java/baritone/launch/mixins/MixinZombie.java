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

package baritone.launch.mixins;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.utils.MinecraftServerUtil;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//Example usage of Baritone with mobs
//@Mixin(Zombie.class)
public abstract class MixinZombie extends PathfinderMob {

    protected MixinZombie(EntityType<? extends PathfinderMob> entityType, Level world) {
        super(entityType, world);
    }

    //@Inject(method = "registerGoals", at = @At("TAIL"))
    private void addCustomGoals(CallbackInfo info) {
        //GoalBlock goal = new GoalBlock(0, 60, 200);
        BaritoneAPI.getProvider().createBaritone(MinecraftServerUtil.getMinecraftServer(), this);
        //BaritoneAPI.getProvider().getBaritoneForEntity(this).getCustomGoalProcess().setGoalAndPath(goal);
        //this.goalSelector.add(6, new BreakBlockAndChaseGoal(this, this.goalSelector));
        // Debug log to verify goal addition
        System.out.println("Baritone goal successfully added to ZombieEntity");
    }

    //@Inject(method = "tick", at = @At("HEAD"))
    private void checkZombieState(CallbackInfo info) {
        /*LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            Vec3 playerPosition = Minecraft.getInstance().player.position();
            GoalBlock goal = new GoalBlock((int) playerPosition.x, (int) playerPosition.y, (int) playerPosition.z);
            IBaritone goalBaritone = BaritoneAPI.getProvider().getBaritoneForEntity(this);
            if (goalBaritone != null) {
                goalBaritone.getCustomGoalProcess().setGoalAndPath(goal);
            }
        }*/
    }

    //@Inject(method = "tick", at = @At("TAIL"))
    private void onZombieDespawn(CallbackInfo info) {
        if (!this.isAlive()) {
            IBaritone goalBaritone = BaritoneAPI.getProvider().getBaritoneForEntity(this);
            if (goalBaritone != null) {
                // Clean up Baritone instance for this entity
                BaritoneAPI.getProvider().destroyBaritone(goalBaritone);
                // Debug log to verify cleanup
                System.out.println("Baritone instance successfully removed for ZombieEntity on despawn");
            }
        }
    }
}