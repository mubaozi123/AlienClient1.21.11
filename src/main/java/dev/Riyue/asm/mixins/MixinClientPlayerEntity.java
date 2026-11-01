package dev.Riyue.asm.mixins;

import com.mojang.authlib.GameProfile;
import dev.Riyue.RiyueClient;
import dev.Riyue.api.events.Event;
import dev.Riyue.api.events.impl.*;
import dev.Riyue.api.utils.world.InteractUtil;
import dev.Riyue.asm.accessors.IClientPlayerEntity;
import dev.Riyue.core.impl.CommandManager;
import dev.Riyue.core.impl.RotationManager;
import dev.Riyue.mod.modules.impl.client.AntiCheat;
import dev.Riyue.mod.modules.impl.client.ClientSetting;
import dev.Riyue.mod.modules.impl.exploit.PacketControl;
import dev.Riyue.mod.modules.impl.movement.NoSlow;
import dev.Riyue.mod.modules.impl.movement.Velocity;
import dev.Riyue.mod.modules.impl.player.Freecam;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerEntity.class)
public abstract class MixinClientPlayerEntity extends AbstractClientPlayerEntity {

    @Shadow
    public Input input;
    @Final
    @Shadow
    protected MinecraftClient client;
    @Shadow
    private int ticksSinceLastPositionPacketSent;
    @Shadow
    private double lastXClient;
    @Shadow
    private double lastYClient;
    @Shadow
    private double lastZClient;
    @Shadow
    private float lastYawClient;
    @Shadow
    private float lastPitchClient;

    @Unique
    private float riyue$preYaw;
    @Unique
    private float riyue$prePitch;
    @Unique
    private boolean riyue$rotating;

    public MixinClientPlayerEntity(ClientWorld world, GameProfile profile) {
        super(world, profile);
    }

    @Inject(
            method = "getCrosshairTarget(FLnet/minecraft/entity/Entity;)Lnet/minecraft/util/hit/HitResult;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void freecamCrosshairTarget(float tickProgress, Entity cameraEntity, CallbackInfoReturnable<HitResult> cir) {
        if (Freecam.INSTANCE != null && Freecam.INSTANCE.isOn()) {
            cir.setReturnValue(InteractUtil.getRtxTarget(
                    Freecam.INSTANCE.getFakeYaw(),
                    Freecam.INSTANCE.getFakePitch(),
                    Freecam.INSTANCE.getFakeX(),
                    Freecam.INSTANCE.getFakeY(),
                    Freecam.INSTANCE.getFakeZ()
            ));
        }
    }

    @Inject(method = "pushOutOfBlocks",
            at = @At("HEAD"),
            cancellable = true)
    private void onPushOutOfBlocksHook(double x, double d, CallbackInfo info) {
        if (Velocity.INSTANCE.isOn() && Velocity.INSTANCE.blockPush.getValue()) {
            info.cancel();
        }
    }

    @Redirect(
            method = "applyMovementSpeedFactors",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z"),
            require = 0
    )
    private boolean tickMovementHook(ClientPlayerEntity player) {
        if (NoSlow.INSTANCE.noSlow()) {
            return false;
        }
        return player.isUsingItem();
    }



    @Inject(at = {@At("HEAD")}, method = {"tickNausea"}, cancellable = true)
    private void updateNausea(CallbackInfo ci) {
        if (ClientSetting.INSTANCE.portalGui()) {
            ci.cancel();
        }
    }

