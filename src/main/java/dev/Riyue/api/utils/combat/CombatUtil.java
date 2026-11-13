package dev.Riyue.api.utils.combat;

import dev.Riyue.RiyueClient;
import dev.Riyue.api.utils.Wrapper;
import dev.Riyue.api.utils.math.MathUtil;
import dev.Riyue.api.utils.math.Timer;
import dev.Riyue.api.utils.player.EntityUtil;
import dev.Riyue.api.utils.world.BlockUtil;
import dev.Riyue.mod.modules.impl.client.AntiCheat;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;

public class CombatUtil implements Wrapper {
    public static final Timer breakTimer = new Timer();
    public static boolean terrainIgnore = false;
    public static BlockPos modifyPos;
    public static BlockState modifyBlockState = Blocks.AIR.getDefaultState();

    /**
     * 伤害计算期间 terrainIgnore 会把大量 getBlockState 引到 MixinWorld 的改写路径，
     * 该路径需要逐方块做 chunk 定位（成本约等于原始 getBlockState，等于双倍开销）。
     * 这里按线程缓存最近使用的区块引用：爆炸暴露计算空间连续，命中率极高。
     * 缓存生命周期 = 单次伤害计算，调用方在计算开始时调用 {@link #invalidateChunkCache()}。
     */
    private static final ThreadLocal<WorldChunk> CACHED_CHUNK = new ThreadLocal<>();
    /** key: [world引用, chunkX, chunkZ] —— world 引用参与比较，世界重建/换维度时自动失配。 */
    private static final ThreadLocal<Object[]> CACHED_CHUNK_KEY = ThreadLocal.withInitial(() -> new Object[3]);

    public static WorldChunk cachedChunk(World world, int chunkX, int chunkZ) {
        Object[] key = CACHED_CHUNK_KEY.get();
        if (key[0] == world && (Integer) key[1] == chunkX && (Integer) key[2] == chunkZ) {
            WorldChunk cached = CACHED_CHUNK.get();
            if (cached != null) {
                return cached;
            }
        }
        WorldChunk chunk = world.getChunk(chunkX, chunkZ);
        CACHED_CHUNK.set(chunk);
        key[0] = world;
        key[1] = chunkX;
        key[2] = chunkZ;
        return chunk;
    }

    public static void invalidateChunkCache() {
        CACHED_CHUNK_KEY.get()[0] = null;
        CACHED_CHUNK_KEY.get()[1] = Integer.MIN_VALUE;
        CACHED_CHUNK_KEY.get()[2] = Integer.MIN_VALUE;
        CACHED_CHUNK.set(null);
    }

    public static List<PlayerEntity> getEnemies(double range) {
        List<PlayerEntity> list = new ArrayList<>();
        for (AbstractClientPlayerEntity player : RiyueClient.THREAD.getPlayers()) {
            if (!isValid(player, range)) continue;
            list.add(player);
        }
        return list;
    }

    public static void attackCrystal(BlockPos pos, boolean rotate, boolean eatingPause) {
        attackCrystal(new Box(pos), rotate, eatingPause);
    }

    public static void attackCrystal(Box box, boolean rotate, boolean eatingPause) {
        for (EndCrystalEntity entity : BlockUtil.getEndCrystals(box)) {
            attackWithDelay(entity, rotate, eatingPause);
        }
    }

    public static void attackWithDelay(Entity entity, boolean rotate, boolean usingPause) {
        if (!CombatUtil.breakTimer.passed((long) (AntiCheat.INSTANCE.attackDelay.getValue() * 1000))) return;
        if (usingPause && mc.player.isUsingItem())
            return;
        attack(entity, rotate);
    }

    public static void attack(Entity entity, boolean rotate) {
        if (entity != null) {
            Vec3d attackVec = MathUtil.getClosestPointToBox(mc.player.getEyePos(), entity.getBoundingBox());
            if (mc.player.getEyePos().distanceTo(attackVec) > AntiCheat.INSTANCE.ieRange.getValue()) return;
            CombatUtil.breakTimer.reset();
            if (rotate && AntiCheat.INSTANCE.attackRotate.getValue())
                RiyueClient.ROTATION.lookAt(attackVec);
            mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(entity, mc.player.isSneaking()));
            mc.player.resetTicksSinceLastAttack();
            EntityUtil.swingHand(Hand.MAIN_HAND, AntiCheat.INSTANCE.attackSwing.getValue());
            if (rotate && AntiCheat.INSTANCE.attackRotate.getValue()) {
                RiyueClient.ROTATION.snapBack();
            }
        }
    }

    public static boolean isntValid(Entity entity, double range) {
        return !isValid(entity, range);
    }

    public static boolean isValid(Entity entity, double range) {
        boolean invalid = entity == null || !entity.isAlive() || entity.equals(mc.player) || entity instanceof PlayerEntity player && RiyueClient.FRIEND.isFriend(player) || mc.player.getEntityPos().distanceTo(entity.getEntityPos()) > range;

        return !invalid;
    }

    public static boolean isValid(Entity entity) {
        boolean invalid = entity == null || !entity.isAlive() || entity.equals(mc.player) || entity instanceof PlayerEntity player && RiyueClient.FRIEND.isFriend(player);

        return !invalid;
    }

    public static PlayerEntity getClosestEnemy(double distance) {
        PlayerEntity closest = null;

        for (PlayerEntity player : getEnemies(distance)) {
            if (closest == null) {
                closest = player;
                continue;
            }

            if (!(mc.player.squaredDistanceTo(player.getEntityPos()) < mc.player.squaredDistanceTo(closest))) continue;

            closest = player;
        }
        return closest;
    }
}
