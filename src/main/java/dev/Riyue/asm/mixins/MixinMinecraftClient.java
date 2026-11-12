package dev.Riyue.asm.mixins;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.Riyue.RiyueClient;
import dev.Riyue.api.events.Event;
import dev.Riyue.api.events.impl.*;
import dev.Riyue.core.impl.CommandManager;
import dev.Riyue.core.impl.FontManager;
import dev.Riyue.mod.modules.impl.client.ClientSetting;
import dev.Riyue.mod.modules.impl.player.InteractTweaks;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.RunArgs;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.util.Window;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.thread.ReentrantThreadExecutor;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class MixinMinecraftClient extends ReentrantThreadExecutor<Runnable> {
    @Shadow
    @Final
    public InGameHud inGameHud;
    @Shadow
    public int attackCooldown;
    @Shadow
    public ClientPlayerEntity player;
    @Shadow
    public HitResult crosshairTarget;
    @Shadow
    public ClientPlayerInteractionManager interactionManager;
    @Final
    @Shadow
    public ParticleManager particleManager;
    @Shadow
    public ClientWorld world;
    @Shadow
    private IntegratedServer server;

    public MixinMinecraftClient(String string) {
        super(string);
    }

    @Shadow
    @Final
    private Window window;

    @Inject(method = "onResolutionChanged", at = @At("TAIL"))
    private void captureResize(CallbackInfo ci) {
        RiyueClient.EVENT_BUS.post(new ResizeEvent(this.window));
    }

    /**
     * 三态缓存：null = 尚未检测，true = AMD/ATI OpenGL 驱动，false = 其他驱动或检测失败。
     * 只检测一次，避免每帧调用 glGetString。
     */
    @Unique
    private static Boolean riyue$amdGlDriver;

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;limitDisplayFPS(I)V"), require = 0)
    public void fpsHook(int fps) {
        if (riyue$amdGlDriver == null) {
            try {
                boolean amd = RiyueClient.isAmdGlDriver(
                        GL11.glGetString(GL11.GL_VENDOR),
                        GL11.glGetString(GL11.GL_RENDERER));
                riyue$amdGlDriver = amd;
                if (amd) {
                    // AMD 的 atio6axx.dll OpenGL ICD 在无限帧率、单帧 UBO 暴涨时会触发
                    // EXCEPTION_ACCESS_VIOLATION native 崩溃；命中时强制保留原版帧率限制，
                    // 即使老配置文件里仍存着 FuckFPSLimit=true 也一样。
                    System.out.println("[RiyueClient] AMD/ATI OpenGL driver detected: keeping vanilla FPS limit "
                            + "to avoid atio6axx.dll native crash.");
                }
            } catch (Throwable ignored) {
                // 检测失败时完全不干预用户设置，避免引入任何新的启动 / 渲染期风险。
                riyue$amdGlDriver = Boolean.FALSE;
            }
        }

        // ClientSetting.INSTANCE 可能为 null（初始化早期），此时保守地保留帧率限制。
        boolean keepLimit = riyue$amdGlDriver
                || ClientSetting.INSTANCE == null
                || !ClientSetting.INSTANCE.fuckFPSLimit.getValue();
        if (keepLimit) {
            RenderSystem.limitDisplayFPS(fps);
        }
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    void postWindowInit(RunArgs args, CallbackInfo ci) {
        // 启动关键路径：字体初始化失败不能阻断后续重量级管理器的延迟初始化，
        // 否则 deferredInit 里的各 Manager 全为 null，进世界后会连锁 NPE。
        try {
            FontManager.init();
        } catch (Throwable t) {
            RiyueClient.rethrowFatal(t);
            System.err.println("[RiyueClient] FontManager init failed (ignored): " + t);
        }
        RiyueClient.deferredInit();
    }

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void onSetScreen(Screen screen, CallbackInfo info) {
        OpenScreenEvent event = OpenScreenEvent.get(screen);
        RiyueClient.EVENT_BUS.post(event);

        if (event.isCancelled()) info.cancel();
    }

    @Inject(method = {"doAttack"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/util/hit/HitResult;getType()Lnet/minecraft/util/hit/HitResult$Type;", shift = At.Shift.BEFORE))
    public void onAttack(CallbackInfoReturnable<Boolean> cir) {
        RiyueClient.EVENT_BUS.post(DoAttackEvent.getPre());
    }

    @Inject(method = {"doAttack"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;swingHand(Lnet/minecraft/util/Hand;)V", shift = At.Shift.AFTER))
    public void onAttackPost(CallbackInfoReturnable<Boolean> cir) {
        RiyueClient.EVENT_BUS.post(DoAttackEvent.getPost());
    }

    @Inject(method = "disconnect(Lnet/minecraft/text/Text;)V", at = @At("HEAD"))
    private void onGameLeave(Text reasonText, CallbackInfo ci) {
        if (world != null) {
            RiyueClient.EVENT_BUS.post(GameLeftEvent.INSTANCE);
        }
        if (ClientSetting.INSTANCE.titleFix.getValue()) {
            inGameHud.clearTitle();
            inGameHud.setDefaultTitleFade();
        }
    }

    @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void handleBlockBreaking(boolean breaking, CallbackInfo ci) {
        if (this.attackCooldown <= 0 && this.player.isUsingItem() && InteractTweaks.INSTANCE.multiTask()) {
            if (breaking && this.crosshairTarget != null && this.crosshairTarget.getType() == HitResult.Type.BLOCK) {
                BlockHitResult blockHitResult = (BlockHitResult) this.crosshairTarget;
                BlockPos blockPos = blockHitResult.getBlockPos();
                if (!this.world.getBlockState(blockPos).isAir()) {
                    Direction direction = blockHitResult.getSide();
                    if (this.interactionManager.updateBlockBreakingProgress(blockPos, direction)) {
                        this.world.spawnBlockBreakingParticle(blockPos, direction);
                        this.player.swingHand(Hand.MAIN_HAND);
                    }
                }
            } else {
                this.interactionManager.cancelBlockBreaking();
            }
            ci.cancel();
        }
    }

    @Inject(at = @At("HEAD"), method = "tick()V")
    public void tickHead(CallbackInfo info) {
        try {
            RiyueClient.EVENT_BUS.post(ClientTickEvent.get(Event.Stage.Pre));
        } catch (Throwable e) {
            // tick() 每帧都分发事件；catch(Exception) 兜不住 AbstractMethodError 等 Error，
            // 任何一个坏模块都会直接崩穿主循环。EventBus 内部另有逐个订阅者的隔离，这里是第二道防线。
            RiyueClient.rethrowFatal(e);
            e.printStackTrace();
            if (ClientSetting.INSTANCE != null && ClientSetting.INSTANCE.debug.getValue())
                CommandManager.sendMessage("§4An error has occurred (MinecraftClient.tick() [HEAD]) Message: [" + e.getMessage() + "]");
        }
    }

    @Inject(at = @At("TAIL"), method = "tick()V")
    public void tickTail(CallbackInfo info) {
        try {
            RiyueClient.EVENT_BUS.post(ClientTickEvent.get(Event.Stage.Post));
        } catch (Throwable e) {
            RiyueClient.rethrowFatal(e);
            e.printStackTrace();
            if (ClientSetting.INSTANCE != null && ClientSetting.INSTANCE.debug.getValue())
                CommandManager.sendMessage("§4An error has occurred (MinecraftClient.tick() [TAIL]) Message: [" + e.getMessage() + "]");
        }
    }

    /**
     * @author me
     * @reason title
     */
    @Overwrite
    private String getWindowTitle() {
        if (ClientSetting.INSTANCE == null) {
            return RiyueClient.NAME + ": Loading..";
        }
        if (ClientSetting.INSTANCE.titleOverride.getValue()) {
            return ClientSetting.INSTANCE.windowTitle.getValue();
        }
        StringBuilder stringBuilder = new StringBuilder(ClientSetting.INSTANCE.windowTitle.getValue());

        stringBuilder.append(" ");
        stringBuilder.append(SharedConstants.getGameVersion().name());

        ClientPlayNetworkHandler clientPlayNetworkHandler = this.getNetworkHandler();
        if (clientPlayNetworkHandler != null && clientPlayNetworkHandler.getConnection().isOpen()) {
            stringBuilder.append(" - ");
            ServerInfo serverInfo = this.getCurrentServerEntry();
            if (this.server != null && !this.server.isRemote()) {
                stringBuilder.append(I18n.translate("title.singleplayer"));
            } else if (serverInfo != null && serverInfo.isRealm()) {
                stringBuilder.append(I18n.translate("title.multiplayer.realms"));
            } else if (this.server == null && (serverInfo == null || !serverInfo.isLocal())) {
                stringBuilder.append(I18n.translate("title.multiplayer.other"));
            } else {
                stringBuilder.append(I18n.translate("title.multiplayer.lan"));
            }
        }

        return stringBuilder.toString();
    }

    @Shadow
    public ClientPlayNetworkHandler getNetworkHandler() {
        return null;
    }

    @Shadow
    public ServerInfo getCurrentServerEntry() {
        return null;
    }
}
