package dev.Riyue.asm.accessors;

import net.minecraft.client.gl.PostEffectPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 1.21.11 中 PostEffectPass 已重写为渲染管线架构，旧的 input/output Framebuffer 字段
 * 已被移除（改为 outputTargetId + Handle<Framebuffer>），且本模组代码无任何调用点。
 * 保留该接口以兼容旧引用，但不再声明任何 @Accessor，避免运行时 InvalidAccessorException。
 */
@Mixin(PostEffectPass.class)
public interface IPostProcessShader {
    @Accessor
    String getId();
}