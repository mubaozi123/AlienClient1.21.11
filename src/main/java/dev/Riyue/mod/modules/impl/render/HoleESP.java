package dev.Riyue.mod.modules.impl.render;

import dev.Riyue.RiyueClient;
import dev.Riyue.api.utils.math.Timer;
import dev.Riyue.api.utils.render.ColorUtil;
import dev.Riyue.api.utils.render.Render3DScheduler;
import dev.Riyue.api.utils.render.Render3DUtil;
import dev.Riyue.api.utils.world.BlockUtil;
import dev.Riyue.mod.modules.Module;
import dev.Riyue.mod.modules.settings.impl.BooleanSetting;
import dev.Riyue.mod.modules.settings.impl.ColorSetting;
import dev.Riyue.mod.modules.settings.impl.SliderSetting;
import net.minecraft.block.Blocks;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class HoleESP extends Module {

    public static HoleESP INSTANCE;
    public final SliderSetting startFade = add(new SliderSetting("StartFade", 5f, 1f, 20f));
    public final SliderSetting distance = add(new SliderSetting("Distance", 6f, 1f, 20f));
    public final SliderSetting airHeight = add(new SliderSetting("AirHeight", 1, -3, 3, .01));
    public final BooleanSetting airYCheck = add(new BooleanSetting("AirYCheck", true));
    public final SliderSetting height = add(new SliderSetting("Height", 1, -3, 3, .1));
    public final SliderSetting wallHeight = add(new SliderSetting("WallHeight", 3, -3, 3, .1));
    public final BooleanSetting sideCheck = add(new BooleanSetting("SideCheck", true));
    private final ColorSetting airFill = add(new ColorSetting("AirFill", new Color(148, 0, 0, 100)).injectBoolean(true));
    private final ColorSetting airBox = add(new ColorSetting("AirBox", new Color(148, 0, 0, 100)).injectBoolean(true));
    private final ColorSetting airFade = add(new ColorSetting("AirFade", new Color(148, 0, 0, 0)).injectBoolean(true));
    private final ColorSetting normalFill = add(new ColorSetting("UnsafeFill", new Color(255, 0, 0, 50)).injectBoolean(true));
    private final ColorSetting normalBox = add(new ColorSetting("UnsafeBox", new Color(255, 0, 0, 100)).injectBoolean(true));
    private final ColorSetting normalFade = add(new ColorSetting("UnsafeFade", new Color(255, 0, 0, 0)).injectBoolean(true));
    private final ColorSetting bedrockFill = add(new ColorSetting("SafeFill", new Color(8, 255, 79, 50)).injectBoolean(true));
    private final ColorSetting bedrockBox = add(new ColorSetting("SafeBox", new Color(8, 255, 79, 100)).injectBoolean(true));
    private final ColorSetting bedrockFade = add(new ColorSetting("SafeFade", new Color(8, 255, 79, 100)).injectBoolean(true));
    private final ColorSetting wallFill = add(new ColorSetting("WallFill", new Color(0, 255, 255, 128)).injectBoolean(true));
    private final ColorSetting wallBox = add(new ColorSetting("WallBox", new Color(0, 225, 255, 255)).injectBoolean(true));
    private final ColorSetting wallFade = add(new ColorSetting("WallFade", new Color(0, 255, 255, 64)).injectBoolean(true));
    private final ColorSetting wallSideFill = add(new ColorSetting("WallSideFill", new Color(0, 255, 255, 128)).injectBoolean(true));
    private final ColorSetting wallSideBox = add(new ColorSetting("WallSideBox", new Color(0, 225, 255, 255)).injectBoolean(true));
    private final ColorSetting wallSideFade = add(new ColorSetting("WallSideFade", new Color(0, 255, 255, 64)).injectBoolean(true));
    private final SliderSetting updateDelay =
            add(new SliderSetting("UpdateDelay", 50, 0, 1000));
    private final List<BlockPos> tempNormalList = new ArrayList<>();
    private final List<BlockPos> tempBedrockList = new ArrayList<>();
    private final List<BlockPos> tempAirList = new ArrayList<>();
    private final List<BlockPos> tempWallList = new ArrayList<>();
    private final List<BlockPos> tempWallSideList = new ArrayList<>();
    private final Timer timer = new Timer();
    boolean drawing = false;
    private List<BlockPos> normalList = new ArrayList<>();
    private List<BlockPos> bedrockList = new ArrayList<>();
    private List<BlockPos> airList = new ArrayList<>();
    private List<BlockPos> wallList = new ArrayList<>();
    private List<BlockPos> wallSideList = new ArrayList<>();

    public HoleESP() {
        super("HoleESP", Category.Render);
        setChinese("坑透视");
        INSTANCE = this;
    }

    public void onThread() {
        if (HoleESP.nullCheck() || this.isOff()) {
            return;
        }
        if (!this.drawing && this.timer.passedMs(this.updateDelay.getValue())) {
            this.timer.reset();
            // 多核并行扫描：把球形区域内的坑位判定切分成多个子任务，
            // 提交到 WorkPool.worldScan() 线程池在各 CPU 核心上同时执行。
            final List<BlockPos> sphere = BlockUtil.getSphere(this.distance.getValueFloat(), HoleESP.mc.player.getEntityPos());
            final Vec3d eyePos = HoleESP.mc.player.getEyePos();
            final boolean sideCheck = this.sideCheck.getValue();
            final int cores = Math.min(dev.Riyue.core.impl.WorkPool.CORES, 8);
            final int chunkSize = Math.max(1, (sphere.size() + cores - 1) / cores);
            List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
            final java.util.List<BlockPos>[] rNormal = new List[]{null};
            final java.util.List<BlockPos>[] rBedrock = new List[]{null};
            final java.util.List<BlockPos>[] rAir = new List[]{null};
            final java.util.List<BlockPos>[] rWall = new List[]{null};
            final java.util.List<BlockPos>[] rWallSide = new List[]{null};
            for (int start = 0; start < sphere.size(); start += chunkSize) {
                final int s = start;
                final int e = Math.min(sphere.size(), start + chunkSize);
                futures.add(dev.Riyue.core.impl.WorkPool.worldScan().submit(() -> {
                    List<BlockPos> normal = new ArrayList<>();
                    List<BlockPos> bedrock = new ArrayList<>();
                    List<BlockPos> air = new ArrayList<>();
                    List<BlockPos> wall = new ArrayList<>();
                    List<BlockPos> wallSide = new ArrayList<>();
                    for (int idx = s; idx < e; idx++) {
                        BlockPos pos = sphere.get(idx);
                        Type type;
                        if (this.isBedrock(pos) && this.isBedrock(pos.up(2)) && this.isBedrock(pos.down())) {
                            Direction side = this.getWallSide(pos, eyePos);
                            if (side != null || !sideCheck) {
                                wall.add(pos);
                            }
                            if (side != null) {
                                wallSide.add(pos.offset(side));
                            }
                        }
                        if ((type = this.isHole(pos)) == Type.Bedrock) {
                            bedrock.add(pos);
                            continue;
                        }
                        if (type == Type.Normal) {
                            normal.add(pos);
                            continue;
                        }
                        if (type != Type.Air) continue;
                        air.add(pos);
                    }
                    synchronized (this) {
                        if (rNormal[0] == null) {
                            rNormal[0] = normal;
                            rBedrock[0] = bedrock;
                            rAir[0] = air;
                            rWall[0] = wall;
                            rWallSide[0] = wallSide;
                        } else {
                            rNormal[0].addAll(normal);
                            rBedrock[0].addAll(bedrock);
                            rAir[0].addAll(air);
                            rWall[0].addAll(wall);
                            rWallSide[0].addAll(wallSide);
                        }
                    }
                }));
            }
            for (java.util.concurrent.Future<?> f : futures) {
                try {
                    f.get();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (java.util.concurrent.ExecutionException ee) {
                    // 单块扫描失败不影响其它结果
                }
            }
            // 原子交换渲染列表（渲染线程只读引用）
            this.normalList = rNormal[0] != null ? rNormal[0] : new ArrayList<>();
            this.bedrockList = rBedrock[0] != null ? rBedrock[0] : new ArrayList<>();
            this.airList = rAir[0] != null ? rAir[0] : new ArrayList<>();
            this.wallList = rWall[0] != null ? rWall[0] : new ArrayList<>();
            this.wallSideList = rWallSide[0] != null ? rWallSide[0] : new ArrayList<>();
        }
    }

    private Direction getWallSide(BlockPos pos, Vec3d eyePos) {
        double getDistance = Double.MAX_VALUE;
        Direction side = null;
        for (Direction direction : Direction.values()) {
            BlockPos offsetPos;
            if (direction == Direction.UP || direction == Direction.DOWN || !BlockUtil.canCollide(new Box((offsetPos = pos.offset(direction)).down())) || BlockUtil.canCollide(new Box(offsetPos)) || BlockUtil.canCollide(new Box(offsetPos.up()))) continue;
            double d = eyePos.distanceTo(offsetPos.toCenterPos());
            if (side == null || d < getDistance) {
                side = direction;
                getDistance = d;
            }
        }
        return side;
    }

    private boolean isBedrock(BlockPos pos) {
        return HoleESP.mc.world.getBlockState(pos).getBlock() == Blocks.BEDROCK;
    }

    Type isHole(BlockPos pos) {
        if (HoleESP.mc.world.isAir(pos) && (!this.airYCheck.getValue() || pos.getY() == HoleESP.mc.player.getBlockY() - 1 || pos.getY() == HoleESP.mc.player.getBlockY()) && RiyueClient.HOLE.isHard(pos.up())) {
            return Type.Air;
        }
        int blockProgress = 0;
        boolean bedRock = true;
        for (Direction i : Direction.values()) {
            if (i == Direction.UP || i == Direction.DOWN || !RiyueClient.HOLE.isHard(pos.offset(i))) continue;
            if (HoleESP.mc.world.getBlockState(pos.offset(i)).getBlock() != Blocks.BEDROCK) {
                bedRock = false;
            }
            ++blockProgress;
        }
        if (HoleESP.mc.world.isAir(pos) && HoleESP.mc.world.isAir(pos.up()) && HoleESP.mc.world.isAir(pos.up(2)) && blockProgress > 3 && BlockUtil.canCollide((Entity)HoleESP.mc.player, new Box(pos.down()))) {
            if (bedRock) {
                return Type.Bedrock;
            }
            return Type.Normal;
        }
        if (RiyueClient.HOLE.isDoubleHole(pos)) {
            return Type.Normal;
        }
        return Type.None;
    }

    @Override
    public void onRender3D(MatrixStack matrixStack) {
        this.drawing = true;
        this.draw(matrixStack, this.bedrockList, this.bedrockFill, this.bedrockFade, this.bedrockBox, this.height.getValue());
        this.draw(matrixStack, this.airList, this.airFill, this.airFade, this.airBox, this.airHeight.getValue());
        this.draw(matrixStack, this.normalList, this.normalFill, this.normalFade, this.normalBox, this.height.getValue());
        this.draw(matrixStack, this.wallList, this.wallFill, this.wallFade, this.wallBox, this.wallHeight.getValue());
        this.draw(matrixStack, this.wallSideList, this.wallSideFill, this.wallSideFade, this.wallSideBox, this.height.getValue());
        this.drawing = false;
    }

    private void draw(MatrixStack matrixStack, List<BlockPos> list, ColorSetting fill, ColorSetting fade, ColorSetting box, double height) {
        for (BlockPos pos : list) {
            double getDistance = HoleESP.mc.player.getEntityPos().distanceTo(pos.toCenterPos());
            double fadeRange = Math.max(1.0E-4, this.distance.getValue() - this.startFade.getValue());
            double alpha = getDistance > this.startFade.getValue() ? Math.max(Math.min(1.0, 1.0 - (getDistance - this.startFade.getValue()) / fadeRange), 0.0) : 1.0;
            Box espBox = new Box((double)pos.getX(), (double)pos.getY(), (double)pos.getZ(), (double)(pos.getX() + 1), (double)pos.getY() + height, (double)(pos.getZ() + 1));
            if (fill.booleanValue) {
                if (fade.booleanValue) {
                    Render3DScheduler.INSTANCE.addFade(espBox, ColorUtil.injectAlpha(fill.getValue(), (int)((double)fill.getValue().getAlpha() * alpha)), ColorUtil.injectAlpha(fade.getValue(), (int)((double)fade.getValue().getAlpha() * alpha)));
                } else {
                    Render3DScheduler.INSTANCE.addBox(espBox, ColorUtil.injectAlpha(fill.getValue(), (int)((double)fill.getValue().getAlpha() * alpha)), new Color(0, 0, 0, 0), false, true);
                }
            }
            if (!box.booleanValue) continue;
            Render3DScheduler.INSTANCE.addBox(espBox, new Color(0, 0, 0, 0), ColorUtil.injectAlpha(box.getValue(), (int)((double)box.getValue().getAlpha() * alpha)), true, false);
        }
    }

    public static enum Type {
        None,
        Air,
        Normal,
        Bedrock;

    }
}
