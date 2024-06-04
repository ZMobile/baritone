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
import baritone.api.event.events.TickEvent;
import baritone.api.event.events.WorldEvent;
import baritone.api.event.events.type.EventState;
import baritone.api.utils.MinecraftServerUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.level.Level;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.function.BiFunction;

/**
 * Converted to server-side mixin for Baritone functionality.
 */
@Mixin(MinecraftServer.class)
public class MixinMinecraftServer {
    @Shadow
    private Map<ResourceKey<Level>, ServerLevel> levels;

    @Unique
    private BiFunction<EventState, TickEvent.Type, TickEvent> tickProvider;

    @Inject(
            method = "<init>",
            at = @At("RETURN")
    )
    private void postInit(CallbackInfo ci) {
        MinecraftServerUtil.setMinecraftServer((MinecraftServer) (Object) this);
    }

    @Inject(
            method = "tickServer",
            at = @At(
                    value = "FIELD",
                    opcode = Opcodes.GETFIELD,
                    target = "net/minecraft/server/MinecraftServer.tickCount:I",
                    ordinal = 0,
                    shift = At.Shift.BEFORE
            ),
            slice = @Slice(
                    from = @At(
                            value = "FIELD",
                            opcode = Opcodes.PUTFIELD,
                            target = "net/minecraft/server/MinecraftServer.nextTickTime:Z"
                    )
            )
    )
    private void runTick(CallbackInfo ci) {
        this.tickProvider = TickEvent.createNextProvider();

        for (IBaritone baritone : BaritoneAPI.getProvider().getAllBaritones()) {
            TickEvent.Type type = baritone.getPlayerContext().baritonePlayer() != null && baritone.getPlayerContext().world() != null
                    ? TickEvent.Type.IN
                    : TickEvent.Type.OUT;
            baritone.getGameEventHandler().onTick(this.tickProvider.apply(EventState.PRE, type));
        }
    }

    @Inject(
            method = "tickServer",
            at = @At("RETURN")
    )
    private void postRunTick(CallbackInfo ci) {
        if (this.tickProvider == null) {
            return;
        }

        for (IBaritone baritone : BaritoneAPI.getProvider().getAllBaritones()) {
            TickEvent.Type type = baritone.getPlayerContext().baritonePlayer() != null && baritone.getPlayerContext().world() != null
                    ? TickEvent.Type.IN
                    : TickEvent.Type.OUT;
            baritone.getGameEventHandler().onPostTick(this.tickProvider.apply(EventState.POST, type));
        }

        this.tickProvider = null;
    }

    @Inject(
            method = "createLevels",
            at = @At("HEAD")
    )
    private void preLoadWorld(ChunkProgressListener listener, CallbackInfo ci) {
        // Get the primary Baritone instance
       for (IBaritone baritone : BaritoneAPI.getProvider().getAllBaritones()) {
            baritone.getGameEventHandler().onWorldEvent(
                    new WorldEvent(
                            null,
                            EventState.PRE
                    )
            );
        }
    }

    @Inject(
            method = "createLevels",
            at = @At("RETURN")
    )
    private void postLoadWorld(ChunkProgressListener listener, CallbackInfo ci) {
        // Get the primary Baritone instance
       for (IBaritone baritone : BaritoneAPI.getProvider().getAllBaritones()) {
            baritone.getGameEventHandler().onWorldEvent(
                    new WorldEvent(
                            null,
                            EventState.POST
                    )
            );
        }
    }
}
