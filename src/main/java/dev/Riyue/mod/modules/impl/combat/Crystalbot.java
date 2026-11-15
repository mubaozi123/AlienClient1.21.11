package dev.Riyue.mod.modules.impl.combat;

import dev.Riyue.RiyueClient;
import dev.Riyue.api.events.eventbus.EventListener;
import dev.Riyue.api.events.eventbus.EventPriority;
import dev.Riyue.api.events.impl.ClientTickEvent;
import dev.Riyue.api.events.impl.GameLeftEvent;
import dev.Riyue.api.events.impl.PacketEvent;
import dev.Riyue.api.events.impl.RotationEvent;
import dev.Riyue.api.events.impl.ServerChangePositionEvent;
import dev.Riyue.api.utils.math.DamageUtils;
import dev.Riyue.api.utils.math.ExplosionUtil;
import dev.Riyue.api.utils.math.Timer;
import dev.Riyue.api.utils.player.EntityUtil;
import dev.Riyue.api.utils.player.InventoryUtil;
import dev.Riyue.api.utils.render.Render3DUtil;
import dev.Riyue.api.utils.world.BlockUtil;
import dev.Riyue.core.impl.BreakManager;
import dev.Riyue.core.impl.RotationManager;
import dev.Riyue.mod.modules.Module;
import dev.Riyue.mod.modules.impl.client.AntiCheat;
import dev.Riyue.mod.modules.impl.movement.MovementSync;
import dev.Riyue.mod.modules.impl.player.AutoPot;
import dev.Riyue.mod.modules.impl.player.SpeedMine;
import dev.Riyue.mod.modules.settings.enums.SwingSide;
import dev.Riyue.mod.modules.settings.impl.BooleanSetting;
import dev.Riyue.mod.modules.settings.impl.ColorSetting;
import dev.Riyue.mod.modules.settings.impl.EnumSetting;
import dev.Riyue.mod.modules.settings.impl.SliderSetting;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import dev.Riyue.asm.accessors.IPlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.DeathMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.entity.EntityType;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Crystalbot —— open-zio AutoCrystalModule 的 1:1 移植。
 *
 * 保留 zio 的完整功能：place/break 双循环、全部延迟与距离、伤害策略(PLACE/HIT)、
 * sequential、requireUpperAir、strictDirection、lowerHitBox、mineIgnore、attackFactor、
 * antiSuicide、instant/await/ticksExisted、weakness、basePlace、place/break 旋转、
 * facePlace(血量/护甲/盾/伤害同步)、prediction + drawPrediction、
 * render(fill/outline/damage/fade/multiFade/fadeTime)、pause(health/mining/eating/mending)、
 * targeting(bosses/targetRange/antiFriendKill/antiQuiver/ignoreNakeds/assumeBestArmor/
 * forceSuicide/assumeInvincibility/invincTimeout)、autoSwap/silent/swapBack/strictTiming/
 * swapDelay/swapPenalty/multipoint。
 *
 * 原实现中的 CompletableFuture + 4 线程池被替换为同步 runCalculation()（由 onTick 或
 * onThread 驱动），计算流程、晶体 ID 预测、顺序放置、silent swap、伤害同步、反自杀、
 * 垫底座、faceplace、旋转、渲染、spawn packet 处理与槽位还原全部保留。
 */
public class Crystalbot extends Module {
    public static Crystalbot INSTANCE;

    // ── zio enums (constant names kept for config compatibility) ────────────
    public enum Page {
        General, Place, Break, Base, Rotate, FacePlace, Prediction, Render, Pause, Targeting, Misc
    }

    public enum SequentialChoice {
        NONE, STRICT, STRONG;

        public boolean isEnabled() {
            return this != NONE;
        }
    }

    public enum InstantChoice {
        ALWAYS, STRICT, NONE;

        public boolean isEnabled() {
            return this != NONE;
        }
    }

    public enum WeaknessChoice {
        NONE, NORMAL, SILENT
    }

    public enum SilentChoice {
        NORMAL, ALTERNATIVE
    }

    public enum ItemSwapMode {
        NONE, NORMAL, SILENT
    }

    public enum RebreakChoice {
        NONE, FAST, INSTANT;

        public boolean isNone() {
            return this == NONE;
        }

        public boolean isFast() {
            return this == FAST;
        }

        public boolean isInstant() {
            return this == INSTANT;
        }
    }

    // ── settings ────────────────────────────────────────────────────────────
    final EnumSetting<Page> page = add(new EnumSetting<>("Page", Page.General));
    final BooleanSetting thread = add(new BooleanSetting("Thread", false, () -> page.is(Page.General)));

    // Place
    final BooleanSetting place = add(new BooleanSetting("Place", true, () -> page.is(Page.Place)).setParent());
    final SliderSetting placeDelay = add(new SliderSetting("PlaceDelay", 0, 0, 1000, () -> page.is(Page.Place) && place.isOpen()).setSuffix("ms"));
    final SliderSetting placeRange = add(new SliderSetting("PlaceRange", 6.0, 0.0, 6.0, 0.01, () -> page.is(Page.Place) && place.isOpen()).setSuffix("m"));
    final SliderSetting placeWallRange = add(new SliderSetting("PlaceWallRange", 6.0, 0.0, 6.0, 0.01, () -> page.is(Page.Place) && place.isOpen()).setSuffix("m"));
    final SliderSetting minPlaceDamage = add(new SliderSetting("MinPlaceDamage", 6.0, 0.1, 20.1, 0.1, () -> page.is(Page.Place) && place.isOpen()).setSuffix("hp"));
    final SliderSetting maxPlaceSelfDamage = add(new SliderSetting("MaxSelfPlace", 12.0, 0.0, 20.0, 0.1, () -> page.is(Page.Place) && place.isOpen()).setSuffix("hp"));
    final SliderSetting placeDamageRatio = add(new SliderSetting("DamageRatio", 1.0, 0.5, 2.0, 0.01, () -> page.is(Page.Place) && place.isOpen()).setSuffix("x"));
    final EnumSetting<SequentialChoice> sequential = add(new EnumSetting<>("Sequential", SequentialChoice.STRICT, () -> page.is(Page.Place) && place.isOpen()));
    final BooleanSetting requireUpperAir = add(new BooleanSetting("RequireUpperAir", false, () -> page.is(Page.Place) && place.isOpen()));
    final BooleanSetting strictDirection = add(new BooleanSetting("StrictDirection", false, () -> page.is(Page.Place) && place.isOpen()));
    final BooleanSetting lowerHitBox = add(new BooleanSetting("LowerHitBox", false, () -> page.is(Page.Place) && place.isOpen()));
    final BooleanSetting mineIgnore = add(new BooleanSetting("MineIgnore", true, () -> page.is(Page.Place) && place.isOpen()));
    final BooleanSetting alwaysIgnoreMining = add(new BooleanSetting("AlwaysIgnoreMining", false, () -> page.is(Page.Place) && place.isOpen() && mineIgnore.getValue()));
    final SliderSetting miningIgnoreThreshold = add(new SliderSetting("MiningIgnoreThreshold", 1, 1, 5, () -> page.is(Page.Place) && place.isOpen() && mineIgnore.getValue()));
    final EnumSetting<RebreakChoice> rebreak = add(new EnumSetting<>("Rebreak", RebreakChoice.NONE, () -> page.is(Page.Place) && place.isOpen() && mineIgnore.getValue()));

    // Break
    final BooleanSetting breakSetting = add(new BooleanSetting("Break", true, () -> page.is(Page.Break)).setParent());
    final SliderSetting breakDelay = add(new SliderSetting("HitDelay", 0, 0, 1000, () -> page.is(Page.Break) && breakSetting.isOpen()).setSuffix("ms"));
    final SliderSetting attackFactor = add(new SliderSetting("AttackFactor", 1, 1, 5, () -> page.is(Page.Break) && breakSetting.isOpen()));
    final SliderSetting breakRange = add(new SliderSetting("BreakRange", 6.0, 0.0, 6.0, 0.01, () -> page.is(Page.Break) && breakSetting.isOpen()).setSuffix("m"));
    final SliderSetting breakWallRange = add(new SliderSetting("BreakWallRange", 6.0, 0.0, 6.0, 0.01, () -> page.is(Page.Break) && breakSetting.isOpen()).setSuffix("m"));
    final SliderSetting minBreakDamage = add(new SliderSetting("MinBreakDamage", 3.0, 0.1, 20.0, 0.1, () -> page.is(Page.Break) && breakSetting.isOpen()).setSuffix("hp"));
    final SliderSetting maxBreakSelfDamage = add(new SliderSetting("MaxSelfHit", 8.0, 0.0, 20.0, 0.1, () -> page.is(Page.Break) && breakSetting.isOpen()).setSuffix("hp"));
    final BooleanSetting antiSuicide = add(new BooleanSetting("AntiSuicide", true, () -> page.is(Page.Break) && breakSetting.isOpen()));
    final EnumSetting<InstantChoice> instant = add(new EnumSetting<>("Instant", InstantChoice.ALWAYS, () -> page.is(Page.Break) && breakSetting.isOpen()));
    final BooleanSetting await = add(new BooleanSetting("Await", true, () -> page.is(Page.Break) && breakSetting.isOpen() && instant.getValue() != InstantChoice.NONE));
    final SliderSetting ticksExisted = add(new SliderSetting("TicksExisted", 2, 0, 40, () -> page.is(Page.Break) && breakSetting.isOpen() && instant.getValue() != InstantChoice.ALWAYS));
    final EnumSetting<WeaknessChoice> weakness = add(new EnumSetting<>("Weakness", WeaknessChoice.NONE, () -> page.is(Page.Break) && breakSetting.isOpen()));

