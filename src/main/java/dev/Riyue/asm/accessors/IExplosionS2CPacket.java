package dev.Riyue.asm.accessors;

import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 1.21.11 中 ExplosionS2CPacket 已重构为 record，playerVelocityX/Y/Z 字段不复存在，
 * 玩家击退合并为 Optional<Vec3d> playerKnockback 组件。旧访问器会导致运行时崩溃，
 * 现改为暴露 record 分量。（Blink 仅 import 未实际调用，行为不受影响。）
 */
@Mixin(ExplosionS2CPacket.class)
public interface IExplosionS2CPacket {
    @Accessor("playerKnockback")
    java.util.Optional<net.minecraft.util.math.Vec3d> getPlayerKnockbackCompat();
}
