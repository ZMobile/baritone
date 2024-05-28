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
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of {@link IPlayerContext} that provides information about the primary player.
 *
 * @author Brady
 * @since 11/12/2018
 */
public final class BaritonePlayerContext implements IPlayerContext {
    List<String> excludedStackTraces = new ArrayList<>();

    private final Baritone baritone;
    private final Minecraft mc;
    private final IPlayer player;
    private final IPlayerController playerController;

    public BaritonePlayerContext(Baritone baritone, Minecraft mc) {
        this.excludedStackTraces = new ArrayList<>();
        this.baritone = baritone;
        this.mc = mc;
        this.playerController = new BaritonePlayerController(mc);
        this.player = new BaritonePlayer(mc.player);
    }

    public BaritonePlayerContext(Baritone baritone, Minecraft mc, LivingEntity livingEntity) {
        this.excludedStackTraces = new ArrayList<>();
        this.baritone = baritone;
        this.mc = mc;
        this.playerController = new BaritonePlayerController(mc);
        this.player = new BaritonePlayer(livingEntity);
    }


    @Override
    public Minecraft minecraft() {
        return this.mc;
    }

    @Override
    public IPlayer player() {
        return this.player;
    }

    private void logStackTraceToFile(String filePath, boolean controller) {
        File file = new File(filePath);
        if (!file.exists()) {
            try {
                Files.createFile(file.toPath());
            } catch (IOException e) {
                System.err.println("An error occurred while creating the file: " + e.getMessage());
                e.printStackTrace();
            }
        }
        try (FileWriter fileWriter = new FileWriter(filePath, true);
             PrintWriter printWriter = new PrintWriter(fileWriter)) {
            StringBuilder stringBuilder = new StringBuilder();
            for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
                stringBuilder.append(element);
            }
            //Remove the first line
            stringBuilder.delete(0, stringBuilder.indexOf("\n") + 1);
            if (excludedStackTraces.contains(stringBuilder.toString())) {
                return;
            }
            excludedStackTraces.add(stringBuilder.toString());
            printWriter.println();
            if (controller) {
                printWriter.println("Player Controller Fetched");
            } else {
                printWriter.println("Player Fetched");
            }
            printWriter.println("Stack trace at " + java.time.LocalDateTime.now() + ":");
            for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
                printWriter.println(element);
            }
            printWriter.println();
        } catch (IOException e) {
            System.err.println("An error occurred while writing the stack trace to the file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public IPlayerController playerController() {
        //logStackTraceToFile("C:/Users/hzant/OneDrive/Documents/LocalGPT/player-fetched.txt", true);
        return this.playerController;
        //return null;
    }

    @Override
    public Level world() {
        return this.mc.level;
    }

    @Override
    public IWorldData worldData() {
        return this.baritone.getWorldProvider().getCurrentWorld();
    }

    @Override
    public BetterBlockPos viewerPos() {
        final Entity entity = this.mc.getCameraEntity();
        return entity == null ? this.playerFeet() : BetterBlockPos.from(entity.blockPosition());
    }

    @Override
    public Rotation playerRotations() {
        return this.baritone.getLookBehavior().getEffectiveRotation().orElseGet(IPlayerContext.super::playerRotations);
    }

    @Override
    public HitResult objectMouseOver() {
        return RayTraceUtils.rayTraceTowards(player().getEntity(), playerRotations(), playerController().getBlockReachDistance());
    }
}
