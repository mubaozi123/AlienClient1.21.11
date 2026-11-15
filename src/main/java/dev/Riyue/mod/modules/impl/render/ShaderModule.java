package dev.Riyue.mod.modules.impl.render;

import dev.Riyue.RiyueClient;
import dev.Riyue.api.events.eventbus.EventListener;
import dev.Riyue.api.events.impl.Render3DEvent;
import dev.Riyue.api.events.impl.TickEvent;
import dev.Riyue.asm.accessors.IGameRenderer;
import dev.Riyue.core.impl.ShaderManager;
import dev.Riyue.mod.modules.Module;
import dev.Riyue.mod.modules.impl.player.Freecam;
import dev.Riyue.mod.modules.settings.impl.BooleanSetting;
import dev.Riyue.mod.modules.settings.impl.ColorSetting;
import dev.Riyue.mod.modules.settings.impl.EnumSetting;
import dev.Riyue.mod.modules.settings.impl.SliderSetting;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.FishEntity;
import net.minecraft.entity.passive.SquidEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.entity.projectile.thrown.ExperienceBottleEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Shader / ESP 模块。
 *
 * <p>本文件是 open-zio {@code dev.zio.client.modules.render.ShaderModule} 的完整移植版：
 * 保留 zio 的全部 40+ 设置、默认值、可见性依赖、颜色混合、簇透明度、距离淡出、
 * 目标过滤、手部通道等语义；同时保留仓库1 原有被外部代码引用的公开成员
 * （{@link #INSTANCE}、{@link #shouldRender(Entity)}、{@link #mode}、{@link #speed}、
 * {@link #fill}、{@link #maxSample}、{@link #divider}、{@link #radius}、
 * {@link #smoothness}、{@link #alpha}、{@link #maxRange}、{@link #pulseSpeed}、
 * {@link #pulse}、{@link #step}、{@link #octaves}、{@link #smoke1}..{@link #smoke4}、
 * {@link #hands}、{@link #self}、{@link #players}、{@link #friends}、{@link #crystals}、
 * {@link #creatures}、{@link #monsters}、{@link #ambients}、{@link #items}、{@link #others}），
 * 以及 {@link #getInfo()}、{@link #onRender3D(MatrixStack)}、{@link #onToggle()}、
 * {@link #onLogin()} 等框架回调，名字与签名均未改动。</p>
 *
 * <p>映射说明（Mojang 映射 -> Yarn 映射 / zio 框架 -> dev.Riyue 框架）：
 * <ul>
 *   <li>{@code Setting<T>} -> {@code BooleanSetting / SliderSetting / ColorSetting / EnumSetting}</li>
 *   <li>{@code NumberSetting} 的可见性谓词 / 分组 / configName -> {@code BooleanSupplier} 可见性 + 独立配置键</li>
 *   <li>{@code ClientContext.getModule(...)} -> 静态 {@code INSTANCE} 单例</li>
 *   <li>{@code @EventHandler(WorldRenderEvent.Overlay)} -> {@code @EventListener(Render3DEvent)}</li>
 *   <li>{@code @EventHandler(LocalPlayerTickEvent)} -> {@code @EventListener(TickEvent)}</li>
 *   <li>{@code SubmitNodeCollector / RenderTarget / ShaderRegistry} -> 仓库1 的
 *       {@code isGlowing} 注入 + {@code ShaderManager.renderShader(...)} 渲染管线（按硬约束不照搬 blaze3d）</li>
 *   <li>{@code entity.getBbWidth()} -> {@code entity.getWidth()}；
 *       {@code Enemy} -> {@code Monster}；{@code AgeableMob/AbstractFish/Squid} ->
 *       {@code AnimalEntity/FishEntity/SquidEntity}；{@code ThrownEnderpearl} -> {@code EnderPearlEntity}；
 *       {@code ThrownExperienceBottle} -> {@code ExperienceBottleEntity}；
 *       {@code AbstractMinecart} -> {@code AbstractMinecartEntity}</li>
 * </ul>
 * </p>
 */
public class ShaderModule extends Module {

    public static ShaderModule INSTANCE;

    /* =====================================================================
     * 仓库1 原有设置 —— 外部引用（ShaderManager / MixinEntity）依赖，禁止改名/改类型
     * ===================================================================== */
    private final EnumSetting<Page> page = this.add(new EnumSetting<Page>("Page", Page.Shader));
    public final EnumSetting<ShaderManager.Shader> mode = this.add(new EnumSetting<ShaderManager.Shader>("Mode", ShaderManager.Shader.Solid, () -> this.page.getValue() == Page.Shader));
    public final SliderSetting speed = this.add(new SliderSetting("Speed", 4.0, 0.0, 20.0, 0.1, () -> this.page.getValue() == Page.Shader));
    public final ColorSetting fill = this.add(new ColorSetting("Color", () -> this.page.getValue() == Page.Shader));
    public final SliderSetting maxSample = this.add(new SliderSetting("MaxSample", 10.0, 0.0, 20.0, () -> this.page.getValue() == Page.Shader));
    public final SliderSetting divider = this.add(new SliderSetting("Divider", 150.0, 0.0, 300.0, () -> this.page.getValue() == Page.Shader));
    public final SliderSetting radius = this.add(new SliderSetting("Radius", 2.0, 0.0, 6.0, () -> this.page.getValue() == Page.Shader));
    public final SliderSetting smoothness = this.add(new SliderSetting("Smoothness", 1.0, 0.0, 1.0, 0.01, () -> this.page.getValue() == Page.Shader));
    public final SliderSetting alpha = this.add(new SliderSetting("GlowAlpha", 255, 0, 255, () -> this.page.getValue() == Page.Shader));
    public final SliderSetting maxRange = this.add(new SliderSetting("MaxRange", 64, 16, 512, () -> this.page.getValue() == Page.Target));
    public final SliderSetting pulseSpeed = this.add(new SliderSetting("PulseSize", 10.0, 0.0, 200.0, 0.1, () -> this.page.getValue() == Page.Color));
    public final ColorSetting pulse = this.add(new ColorSetting("Pulse", new Color(255, 255, 255), () -> this.page.getValue() == Page.Color));
    public final SliderSetting step = this.add(new SliderSetting("Step", 0.5, 0.0, 4.0, 0.01f, () -> this.page.getValue() == Page.Color));
    public final SliderSetting octaves = this.add(new SliderSetting("Octaves", 10, 5, 30, () -> this.page.getValue() == Page.Color));
    public final ColorSetting smoke1 = this.add(new ColorSetting("Color1", new Color(255, 255, 255), () -> this.page.getValue() == Page.Color));
    public final ColorSetting smoke2 = this.add(new ColorSetting("Color2", new Color(255, 255, 255), () -> this.page.getValue() == Page.Color));
    public final ColorSetting smoke3 = this.add(new ColorSetting("Color3", new Color(255, 255, 255), () -> this.page.getValue() == Page.Color));
    public final ColorSetting smoke4 = this.add(new ColorSetting("Color4", new Color(255, 255, 255), () -> this.page.getValue() == Page.Color));
    private final BooleanSetting hands = this.add(new BooleanSetting("Hands", true, () -> this.page.getValue() == Page.Target));
    private final BooleanSetting self = this.add(new BooleanSetting("Self", true, () -> this.page.getValue() == Page.Target));
    private final BooleanSetting players = this.add(new BooleanSetting("Players", true, () -> this.page.getValue() == Page.Target));
    private final BooleanSetting friends = this.add(new BooleanSetting("Friends", true, () -> this.page.getValue() == Page.Target));
    private final BooleanSetting crystals = this.add(new BooleanSetting("Crystals", true, () -> this.page.getValue() == Page.Target));
    private final BooleanSetting creatures = this.add(new BooleanSetting("Creatures", false, () -> this.page.getValue() == Page.Target));
    private final BooleanSetting monsters = this.add(new BooleanSetting("Monsters", false, () -> this.page.getValue() == Page.Target));
    private final BooleanSetting ambients = this.add(new BooleanSetting("Ambients", false, () -> this.page.getValue() == Page.Target));
    private final BooleanSetting items = this.add(new BooleanSetting("Items", true, () -> this.page.getValue() == Page.Target));
    private final BooleanSetting others = this.add(new BooleanSetting("Others", false, () -> this.page.getValue() == Page.Target));

    /* =====================================================================
     * open-zio ShaderModule 设置 —— 完整移植
     *
     * 说明：zio 使用 setConfigName("GradientSpeed") 等配置别名，仓库1 的
     * ConfigManager 以 setting.getName() 作为配置键，因此这里直接使用 zio 的
     * configName 作为显示名（同时避免与仓库1 原有 "Speed/Step/Radius/..." 键冲突）。
     * ===================================================================== */

    // ---- 主参数页 ----
    public final EnumSetting<ShaderChoice> shaderSetting =
            this.add(new EnumSetting<>("ZioShader", ShaderChoice.SOLID, this::onZioPage));
    public final SliderSetting rangeSetting =
            this.add(new SliderSetting("ZioRange", 60, 1, 300, this::onZioPage));
    public final SliderSetting fadeSetting =
            this.add(new SliderSetting("Fade", 100.0, 25.0, 100.0,
                    () -> onZioPage() && this.shaderSetting.getValue() != ShaderChoice.SOLID));
    public final SliderSetting lineWidthSetting =
            this.add(new SliderSetting("LineWidth", 1, 0, 10, this::onZioPage));
    public final BooleanSetting fastLinesSetting =
            this.add(new BooleanSetting("FastLines", false,
                    () -> onZioPage() && this.lineWidthSetting.getValue() > 2));
    public final SliderSetting stepSetting =
            this.add(new SliderSetting("GradientStep", 0.5, 0.1, 2.0, 0.1,
                    () -> onZioPage() && this.shaderSetting.getValue() == ShaderChoice.GRADIENT));
    public final SliderSetting speedSetting =
            this.add(new SliderSetting("GradientSpeed", 1.0, 0.1, 5.0, 0.1,
                    () -> onZioPage() && this.shaderSetting.getValue() == ShaderChoice.GRADIENT));
    public final SliderSetting rainbowSpeedSetting =
            this.add(new SliderSetting("RainbowSpeed", 1.0, 0.0, 5.0, 0.1,
                    () -> onZioPage() && this.shaderSetting.getValue() == ShaderChoice.RAINBOW));
    public final SliderSetting lineStrengthSetting =
            this.add(new SliderSetting("LineStrength", 1.0, 0.0, 5.0, 0.1,
                    () -> onZioPage() && this.shaderSetting.getValue() == ShaderChoice.RAINBOW));
    public final SliderSetting fillStrengthSetting =
            this.add(new SliderSetting("FillStrength", 1.0, 0.0, 5.0, 0.1,
                    () -> onZioPage() && this.shaderSetting.getValue() == ShaderChoice.RAINBOW));
    public final BooleanSetting imageSetting =
            this.add(new BooleanSetting("Image", false, this::onZioPage));
    public final SliderSetting overlayAlphaSetting =
            this.add(new SliderSetting("OverlayAlpha", 1.0, 0.0, 1.0, 0.01,
                    () -> onZioPage() && this.imageSetting.getValue()));
    /**
     * zio 语义总开关：为 true 时 {@code ShaderChoice} 接管管线选择，且 zio 的颜色 / 发光 /
     * 渐变 / 淡出参数<b>无条件</b>参与 UBO 组装；为 false 时全部回落仓库1 原设置
     * （画面 100% 等价改动前，管线选择也交回 {@link #mode}）。
     *
     * <p>zio 原版没有这个开关，它是本移植版提供的可回退兼容路径。注意
     * {@link #colorsSetting} / {@link #glowSetting} / {@link #decoratorSetting} 等分组布尔
     * 在 zio 中只是 {@code markAsGroup()} 的 UI 分组，渲染端没有任何 {@code getValue}
     * 读取，因此<b>不能</b>用它们 gating 效果（见 {@code ZioShaderAdapter.active}）。</p>
     */
    public final BooleanSetting zioMode =
            this.add(new BooleanSetting("ZioMode", true, this::onZioPage));
    /** 为 true 时目标过滤完全使用 zio 语义；默认与仓库1 原有过滤取并集（不破坏原效果）。 */
    public final BooleanSetting zioTargetsOverride =
            this.add(new BooleanSetting("ZioTargetsOverride", false, this::onZioPage));

    // ---- Glow / Decorator 分组 ----
    // zio 里 Glow / Decorator 是 markAsGroup() 的 UI 分组开关，只控制子设置的可见性；
    // quality / weight / inner / type / radius / alpha 在 ShaderPass 中是无条件读取的。
    // 这里保留同样的可见性谓词，但渲染端统一由 ZioMode 判断（见 ZioShaderAdapter.active）。
    public final BooleanSetting glowSetting =
            this.add(new BooleanSetting("Glow", false,
                    () -> onGlowPage() && this.lineWidthSetting.getValue() > 0));
    public final SliderSetting qualitySetting =
            this.add(new SliderSetting("Quality", 1, 1, 6,
                    () -> onGlowPage() && this.glowSetting.getValue()));
    public final SliderSetting weightSetting =
            this.add(new SliderSetting("Weight", 1.0, 0.0, 10.0, 0.1,
                    () -> onGlowPage() && this.glowSetting.getValue()));
    public final BooleanSetting innerSetting =
            this.add(new BooleanSetting("Inner", true,
                    () -> onGlowPage() && this.glowSetting.getValue()));
    public final BooleanSetting decoratorSetting =
            this.add(new BooleanSetting("Decorator", false, this::onGlowPage));
    public final EnumSetting<TypeChoice> typeSetting =
            this.add(new EnumSetting<>("Type", TypeChoice.NONE,
                    () -> onGlowPage() && this.decoratorSetting.getValue()));
    public final SliderSetting radiusSetting =
            this.add(new SliderSetting("DecoratorRadius", 5, 2, 20,
                    () -> onGlowPage() && this.decoratorSetting.getValue()));
    public final SliderSetting alphaSetting =
            this.add(new SliderSetting("DecoratorAlpha", 1.0, 0.0, 1.0, 0.01,
                    () -> onGlowPage() && this.decoratorSetting.getValue()));

    // ---- Colors 分组 ----
    // zio 里 Colors 是 markAsGroup() 的 UI 分组开关（默认 false，界面上折叠），
    // 但 fill / fillSecond / outline / outlineSecond / smooth 在 SolidShaderPass、
    // GradientShaderPass 里是无条件读取并写入 uniform 的 —— 分组值不参与渲染判定。
    public final BooleanSetting colorsSetting =
            this.add(new BooleanSetting("Colors", false, this::onColorsPage));
    public final BooleanSetting smoothSetting =
            this.add(new BooleanSetting("Smooth", true,
                    () -> onColorsPage() && this.colorsSetting.getValue()));
    public final ColorSetting fillSetting =
            this.add(new ColorSetting("ZioFill", new Color(2025691647, true),
                    () -> onColorsPage() && this.colorsSetting.getValue()));
    public final ColorSetting fillSecondSetting =
            this.add(new ColorSetting("FillSecond", new Color(1936677274, true),
                    () -> onColorsPage() && this.colorsSetting.getValue()
                            && this.shaderSetting.getValue() == ShaderChoice.GRADIENT));
    public final ColorSetting outlineColorSetting =
            this.add(new ColorSetting("ZioOutline", new Color(-4351489, true),
                    () -> onColorsPage() && this.colorsSetting.getValue()));
    public final ColorSetting outlineSecondSetting =
            this.add(new ColorSetting("OutlineSecond", new Color(-8625746, true),
                    () -> onColorsPage() && this.colorsSetting.getValue()
                            && this.shaderSetting.getValue() == ShaderChoice.GRADIENT));

    // ---- Targets 分组 ----
    public final BooleanSetting targetsSetting =
            this.add(new BooleanSetting("Targets", true, this::onTargetsPage));
    public final BooleanSetting optimizedSetting =
            this.add(new BooleanSetting("Optimized", true, this::onTargetsPage));
    public final BooleanSetting handsSetting =
            this.add(new BooleanSetting("ZioHands", true,
                    () -> onTargetsPage() && this.targetsSetting.getValue()));
    public final BooleanSetting handFillSetting =
            this.add(new BooleanSetting("HandsFill", true,
                    () -> onTargetsPage() && this.targetsSetting.getValue()
                            && this.handsSetting.getValue() && !this.optimizedSetting.getValue()));
    public final BooleanSetting outlineSetting =
            this.add(new BooleanSetting("HandsOutline", true,
                    () -> onTargetsPage() && this.targetsSetting.getValue()
                            && this.handsSetting.getValue() && !this.optimizedSetting.getValue()));
    public final BooleanSetting crystalsSetting =
            this.add(new BooleanSetting("ZioCrystals", false,
                    () -> onTargetsPage() && this.targetsSetting.getValue()));
    public final SliderSetting crystalRangeSetting =
            this.add(new SliderSetting("CrystalRange", 60, 1, 300,
                    () -> onTargetsPage() && this.targetsSetting.getValue() && this.crystalsSetting.getValue()));
    public final BooleanSetting itemsSetting =
            this.add(new BooleanSetting("ZioItems", false,
                    () -> onTargetsPage() && this.targetsSetting.getValue()));
    public final SliderSetting itemRangeSetting =
            this.add(new SliderSetting("ItemRange", 60, 1, 300,
                    () -> onTargetsPage() && this.targetsSetting.getValue() && this.itemsSetting.getValue()));
    public final BooleanSetting animalsSetting =
            this.add(new BooleanSetting("Animals", false,
                    () -> onTargetsPage() && this.targetsSetting.getValue()));
    public final BooleanSetting hostilesSetting =
            this.add(new BooleanSetting("Hostiles", false,
                    () -> onTargetsPage() && this.targetsSetting.getValue()));
    public final BooleanSetting playersSetting =
            this.add(new BooleanSetting("ZioPlayers", false,
                    () -> onTargetsPage() && this.targetsSetting.getValue()));
    public final BooleanSetting selfSetting =
            this.add(new BooleanSetting("ZioSelf", false,
                    () -> onTargetsPage() && this.targetsSetting.getValue() && this.playersSetting.getValue()));
    public final BooleanSetting expSetting =
            this.add(new BooleanSetting("Exp", false,
                    () -> onTargetsPage() && this.targetsSetting.getValue()));
    public final BooleanSetting pearlsSetting =
            this.add(new BooleanSetting("Pearls", false,
                    () -> onTargetsPage() && this.targetsSetting.getValue()));
    public final BooleanSetting minecartsSetting =
            this.add(new BooleanSetting("Minecarts", false,
                    () -> onTargetsPage() && this.targetsSetting.getValue()));
    public final BooleanSetting deadSetting =
            this.add(new BooleanSetting("Dead", false,
                    () -> onTargetsPage() && this.targetsSetting.getValue()));
    public final BooleanSetting nameTagsSetting =
            this.add(new BooleanSetting("NameTags", false,
                    () -> onTargetsPage() && this.targetsSetting.getValue()));
    /** 仓库1 的 NoRender 没有 zio 的 NoCluster / ClusterAlpha，这里给出等价设置。 */
    public final BooleanSetting noClusterSetting =
            this.add(new BooleanSetting("ZioNoCluster", false, this::onTargetsPage));
    public final SliderSetting clusterAlphaSetting =
            this.add(new SliderSetting("ZioClusterAlpha", 120, 0, 255,
                    () -> onTargetsPage() && this.noClusterSetting.getValue()));

    // ---- zio 运行时状态 ----
    private final List<RenderTargetEntry> renderTargets = new ArrayList<>(128);
    private volatile float minimumClusterAlpha = 1.0f;

    public ShaderModule() {
        super("Shader", Module.Category.Render);
        this.setChinese("\u7740\u8272\u5668");
        INSTANCE = this;
    }

    private boolean onZioPage() {
        return this.page.getValue() == Page.Zio;
    }

    private boolean onGlowPage() {
        return this.page.getValue() == Page.ZioGlow;
    }

    private boolean onColorsPage() {
        return this.page.getValue() == Page.ZioColors;
    }

    private boolean onTargetsPage() {
        return this.page.getValue() == Page.ZioTargets;
    }

    /* =====================================================================
     * 框架回调
     * ===================================================================== */

    @Override
    public String getInfo() {
        return this.zioMode.getValue()
                ? this.shaderSetting.getValue().getName()
                : this.mode.getValue().name();
    }

    /**
     * 由 MixinEntity.isGlowingHook 调用 —— 保留原有签名与语义。
     * 默认使用「仓库1 原过滤 || zio 过滤」的并集，保证原有效果不被破坏；
     * 打开 ZioTargetsOverride 后完全使用 zio 的目标过滤语义。
     */
    public boolean shouldRender(Entity entity) {
        boolean legacy = legacyShouldRender(entity);
        if (this.zioTargetsOverride.getValue()) {
            return isZioShaderTarget(entity);
        }
        return legacy || isZioShaderTarget(entity);
    }

    /** 仓库1 原始 shouldRender 的完整等价实现。 */
    private boolean legacyShouldRender(Entity entity) {
        if (entity == null) {
            return false;
        }
        if (mc.player == null) {
            return false;
        }
        if ((double) MathHelper.sqrt((float) mc.player.squaredDistanceTo(entity.getEntityPos())) > this.maxRange.getValue()) {
            return false;
        }
        if (entity instanceof PlayerEntity) {
            PlayerEntity playerEntity = (PlayerEntity) entity;
            if (entity == mc.player) {
                return this.self.getValue();
            }
            if (RiyueClient.FRIEND != null && RiyueClient.FRIEND.isFriend(playerEntity)) {
                return this.friends.getValue();
            }
            return this.players.getValue();
        }
        if (entity instanceof EndCrystalEntity) {
            return this.crystals.getValue();
        }
        if (entity instanceof ItemEntity) {
            return this.items.getValue();
        }
        return switch (entity.getType().getSpawnGroup()) {
            case CREATURE, WATER_CREATURE -> this.creatures.getValue();
            case MONSTER -> this.monsters.getValue();
            case AMBIENT, WATER_AMBIENT -> this.ambients.getValue();
            default -> this.others.getValue();
        };
    }

    @Override
    public void onRender3D(MatrixStack matrixStack) {
        ShaderManager manager = RiyueClient.SHADER;
        if (manager == null) return;
        if (!this.hands.getValue() && !this.handsSetting.getValue()) return;
        ShaderManager.Shader pipeline = this.zioMode.getValue()
                ? mapZioShader(this.shaderSetting.getValue())
                : this.mode.getValue();
        Runnable draw = () -> {
            boolean previous = manager.renderingHands;
            // zio：Optimized=true 时手部与主通道共用一次绘制；false 时单独的 hands 通道。
            manager.renderingHands = !this.optimizedSetting.getValue();
            try {
                ((IGameRenderer) mc.gameRenderer).IRenderHand(
                        mc.getRenderTickCounter().getTickProgress(true), false,
                        matrixStack.peek().getPositionMatrix());
            } finally {
                manager.renderingHands = previous;
            }
        };
        manager.renderShader(draw, pipeline);
    }

    @Override
    public void onToggle() {
        if (RiyueClient.SHADER == null) return;
        RiyueClient.SHADER.reloadShaders();
    }

    @Override
    public void onLogin() {
        if (RiyueClient.SHADER == null) return;
        RiyueClient.SHADER.reloadShaders();
        synchronized (this.renderTargets) {
            this.renderTargets.clear();
        }
        this.minimumClusterAlpha = 1.0f;
    }

    /* =====================================================================
     * zio 运行时语义的等价实现
     * ===================================================================== */

    /** 对应 zio @EventHandler(WorldRenderEvent.Overlay) 中的目标收集 + 距离排序。 */
    @EventListener
    public void onZioRenderFrame(Render3DEvent event) {
        if (this.isOff()) return;
        this.rebuildRenderTargets();
    }

    /** 对应 zio @EventHandler(LocalPlayerTickEvent) 中的 minimumClusterAlpha 计算。 */
    @EventListener
    public void onZioTick(TickEvent event) {
        float min = 1.0f;
        for (RenderTargetEntry target : snapshotTargets()) {
            min = Math.min(min, getTargetClusterAlpha(target));
        }
        this.minimumClusterAlpha = min;
    }

    private void rebuildRenderTargets() {
        synchronized (this.renderTargets) {
            this.renderTargets.clear();
            if (mc.player == null || mc.world == null || mc.gameRenderer == null) return;
            Vec3d cameraPosition = mc.gameRenderer.getCamera().getCameraPos();
            for (Entity entity : mc.world.getEntities()) {
                if (isZioShaderTarget(entity)) {
                    this.renderTargets.add(new RenderTargetEntry(
                            entity, -cameraPosition.squaredDistanceTo(entity.getEntityPos())));
                }
            }
            this.renderTargets.sort(Comparator.comparingDouble(RenderTargetEntry::negativeDistanceSquared));
        }
    }

    /** 渲染线程与逻辑线程之间取快照，避免遍历时发生并发修改。 */
    private List<RenderTargetEntry> snapshotTargets() {
        synchronized (this.renderTargets) {
            return new ArrayList<>(this.renderTargets);
        }
    }

    /** zio ShaderModule.isShaderTarget 的 Yarn 等价实现。 */
    public boolean isZioShaderTarget(Entity entity) {
        if (entity == null || mc.player == null || mc.world == null) {
            return false;
        }
        float range = getEntityRange(entity);
        boolean outsideViewOrRange;
        if (isVisible(entity.getBoundingBox())) {
            outsideViewOrRange = mc.player.distanceTo(entity) > range;
        } else {
            outsideViewOrRange = true;
        }
        if (entity instanceof PlayerEntity player && player.isDead()) {
            return false;
        }
        if (outsideViewOrRange) {
            return false;
        }
        // zio: renderEntity() 中的 dead 过滤，等价下沉到目标过滤（仓库1 使用 isGlowing 钩子）
        if (!entity.isAlive() && entity instanceof net.minecraft.entity.LivingEntity
                && !this.deadSetting.getValue()) {
            return false;
        }
        if (isFirstPersonCameraEntity(entity)) {
            return false;
        }
        boolean detachedView = !mc.options.getPerspective().isFirstPerson();
        if (!detachedView && Freecam.INSTANCE != null) {
            detachedView = Freecam.INSTANCE.isOn();
        }
        if (entity instanceof PlayerEntity) {
            if (detachedView && this.selfSetting.getValue() && entity == mc.player) {
                return this.playersSetting.getValue();
            }
            return this.playersSetting.getValue() && entity != mc.player;
        }
        return (entity instanceof ItemEntity && this.itemsSetting.getValue())
                || (entity instanceof ExperienceBottleEntity && this.expSetting.getValue())
                || ((entity instanceof AnimalEntity || entity instanceof FishEntity
                        || entity instanceof SquidEntity) && this.animalsSetting.getValue())
                || (entity instanceof Monster && this.hostilesSetting.getValue())
                || (entity instanceof EnderPearlEntity && this.pearlsSetting.getValue())
                || (entity instanceof EndCrystalEntity && this.crystalsSetting.getValue())
                || (entity instanceof AbstractMinecartEntity && this.minecartsSetting.getValue());
    }

    /** zio RotationManager.isVisible(AABB) 的等价实现：无 frustum 时返回 true。 */
    private boolean isVisible(Box bounds) {
        try {
            if (mc.worldRenderer == null) return true;
            Frustum frustum = mc.worldRenderer.getCapturedFrustum();
            return frustum == null || frustum.isVisible(bounds);
        } catch (Throwable ignored) {
            return true;
        }
    }

    /** zio EntityUtils.isFirstPersonCameraEntity 的等价实现。 */
    private boolean isFirstPersonCameraEntity(Entity entity) {
        if (!mc.options.getPerspective().isFirstPerson()) return false;
        if (entity == mc.player) return false;
        return mc.getCameraEntity() == entity;
    }

    /** zio ShaderModule.getEntityRange 的等价实现。 */
    public float getEntityRange(Entity entity) {
        if (entity instanceof EndCrystalEntity) {
            return this.crystalRangeSetting.getValueFloat();
        }
        if (entity instanceof ItemEntity) {
            return this.itemRangeSetting.getValueFloat();
        }
        return this.rangeSetting.getValueFloat();
    }

    private float getTargetClusterAlpha(RenderTargetEntry target) {
        Entity entity = target.entity();
        if (entity != null && NoRender.INSTANCE != null && NoRender.INSTANCE.isOn()
                && this.noClusterSetting.getValue() && mc.player != null) {
            double distance = mc.player.distanceTo(entity);
            float entityWidth = entity.getWidth();
            if (entityWidth > 0.0f && distance <= entityWidth && entity != mc.player) {
                float distanceFraction = (float) (distance / entityWidth);
                float clusterAlpha = this.clusterAlphaSetting.getValueInt() / 255.0f;
                return clusterAlpha + distanceFraction * (1.0f - clusterAlpha);
            }
        }
        return 1.0f;
    }

    public float getMinimumClusterAlpha() {
        return this.minimumClusterAlpha;
    }

    /**
     * 对应 zio renderEntity() 中的距离淡出：对所有目标取最小 alpha，
     * 供 ShaderManager 统一乘进 UBO 的 alpha 通道（仓库1 无逐实体颜色通道）。
     */
    public float getMinimumFadeAlpha() {
        if (this.renderTargets.isEmpty() || mc.gameRenderer == null) return 1.0f;
        float fadeStart = this.fadeSetting.getValueFloat() / 100.0f;
        if (fadeStart >= 1.0f) return 1.0f;   // zio 的 Fade="None" 边界
        Vec3d cameraPosition = mc.gameRenderer.getCamera().getCameraPos();
        float minimum = 1.0f;
        for (RenderTargetEntry target : snapshotTargets()) {
            Entity entity = target.entity();
            if (entity == null || entity.isRemoved()) continue;
            float range = getEntityRange(entity);
            if (range <= 0.0f) continue;
            double distanceToCamera = Math.sqrt(cameraPosition.squaredDistanceTo(entity.getEntityPos()));
            float distanceAlpha = (float) (distanceToCamera / range);
            if (distanceAlpha >= fadeStart) {
                float fadeProgress = (distanceAlpha - fadeStart) / (1.0f - fadeStart);
                distanceAlpha = 1.0f - clamp01(fadeProgress);
            } else {
                distanceAlpha = 1.0f;
            }
            minimum = Math.min(minimum, distanceAlpha);
        }
        return minimum;
    }

    private boolean renderingHands() {
        return RiyueClient.SHADER != null && RiyueClient.SHADER.renderingHands;
    }

    /** zio ShaderModule.applyClusterAlpha。 */
    public Color applyClusterAlpha(Color color) {
        float alphaMultiplier = this.renderingHands() ? 1.0f : this.minimumClusterAlpha;
        return withAlpha(color, (int) (alphaMultiplier * color.getAlpha()));
    }

    /** zio ShaderModule.getOverlayAlpha。 */
    public float getOverlayAlpha() {
        if (this.renderingHands()) {
            return this.overlayAlphaSetting.getValueFloat();
        }
        return this.overlayAlphaSetting.getValueFloat() * this.minimumClusterAlpha;
    }

    public boolean shouldSkipHandFill() {
        return this.renderingHands() && !this.handFillSetting.getValue();
    }

    public boolean shouldSkipHandOutline() {
        return this.renderingHands() && !this.outlineSetting.getValue();
    }

    public boolean includesNameTags() {
        return this.isOn() && this.nameTagsSetting.getValue();
    }

    /** zio 的 NameTags 目标 blit 是否生效（仓库1 无独立 nameTag RenderTarget）。 */
    public boolean nameTagsActive() {
        return this.includesNameTags() && NameTags.INSTANCE != null && NameTags.INSTANCE.isOn();
    }

    /** zio ShaderModule.blendSettingColors。 */
    public Color blendSettingColors(ColorSetting firstSetting, ColorSetting secondSetting) {
        return this.blendColorsHsb(firstSetting.getValue(), secondSetting.getValue());
    }

    /** zio ShaderModule.blendColorsHsb，逐行等价移植（HSB 环形插值 + alpha 平均）。 */
    public Color blendColorsHsb(Color firstColor, Color secondColor) {
        float[] firstHsb = Color.RGBtoHSB(firstColor.getRed(), firstColor.getGreen(), firstColor.getBlue(), null);
        float[] secondHsb = Color.RGBtoHSB(secondColor.getRed(), secondColor.getGreen(), secondColor.getBlue(), null);
        float hueDifference = secondHsb[0] - firstHsb[0];
        if (hueDifference > 0.5f) {
            firstHsb[0] += 1.0f;
        } else if (hueDifference < -0.5f) {
            secondHsb[0] += 1.0f;
        }
        float[] hsb = new float[3];
        hsb[0] = firstHsb[0] * 0.5f + secondHsb[0] * 0.5f;
        hsb[1] = firstHsb[1] * 0.5f + secondHsb[1] * 0.5f;
        hsb[2] = firstHsb[2] * 0.5f + secondHsb[2] * 0.5f;
        hsb[0] = (hsb[0] + 1.0f) % 1.0f;
        Color blended = Color.getHSBColor(hsb[0], hsb[1], hsb[2]);
        float firstAlpha = firstColor.getAlpha() / 255.0f;
        float secondAlpha = secondColor.getAlpha() / 255.0f;
        return withAlpha(blended, firstAlpha * 0.5f + secondAlpha * 0.5f);
    }

    /** zio ColorUtils.withAlpha(Color, int) / (Color, float) / transparent 的等价实现。 */
    public static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(),
                Math.max(0, Math.min(255, alpha)));
    }

    public static Color withAlpha(Color color, float alpha) {
        return withAlpha(color, Math.round(alpha * 255.0f));
    }

    public static Color transparent(Color color) {
        return withAlpha(color, 0);
    }

    private static float clamp01(float value) {
        if (Float.isNaN(value)) return 0.0f;
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    /** zio ShaderChoice -> 仓库1 现有 ShaderManager.Shader 管线的等价映射。 */
    public static ShaderManager.Shader mapZioShader(ShaderChoice choice) {
        return switch (choice) {
            case RAINBOW -> ShaderManager.Shader.Rainbow;
            case GRADIENT -> ShaderManager.Shader.Gradient;
            case BLOOM -> ShaderManager.Shader.Smoke;      // 发光/模糊近似
            case GLOWY -> ShaderManager.Shader.Pulse;       // 发光脉冲近似
            case CUSTOM -> ShaderManager.Shader.Flow;       // 程序化自定义近似
            case SKY -> ShaderManager.Shader.Skybox;
            case DOF -> ShaderManager.Shader.Snow;          // 景深模糊近似
            case SOLID, OUTLINE, BLUR_MASK, BLOOM_MASK -> ShaderManager.Shader.Solid;
        };
    }

    private record RenderTargetEntry(Entity entity, double negativeDistanceSquared) {
    }

    /** 仓库1 原有分页 + zio 设置分页（仅用于 UI 可见性，不影响配置兼容）。 */
    private enum Page {
        Shader,
        Target,
        Color,
        Zio,
        ZioGlow,
        ZioColors,
        ZioTargets;
    }

    /**
     * zio ShaderModule.ShaderChoice 的完整枚举。
     * 前 5 项是 zio 的实际 ShaderChoice；后 6 项是 ShaderRegistry 中注册的
     * 其余 pass id（outline / sky / dof / blur_mask / bloom_mask / glowy），
     * 一并保留以免简化 zio 的 shader 模式选择语义。
     */
    public enum ShaderChoice {
        SOLID("Solid", "solid"),
        RAINBOW("Rainbow", "rainbow"),
        GRADIENT("Gradient", "gradient"),
        BLOOM("Bloom", "bloom"),
        CUSTOM("Custom", "custom"),
        OUTLINE("Outline", "outline"),
        SKY("Sky", "sky"),
        DOF("Dof", "dof"),
        BLUR_MASK("BlurMask", "blur_mask"),
        BLOOM_MASK("BloomMask", "bloom_mask"),
        GLOWY("Glowy", "glowy");

        private final String displayName;
        private final String id;

        ShaderChoice(String displayName, String id) {
            this.displayName = displayName;
            this.id = id;
        }

        /** zio NamedEntry.getName()。 */
        public String getName() {
            return this.displayName;
        }

        /** zio ShaderChoice.getId()（对应 ShaderRegistry 的 pass id）。 */
        public String getId() {
            return this.id;
        }

        public String getLowercaseName() {
            return this.displayName.toLowerCase(Locale.ROOT);
        }
    }

    /** zio ShaderModule.TypeChoice。 */
    public enum TypeChoice {
        NONE("None"),
        DOTS("Dots"),
        GRID("Grid");

        private final String displayName;

        TypeChoice(String displayName) {
            this.displayName = displayName;
        }

        public String getName() {
            return this.displayName;
        }
    }
}