    // BasePlace
    final BooleanSetting basePlace = add(new BooleanSetting("BasePlace", false, () -> page.is(Page.Base)).setParent());
    final SliderSetting basePlaceDelay = add(new SliderSetting("BaseDelay", 500, 0, 1000, () -> page.is(Page.Base) && basePlace.isOpen()).setSuffix("ms"));
    final BooleanSetting basePlaceAir = add(new BooleanSetting("BasePlaceAir", false, () -> page.is(Page.Base) && basePlace.isOpen()));

    // Rotate
    final BooleanSetting rotate = add(new BooleanSetting("Rotate", false, () -> page.is(Page.Rotate)).setParent());
    final BooleanSetting rotatePlace = add(new BooleanSetting("PlaceRotate", true, () -> page.is(Page.Rotate) && rotate.isOpen()));
    final BooleanSetting rotateBreak = add(new BooleanSetting("BreakRotate", true, () -> page.is(Page.Rotate) && rotate.isOpen()));

    // FacePlace
    final BooleanSetting facePlace = add(new BooleanSetting("FacePlace", false, () -> page.is(Page.FacePlace)).setParent());
    final SliderSetting facePlaceHealth = add(new SliderSetting("FacePlaceHealth", 4.0, 0.0, 36.0, 0.1, () -> page.is(Page.FacePlace) && facePlace.isOpen()).setSuffix("hp"));
    final SliderSetting facePlaceArmorThreshold = add(new SliderSetting("FacePlaceArmor", 10, 0, 100, () -> page.is(Page.FacePlace) && facePlace.isOpen()).setSuffix("%"));
    final BooleanSetting facePlaceShieldBreaker = add(new BooleanSetting("ShieldBreaker", false, () -> page.is(Page.FacePlace) && facePlace.isOpen()));
    final BooleanSetting damageSync = add(new BooleanSetting("DamageSync", false, () -> page.is(Page.FacePlace) && facePlace.isOpen()));

    // Prediction
    final BooleanSetting prediction = add(new BooleanSetting("ExtraTicks", true, () -> page.is(Page.Prediction)).setParent());
    final SliderSetting predictionTicks = add(new SliderSetting("PredictionTicks", 4, 1, 8, () -> page.is(Page.Prediction) && prediction.isOpen()).setSuffix("tick"));
    final BooleanSetting drawPrediction = add(new BooleanSetting("DrawPrediction", false, () -> page.is(Page.Prediction) && prediction.isOpen()));

    // Render
    final BooleanSetting render = add(new BooleanSetting("Render", true, () -> page.is(Page.Render)).setParent());
    final ColorSetting fill = add(new ColorSetting("Fill", 1136499199, () -> page.is(Page.Render) && render.isOpen()));
    final ColorSetting outline = add(new ColorSetting("Outline", -910321153, () -> page.is(Page.Render) && render.isOpen()));
    final SliderSetting lineWidth = add(new SliderSetting("LineWidth", 1.0, 0.1, 3.0, 0.01, () -> page.is(Page.Render) && render.isOpen()));
    final BooleanSetting renderDamage = add(new BooleanSetting("RenderDamage", false, () -> page.is(Page.Render) && render.isOpen()));
    final BooleanSetting fade = add(new BooleanSetting("Fade", true, () -> page.is(Page.Render) && render.isOpen()).setParent());
    final BooleanSetting multiFade = add(new BooleanSetting("MultiFade", false, () -> page.is(Page.Render) && render.isOpen() && fade.isOpen()));
    final SliderSetting fadeTime = add(new SliderSetting("FadeTime", 1.0, 0.1, 1.5, 0.01, () -> page.is(Page.Render) && render.isOpen() && fade.isOpen()).setSuffix("s"));

    // Pause
    final BooleanSetting pause = add(new BooleanSetting("Pause", false, () -> page.is(Page.Pause)).setParent());
    final BooleanSetting pauseMining = add(new BooleanSetting("PauseMining", false, () -> page.is(Page.Pause) && pause.isOpen()));
    final BooleanSetting pauseEating = add(new BooleanSetting("PauseEating", false, () -> page.is(Page.Pause) && pause.isOpen()));
    final BooleanSetting pauseMending = add(new BooleanSetting("PauseMending", false, () -> page.is(Page.Pause) && pause.isOpen()));

    // Targeting
    final BooleanSetting targeting = add(new BooleanSetting("Targeting", false, () -> page.is(Page.Targeting)).setParent());
    final SliderSetting targetRange = add(new SliderSetting("TargetRange", 12.0, 1.0, 16.0, 0.1, () -> page.is(Page.Targeting) && targeting.isOpen()).setSuffix("m"));
    final SliderSetting crystalRange = add(new SliderSetting("CrystalRange", 8.0, 0.1, 12.0, 0.1, () -> page.is(Page.Targeting) && targeting.isOpen()).setSuffix("m"));
    final BooleanSetting antiFriendKill = add(new BooleanSetting("AntiFriendKill", false, () -> page.is(Page.Targeting) && targeting.isOpen()));
    final BooleanSetting antiQuiver = add(new BooleanSetting("AntiQuiver", false, () -> page.is(Page.Targeting) && targeting.isOpen()));
    final BooleanSetting ignoreNakeds = add(new BooleanSetting("IgnoreNakeds", false, () -> page.is(Page.Targeting) && targeting.isOpen()));
    final BooleanSetting assumeBestArmor = add(new BooleanSetting("AssumeBestArmor", false, () -> page.is(Page.Targeting) && targeting.isOpen()));
    final BooleanSetting bosses = add(new BooleanSetting("Bosses", false, () -> page.is(Page.Targeting) && targeting.isOpen()));
    final BooleanSetting forceSuicide = add(new BooleanSetting("ForceSuicide", false, () -> page.is(Page.Targeting) && targeting.isOpen()).setParent());
    final BooleanSetting forceSuicideNoArmor = add(new BooleanSetting("ForceSuicideNoArmor", false, () -> page.is(Page.Targeting) && targeting.isOpen() && forceSuicide.isOpen()));
    final BooleanSetting assumeInvincibility = add(new BooleanSetting("AssumeInvincibility", false, () -> page.is(Page.Targeting) && targeting.isOpen()).setParent());
    final BooleanSetting assumeSelfInvincibility = add(new BooleanSetting("AssumeSelfInvincibility", true, () -> page.is(Page.Targeting) && targeting.isOpen() && assumeInvincibility.isOpen()));
    final SliderSetting invincibilityTimeout = add(new SliderSetting("InvincTimeout", 0.0, 0.0, 4.0, 0.1, () -> page.is(Page.Targeting) && targeting.isOpen() && assumeInvincibility.isOpen()).setSuffix("s"));

    // Misc
    final BooleanSetting misc = add(new BooleanSetting("Misc", false, () -> page.is(Page.Misc)).setParent());
    final EnumSetting<ItemSwapMode> autoSwap = add(new EnumSetting<>("AutoSwap", ItemSwapMode.NONE, () -> page.is(Page.Misc) && misc.isOpen()));
    final EnumSetting<SilentChoice> silent = add(new EnumSetting<>("Silent", SilentChoice.NORMAL, () -> page.is(Page.Misc) && misc.isOpen() && autoSwap.getValue() == ItemSwapMode.SILENT));
    final BooleanSetting swapBack = add(new BooleanSetting("SwapBack", true, () -> page.is(Page.Misc) && misc.isOpen() && autoSwap.getValue() == ItemSwapMode.NORMAL));
    final BooleanSetting strictTiming = add(new BooleanSetting("StrictTiming", false, () -> page.is(Page.Misc) && misc.isOpen() && autoSwap.getValue() == ItemSwapMode.SILENT && silent.getValue() == SilentChoice.ALTERNATIVE));
    final SliderSetting swapDelay = add(new SliderSetting("SwapDelay", 0.0, 0.0, 10.0, 0.1, () -> page.is(Page.Misc) && misc.isOpen() && autoSwap.getValue() == ItemSwapMode.NORMAL));
    final SliderSetting swapPenalty = add(new SliderSetting("SwapPenalty", 0.0, 0.0, 10.0, 0.1, () -> page.is(Page.Misc) && misc.isOpen()));
    final BooleanSetting multipoint = add(new BooleanSetting("Multipoint", false, () -> page.is(Page.Misc) && misc.isOpen()));

    // ── state ───────────────────────────────────────────────────────────────
    final CrystalbotTargeting targetingHelper = new CrystalbotTargeting(this);
    final CrystalbotRender boxRenderer = new CrystalbotRender();

    final AtomicReference<CrystalbotPlacementResult> placementData = new AtomicReference<>();
    final AtomicReference<Vec3d> rotationTarget = new AtomicReference<>();
    final AtomicBoolean placementGate = new AtomicBoolean();

    private final Timer crystalHoldingTimer = new Timer();
    final Timer rotationTimer = new Timer();
    final Timer placeTimer = new Timer();
    final Timer breakTimer = new Timer();
    final Timer swapDelayTimer = new Timer();
    final Timer swapPenaltyTimer = new Timer();
    final Timer sequentialPlacementTimer = new Timer();
    final Timer attackRetryTimer = new Timer();
    final Timer basePlaceTimer = new Timer();
    private final Timer setbackTimer = new Timer();
    private final Timer mineIgnoreTimer = new Timer();
    private final Timer interactionCooldown = new Timer();
    private final Timer offhandSwapTimer = new Timer();

