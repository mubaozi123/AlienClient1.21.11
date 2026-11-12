package dev.Riyue.asm.mixins;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Particle.class)
public abstract class MixinParticle {

    @Shadow
    protected double velocityX;
    @Shadow
    protected double velocityY;
    @Shadow
    protected double velocityZ;

    // 这里不要再声明任何 setColor / setAlpha 方法。
    // 1.21.11 的 Particle 基类本身没有这两个方法（它们定义在子类 BillboardParticle 上）。
    // 旧实现在此写了一个裸 abstract setColor(float,float,float)，Mixin 会把它并入
    // 抽象基类 Particle，于是所有具体粒子子类都「继承」了一个没有实现的抽象方法，
    // 一旦调用就抛 AbstractMethodError（图腾粒子构造时即崩溃）。
    // 需要设置颜色的 mixin 请自行用 @Shadow 引用 BillboardParticle 上的真实方法。
}