package dev.luminous.asm.mixins;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.luminous.RiyueClient;
import dev.luminous.api.utils.render.EntityOverlayRenderer;
import dev.luminous.api.utils.render.TextUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.player.Freecam;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RawProjectionMatrix;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.memory.ObjectAllocator;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class MixinWorldRenderer {
    @Unique
    private static RawProjectionMatrix riyueWorldProjection;

    @Unique
    private static RawProjectionMatrix riyueWorldProjection() {
        if (riyueWorldProjection == null) {
            riyueWorldProjection = new RawProjectionMatrix("riyue_world_3d");
        }
        return riyueWorldProjection;
    }

    @Inject(method = "render", at = @At("TAIL"))
    public void render3dHook(
            ObjectAllocator allocator,
            RenderTickCounter tickCounter,
            boolean renderBlockOutline,
            Camera camera,
            Matrix4f positionMatrix,
            Matrix4f basicProjectionMatrix,
            Matrix4f projectionMatrix,
            GpuBufferSlice fogBuffer,
            Vector4f fogColor,
            boolean renderSky,
            CallbackInfo ci) {
        if (RiyueClient.MODULE == null || RiyueClient.FPS == null || Module.nullCheck()) {
            return;
        }
        MatrixStack matrices = new MatrixStack();
        matrices.multiplyPositionMatrix(positionMatrix);

        TextUtil.lastProjMat.set(basicProjectionMatrix);
        TextUtil.lastModMat.set(RenderSystem.getModelViewMatrix());
        TextUtil.lastWorldSpaceMatrix.set(matrices.peek().getPositionMatrix());

        RiyueClient.FPS.record();

        RenderSystem.backupProjectionMatrix();
        try {
            RenderSystem.setProjectionMatrix(riyueWorldProjection().set(basicProjectionMatrix), ProjectionType.PERSPECTIVE);
            if (RiyueClient.MODULE.render3DQueue(matrices)) {
                EntityOverlayRenderer.flushThroughWall();
            }
            RiyueClient.MODULE.render3D(matrices);
        } finally {
            RenderSystem.restoreProjectionMatrix();
        }
    }

    @ModifyArg(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/WorldRenderer;updateCamera(Lnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/Frustum;Z)V"
            ),
            index = 2
    )
    private boolean freecamSpectator(boolean spectator) {
        return Freecam.INSTANCE != null && Freecam.INSTANCE.isOn() || spectator;
    }
}
