package dev.Riyue.core.impl;

import dev.Riyue.mod.modules.impl.render.ShaderModule;

import java.awt.Color;

/**
 * open-zio 的 shader 设置语义 -> 仓库1 {@code RiyueParams} UBO 的适配层。
 *
 * <p>仓库1 的 GLSL（assets/riyueclient/shaders/core/*.fsh）使用一个固定的 std140 UBO：
 * <pre>
 *   vec4 Color;       // 主色（.a = alpha2 / alpha1）
 *   vec4 Color2;      // 次色
 *   vec4 Resolution;  // xy=尺寸 z=时间 w=不透明度(mixFactor)
 *   vec4 Glow;        // x=半径 y=质量 z=divider w=maxSample
 *   vec4 Color3;      // 第三色 / .w=octaves
 *   vec4 Color4;      // 第四色
 *   vec4 Extra;       // 仅 gradient.fsh 读取：x=factor y=moreGradient；z/w 全管线未使用
 * </pre>
 * zio 的 GLSL 则使用大量独立 uniform（u_FillColor / u_OutlineColor / u_Width /
 * u_GlowQuality / u_GlowInner / u_FastLines / u_Dots / u_Image / u_Overlay ...）
 * 并依赖 coverage/distance 多通道预处理。两者接口不兼容，且硬约束禁止改动资源文件，
 * 因此这里把 zio 设置「等价映射」到仓库1 现有 UBO 能表达的字段上，
 * 让 zio 的颜色、动画速度、渐变步长、描边宽度、发光强度、距离淡出等语义
 * 在不破坏现有可运行管线的前提下真正影响渲染结果。</p>
 *
 * <p><b>总开关语义（与 zio 实际行为对齐）</b>：
 * <ul>
 *   <li>zio 的 {@code Colors} / {@code Glow} / {@code Decorator} / {@code Targets} 等布尔
 *       都是 {@code markAsGroup()} 的 <i>UI 分组开关</i>：全工程没有任何
 *       {@code getValue()} 在渲染时读取它们，只决定子设置是否在界面展开。
 *       子设置（fill / outline / lineWidth / quality / weight / smooth / type ...）
 *       在 {@code SolidShaderPass} / {@code GradientShaderPass} / {@code RainbowShaderPass}
 *       中是<b>无条件</b>读取并写入 uniform 的。</li>
 *   <li>因此本适配层以 {@code ZioMode} 为唯一总开关：
 *       {@code ZioMode=true}（默认，对应「完整替换」）时 zio 的颜色/发光/渐变/淡出参数
 *       <b>无条件</b>参与 UBO 组装；{@code ZioMode=false} 时所有字段回落仓库1 原设置，
 *       画面 100% 等价于改动前。</li>
 *   <li>分组布尔仍然保留（UI 可见性谓词 + 配置键兼容），但不再错误地把效果关掉。</li>
 *   <li>簇透明度 / 距离淡出 / Image 叠加透明度默认均为 1.0；Smooth 默认 true，
 *       因此默认参数下不产生额外衰减。</li>
 * </ul>
 * </p>
 */
public final class ZioShaderAdapter {

    private ZioShaderAdapter() {
    }

    public static boolean zioMode(ShaderModule m) {
        return m != null && m.zioMode.getValue();
    }

    /**
     * zio 参数是否参与渲染。
     *
     * <p>名字叫 active 而不是 zioColors/zioGlow，是为了强调：zio 的颜色组与发光组
     * 都不是渲染开关，不能分别 gating；{@link #zioMode(ShaderModule)} 才是唯一开关。</p>
     */
    public static boolean active(ShaderModule m) {
        return zioMode(m);
    }

    /* ---------------- 颜色（对应 zio 的 u_FillColor / u_OutlineColor / 渐变三色） ---------------- */

    public static Color primary(ShaderModule m, Color legacy) {
        if (!active(m)) return legacy;
        // zio SolidShaderPass: shouldSkipHandFill -> ColorUtils.TRANSPARENT
        Color color = m.applyClusterAlpha(m.fillSetting.getValue());
        return m.shouldSkipHandFill() ? ShaderModule.transparent(color) : color;
    }