    final LinkedList<Long> placementTimes = new LinkedList<>();
    final List<Integer> predictedAttackIds = Collections.synchronizedList(new ArrayList<>());
    final Set<BlockPos> ignoredBlocks = Collections.synchronizedSet(new HashSet<>());
    final Set<Integer> clientAttackedIds = ConcurrentHashMap.newKeySet();
    final Set<Integer> serverSideDeadIds = ConcurrentHashMap.newKeySet();
    final Map<UUID, Long> joinTimes = new ConcurrentHashMap<>();
    final BlockPos.Mutable scanPosition = new BlockPos.Mutable();

    volatile BlockPos lastPlacedBase;
    volatile BlockPos lastAttackedBase;
    volatile BlockPos lastForcedBase;
    volatile BlockPos previousPlacement;
    volatile boolean spawnFollowupQueued;
    volatile boolean breakRotationRequested;
    volatile boolean obsidianDropNearTarget;
    volatile boolean quiverThreatActive;
    volatile boolean skip;
    volatile boolean auraHasAttackTarget;
    volatile float rotationPriority = 100.0f;
    volatile float selectedDamage;
    volatile Vec3d eyePosition;

    int lastAttackedCrystalId = -1;
    int attackRetries;
    int ignoredSlotChanges;
    int restoreSlot = -1;
    int lastInteractionId = -1;
    boolean calculationPending;
    boolean crystalAttackPending;
    boolean miningSwapPending;

    LivingEntity target;
    LivingEntity previousTarget;

    public Crystalbot() {
        super("Crystalbot", Module.Category.Combat);
        setChinese("水晶机器人");
        INSTANCE = this;
        clearTimer(basePlaceTimer);
        clearTimer(interactionCooldown);
        clearTimer(offhandSwapTimer);
    }

    static boolean isWorldReady() {
        return !nullCheck();
    }

    private static void clearTimer(Timer timer) {
        // zio ElapsedTimer#clear(): make every hasElapsed(...) return true.
        timer.setMs(Long.MAX_VALUE / 4L);
    }

    private static boolean hasElapsedAndReset(Timer timer, long millis) {
        if (timer.passed(millis)) {
            timer.reset();
            return true;
        }
        return false;
    }

    // ── lifecycle ───────────────────────────────────────────────────────────

    @Override
    public String getInfo() {
        if (target != null && !isInvalidTarget(target)) {
            String targetName = target.getName().getString();
            return "%s, %.1f, %d".formatted(targetName, selectedDamage, placementTimes.size());
        }
        return super.getInfo();
    }

    @Override
    public void onEnable() {
        updateEyePosition();
        lastAttackedCrystalId = -1;
        previousTarget = target;
        target = null;
        lastPlacedBase = null;
        placementData.set(null);
        completeCalculation();
        swapDelayTimer.reset();
        clearTimer(basePlaceTimer);
        restoreSlot = -1;
        clientAttackedIds.clear();
        serverSideDeadIds.clear();
        joinTimes.clear();
    }

    @Override
    public void onDisable() {
        restoreSelectedSlot();
    }

    @Override
    public void onLogin() {
        lastAttackedCrystalId = -1;
        clientAttackedIds.clear();
        serverSideDeadIds.clear();
        joinTimes.clear();
        predictedAttackIds.clear();
        previousPlacement = null;
    }

    @Override
    public void onLogout() {
        forceSuicide.setValueWithoutTask(false);
        onLogin();
    }

    public void setRotationTarget(Vec3d point) {
        if (point != null) {
            rotationTimer.reset();
            rotationTarget.set(point);
        }
    }

    // ── targeting / validity ────────────────────────────────────────────────

    boolean isInvalidTarget(LivingEntity candidate) {
        if (candidate == null || mc.player == null) {
            return true;
        }
        if (forceSuicide.getValue()) {
            return candidate != mc.player;
        }
        if (candidate instanceof PlayerEntity player) {
            if (RiyueClient.FRIEND != null && RiyueClient.FRIEND.isFriend(player)) {
                return true;
            }
            if (player.isCreative()) {
                return true;
            }
        }
        if (!candidate.isAlive() || candidate.isDead()) {
            return true;
        }
        if (candidate.distanceTo(mc.player) > targetRange.getValueFloat()
                || serverSideDeadIds.contains(candidate.getId())
                || candidate == mc.player) {
            return true;
        }
        return candidate.isSpectator();
    }

    private void clearInvalidTarget() {
        if (isInvalidTarget(target)) {
            target = null;
        }
    }

    boolean isFacePlaceEnabled() {
        if (facePlace.getValue()) {
            if (!auraHasAttackTarget) {
                return true;
            }
            return !damageSync.getValue();
        }
        return false;
    }

    boolean isSelfInvulnerable() {
        if (!assumeSelfInvincibility.getValue()) {
            return false;
        }
        float scaledAge = mc.player.age / 20.0f;
        return scaledAge < getInvulnerabilityTimeout();
    }

    private float getInvulnerabilityTimeout() {
        return assumeInvincibility.getValue() ? invincibilityTimeout.getValueFloat() : -1.0f;
    }

    private boolean isPlayerInvulnerable(LivingEntity candidate) {
        if (isSelfInvulnerable() || !(candidate instanceof PlayerEntity player)) {
            return false;
        }
        if (mc.getNetworkHandler() == null) {
            return false;
        }
        UUID uuid = player.getUuid();
        // PlayerInfoJoinTime mixin substitution: remember first sight of the player.
        joinTimes.computeIfAbsent(uuid, ignored -> System.currentTimeMillis());
        if (mc.getNetworkHandler().getPlayerListEntry(uuid) == null) {
            return false;
        }
        float elapsed = System.currentTimeMillis() - joinTimes.getOrDefault(uuid, System.currentTimeMillis());
        return elapsed <= getInvulnerabilityTimeout() * 1000.0f;
    }

    Box getTargetBounds(LivingEntity candidate) {
        if (prediction.getValue() && candidate instanceof PlayerEntity player) {
            return CrystalbotUtil.predictedBounds(player, predictionTicks.getValueInt());
        }
        return CrystalbotUtil.interpolationBounds(candidate);
    }

    // ── mining / world state substitutions ──────────────────────────────────

    private BlockPos getMiningPos() {
        return SpeedMine.INSTANCE == null ? null : SpeedMine.getBreakPos();
    }

    private BlockPos getMiningOrNearlyBrokenPos() {
        BlockPos mining = getMiningPos();
        if (mining != null && CrystalbotUtil.canMineBlock(mining)) {
            return mining;
        }
        if (SpeedMine.secondPos != null && CrystalbotUtil.canMineBlock(SpeedMine.secondPos)) {
            return SpeedMine.secondPos;
        }
        return null;
    }

    BlockPos getUnconfirmedBreak() {
        if (SpeedMine.INSTANCE != null && SpeedMine.INSTANCE.isOn() && SpeedMine.progress >= 0.8) {
            BlockPos pos = getMiningPos();
            if (pos != null) {
                return pos;
            }
        }
        if (RiyueClient.BREAK != null) {
            for (BreakManager.BreakData data : RiyueClient.BREAK.breakMap.values()) {
                if (data != null && data.complete && data.pos != null) {
                    return data.pos;
                }
            }
        }
        return null;
    }

    private double getTickBreakProgress() {
        BlockPos pos = getMiningOrNearlyBrokenPos();
        if (pos == null || SpeedMine.INSTANCE == null) {
            return 0.0;
        }
        try {
            double seconds = SpeedMine.getBreakTime(pos);
            return seconds <= 0.0 ? 0.0 : 1.0 / (seconds * 20.0);
        } catch (Throwable ignored) {
            return 0.0;
        }
    }

    boolean shouldIgnoreMiningBlock() {
        boolean nearlyBroken = false;
        float thresholdTicks = 2.0f + 0.5f * miningIgnoreThreshold.getValueInt();
        double predictedProgress = SpeedMine.progress + getTickBreakProgress() * thresholdTicks;
        // zio SpeedMineModule#damageSetting is private in repo1; 0.9 mirrors its default.
        double miningDamageThreshold = 0.9;
        if (predictedProgress >= miningDamageThreshold) {
            BlockPos pos = getMiningOrNearlyBrokenPos();
            if (pos != null && CrystalbotUtil.canMineBlock(pos)) {
                nearlyBroken = true;
            }
        }
        if (mineIgnore.getValue()) {
            if (nearlyBroken) {
                return true;
            }
            // zio SpeedMineModule#hasCrystalDelayElapsed(100L).
            if (!mineIgnoreTimer.passed(100L)) {
                return true;
            }
        }
        return false;
    }

    private boolean isMovementSyncEnabled() {
        return MovementSync.INSTANCE != null && MovementSync.INSTANCE.isOn();
    }

    private boolean isSilentRotationMode() {
        return RotationManager.snapBack;
    }

    private boolean is2b2tMode() {
        return AntiCheat.INSTANCE != null && AntiCheat.INSTANCE.serverSide.getValue();
    }

    private long getTickDurationMillis() {
        float factor = 1.0f;
        if (RiyueClient.SERVER != null) {
            factor = Math.max(0.1f, RiyueClient.SERVER.getTPSFactor());
        }
        return (long) (500.0f / factor);
    }

    private boolean isSwapPenaltyActive() {
        return !swapPenaltyTimer.passed((long) (50.0f * swapPenalty.getValueFloat()));
    }

    private boolean isSwapDelayActive() {
        if (autoSwap.getValue() != ItemSwapMode.NORMAL) {
            return false;
        }
        return !swapDelayTimer.passed((long) (50.0f * swapDelay.getValueFloat()));
    }

