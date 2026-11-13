package dev.Riyue.api.utils.rotation;

/**
 * 简单旋转容器，随仓库2 (dev.luminous) 的 ElytraFly 一起移植。
 * 保留仓库1 现有 RotationManager 不受影响；此处仅作为 ElytraFly FollowPlayer
 * 计算结果的载体（yaw/pitch）。
 */
public class Rotation {
    private float yaw;
    private float pitch;

    public Rotation(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public float getYaw() {
        return yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }
}
