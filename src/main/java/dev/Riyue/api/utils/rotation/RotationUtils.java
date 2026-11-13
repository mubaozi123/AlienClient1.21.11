package dev.Riyue.api.utils.rotation;

import net.minecraft.util.math.Vec3d;

/**
 * 随仓库2 (dev.luminous) 的 ElytraFly 一起移植的精简版旋转计算工具。
 *
 * <p>仓库2 的完整 RotationUtils 还依赖 RayCastUtil、仓库2 自己的 RotationManager、
 * Alien.FPS、mc.player.prevYaw/prevPitch 等（这些在 MC 1.21.11 / 仓库1 中
 * 要么不存在、要么语义已变），而 ElytraFly 实际只用到 calculate(from, to)，
 * 因此这里只保留这一个自包含、无外部依赖的重载，算法与仓库2 完全一致：
 * <pre>
 *   diff = to - from; distance = hypot(diff.x, diff.z);
 *   yaw   = atan2(diff.z, diff.x) * 180/PI - 90;
 *   pitch = -(atan2(diff.y, distance) * 180/PI);
 * </pre>
 * 这样避免把一堆用不到且会编译失败的 1.21.1 依赖带进仓库1。
 */
public final class RotationUtils {
    private RotationUtils() {
    }

    public static Rotation calculate(Vec3d from, Vec3d to) {
        Vec3d diff = to.subtract(from);
        double distance = Math.hypot(diff.x, diff.z);
        float yaw = (float) (Math.atan2(diff.z, diff.x) * 57.2957763671875) - 90.0f;
        float pitch = (float) (-(Math.atan2(diff.y, distance) * 57.2957763671875));
        return new Rotation(yaw, pitch);
    }
}
