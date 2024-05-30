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

import baritone.api.utils.IPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class BaritonePlayer implements IPlayer {
    private Minecraft minecraft;
    private LivingEntity entity;
    private boolean isLocalPlayer;

    public BaritonePlayer(Minecraft minecraft) {
        this.isLocalPlayer = true;
        this.minecraft = minecraft;
    }

    public BaritonePlayer(LivingEntity livingEntity) {
        this.entity = livingEntity;
        this.isLocalPlayer = false;
    }

    public LocalPlayer getPlayer() {
        if (isLocalPlayer) {
            return minecraft.player;
        } else {
            return null;
        }
    }

    public boolean isLocalPlayer() {
        return isLocalPlayer;
    }

    @Override
    public LivingEntity getEntity() {
        if (isLocalPlayer) {
            return minecraft.player;
        }
        return entity;
    }
}
