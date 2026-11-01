package dev.Riyue.mod.modules.impl.movement;

import dev.Riyue.RiyueClient;
import dev.Riyue.api.events.eventbus.EventListener;
import dev.Riyue.api.events.impl.UpdateEvent;
import dev.Riyue.api.utils.combat.CombatUtil;
import dev.Riyue.api.utils.math.Timer;
import dev.Riyue.api.utils.player.EntityUtil;
import dev.Riyue.api.utils.player.InventoryUtil;
import dev.Riyue.api.utils.world.BlockPosX;
import dev.Riyue.api.utils.world.BlockUtil;
import dev.Riyue.mod.modules.Module;
import dev.Riyue.mod.modules.impl.exploit.Blink;
import dev.Riyue.mod.modules.settings.impl.BooleanSetting;
import dev.Riyue.mod.modules.settings.impl.SliderSetting;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.thrown.ExperienceBottleEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

public class Flatten extends Module {
    public static Flatten INSTANCE;
    private final BooleanSetting rotate =
            add(new BooleanSetting("Rotate", true));
    private final BooleanSetting checkMine =
            add(new BooleanSetting("DetectMining", true));
    private final BooleanSetting inventory =
            add(new BooleanSetting("InventorySwap", true));
    private final BooleanSetting usingPause =
            add(new BooleanSetting("UsingPause", true));
    private final BooleanSetting cover =
            add(new BooleanSetting("Cover", false));
    private final SliderSetting blocksPer =
            add(new SliderSetting("BlocksPer", 2, 1, 8));
    private final SliderSetting delay =
            add(new SliderSetting("Delay", 100, 0, 1000));
    private final Timer timer = new Timer();
    int progress = 0;

    public Flatten() {
        super("Flatten", Category.Movement);
        setChinese("填平脚下");
        INSTANCE = this;
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        progress = 0;
        if (inventory.getValue() && !EntityUtil.inInventory()) return;
        if (Blink.INSTANCE.isOn() && Blink.INSTANCE.pauseModule.getValue()) return;
        if (usingPause.getValue() && mc.player.isUsingItem()) {
            return;
        }
        if (!mc.player.isOnGround()) {
            return;
        }
        if (!timer.passed(delay.getValueInt())) return;
        int oldSlot = mc.player.getInventory().selectedSlot;
        int block;
        if ((block = getBlock()) == -1) {
            return;
        }
        if (!EntityUtil.isInsideBlock()) return;

        BlockPos pos1 = new BlockPosX(mc.player.getX() + 0.5, mc.player.getY() + 0.5, mc.player.getZ() + 0.5).down();
        BlockPos pos2 = new BlockPosX(mc.player.getX() - 0.5, mc.player.getY() + 0.5, mc.player.getZ() + 0.5).down();
        BlockPos pos3 = new BlockPosX(mc.player.getX() + 0.5, mc.player.getY() + 0.5, mc.player.getZ() - 0.5).down();
        BlockPos pos4 = new BlockPosX(mc.player.getX() - 0.5, mc.player.getY() + 0.5, mc.player.getZ() - 0.5).down();

        if (!canPlace(pos1) && !canPlace(pos2) && !canPlace(pos3) && !canPlace(pos4)) {
            return;
        }

        CombatUtil.attackCrystal(pos1, rotate.getValue(), usingPause.getValue());
        CombatUtil.attackCrystal(pos2, rotate.getValue(), usingPause.getValue());
        CombatUtil.attackCrystal(pos3, rotate.getValue(), usingPause.getValue());
        CombatUtil.attackCrystal(pos4, rotate.getValue(), usingPause.getValue());

        doSwap(block);
        tryPlaceObsidian(pos1, rotate.getValue());
        tryPlaceObsidian(pos2, rotate.getValue());
        tryPlaceObsidian(pos3, rotate.getValue());
        tryPlaceObsidian(pos4, rotate.getValue());

        if (inventory.getValue()) {
            doSwap(block);
            EntityUtil.syncInventory();
        } else {
            doSwap(oldSlot);
        }
    }

    private void tryPlaceObsidian(BlockPos pos, boolean rotate) {
        if (canPlace(pos)) {
            if (!(progress < blocksPer.getValue())) return;
            if (BlockUtil.allowAirPlace()) {
                BlockUtil.placedPos.add(pos);
                BlockUtil.airPlace(pos, rotate);
                timer.reset();
                progress++;
                return;
            }
            Direction side = BlockUtil.getPlaceSide(pos);
            if (side == null) return;
            progress++;
            BlockUtil.placedPos.add(pos);
            BlockUtil.clickBlock(pos.offset(side), side.getOpposite(), rotate);
            timer.reset();
        }
    }

    private void doSwap(int slot) {
        if (inventory.getValue()) {
            InventoryUtil.inventorySwap(slot, mc.player.getInventory().selectedSlot);
        } else {
            InventoryUtil.switchToSlot(slot);
        }
    }

    private boolean canPlace(BlockPos pos) {
        if (checkMine.getValue() && RiyueClient.BREAK.isMining(pos)) {
            return false;
        }
        if (cover.getValue() && mc.world.isAir(pos.up())) {
            return false;
        }
        if (BlockUtil.getPlaceSide(pos) == null) {
            return false;
        }
        if (!BlockUtil.canReplace(pos)) {
            return false;
        }
        return !hasEntity(pos);
    }

    private boolean hasEntity(BlockPos pos) {
        for (Entity entity : BlockUtil.getEntities(new Box(pos))) {
            if (entity == mc.player) continue;
            if (!entity.isAlive() || entity instanceof ItemEntity || entity instanceof ExperienceOrbEntity || entity instanceof ExperienceBottleEntity || entity instanceof ArrowEntity || entity instanceof EndCrystalEntity)
                continue;
            return true;
        }
        return false;
    }

    private int getBlock() {
        if (inventory.getValue()) {
            return InventoryUtil.findBlockInventorySlot(Blocks.OBSIDIAN);
        } else {
            return InventoryUtil.findBlock(Blocks.OBSIDIAN);
        }
    }
}