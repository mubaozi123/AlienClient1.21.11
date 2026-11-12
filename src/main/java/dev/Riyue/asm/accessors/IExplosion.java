package dev.Riyue.asm.accessors;

import net.minecraft.world.explosion.Explosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 1.21.11 中 Explosion 已改为接口，旧的可写字段(x/y/z/power/entity/world/damageSource)
 * 已不存在。原来用 @Accessor("power") 会在重映射阶段报
 * "Cannot remap power ... does not exist in any of the targets"，并可能让该 mixin 失效。
 * 这里改为通过 @Invoker 调用 1.21.11 真实存在的抽象方法 getPower()。
 */
@Mixin(Explosion.class)
public interface IExplosion {
    @Invoker("getPower")
    float getPowerValue();
}
