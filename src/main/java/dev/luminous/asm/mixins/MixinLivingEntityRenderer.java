package dev.luminous.asm.mixins;

import dev.luminous.core.impl.RotationManager;
import dev.luminous.mod.modules.impl.client.ClientSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRenderer<T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<? super S>> {

    @Unique
    private boolean riyue$rotating;
    @Unique
    private float riyue$originalYaw;
    @Unique
    private float riyue$originalHeadYaw;
    @Unique
    private float riyue$originalBodyYaw;
    @Unique
    private float riyue$originalPitch;
    @Unique
    private float riyue$originalLastYaw;
    @Unique
    private float riyue$originalLastHeadYaw;
    @Unique
    private float riyue$originalLastBodyYaw;
    @Unique
    private float riyue$originalLastPitch;

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V", at = @At("HEAD"))
    private void riyue$beforeUpdateRenderState(LivingEntity entity, LivingEntityRenderState state, float tickProgress, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        this.riyue$rotating = mc.player != null && entity == mc.player && ClientSetting.INSTANCE.rotations.getValue();
        if (!this.riyue$rotating) {
            return;
        }

        this.riyue$originalYaw = entity.getYaw();
        this.riyue$originalHeadYaw = entity.headYaw;
        this.riyue$originalBodyYaw = entity.bodyYaw;
        this.riyue$originalPitch = entity.getPitch();
        this.riyue$originalLastYaw = entity.lastYaw;
        this.riyue$originalLastHeadYaw = entity.lastHeadYaw;
        this.riyue$originalLastBodyYaw = entity.lastBodyYaw;
        this.riyue$originalLastPitch = entity.lastPitch;

        entity.setYaw(RotationManager.getRenderYawOffset());
        entity.headYaw = RotationManager.getRotationYawHead();
        entity.bodyYaw = RotationManager.getRenderYawOffset();
        entity.setPitch(RotationManager.getRenderPitch());
        entity.lastYaw = RotationManager.getPrevRenderYawOffset();
        entity.lastHeadYaw = RotationManager.getPrevRotationYawHead();
        entity.lastBodyYaw = RotationManager.getPrevRenderYawOffset();
        entity.lastPitch = RotationManager.getPrevRenderPitch();
    }

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"))
    private void riyue$afterUpdateRenderState(LivingEntity entity, LivingEntityRenderState state, float tickProgress, CallbackInfo ci) {
        if (!this.riyue$rotating) {
            return;
        }
        this.riyue$rotating = false;

        entity.setYaw(this.riyue$originalYaw);
        entity.headYaw = this.riyue$originalHeadYaw;
        entity.bodyYaw = this.riyue$originalBodyYaw;
        entity.setPitch(this.riyue$originalPitch);
        entity.lastYaw = this.riyue$originalLastYaw;
        entity.lastHeadYaw = this.riyue$originalLastHeadYaw;
        entity.lastBodyYaw = this.riyue$originalLastBodyYaw;
        entity.lastPitch = this.riyue$originalLastPitch;
    }
}
