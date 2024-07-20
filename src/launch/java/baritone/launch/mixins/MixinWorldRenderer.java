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
import baritone.api.event.events.RenderEvent;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

/**
 * @author Brady
 * @since 2/13/2020
 */
@Mixin(LevelRenderer.class)
public class MixinWorldRenderer {

    @Inject(
            method = "renderLevel",
            at = @At("RETURN"),
            locals = LocalCapture.CAPTURE_FAILSOFT
    )
    private void onStartHand(DeltaTracker arg, boolean bl, Camera arg2, GameRenderer arg3, LightTexture arg4, Matrix4f matrix4f, Matrix4f matrix4f2, CallbackInfo ci) {
        //PoseStack matrixStackIn, float partialTicks, long finishTimeNano, boolean drawBlockOutline, Camera activeRenderInfoIn, GameRenderer gameRendererIn, LightTexture lightmapIn, Matrix4f projectionIn, CallbackInfo ci
        for (IBaritone ibaritone : BaritoneAPI.getProvider().getAllBaritones()) {
            // Assuming RenderEvent can take partialTicks and matrixStackIn arguments
            ibaritone.getGameEventHandler().onRenderPass(new RenderEvent(arg.getGameTimeDeltaPartialTick(true), new PoseStack(), matrix4f));
        }
    }
}