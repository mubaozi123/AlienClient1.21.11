package dev.Riyue.asm.accessors;

import net.minecraft.world.explosion.Explosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 1.21.11 中 Explosion 已改为接口，旧的可写字段(x/y/z/power/entity/world/damageSource)
 * 已不存在，且本模组代码无任何调用点。仅保留对现存抽象 getter 的访问器以兼容旧引用。
 */
@Mixin(Explosion.class)
public interface IExplosion {
    @Accessor("power")
    float getPowerValue();
}