    boolean isAwaitEnabled() {
        return await.getValue();
    }

    private boolean isSilentSwap() {
        return autoSwap.getValue() == ItemSwapMode.SILENT;
    }

    private boolean shouldRestoreSlot() {
        return autoSwap.getValue() == ItemSwapMode.NORMAL && swapBack.getValue();
    }

    void ignoreNextSlotChange() {
        ignoredSlotChanges++;
    }

    private void restoreSelectedSlot() {
        if (isWorldReady() && shouldRestoreSlot() && restoreSlot != -1) {
            InventoryUtil.switchToSlot(restoreSlot);
            restoreSlot = -1;
        }
    }

    private boolean shouldPause() {
        if (!isWorldReady()) {
            return true;
        }
        // zio BlockInteractionUtils#isInteractionCooldownActive -> mc.itemUseCooldown / local timer.
        if (mc.itemUseCooldown > 0 || !interactionCooldown.passed(50L)) {
            return true;
        }
        // zio OffhandModule#isRecentSwapWindowActive / SpeedMineModule#isSwapPending.
        if (!offhandSwapTimer.passed(50L) || miningSwapPending || crystalAttackPending) {
            return true;
        }
        if (isInvalidTarget(target)) {
            return true;
        }
        if (mc.player.isSleeping()) {
            return true;
        }
        if (mc.player.isUsingItem() && pauseEating.getValue()) {
            return true;
        }
        if (pauseMending.getValue()
                && ((AutoMend.INSTANCE != null && AutoMend.INSTANCE.isOn())
                || (AutoPot.INSTANCE != null && AutoPot.INSTANCE.isOn()))) {
            return true;
        }
        boolean miningFlag = mc.interactionManager != null && mc.interactionManager.isBreakingBlock();
        if (!miningFlag) {
            BlockPos miningPos = getMiningPos();
            if (CrystalbotUtil.canMineBlock(miningPos)
                    && SpeedMine.INSTANCE != null && SpeedMine.INSTANCE.isOn()) {
                miningFlag = true;
            }
        }
        if (miningFlag && pauseMining.getValue()) {
            return true;
        }
        if (!forceSuicide.getValue()) {
            return false;
        }
        if (AutoAnchor.INSTANCE != null && AutoAnchor.INSTANCE.isOn()
                && AutoAnchor.INSTANCE.currentPos != null
                && placementData.get() != null
                && AutoAnchor.INSTANCE.lastDamage > placementData.get().damage()) {
            return true;
        }
        if (isMovementSyncEnabled() && !isSilentRotationMode() && mc.player.isGliding()) {
            return true;
        }
        return mc.player.isDead();
    }

    private void updateIgnoredBlocks() {
        synchronized (ignoredBlocks) {
            ignoredBlocks.clear();
            if (mineIgnore.getValue()) {
                if (shouldIgnoreMiningBlock()) {
                    BlockPos mining = getMiningPos();
                    if (mining != null) {
                        ignoredBlocks.add(mining);
                    }
                    if (SpeedMine.secondPos != null) {
                        ignoredBlocks.add(SpeedMine.secondPos);
                    }
                }
                // zio AntiCheatModule#assumeBlockBreakingSetting / blockBreakTracker.
                if (RiyueClient.BREAK != null) {
                    for (BreakManager.BreakData data : RiyueClient.BREAK.breakMap.values()) {
                        if (data == null || data.pos == null || data.breakTime <= 0) {
                            continue;
                        }
                        double progress = data.timer.getMs() / data.breakTime;
                        if (progress >= 0.8
                                && data.pos.getSquaredDistance(mc.player.getBlockPos()) <= 144.0) {
                            ignoredBlocks.add(data.pos);
                        }
                    }
                }
            }
        }
    }

    private void updateEyePosition() {
        if (isWorldReady()) {
            eyePosition = mc.player.getEyePos();
        }
    }

    // ── damage ──────────────────────────────────────────────────────────────

    double calculateExplosionDamage(BlockPos base, LivingEntity candidate, Box bounds,
                                    boolean ignoreBaseBlock) {
        BlockPos trackedBlock = getUnconfirmedBreak();
        BlockPos miningPos = getMiningOrNearlyBrokenPos();
        boolean miningMatch = trackedBlock != null && miningPos != null && trackedBlock.equals(miningPos);
        boolean useIgnoredBlocks = ignoreBaseBlock || miningMatch;
        Vec3d explosionPosition = Vec3d.ofBottomCenter(base).add(0.0, 1.0, 0.0);
        Collection<BlockPos> ignored = useIgnoredBlocks ? ignoredBlocks : Collections.emptyList();
        BlockPos ignoredBase = ignoreBaseBlock ? base.toImmutable() : null;
        return CrystalbotUtil.getExplosionDamage(explosionPosition, candidate, bounds,
                6.0, true, ignored, ignoredBase);
    }

    /** Uses repo1's ExplosionUtil as a quick damage estimate (not part of the core algorithm). */
    public float getQuickDamage(Vec3d pos, LivingEntity entity) {
        return ExplosionUtil.calculateDamage(pos, entity, entity, 6.0f);
    }

    // ── placement calculation ───────────────────────────────────────────────

    private CrystalbotPlacementResult calculatePlacement(List<LivingEntity> targets) {
        float selectedCrystalRange = shouldIgnoreMiningBlock() ? 999.0f : breakRange.getValueFloat();
        boolean ignoreMiningBlock = shouldIgnoreMiningBlock();
        if (rebreak.getValue() == RebreakChoice.INSTANT && !rebreakAsyncEnabled()) {
            ignoreMiningBlock = false;
        }

        CrystalbotPlacementResult currentPlacement = placementData.get();
        CrystalbotTargetDamage currentTargetDamage = null;
        CrystalbotPlacementResult bestPlacement = null;

        CrystalbotValidator validator = new CrystalbotValidator()
                .requireUpperAir(requireUpperAir.getValue())
                .lowerHitbox(lowerHitBox.getValue())
                .expandMovingPlayers(!isMovementSyncEnabled() && !shouldIgnoreMiningBlock())
                .ignoreBaseBlock(basePlace.getValue() && canRetryBasePlacement())
                .ignoreYoungItems(ignoreMiningBlock)
                .predictionTicks(prediction.getValue() ? predictionTicks.getValueInt() : 0)
                .ignoredCrystalRange(selectedCrystalRange);

        if (currentPlacement != null && validator.canPlace(currentPlacement.base())) {
            boolean currentHasBase = CrystalbotUtil.isBedrockOrObsidian(currentPlacement.base());
            currentTargetDamage = CrystalbotTargetDamage.calculateForBase(
                    this, currentPlacement.base(), currentHasBase, targets);
        }

        double range = placeRange.getValueFloat();
        for (double offsetX = -Math.floor(range); offsetX < Math.ceil(range); offsetX += 1.0) {
            for (double offsetY = -Math.floor(range); offsetY < Math.ceil(range); offsetY += 1.0) {
                for (double offsetZ = -Math.floor(range); offsetZ < Math.ceil(range); offsetZ += 1.0) {
                    if (eyePosition == null) {
                        continue;
                    }
                    scanPosition.set(eyePosition.x + offsetX, eyePosition.y + offsetY, eyePosition.z + offsetZ);
                    boolean hasCrystalBase = CrystalbotUtil.isBedrockOrObsidian(scanPosition);
                    if (bestPlacement != null
                            && bestPlacement.kind() != CrystalbotPlacementKind.OBSIDIAN
                            && !hasCrystalBase) {
                        continue;
                    }
                    Vec3d center = scanPosition.toCenterPos();
                    double distance = eyePosition.distanceTo(center);
                    if (distance > range || distance > selectedCrystalRange) {
                        continue;
                    }
                    if (!validator.canPlace(scanPosition)) {
                        continue;
                    }
                    boolean visible = true;
                    if (!Objects.equals(placeWallRange.getValue(), placeRange.getValue())) {
                        CrystalbotBlockSampleMode mode = multipoint.getValue()
                                ? CrystalbotBlockSampleMode.X8 : CrystalbotBlockSampleMode.FAST;
                        visible = CrystalbotUtil.canSeeAny(eyePosition, mode.sampleBlock(scanPosition));
                    }
                    if (!visible && distance > placeWallRange.getValueFloat()) {
                        continue;
                    }
                    if (strictDirection.getValue() && CrystalbotUtil.getStrictDirections(scanPosition).isEmpty()) {
                        continue;
                    }

                    Vec3d explosionPosition = Vec3d.ofBottomCenter(scanPosition).add(0.0, 1.0, 0.0);
                    double selfDamage = CrystalbotUtil.getExplosionDamage(explosionPosition,
                            mc.player, getTargetBounds(mc.player), 6.0, true,
                            Collections.emptyList(), scanPosition.toImmutable());

                    CrystalbotTargetDamage candidate = CrystalbotTargetDamage.calculateForBase(
                            this, scanPosition, hasCrystalBase, targets);
                    if (candidate == null) {
                        continue;
                    }
                    CrystalbotLethality lethality = CrystalbotLethality.classifyLethality(
                            candidate.target(), candidate.damage());
                    if (CrystalbotDamagePolicy.PLACE.shouldRejectDamage(
                            candidate.target(), selfDamage, candidate.damage(), lethality)) {
                        continue;
                    }
                    CrystalbotPlacementKind kind = CrystalbotPlacementKind.NONE;
                    if (currentTargetDamage != null && !currentPlacement.base().equals(scanPosition)
                            && Math.round(currentTargetDamage.damage()) >= candidate.damage()) {
                        continue;
                    }
                    if (!visible && distance > placeWallRange.getValueFloat()) {
                        kind = CrystalbotPlacementKind.RAYTRACE;
                    }

                    if (basePlace.getValue() && !hasCrystalBase) {
                        Box predictedBounds = getTargetBounds(candidate.target());
                        Box targetBoundsUnion = predictedBounds.union(candidate.target().getBoundingBox());
                        if (targetBoundsUnion.intersects(new Box(scanPosition))) {
                            continue;
                        }
                        if (!CrystalbotUtil.canPlaceAt(scanPosition, false)) {
                            continue;
                        }
                        if (!basePlaceAir.getValue()
                                && CrystalbotUtil.findSupportDirection(scanPosition, strictDirectionEnabled()) == null) {
                            continue;
                        }
                        kind = CrystalbotPlacementKind.OBSIDIAN;
                    }
                    if (!(target instanceof PlayerEntity)) {
                        kind = CrystalbotPlacementKind.NON_PLAYER;
                    }

                    CrystalbotPlacementResult newPlacement = new CrystalbotPlacementResult(
                            scanPosition.toImmutable(), candidate.target(), candidate.damage(), kind);
                    bestPlacement = newPlacement.selectBetterPlacement(bestPlacement);
                }
            }
        }
        return bestPlacement;
    }

