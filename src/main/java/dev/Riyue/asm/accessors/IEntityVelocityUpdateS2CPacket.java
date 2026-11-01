package dev.Riyue.asm.accessors;

import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 1.21.11 中该数据包的 velocityX/Y/Z 三个 int 字段已被合并为单个 Vec3d velocity 字段。
 * 旧的分量访问器会导致运行时 InvalidAccessorException，故改为暴露新字段。
 * （本模组代码当前无调用点。）
 */
@Mixin(EntityVelocityUpdateS2CPacket.class)
public interface IEntityVelocityUpdateS2CPacket {
    @Accessor("velocity")
    net.minecraft.util.math.Vec3d getVelocityVec();
}