    @Inject(method = "move", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;move(Lnet/minecraft/entity/MovementType;Lnet/minecraft/util/math/Vec3d;)V"), cancellable = true)
    public void onMoveHook(MovementType movementType, Vec3d movement, CallbackInfo ci) {
        MoveEvent event = MoveEvent.get(movement.x, movement.y, movement.z);
        RiyueClient.EVENT_BUS.post(event);
        if (event.isCancelled()) {
            ci.cancel();
        } else if (event.modify) {
            ci.cancel();
            super.move(movementType, new Vec3d(event.getX(), event.getY(), event.getZ()));
            RiyueClient.EVENT_BUS.post(MovedEvent.INSTANCE);
        }
    }

    @Inject(method = "move", at = @At(value = "TAIL"))
    public void onMoveReturnHook(MovementType movementType, Vec3d movement, CallbackInfo ci) {
        RiyueClient.EVENT_BUS.post(MovedEvent.INSTANCE);
    }

    @Shadow
    public abstract float getPitch(float tickDelta);

    @Shadow
    public abstract float getYaw(float tickDelta);

    @Inject(method = "tick",
            at = @At(value = "HEAD"))
    private void tickHead(CallbackInfo ci) {
        try {
            RiyueClient.EVENT_BUS.post(TickEvent.get(Event.Stage.Pre));
        } catch (Exception e) {
            e.printStackTrace();
            if (ClientSetting.INSTANCE.debug.getValue())
                CommandManager.sendMessage("§4An error has occurred (ClientPlayerEntity.tick() [HEAD]) Message: [" + e.getMessage() + "]");
        }
    }

    @Inject(method = "tick",
            at = @At(value = "RETURN"))
    private void tickReturn(CallbackInfo ci) {
        try {
            RiyueClient.EVENT_BUS.post(TickEvent.get(Event.Stage.Post));
        } catch (Exception e) {
            e.printStackTrace();
            if (ClientSetting.INSTANCE.debug.getValue())
                CommandManager.sendMessage("§4An error has occurred (ClientPlayerEntity.tick() [RETURN]) Message: [" + e.getMessage() + "]");
        }
    }

    @Inject(method = "sendMovementPackets", at = @At("HEAD"))
    private void onSendMovementPacketsHead(CallbackInfo info) {
        this.riyue$applyRotation();

        if (PacketControl.INSTANCE.isOn() && PacketControl.INSTANCE.positionSync.getValue()) {
            if (this.ticksSinceLastPositionPacketSent >= PacketControl.INSTANCE.positionDelay.getValueInt() - 1) {
                ((IClientPlayerEntity) this).setTicksSinceLastPositionPacketSent(50);
            }
        }
        if (RotationManager.snapBack) {
            ((IClientPlayerEntity) this).setTicksSinceLastPositionPacketSent(50);
            ((IClientPlayerEntity) this).setLastYaw(999);
            RotationManager.snapBack = false;
            return;
        }
        if (AntiCheat.INSTANCE.fullPackets.getValue()) {
            double d = this.getX() - this.lastXClient;
            double e = this.getY() - this.lastYClient;
            double f = this.getZ() - this.lastZClient;
            double g = this.getYaw() - this.lastYawClient;
            double h = this.getPitch() - this.lastPitchClient;
            boolean bl3 = g != 0.0 || h != 0.0;
            if (AntiCheat.INSTANCE.force.getValue() || !(MathHelper.squaredMagnitude(d, e, f) > MathHelper.square(2.0E-4)) && this.ticksSinceLastPositionPacketSent >= 19 || bl3) {
                ((IClientPlayerEntity) this).setTicksSinceLastPositionPacketSent(50);
                ((IClientPlayerEntity) this).setLastYaw(999);
            }
        }
    }

    @Inject(method = "sendMovementPackets", at = @At("TAIL"))
    private void onSendMovementPacketsTail(CallbackInfo info) {
        this.riyue$restoreRotation();
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getYaw()F"))
    private float riyue$redirectVehicleYaw(ClientPlayerEntity instance) {
        if (instance.hasVehicle()) {
            this.riyue$applyRotation();
        }
        return instance.getYaw();
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayNetworkHandler;sendPacket(Lnet/minecraft/network/packet/Packet;)V", ordinal = 1, shift = At.Shift.AFTER))
    private void onTickVehicleAfterSendPackets(CallbackInfo info) {
        this.riyue$restoreRotation();
    }

    @Unique
    private void riyue$applyRotation() {
        if (this.riyue$rotating) {
            return;
        }

        float currentYaw = this.getYaw();
        float currentPitch = this.getPitch();
        SendMovementPacketsEvent event = SendMovementPacketsEvent.get(currentYaw, currentPitch);
        RiyueClient.EVENT_BUS.post(event);

        RiyueClient.ROTATION.rotationYaw = event.getYaw();
        RiyueClient.ROTATION.rotationPitch = event.getPitch();
        if (Float.compare(currentYaw, event.getYaw()) == 0 && Float.compare(currentPitch, event.getPitch()) == 0) {
            return;
        }

        this.riyue$preYaw = currentYaw;
        this.riyue$prePitch = currentPitch;
        this.riyue$rotating = true;
        this.setYaw(event.getYaw());
        this.setPitch(event.getPitch());
    }

    @Unique
    private void riyue$restoreRotation() {
        if (this.riyue$rotating) {
            this.setYaw(this.riyue$preYaw);
            this.setPitch(this.riyue$prePitch);
            this.riyue$rotating = false;
        }
    }

    @Inject(method = "tickMovement", at = @At("HEAD"))
    private void tickMovement(CallbackInfo ci) {
        RiyueClient.EVENT_BUS.post(TickMovementEvent.INSTANCE);
    }
}
