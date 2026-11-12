package dev.Riyue.asm.plugin;

import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Sodium 兼容 mixin 的条件开关。
 *
 * <p>riyue.sodium.mixins.json 里的 4 个 mixin 目标类全部来自 Sodium（remap = false）。
 * 未安装 Sodium 时，Mixin 仍会尝试解析这些目标类并打印：</p>
 *
 * <pre>
 * Error loading class: net/caffeinemc/mods/sodium/... (ClassNotFoundException)
 * &#64;Mixin target ... was not found riyue.sodium.mixins.json:SodiumBlockRendererMixin
 * </pre>
 *
 * <p>{@code required: false} 只能保证不崩溃，无法消除这些警告。通过在 config plugin 的
 * {@code shouldApplyMixin} 阶段直接拒绝，Mixin 连目标类都不会去加载，缺依赖时日志保持干净；
 * 安装 Sodium 时 4 个 mixin 照常生效（Xray 的 Sodium 兼容逻辑）。</p>
 *
 * <p>注意：项目编译 classpath 中的 sponge-mixin 0.17.3+mixin.0.8.7 把
 * IMixinConfigPlugin 的 7 个方法都声明为 abstract，而运行时的 0.17.5 版本是 default。
 * 因此这里显式实现全部方法（空实现走默认行为），保证两个版本下都能编译并正常工作。</p>
 */
public class SodiumMixinPlugin implements IMixinConfigPlugin {

    private static final String SODIUM_MOD_ID = "sodium";

    /**
     * 默认 false：即使 onLoad 因加载器尚未就绪而失败，也保守地跳过，
     * 避免缺依赖时去加载不存在的 Sodium 目标类。
     */
    private boolean sodiumLoaded;

    @Override
    public void onLoad(String mixinPackage) {
        try {
            this.sodiumLoaded = FabricLoader.getInstance().isModLoaded(SODIUM_MOD_ID);
        } catch (Throwable ignored) {
            // FabricLoader 在 mixin 配置加载阶段理论上已就绪；万一未就绪，
            // 宁可跳过 Sodium 兼容 mixin，也不要产生 ClassNotFoundException 噪音。
            this.sodiumLoaded = false;
        }
        // 与项目其余部分的日志风格一致（RiyueClient 也用 System.out.println）。
        // 启动时明确打印判定结果，便于确认 Sodium 兼容逻辑确实按预期启用/关闭。
        System.out.println("[RiyueClient] Sodium compat mixins: "
                + (this.sodiumLoaded ? "enabled" : "disabled (sodium not installed)"));
    }

    @Override
    public String getRefMapperConfig() {
        return null; // 使用 mixin 配置自带的 refMap（本配置 remap = false，实际不需要）
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return this.sodiumLoaded;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        // 不修改目标集合
    }

    @Override
    public List<String> getMixins() {
        return null; // 不额外追加 mixin
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass,
                         String mixinClassName, IMixinInfo mixinInfo) {
        // 默认行为
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass,
                          String mixinClassName, IMixinInfo mixinInfo) {
        // 默认行为
    }
}