    public static Color secondary(ShaderModule m, Color legacy) {
        if (!active(m)) return legacy;
        return m.applyClusterAlpha(m.outlineColorSetting.getValue());
    }

    public static Color third(ShaderModule m, Color legacy) {
        if (!active(m)) return legacy;
        return m.applyClusterAlpha(m.fillSecondSetting.getValue());
    }

    public static Color fourth(ShaderModule m, Color legacy) {
        if (!active(m)) return legacy;
        return m.applyClusterAlpha(m.outlineSecondSetting.getValue());
    }

    /** zio GradientShaderPass 的 u_FillColor2 / u_OutlineColor2（HSB 混合色）。 */
    public static Color middle(ShaderModule m, Color legacy) {
        if (!active(m)) return legacy;
        return m.applyClusterAlpha(m.blendSettingColors(m.fillSetting, m.fillSecondSetting));
    }

    public static Color middleOutline(ShaderModule m, Color legacy) {
        if (!active(m)) return legacy;
        return m.applyClusterAlpha(m.blendSettingColors(m.outlineColorSetting, m.outlineSecondSetting));
    }

    /* ---------------- 时间 / 渐变 ---------------- */

    /** zio 的 GradientSpeed / RainbowSpeed 作为时间倍率（默认 1.0，不改变原速度）。 */
    public static float speed(ShaderModule m, ShaderManager.Shader shader) {
        float legacy = (m == null) ? 4.0f : m.speed.getValueFloat();
        if (!active(m)) return legacy;
        ShaderModule.ShaderChoice choice = m.shaderSetting.getValue();
        float factor = 1.0f;
        if (shader == ShaderManager.Shader.Rainbow || choice == ShaderModule.ShaderChoice.RAINBOW) {
            factor = m.rainbowSpeedSetting.getValueFloat();
        } else if (shader == ShaderManager.Shader.Gradient || choice == ShaderModule.ShaderChoice.GRADIENT) {
            factor = m.speedSetting.getValueFloat();
        }
        return legacy * factor;
    }

    /** zio GradientShaderPass: u_Step = step * 4.0F -> 仓库1 gradient.fsh 的 Extra.x (factor)。 */
    public static float gradientFactor(ShaderModule m, float legacy) {
        if (!active(m)) return legacy;
        return m.stepSetting.getValueFloat() * 4.0f;
    }

    /**
     * 仓库1 gradient.fsh 的 {@code Extra.y (moreGradient)} 是噪声空间缩放系数，
     * <b>zio 没有对应语义</b>：zio 的 {@code smoothSetting} 对应 {@code u_Smooth}
     * （GradientShaderPass -> util.frag 的 HSB 颜色插值平滑），不是空间缩放。
     * 它已被打包进 {@link #flags(ShaderModule)} 的 bit 32，留给后续 zio GLSL 适配。
     *
     * <p>因此这里始终回落 legacy(=1.0)。若把「关掉颜色平滑」误映射成
     * {@code moreGradient=0}，gradient.fsh 的噪声坐标会塌缩为 0、渐变退化成纯色，
     * 属于破坏现有可运行画面，故不这样映射。</p>
     */
    public static float gradientMore(ShaderModule m, float legacy) {
        return legacy;
    }

    /* ---------------- 发光（zio 的 lineWidth / quality / weight） ---------------- */

    /** zio u_Width -> 仓库1 GLSL 的 glowShader() 半径（Glow.x）。 */
    public static float glowRadius(ShaderModule m, float legacy) {
        if (!active(m)) return legacy;
        return Math.max(0.0f, m.lineWidthSetting.getValueFloat());
    }

    /** zio u_GlowQuality -> Glow.y。 */
    public static float glowQuality(ShaderModule m, float legacy) {
        if (!active(m)) return legacy;
        return Math.max(0.01f, m.qualitySetting.getValueFloat());
    }

    /** zio u_Glow (weight) 越大发光越强；仓库1 GLSL 的 divider 越大越弱，故取反比。 */
    public static float glowDivider(ShaderModule m, float legacy) {
        if (!active(m)) return legacy;
        return Math.max(1.0f, legacy / Math.max(0.01f, m.weightSetting.getValueFloat()));
    }

