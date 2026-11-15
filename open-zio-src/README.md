# open-zio 完整源码（原样引入，不参与构建）

本目录是用户提供的 `open-zio-main.zip` 的**完整、未经修改**的源码快照，用于对照与追溯。

- Java 源码：1198 个 `.java`
- 资源：91 个文件（含 25 个 GLSL shader、`fabric.mod.json`、`zio.mixins.json`、`zio.accesswidener`）
- 构建配置：`build.gradle`、`gradle.properties`

## 为什么不直接放进 `src/main/java`

| | open-zio | 仓库1（RiyueClient） |
|---|---|---|
| MC 版本 | 1.21.11 | 1.21.11（一致） |
| 映射 | **Mojang 官方映射** `loom.officialMojangMappings()` | **Yarn** `1.21.11+build.6` |
| 客户端框架 | `dev.zio.*`（自有事件总线 / 设置 / 模块 / 管理器） | `dev.Riyue.*` |
| Mixin | `zio.mixins.json`：218 个 | `riyue.mixins.json`：108 个 |
| AccessWidener | `zio.accesswidener`（官方命名空间，77 条） | `riyue.accesswidener`（Yarn 命名空间，22 条） |

两边虽然 MC 版本相同，但**映射命名空间不同**：同一个类在 zio 里叫
`net.minecraft.client.Minecraft` / `LocalPlayer` / `ClientLevel` / `EndCrystal`，
在仓库1 里叫 `MinecraftClient` / `ClientPlayerEntity` / `ClientWorld` / `EndCrystalEntity`。
因此 zio 源码**无法原样编译进仓库1**；其 218 个 mixin 与 accesswidener 也使用官方命名空间，
与仓库1 的 108 个 mixin、Yarn 版 AW 不兼容。

结论：可运行的代码只能是「保留 zio 全部功能与设置、逐行适配到 Yarn + Riyue 框架」的移植版，
主源码中的适配实现位于：

- `src/main/java/dev/Riyue/mod/modules/impl/combat/Crystalbot.java`（对应 zio 的 `AutoCrystalModule`）
- `src/main/java/dev/Riyue/mod/modules/impl/render/ShaderModule.java`（对应 zio 的 `ShaderModule`）
- `src/main/java/dev/Riyue/core/impl/ShaderManager.java`（渲染管线）
- `src/main/resources/assets/zio/shaders/**`、`assets/minecraft/shaders/core/zio_*`（GLSL 已逐字节原样覆盖）

## 本次任务对应的 zio 源码位置

**AutoCrystal → Crystalbot**

- `src/main/java/dev/zio/client/modules/combat/AutoCrystalModule.java`（2328 行主类）
- 同目录：`AutoCrystalTargeting` / `CrystalDamagePolicy` / `CrystalLethality` /
  `CrystalPlacementValidator` / `CrystalTargetDamage` / `CrystalPlacementKind` /
  `CrystalPlacementResult` / `CrystalDamageRenderer` / `LegacyCrystalModule` /
  `PistonCrystal*` / `TriggerCrystalsVisibilityPredicate`
- `src/main/java/dev/zio/client/managers/CrystalInteractionManager.java`
- `src/main/java/dev/zio/client/settings/options/AutoCrystal*Choice.java`
- `src/main/java/dev/zio/client/utils/combat/EndCrystalState.java`

**Shader**

- `src/main/java/dev/zio/client/modules/render/ShaderModule.java`（661 行）
- `src/main/java/dev/zio/client/managers/ShaderRegistry.java`
- `src/main/java/dev/zio/client/render/`（92 个类：`*ShaderPass` / `*Pipeline` / `ClientRenderPipelines` /
  `BundledResourceInstaller` / `BuiltinResourcePack` / `ShaderUniforms` 等）
- `src/main/java/dev/zio/mixin/` 中与渲染相关的 mixin（`GameRendererMixin` / `ShaderManagerMixin` /
  `LevelRendererMixin` 等）
- `src/main/resources/assets/zio/shaders/**`（25 个 GLSL）
