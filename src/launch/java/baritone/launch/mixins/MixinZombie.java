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
import baritone.api.pathing.goals.GoalBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Zombie.class)
public abstract class MixinZombie extends PathfinderMob {

    protected MixinZombie(EntityType<? extends PathfinderMob> entityType, Level world) {
        super(entityType, world);
    }

    @Inject(method = "registerGoals", at = @At("TAIL"))
    private void addCustomGoals(CallbackInfo info) {
        // Debug log to indicate injection point
        // Ensure we don't block the default behavior
        // Add the custom goal with a lower priority to avoid blocking essential default goals
        GoalBlock goal = new GoalBlock(0, 60, 200);
        BaritoneAPI.getProvider().createBaritone(Minecraft.getInstance(), this);
        BaritoneAPI.getProvider().getBaritoneForEntity(this).getCustomGoalProcess().setGoalAndPath(goal);
        //this.goalSelector.add(6, new BreakBlockAndChaseGoal(this, this.goalSelector));
        // Debug log to verify goal addition
        System.out.println("Baritone goal successfully added to ZombieEntity");
    }
}