    public static float maxSample(ShaderModule m, float legacy) {
        if (!active(m)) return legacy;
        return legacy * Math.max(0.01f, m.weightSetting.getValueFloat());
    }

    /* ---------------- alpha ---------------- */

    /**
     * 把 zio 的距离淡出（Fade）和 Image/OverlayAlpha 折算成一个 UBO alpha 倍率。
     * 默认 1.0；{@code ZioMode=false} 时恒为 1.0（不改动原画面的任何 alpha）。
     *
     * <p><b>不</b>在这里再乘 {@code minimumClusterAlpha}：簇透明度已经由
     * {@code ShaderModule.applyClusterAlpha()} 乘进各个颜色通道（与 zio 一致），
     * 若再乘一次会变成平方衰减，导致开 ZioNoCluster 时画面过度变暗。</p>
     */
    public static float alphaMultiplier(ShaderModule m) {
        if (!active(m)) return 1.0f;
        float alpha = m.getMinimumFadeAlpha();
        if (m.imageSetting.getValue()) {
            alpha *= m.overlayAlphaSetting.getValueFloat();
        }
        return clamp01(alpha);
    }

    public static float glowAlpha(ShaderModule m, float legacy) {
        return legacy * alphaMultiplier(m);
    }

    /* ---------------- zio 专有参数打包（当前 GLSL 未读取，保留给后续 zio GLSL 适配） ---------------- */

    /**
     * 位标志打包进 UBO 的 Extra.z（Gradient/Smoke/Snow/Flow）或 Extra.w（Solid/Pulse/Rainbow）：
     * Image(1) / FastLines(2) / Glow(4) / Inner(8) / Decorator(16) /
     * Smooth(32) / Optimized(64) / NameTags(128) / HandFill(256) / HandOutline(512)。
     *
     * <p>注意：这 10 位里 Glow / Decorator 取的是 zio 的 UI 分组布尔值，
     * 仅作为「用户是否展开了该分组」的状态记录，不参与渲染判定；
     * 当前仓库1 GLSL 完全不读 Extra.z/w，这些位是惰性的数据占位。</p>
     */
    public static float flags(ShaderModule m) {
        if (m == null) return 0.0f;
        float flags = 0.0f;
        if (m.imageSetting.getValue()) flags += 1.0f;
        if (m.fastLinesSetting.getValue()) flags += 2.0f;
        if (m.glowSetting.getValue()) flags += 4.0f;
        if (m.innerSetting.getValue()) flags += 8.0f;
        if (m.decoratorSetting.getValue()) flags += 16.0f;
        if (m.smoothSetting.getValue()) flags += 32.0f;
        if (m.optimizedSetting.getValue()) flags += 64.0f;
        if (m.includesNameTags()) flags += 128.0f;
        if (m.handFillSetting.getValue()) flags += 256.0f;
        if (m.outlineSetting.getValue()) flags += 512.0f;
        return flags;
    }

    /** TypeChoice / DecoratorRadius / DecoratorAlpha 打包进 UBO 的 Extra.w（惰性）。 */
    public static float decorator(ShaderModule m) {
        if (m == null) return 0.0f;
        return m.typeSetting.getValue().ordinal() * 1000.0f
                + m.radiusSetting.getValueFloat() * 10.0f
                + m.alphaSetting.getValueFloat();
    }

    /**
     * zio rainbow.frag 的 u_Strength / u_StrengthLine 是「按屏幕坐标做色相偏移」的强度，
     * 不是 alpha；仓库1 GLSL 既无该 uniform 也无独立填充/描边通道，
     * 因此不折算进 alpha（避免错误映射），仅保留读取口供后续 zio GLSL 适配。
     */
    public static float fillStrength(ShaderModule m) {
        return m == null ? 1.0f : m.fillStrengthSetting.getValueFloat();
    }

    public static float lineStrength(ShaderModule m) {
        return m == null ? 1.0f : m.lineStrengthSetting.getValueFloat();
    }

    public static float lineWidth(ShaderModule m) {
        return m == null ? 1.0f : m.lineWidthSetting.getValueFloat();
    }

    private static float clamp01(float value) {
        if (Float.isNaN(value)) return 0.0f;
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
