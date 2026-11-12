/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.screen.ChatScreen
 *  net.minecraft.client.gui.screen.GameMenuScreen
 *  net.minecraft.client.gui.screen.ingame.InventoryScreen
 *  net.minecraft.client.gui.screen.option.GameOptionsScreen
 *  net.minecraft.client.gui.screen.option.OptionsScreen
 *  net.minecraft.entity.Entity
 *  net.minecraft.entity.LivingEntity
 *  net.minecraft.entity.player.PlayerEntity
 *  net.minecraft.item.AxeItem
 *  net.minecraft.item.ItemStack
 *  net.minecraft.item.MaceItem
 *  net.minecraft.item.SwordItem
 *  net.minecraft.item.TridentItem
 *  net.minecraft.network.packet.Packet
 *  net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket
 *  net.minecraft.network.packet.c2s.play.HandSwingC2SPacket
 *  net.minecraft.util.Hand
 *  net.minecraft.util.hit.BlockHitResult
 *  net.minecraft.util.hit.HitResult$Type
 *  net.minecraft.util.math.BlockPos
 *  net.minecraft.util.math.Direction
 *  net.minecraft.util.math.Vec3d
 *  net.minecraft.world.RaycastContext
 *  net.minecraft.world.RaycastContext$FluidHandling
 *  net.minecraft.world.RaycastContext$ShapeType
 */
package dev.Riyue.api.utils.player;

import dev.Riyue.api.utils.Wrapper;
import dev.Riyue.api.utils.world.BlockPosX;
import dev.Riyue.api.utils.world.BlockUtil;
import dev.Riyue.mod.gui.ClickGuiScreen;
import dev.Riyue.mod.gui.PeekScreen;
import dev.Riyue.mod.modules.impl.client.AntiCheat;
import dev.Riyue.mod.modules.settings.enums.SwingSide;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.item.MaceItem;
import net.minecraft.item.TridentItem;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public class EntityUtil
implements Wrapper {
    public static boolean inInventory() {
        return EntityUtil.mc.currentScreen == null || EntityUtil.mc.currentScreen instanceof GameOptionsScreen || EntityUtil.mc.currentScreen instanceof OptionsScreen || EntityUtil.mc.currentScreen instanceof PeekScreen || EntityUtil.mc.currentScreen instanceof ChatScreen || EntityUtil.mc.currentScreen instanceof InventoryScreen || EntityUtil.mc.currentScreen instanceof ClickGuiScreen || EntityUtil.mc.currentScreen instanceof GameMenuScreen;
    }

    public static boolean isHoldingWeapon(PlayerEntity player) {
        ItemStack stack = player.getMainHandStack();
        return stack.isIn(ItemTags.SWORDS) || stack.isIn(ItemTags.AXES) || stack.getItem() instanceof MaceItem || stack.getItem() instanceof TridentItem;
    }

    public static boolean isInsideBlock(PlayerEntity player) {
        return BlockUtil.canCollide((Entity)player, player.getBoundingBox());
    }

    public static boolean isInsideBlock() {
        return EntityUtil.isInsideBlock((PlayerEntity)EntityUtil.mc.player);
    }

    public static int getDamagePercent(ItemStack stack) {
        if (stack.getDamage() == stack.getMaxDamage()) {
            return 100;
        }
        return (int)((double)(stack.getMaxDamage() - stack.getDamage()) / Math.max(0.1, (double)stack.getMaxDamage()) * 100.0);
    }

    public static boolean isArmorLow(PlayerEntity player, int durability) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack piece = player.getEquippedStack(slot);
            if (piece == null || piece.isEmpty()) {
                return true;
            }
            if (EntityUtil.getDamagePercent(piece) >= durability) continue;
            return true;
        }
        return false;
    }

    public static void resetLastAttackedTicks() {
        // 1.21.11 修复说明：
        // 旧实现用字符串反射 LivingEntity.class.getDeclaredField("lastAttackTime")：
        //  1) 反射用的字符串不会被 Loom 重映射，生产环境运行时是 intermediary 名（field_xxxx），
        //     必然抛 NoSuchFieldException，AutoCrystal 每次攻击都会刷一整条堆栈；
        //  2) LivingEntity.lastAttackTime 在 1.21.11 只是 onAttacking() 写入的 age 时间戳，
        //     与玩家攻击冷却完全无关，即使反射成功也起不到 resetCD 的作用。
        // 玩家攻击冷却实际由 PlayerEntity.ticksSinceLastAttack 控制，公开的官方重置入口是
        // resetTicksSinceLastAttack()，项目内 Aura / CombatUtil / ServerManager 已统一使用该调用。
        if (EntityUtil.mc.player != null) {
            EntityUtil.mc.player.resetTicksSinceLastAttack();
        }
    }

    public static float getHealth(Entity entity) {
        if (entity.isLiving()) {
            LivingEntity livingBase = (LivingEntity)entity;
            return livingBase.getHealth() + livingBase.getAbsorptionAmount();
        }
        return 0.0f;
    }

    public static BlockPos getEntityPos(Entity entity) {
        return new BlockPosX(entity.getEntityPos());
    }

    public static BlockPos getPlayerPos(boolean fix) {
        return new BlockPosX(EntityUtil.mc.player.getEntityPos(), fix);
    }

    public static BlockPos getEntityPos(Entity entity, boolean fix) {
        return new BlockPosX(entity.getEntityPos(), fix);
    }

    public static boolean canSee(BlockPos pos, Direction side) {
        Vec3d testVec = pos.toCenterPos().add((double)side.getVector().getX() * 0.5, (double)side.getVector().getY() * 0.5, (double)side.getVector().getZ() * 0.5);
        BlockHitResult result = EntityUtil.mc.world.raycast(new RaycastContext(EntityUtil.mc.player.getEyePos(), testVec, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, (Entity)EntityUtil.mc.player));
        return result == null || result.getType() == HitResult.Type.MISS;
    }

    public static void swingHand(Hand hand, SwingSide side) {
        switch (side) {
            case All: {
                EntityUtil.mc.player.swingHand(hand);
                break;
            }
            case Client: {
                EntityUtil.mc.player.swingHand(hand, false);
                break;
            }
            case Server: {
                mc.getNetworkHandler().sendPacket((Packet)new HandSwingC2SPacket(hand));
            }
        }
    }

    public static void syncInventory() {
        if (AntiCheat.INSTANCE.closeScreen.getValue()) {
            mc.getNetworkHandler().sendPacket((Packet)new CloseHandledScreenC2SPacket(EntityUtil.mc.player.currentScreenHandler.syncId));
        }
    }
}