    private boolean strictDirectionEnabled() {
        return AntiCheat.INSTANCE != null
                && AntiCheat.INSTANCE.placement.getValue() != dev.Riyue.mod.modules.settings.enums.Placement.Vanilla;
    }

    private boolean rebreakAsyncEnabled() {
        // zio SpeedMineModule#asyncSetting; repo1 has no public equivalent.
        return rebreak.getValue() == RebreakChoice.FAST;
    }

    // ── placement visibility / direction ────────────────────────────────────

    private boolean isPlacementFaceVisible(BlockPos base, Direction direction) {
        if (Objects.equals(placeRange.getValue(), placeWallRange.getValue())) {
            return true;
        }
        double distance = eyePosition.distanceTo(base.toCenterPos());
        if (distance < placeRange.getValueFloat()) {
            return true;
        }
        return CrystalbotUtil.canSeeAny(
                CrystalbotBlockSampleMode.X8.sampleFace(base, direction));
    }

    private boolean isPlacementHitVisible(Vec3d point, BlockPos base, Direction direction) {
        if (Objects.equals(placeRange.getValue(), placeWallRange.getValue())) {
            return true;
        }
        if (eyePosition.distanceTo(point) >= placeWallRange.getValueFloat()) {
            return CrystalbotUtil.canSee(eyePosition, point, base, direction);
        }
        return true;
    }

    private Direction selectPlacementDirection(BlockPos base) {
        List<Direction> directions = CrystalbotUtil.getStrictDirections(base);
        if (directions.isEmpty()) {
            directions = Arrays.asList(Direction.values());
        }
        Vec3d eye = eyePosition;
        Optional<Direction> result = directions.stream()
                .filter(direction -> isPlacementFaceVisible(base, direction))
                .min(Comparator.comparingDouble(direction ->
                        eye.distanceTo(base.toCenterPos().offset(direction, 0.5))));
        return result.orElse(null);
    }

    // ── base placement retry ────────────────────────────────────────────────

    private boolean canPlaceCrystalAt(BlockPos base, boolean requireTwoAir, boolean checkEntities,
                                      boolean checkCrystals, boolean allowFire, boolean shortHeight,
                                      boolean expandPlayers) {
        if (!CrystalbotUtil.isBedrockOrObsidian(base)) {
            return false;
        }
        BlockPos crystalPosition = base.up();
        BlockState state = mc.world.getBlockState(crystalPosition);
        if (allowFire) {
            if (!state.isAir() && !state.isOf(Blocks.FIRE)) {
                return false;
            }
        } else if (!state.isAir()) {
            return false;
        }
        if (requireTwoAir && !mc.world.getBlockState(base.up(2)).isAir()) {
            return false;
        }
        Box bounds = new Box(crystalPosition).withMaxY(
                crystalPosition.getY() + (shortHeight ? 0.9 : 2.0));
        for (Entity entity : CrystalbotUtil.safeEntities()) {
            if (entity.isRemoved() || !entity.isAlive()) {
                continue;
            }
            Box entityBounds = entity.getBoundingBox();
            if (entity instanceof PlayerEntity && entity != mc.player && expandPlayers) {
                entityBounds = entityBounds.expand(0.01);
            } else if (entity instanceof EndCrystalEntity && !checkCrystals) {
                continue;
            }
            if (entityBounds.intersects(bounds)) {
                return !checkEntities;
            }
        }
        return true;
    }

    private boolean canRetryBasePlacement() {
        if (lastForcedBase != null && target != null) {
            if (!canPlaceCrystalAt(lastForcedBase, requireUpperAir.getValue(), true, false,
                    true, lowerHitBox.getValue(), true)) {
                return true;
            }
            CrystalbotBlockSampleMode mode = multipoint.getValue()
                    ? CrystalbotBlockSampleMode.X8 : CrystalbotBlockSampleMode.FAST;
            boolean visible = true;
            if (!Objects.equals(placeWallRange.getValue(), placeRange.getValue())) {
                visible = CrystalbotUtil.canSeeAny(eyePosition, mode.sampleBlock(lastForcedBase));
            }
            double distance = mc.player.getEyePos().distanceTo(lastForcedBase.toCenterPos());
            if (distance > placeRange.getValueFloat()) {
                return true;
            }
            if (!visible && distance > placeWallRange.getValueFloat()) {
                return true;
            }
            double targetDamage = CrystalbotUtil.getCrystalDamage(
                    Vec3d.ofBottomCenter(lastForcedBase).add(0.0, 1.0, 0.0),
                    target, getTargetBounds(target));
            if (targetDamage >= CrystalbotDamagePolicy.PLACE.getMinimumDamage(target)) {
                return false;
            }
        }
        return basePlaceTimer.passed(Math.max(getTickDurationMillis(), (long) basePlaceDelay.getValueInt()));
    }

    // ── placement cycle ─────────────────────────────────────────────────────

    private void runPlacementCycle() {
        if (!shouldIgnoreMiningBlock()) {
            placementGate.set(false);
        }
        if (!place.getValue() || isSwapPenaltyActive() || placementGate.get()) {
            return;
        }
        boolean crystalInOffhand = mc.player.getOffHandStack().isOf(Items.END_CRYSTAL);
        boolean holdingCrystal = mc.player.isHolding(Items.END_CRYSTAL);
        boolean missingTarget = target == null;
        if (damageSync.getValue() || obsidianDropNearTarget || quiverThreatActive || missingTarget) {
            if (missingTarget || selectedDamage < minPlaceDamage.getValueFloat()) {
                if (missingTarget || selectedDamage < 5.0f) {
                    if (!placeTimer.passed(getTickDurationMillis())) {
                        return;
                    }
                }
            }
        }

        CrystalbotPlacementResult selected = placementData.get();
        if (selected == null) {
            restoreSelectedSlot();
            return;
        }
        BlockPos base = selected.base();
        if (base == null || target == null) {
            restoreSelectedSlot();
            return;
        }

        double minimumDamage = CrystalbotDamagePolicy.PLACE.getMinimumDamage(target);
        Vec3d explosionPosition = Vec3d.ofBottomCenter(base).add(0.0, 1.0, 0.0);
        double targetDamage = CrystalbotUtil.getExplosionDamage(explosionPosition, target,
                getTargetBounds(target), 6.0, true, ignoredBlocks, base);
        if (prediction.getValue() && targetDamage > 2.0 && targetDamage < minimumDamage) {
            Box currentBounds = CrystalbotUtil.interpolationBounds(target);
            targetDamage = Math.max(targetDamage,
                    calculateExplosionDamage(base, target, currentBounds, true));
        }
        if (targetDamage < minimumDamage) {
            restoreSelectedSlot();
            return;
        }
        if (!CrystalbotUtil.isBedrockOrObsidian(base)) {
            if (selected.kind() == CrystalbotPlacementKind.OBSIDIAN && basePlace.getValue()) {
                runForcedPlacement(base);
            }
            return;
        }

        int originalSlot = -1;
        int crystalSlot = InventoryUtil.findItem(Items.END_CRYSTAL);

        if (mc.isOnThread()) {
            BlockPos firePosition = base.up();
            if (mc.world.getBlockState(firePosition).isOf(Blocks.FIRE)) {
                mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                        PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, firePosition, Direction.DOWN));
                mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                        PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, firePosition, Direction.DOWN));
                return;
            }
        }

        boolean alternativeSlotSearch = false;
        boolean alternativeSwap = isSilentSwap() && silent.getValue() == SilentChoice.ALTERNATIVE;
        boolean miningSwapReset = false;
        if (!holdingCrystal && shouldIgnoreMiningBlock() && rebreak.getValue() != RebreakChoice.NONE
                && alternativeSwap) {
            alternativeSwap = true;
            miningSwapReset = true;
        }
        if (alternativeSwap && (!skip || !mc.player.currentScreenHandler.getCursorStack().isEmpty())) {
            skip = true;
            return;
        }
        if (alternativeSwap && crystalSlot == -1) {
            crystalSlot = InventoryUtil.findItemInventorySlot(Items.END_CRYSTAL);
        }
        if (autoSwap.getValue() != ItemSwapMode.NONE && !holdingCrystal
                && !isSwapDelayActive() && crystalSlot != -1) {
            originalSlot = mc.player.getInventory().selectedSlot;
            if (alternativeSwap) {
                InventoryUtil.inventorySwap(crystalSlot, originalSlot);
                alternativeSlotSearch = true;
                placementGate.set(miningSwapReset);
            } else {
                if (autoSwap.getValue() == ItemSwapMode.SILENT) {
                    crystalAttackPending = true;
                    ignoreNextSlotChange();
                }
                if (shouldRestoreSlot()) {
                    restoreSlot = originalSlot;
                }
                InventoryUtil.switchToSlot(crystalSlot);
            }
        }
        if (!holdingCrystal && originalSlot == -1) {
            return;
        }
        placeCrystal(base, crystalInOffhand, targetDamage);
        if (!isSilentSwap() && !alternativeSlotSearch) {
            return;
        }
        if (originalSlot != -1) {
            if (alternativeSlotSearch) {
                InventoryUtil.inventorySwap(crystalSlot, mc.player.getInventory().selectedSlot);
            } else if (autoSwap.getValue() == ItemSwapMode.SILENT) {
                ignoreNextSlotChange();
                InventoryUtil.switchToSlot(originalSlot);
            }
        }
        if (strictTiming.getValue() || miningSwapReset || skip) {
            skip = !skip;
        }
    }

    private void runForcedPlacement(BlockPos base) {
        if (!mc.isOnThread() || !canRetryBasePlacement()) {
            return;
        }
        int crystalSlot = InventoryUtil.findItem(Items.END_CRYSTAL);
        if (autoSwap.getValue() == ItemSwapMode.SILENT && silent.getValue() == SilentChoice.ALTERNATIVE) {
            crystalSlot = InventoryUtil.findItemInventorySlot(Items.END_CRYSTAL);
        }
        if (!mc.player.getOffHandStack().isOf(Items.END_CRYSTAL) && crystalSlot == -1) {
            return;
        }
        boolean strict = strictDirectionEnabled();
        boolean minimumWallRange = placeWallRange.getValueFloat() <= placeWallRange.getMin() + 0.001;
        Direction direction = CrystalbotUtil.findSupportDirection(base, strict, minimumWallRange);
        Vec3d hitPoint = base.toCenterPos();
        if (direction != null) {
            CrystalbotBlockSampleMode mode = multipoint.getValue()
                    ? CrystalbotBlockSampleMode.X8 : CrystalbotBlockSampleMode.FAST;
            Optional<Vec3d> visibleHit = mode.sampleFace(base, direction).stream()
                    .filter(point -> isPlacementHitVisible(point, base, direction))
                    .min(Comparator.comparingDouble(point -> mc.player.getEyePos().distanceTo(point)));
            final Vec3d fallbackHitPoint = hitPoint;
            hitPoint = visibleHit.orElseGet(() -> fallbackHitPoint.offset(direction, 0.5));
        }

        int originalSlot = mc.player.getInventory().selectedSlot;
        int baseSlot = InventoryUtil.findBlock(Blocks.OBSIDIAN);
        boolean alternativeSwap = isSilentSwap() && silent.getValue() == SilentChoice.ALTERNATIVE;
        if (alternativeSwap && baseSlot == -1) {
            baseSlot = InventoryUtil.findBlockInventorySlot(Blocks.OBSIDIAN);
        }
        if (baseSlot == -1) {
            return;
        }
        if (direction == null && !basePlaceAir.getValue()) {
            return;
        }
        if (!CrystalbotUtil.canPlaceAt(base, false)) {
            return;
        }
        if (baseSlot != originalSlot) {
            if (alternativeSwap) {
                InventoryUtil.inventorySwap(baseSlot, originalSlot);
            } else {
                crystalAttackPending = true;
                InventoryUtil.switchToSlot(baseSlot);
            }
        }

        // zio BlockInteractionUtils#sendPlacement: the clicked block is the
        // neighbour on the support side (or the base itself for air placement),
        // and the clicked face is the opposite of the support direction.
        boolean placingInAir = direction == null;
        Direction supportDirection = direction;
        if (supportDirection == null) {
            List<Direction> fallback = CrystalbotUtil.getStrictDirections(base);
            supportDirection = fallback.isEmpty() ? Direction.DOWN : fallback.get(0);
        }
        BlockPos clickedPosition = placingInAir ? base : base.offset(direction);
        Direction clickedSide = placingInAir ? supportDirection : direction.getOpposite();
        BlockHitResult hit = new BlockHitResult(hitPoint, clickedSide, clickedPosition, false);
        sendSequencedPacket(id -> new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, hit, id));
        EntityUtil.swingHand(Hand.MAIN_HAND, SwingSide.All);

        basePlaceTimer.reset();
        if (rotatePlace.getValue() && rotate.getValue()) {
            setRotationTarget(hitPoint);
        }
        lastForcedBase = base;
        if (originalSlot != -1 && baseSlot != originalSlot) {
            if (alternativeSwap) {
                InventoryUtil.inventorySwap(baseSlot, mc.player.getInventory().selectedSlot);
            } else {
                InventoryUtil.switchToSlot(originalSlot);
            }
        }
    }

    private void placeCrystal(BlockPos base, boolean offhand, double damage) {
        if (base == null) {
            return;
        }
        Direction direction = selectPlacementDirection(base);
        if (direction == null) {
            return;
        }
        if (!base.equals(lastAttackedBase) && lastAttackedBase != null
                && !sequential.getValue().isEnabled()) {
            return;
        }
        Vec3d hitPoint = base.toCenterPos().offset(direction, 0.5);
        Optional<Vec3d> visibleHit = CrystalbotUtil.faceCorners(base, direction).stream()
                .filter(point -> isPlacementHitVisible(point, base, direction))
                .min(Comparator.comparingDouble(point -> eyePosition.distanceTo(point)));
        if (visibleHit.isPresent()) {
            hitPoint = visibleHit.get();
        }
        if (Objects.equals(placeRange.getValue(), placeWallRange.getValue())) {
            if (direction.getAxis() == Direction.Axis.Y) {
                hitPoint = base.toCenterPos().offset(direction, 0.5);
            } else {
                hitPoint = base.toCenterPos().offset(direction, 0.5).add(0.0, 0.5, 0.0);
            }
        }
        if (rotatePlace.getValue() && rotate.getValue() && !breakRotationRequested) {
            setRotationTarget(hitPoint);
        }
        Hand hand = offhand ? Hand.OFF_HAND : Hand.MAIN_HAND;
        BlockHitResult hit = new BlockHitResult(hitPoint, direction, base, false);
        sendSequencedPacket(id -> new PlayerInteractBlockC2SPacket(hand, hit, id));
        EntityUtil.swingHand(hand, SwingSide.All);
        lastPlacedBase = base;
        if (render.getValue()) {
            boxRenderer.addBox(base);
            boxRenderer.showDamage(base, damage);
        }
        placeTimer.reset();
        interactionCooldown.reset();
    }

    // ── break cycle ─────────────────────────────────────────────────────────

    private void runBreakCycle() {
        if (!breakSetting.getValue() || !breakTimer.passed(breakDelay.getValueInt())
                || isSwapPenaltyActive() || isPlayerInvulnerable(target)) {
            return;
        }
        long awaitDelay = getLatencyMillis();
        if (!sequentialPlacementTimer.passed(awaitDelay)
                && instant.getValue() != InstantChoice.NONE
                && isAwaitEnabled()) {
            return;
        }
        boolean ignoreMiningBlock = shouldIgnoreMiningBlock();
        BlockPos cachedMining = getMiningOrNearlyBrokenPos();
        if (!CrystalbotUtil.canMineBlock(cachedMining)) {
            ignoreMiningBlock = false;
        }
        BlockPos miningBlock = CrystalbotUtil.canMineBlock(cachedMining) ? cachedMining : null;

        Entity bestCrystal = null;
        double bestDamage = -1337.0;
        for (Entity entity : CrystalbotUtil.safeEntities()) {
            if (!(entity instanceof EndCrystalEntity crystal) || !canAttackCrystal(crystal)) {
                continue;
            }
            double targetDamage;
            if (skip) {
                targetDamage = CrystalbotUtil.getCrystalDamage(entity.getEntityPos(),
                        target, getTargetBounds(target));
            } else {
                BlockPos crystalBase = BlockPos.ofFloored(entity.getEntityPos()).down();
                targetDamage = calculateExplosionDamage(crystalBase, target,
                        getTargetBounds(target), false);
            }
            boolean recentPlacementIntersection = false;
            if (lastPlacedBase != null && !entity.getBlockPos().equals(lastPlacedBase.up())) {
                Box placementBox = new Box(lastPlacedBase.up()).stretch(0.0, 1.0, 0.0);
                if (entity.getBoundingBox().intersects(placementBox) && !placeTimer.passed(200L)) {
                    recentPlacementIntersection = true;
                }
            }
            double selfDamage = CrystalbotUtil.getCrystalDamage(entity.getEntityPos(),
                    mc.player, mc.player.getBoundingBox());
            CrystalbotLethality lethality = CrystalbotLethality.classifyLethality(target, targetDamage);
            if (recentPlacementIntersection) {
                lethality = CrystalbotLethality.INTERSECTION;
            }
            if (targetDamage <= selfDamage && !forceSuicide.getValue()
                    && !lethality.lethalDamage && !recentPlacementIntersection) {
                continue;
            }
            if (!CrystalbotDamagePolicy.HIT.shouldRejectDamage(target, selfDamage,
                    targetDamage, lethality)) {
                if (recentPlacementIntersection && bestCrystal == null
                        && (!ignoreMiningBlock || miningBlock == null)) {
                    bestCrystal = entity;
                }
                if (targetDamage >= CrystalbotDamagePolicy.HIT.getMinimumDamage(target)
                        && targetDamage > bestDamage) {
                    bestDamage = targetDamage;
                    bestCrystal = entity;
                }
            }
        }
        if (bestCrystal instanceof EndCrystalEntity selected) {
            attackCrystal(selected);
        }
    }

    private void attackCrystal(EndCrystalEntity crystal) {
        if (crystal.getId() == lastAttackedCrystalId && attackRetryTimer.passed(500L)
                && crystal.isAlive() && !crystal.isRemoved()) {
            attackRetries++;
        } else {
            attackRetries = 0;
        }
        lastAttackedCrystalId = crystal.getId();
        if (attackRetries >= 5) {
            attackRetryTimer.reset();
        }
        if (attackEntityById(crystal.getId(), false)) {
            clientAttackedIds.add(crystal.getId());
            lastAttackedBase = crystal.getBlockPos().down();
            sendFactorAttacks(crystal.getId());
            if (rotateBreak.getValue() && rotate.getValue()) {
                setRotationTarget(CrystalbotUtil.lowerQuarterCenter(crystal.getBoundingBox()));
                breakRotationRequested = true;
            }
            breakTimer.reset();
            basePlaceTimer.reset();
        }
    }

    boolean canAttackCrystal(EndCrystalEntity crystal) {
        if (crystal.age < ticksExisted.getValueInt() && !shouldIgnoreMiningBlock()) {
            return false;
        }
        if (crystal.isRemoved() || target == null) {
            return false;
        }
        boolean visible = mc.player.canSee(crystal);
        double distance = mc.player.getEyePos().distanceTo(crystal.getEntityPos());
        float selectedRange = visible ? breakRange.getValueFloat() : breakWallRange.getValueFloat();
        return distance <= selectedRange;
    }

    // ── attack packets / weakness ───────────────────────────────────────────

    /**
     * zio CrystalInteractionManager sends attacks against an entity id that may not
     * have spawned on the client yet (factor attacks / spawn-packet instant). Yarn's
     * public factory needs an Entity, so the packet is built for the local player and
     * its id field is patched. The field is located by type as well as by name so the
     * lookup also works when the runtime field name is intermediary.
     */
    private static PlayerInteractEntityC2SPacket attackPacketById(int entityId) {
        // Build a well-formed ATTACK packet. The static factory targets mc.player by
        // default; we override only the entity id via the mixin accessor (the same
        // mechanism Criticals uses to read it), which is remap-safe at runtime.
        // Avoids fragile reflection that could otherwise leave the packet aimed at
        // the local player — the cause of crystals never exploding.
        PlayerInteractEntityC2SPacket packet = PlayerInteractEntityC2SPacket.attack(
                mc.player, mc.player.isSneaking());
        ((IPlayerInteractEntityC2SPacket) packet).setEntityId(entityId);
        return packet;
    }

    private boolean attackEntityById(int entityId, boolean instantAttack) {
        if (!instant.getValue().isEnabled() && instantAttack) {
            return false;
        }
        if (lastInteractionId != -1 && isMovementSyncEnabled()) {
            return false;
        }
        Entity targetEntity = mc.world.getEntityById(entityId);
        PlayerInteractEntityC2SPacket packet = (targetEntity != null && !instantAttack)
                ? PlayerInteractEntityC2SPacket.attack(targetEntity, mc.player.isSneaking())
                : attackPacketById(entityId);
        if (packet == null) {
            return false;
        }
        int previousSlot = mc.player.getInventory().selectedSlot;
        boolean switchedForWeakness = targetEntity instanceof EndCrystalEntity && selectWeaknessWeapon();
        lastInteractionId = entityId;

        Hand hand = selectAttackHand(targetEntity, instantAttack);
        mc.getNetworkHandler().sendPacket(packet);
        if (!instantAttack) {
            mc.player.swingHand(hand);
        }
        mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        EntityUtil.resetLastAttackedTicks();
        mineIgnoreTimer.reset();
        if (isMovementSyncEnabled() && !is2b2tMode()) {
            mc.execute(() -> {
                Entity entity = mc.world.getEntityById(entityId);
                if (entity != null) {
                    mc.player.attack(entity);
                }
            });
        }
        if (switchedForWeakness && weakness.getValue() == WeaknessChoice.SILENT) {
            ignoreNextSlotChange();
            InventoryUtil.switchToSlot(previousSlot);
        }
        return true;
    }

    private Hand selectAttackHand(Entity entity, boolean instantAttack) {
        if (entity instanceof EndCrystalEntity && !instantAttack) {
            return mc.player.getOffHandStack().isOf(Items.END_CRYSTAL) ? Hand.OFF_HAND : Hand.MAIN_HAND;
        }
        return Hand.MAIN_HAND;
    }

    private boolean selectWeaknessWeapon() {
        if (weakness.getValue() == WeaknessChoice.NONE) {
            return false;
        }
        boolean holdingTool = mc.player.getMainHandStack().get(DataComponentTypes.TOOL) != null;
        if (!mc.player.hasStatusEffect(StatusEffects.WEAKNESS) || holdingTool) {
            return false;
        }
        int weaponSlot = -1;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (stack.get(DataComponentTypes.WEAPON) != null) {
                weaponSlot = slot;
                break;
            }
        }
        if (weaponSlot == -1) {
            return false;
        }
        if (weakness.getValue() == WeaknessChoice.SILENT) {
            ignoreNextSlotChange();
        }
        InventoryUtil.switchToSlot(weaponSlot);
        return true;
    }

    private void sendFactorAttacks(int crystalId) {
        if (attackFactor.getValueInt() <= 1) {
            return;
        }
        for (Entity entity : CrystalbotUtil.safeEntities()) {
            if (entity instanceof LivingEntity && entity.isRemoved()) {
                return;
            }
            if (!entity.isAlive()) {
                return;
            }
        }
        int factor = attackFactor.getValueInt();
        for (int index = 1; index < factor; index++) {
            int nextCrystalId = crystalId + index;
            int predictedId = nextCrystalId - (isMovementSyncEnabled() ? 1 : 0);
            PlayerInteractEntityC2SPacket factorPacket = attackPacketById(predictedId);
            if (factorPacket == null) {
                continue;
            }
            mc.getNetworkHandler().sendPacket(factorPacket);
            mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
            predictedAttackIds.add(predictedId);
        }
    }

    // ── spawn packet / instant ──────────────────────────────────────────────

    private void onCrystalSpawn(EntitySpawnS2CPacket packet) {
        if (packet.getEntityType() != EntityType.END_CRYSTAL || target == null) {
            return;
        }
        Vec3d spawnPosition = new Vec3d(packet.getX(), packet.getY(), packet.getZ());
        if (lastPlacedBase != null
                && spawnPosition.squaredDistanceTo(lastPlacedBase.toCenterPos()) <= 9.0) {
            long spawnTime = System.currentTimeMillis();
            mc.execute(() -> placementTimes.add(spawnTime));
        }
        if (instant.getValue() == InstantChoice.NONE || !breakSetting.getValue()
                || isPlayerInvulnerable(target)) {
            return;
        }
        if (shouldIgnoreMiningBlock() && isAwaitEnabled()) {
            return;
        }
        boolean visible = true;
        if (!Objects.equals(breakRange.getValue(), breakWallRange.getValue())) {
            visible = CrystalbotUtil.canSee(eyePosition, spawnPosition);
        }
        double distance = mc.player.getEyePos().distanceTo(spawnPosition);
        float selectedRange = visible ? breakRange.getValueFloat() : breakWallRange.getValueFloat();
        if (distance > selectedRange) {
            return;
        }
        double targetDamage = CrystalbotUtil.getCrystalDamage(spawnPosition, target);
        double selfDamage = CrystalbotUtil.getCrystalDamage(spawnPosition, mc.player);
        CrystalbotLethality lethality = CrystalbotLethality.classifyLethality(target, targetDamage);
        if (targetDamage <= selfDamage && !forceSuicide.getValue() && !lethality.lethalDamage) {
            return;
        }
        if (targetDamage < CrystalbotDamagePolicy.HIT.getMinimumDamage(target)) {
            return;
        }
        if (CrystalbotDamagePolicy.HIT.shouldRejectDamage(target, selfDamage, targetDamage, lethality)) {
            return;
        }
        BlockPos crystalPosition = BlockPos.ofFloored(spawnPosition);
        if (instant.getValue() == InstantChoice.STRICT && crystalPosition.equals(previousPlacement)) {
            return;
        }
        if (rotate.getValue() && rotateBreak.getValue()) {
            setRotationTarget(spawnPosition);
        }
        if (attackEntityById(packet.getEntityId(), true)) {
            lastAttackedBase = crystalPosition;
            previousPlacement = crystalPosition;
            lastAttackedCrystalId = packet.getEntityId();
            attackRetries = 0;
            basePlaceTimer.reset();
        }

        boolean followupAllowed = mc.player.isHolding(Items.END_CRYSTAL)
                || CrystalbotUtil.selfHealth() >= 8.0f;
        if (!is2b2tMode()) {
            followupAllowed = true;
        }
        if (sequential.getValue().isEnabled() && !spawnFollowupQueued && followupAllowed) {
            sequentialPlacementTimer.reset();
            runPlacementCycle();
            if (sequential.getValue() == SequentialChoice.STRICT) {
                spawnFollowupQueued = true;
            }
        }
        sendFactorAttacks(packet.getEntityId());
    }

    // ── calculation scheduling ──────────────────────────────────────────────

    private boolean prepareCalculation() {
        boolean holdingShulker = mc.player.isHolding(stack ->
                stack.getItem() instanceof BlockItem blockItem
                        && blockItem.getBlock() instanceof ShulkerBoxBlock);
        if (!holdingShulker && crystalHoldingTimer.passed(50L)) {
            return false;
        }
        if (holdingShulker) {
            crystalHoldingTimer.reset();
        }
        if (calculationPending) {
            completeCalculation();
        }
        return true;
    }

    public void completeCalculation() {
        calculationPending = false;
    }

    private void runCalculation() {
        if (!isWorldReady()) {
            return;
        }
        if (!prepareCalculation()) {
            return;
        }
        calculationPending = true;
        clearInvalidTarget();
        List<LivingEntity> targets = targetingHelper.snapshot();
        CrystalbotPlacementResult result = calculatePlacement(targets);
        if (result == null) {
            placementData.set(null);
            selectedDamage = 0.0f;
        } else {
            placementData.set(result);
            selectedDamage = (float) result.damage();
            target = result.target();
        }
        completeCalculation();
        synchronized (predictedAttackIds) {
            for (Integer predictedId : predictedAttackIds) {
                if (mc.world.getEntityById(predictedId) instanceof EndCrystalEntity) {
                    clientAttackedIds.add(predictedId);
                }
            }
            predictedAttackIds.removeIf(id -> mc.world.getEntityById(id) != null);
        }
    }

    /** Called by ThreadManager.ClientService in parallel with AutoCrystal#onThread. */
    public void onThread() {
        if (isOff() || !isWorldReady()) {
            return;
        }
        if (thread.getValue()) {
            runCalculation();
        }
    }

    // ── pre/post update ─────────────────────────────────────────────────────

    private void onPreUpdate() {
        targetingHelper.refresh();
        updateEyePosition();
        updateIgnoredBlocks();
        auraHasAttackTarget = Aura.target != null;
        spawnFollowupQueued = false;
        quiverThreatActive = isQuiverThreatPresent();
        if (forceSuicide.getValue() && forceSuicideNoArmor.getValue()) {
            throwUnboundArmor();
        }
        if (obsidianDropNearTarget) {
            obsidianDropNearTarget = false;
        } else {
            obsidianDropNearTarget = false;
            LivingEntity proximityTarget = targetingHelper.findProximityTarget();
            if (proximityTarget != null && targetingHelper.hasObsidianDropNear(proximityTarget)) {
                obsidianDropNearTarget = true;
            }
        }
    }

    private void onPlayerUpdate() {
        breakRotationRequested = false;
        lastAttackedBase = null;
        lastInteractionId = -1;
        crystalAttackPending = false;
        miningSwapPending = false;
        Entity lastAttacked = mc.world.getEntityById(lastAttackedCrystalId);
        if (lastAttacked == null || !lastAttacked.isAlive()) {
            attackRetries = 0;
        }
        if (mc.player.age % 4 == 0) {
            long now = System.currentTimeMillis();
            while (!placementTimes.isEmpty()
                    && placementTimes.getFirst() != null
                    && now - placementTimes.getFirst() > 1000L) {
                placementTimes.remove();
            }
        }

        if (!shouldPause()) {
            runBreakCycle();
            if (placeTimer.passed(placeDelay.getValueInt())) {
                runPlacementCycle();
            }
            Vec3d rotationPoint = rotationTarget.get();
            if (rotate.getValue() && rotationPoint != null) {
                float forward = compareMovementKeys(
                        mc.options.forwardKey.isPressed(), mc.options.backKey.isPressed());
                float strafe = compareMovementKeys(
                        mc.options.leftKey.isPressed(), mc.options.rightKey.isPressed());
                double movementInputSq = forward * forward + strafe * strafe;
                boolean retryWindowActive = isAttackRetryWindowActive(500L);
                boolean silentRotation = isSilentRotationMode();
                boolean rotationFollowup = !(silentRotation && !retryWindowActive && movementInputSq >= 0.01);
                if (mc.player.isGliding()) {
                    rotationFollowup = false;
                }
                float[] rotations = RotationManager.getRotation(mc.player.getEyePos(), rotationPoint);
                // zio requests a second, higher priority (101) rotation for silent
                // rotation follow-up; RotationEvent.setTarget honours the priority.
                rotationPriority = silentRotation && rotationFollowup && !retryWindowActive
                        ? 101.0f : 100.0f;
                if (RiyueClient.ROTATION != null) {
                    RiyueClient.ROTATION.lookAt(rotationPoint);
                }
                if (rotations.length > 1 && rotationTimer.passed(400L)) {
                    placementData.lazySet(null);
                    rotationTarget.lazySet(null);
                }
            }
        }
    }

    private static float compareMovementKeys(boolean positive, boolean negative) {
        return positive == negative ? 0.0f : (positive ? 1.0f : -1.0f);
    }

    private boolean isAttackRetryWindowActive(long retryMillis) {
        if (!setbackTimer.passed(200L)) {
            return true;
        }
        float factor = RiyueClient.SERVER == null ? 1.0f : Math.max(0.1f, RiyueClient.SERVER.getTPSFactor());
        return !attackRetryTimer.passed((long) (retryMillis / factor));
    }

    private long getLatencyMillis() {
        if (target instanceof PlayerEntity player && mc.getNetworkHandler() != null) {
            net.minecraft.client.network.PlayerListEntry entry =
                    mc.getNetworkHandler().getPlayerListEntry(player.getUuid());
            if (entry != null) {
                return Math.max(0L, entry.getLatency());
            }
        }
        return 0L;
    }

    // ── quiver / armor / invulnerability helpers ────────────────────────────

    private boolean isQuiverThreatPresent() {
        if (quiverThreatActive) {
            return false;
        }
        if (!antiQuiver.getValue() || !(target instanceof PlayerEntity) || !target.isAlive()) {
            return false;
        }
        for (Entity entity : CrystalbotUtil.safeEntities()) {
            if (!(entity instanceof ArrowEntity)) {
                continue;
            }
            if (entity.getY() < target.getEyeY()) {
                continue;
            }
            double horizontal = CrystalbotUtil.horizontalDistance(
                    entity.getEntityPos(), target.getEyePos());
            double vertical = Math.abs(target.getEyeY() - entity.getEyeY());
            if (horizontal < 0.5 && vertical < 4.0) {
                return true;
            }
        }
        return false;
    }

    private void throwUnboundArmor() {
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET};
        int[] inventoryIndices = {39, 38, 37, 36};
        int[] containerSlots = {5, 6, 7, 8};
        for (int index = 0; index < slots.length; index++) {
            ItemStack stack = mc.player.getInventory().getStack(inventoryIndices[index]);
            if (stack.isEmpty()) {
                continue;
            }
            if (DamageUtils.getEnchantmentLevel(stack, Enchantments.BINDING_CURSE) == 0) {
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId,
                        containerSlots[index], 1, SlotActionType.THROW, mc.player);
                break;
            }
        }
    }

    // ── events ──────────────────────────────────────────────────────────────

    @EventListener(priority = EventPriority.HIGHEST)
    public void onTick(ClientTickEvent event) {
        if (isOff() || !isWorldReady()) {
            return;
        }
        if (event.isPre()) {
            onPreUpdate();
            if (!thread.getValue()) {
                runCalculation();
            }
        } else {
            onPlayerUpdate();
        }
    }

    @EventListener
    public void onRotate(RotationEvent event) {
        if (isOff() || !rotate.getValue() || !isWorldReady()) {
            return;
        }
        Vec3d point = rotationTarget.get();
        if (point != null) {
            event.setTarget(point, 1.0f, rotationPriority);
        }
    }

    @EventListener
    public void onPacketSend(PacketEvent.Send event) {
        if (event.getPacket() instanceof UpdateSelectedSlotC2SPacket) {
            if (ignoredSlotChanges > 0) {
                ignoredSlotChanges--;
            } else {
                swapPenaltyTimer.reset();
                swapDelayTimer.reset();
                offhandSwapTimer.reset();
            }
        }
    }

    @EventListener
    public void onPacketReceive(PacketEvent.Receive event) {
        if (event.getPacket() instanceof EntitySpawnS2CPacket spawnPacket) {
            if (!isSwapPenaltyActive()) {
                try {
                    if (!shouldPause()) {
                        onCrystalSpawn(spawnPacket);
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        if (event.getPacket() instanceof EntityStatusS2CPacket statusPacket
                && statusPacket.getStatus() == 3 && target != null) {
            Entity entity = statusPacket.getEntity(mc.world);
            if (entity != null && entity.getId() == target.getId()) {
                serverSideDeadIds.add(target.getId());
            }
        }
        if (event.getPacket() instanceof DeathMessageS2CPacket) {
            forceSuicide.setValueWithoutTask(false);
        }
    }

    @EventListener
    public void onGameLeft(GameLeftEvent event) {
        forceSuicide.setValueWithoutTask(false);
    }

    @EventListener
    public void onSetback(ServerChangePositionEvent event) {
        setbackTimer.reset();
    }

    @Override
    public void onRender3D(MatrixStack matrixStack) {
        if (isOff() || !isWorldReady()) {
            return;
        }
        boxRenderer.render(matrixStack, this);
        // Render3DUtil is also used for the current placement outline while the
        // fade renderer is empty (mirrors zio's registered FeatureBoxRenderer).
        if (render.getValue() && boxRenderer.isEmpty()) {
            CrystalbotPlacementResult result = placementData.get();
            if (result != null && result.base() != null) {
                Render3DUtil.drawBox(matrixStack, new Box(result.base()),
                        outline.getValue(), lineWidth.getValueFloat());
            }
        }
    }
}
