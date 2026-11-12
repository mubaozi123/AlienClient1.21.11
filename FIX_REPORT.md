# RiyueClient 1.21.11 修复报告

> 目标仓库：`mubaozi123/AlienClient1.21.11`（本地路径 `/workspace/AlienClient1.21.11`）
> 修复对象：Fabric 1.21.11 客户端，429 个 Java 源文件、107 条 mixin 注册项
> 交付：修复后的完整工程 + 重新构建的 `riyueclient-1.3.3.jar`

---

## 一、结论先行

原仓库**能编译、也能启动**，所以「特别多的 bug」并不是「一启动就崩」，而是集中在一批**升级到 1.21.11 时遗留的结构性缺陷**上：

- mixin 配置的兼容性级别停留在 Java 17，却用 Java 21 编译 → 每次启动产生 86 条 `Class version 65 ... JAVA_17 supports class version 61` 警告，整个 mixin 子系统被降级处理；
- 一个失效的 `@Accessor("power")` → 构建期 `Cannot remap power`；
- 元数据里残留模板占位符、环境声明错误、版本约束过严；
- 配置目录依赖 `static final MinecraftClient mc`，存在启动期空指针脆弱性；
- 同一个 mixin 被重复注册。

以上问题已全部修复，并通过**真实无头启动 + 完整 debug 日志**验证。构建期 `Cannot remap` 警告归零，本项目相关的 mixin 兼容性警告从 86 条降到 0 条。

---

## 二、诊断方法（不是只看编译）

| 手段 | 目的 |
| --- | --- |
| `./gradlew clean build` 全量构建日志 | 捕获 Loom 的 `Cannot remap ...` 警告（编译通过不代表运行期可用） |
| `xvfb-run` + `./gradlew runClient` 无头启动 | 拿到真实的 `run/logs/debug.log`，观察 mixin 应用、模组初始化、崩溃栈 |
| Yarn 命名映射 jar + `javap` | 逐一核对目标类的方法/字段是否真实存在 |
| 对 `lib/*.jar` 做字符串检索 | 判定警告到底来自本项目还是第三方依赖 |

**关键澄清**：首次扫描曾列出 16 条「可疑失效引用」，经用正确的 jar 复核后，其中大部分是**误报**，本次没有盲目改动：

| 曾被误判 | 实测结果 |
| --- | --- |
| `ModelPart.cuboids` / `ModelPart.children` | 1.21.11 中均为 `private final` 字段，**存在** |
| `ModelPart$Cuboid.sides` | `public final Quad[] sides`，**存在** |
| `SimpleRegistry.valueToEntry` | `private final Map<T, Reference<T>>`，**存在** |
| `AbstractBlockState.getLuminance()` | `public int getLuminance()`，**存在**，注入已生效 |
| `<init>` / `<clinit>` 目标 | `javap` 不列出构造/静态初始化名，属脚本误判 |
| 4 个 Sodium mixin 目标 | `shouldDrawSide` / `renderModel` / `shouldUseOcclusionCulling` 均存在；`FluidRendererImpl` 的真实包是 `sodium.fabric.render`，源码 import **本来就是对的** |
| 5 条 `Cannot remap`（`renderSingleBlock`、`iterateVisibleBlockEntities`、`lambda$init$2`、`lambda$addMainPass$1`、`modifiers`） | 字符串命中 `lib/sodium-fabric-0.8.14-beta.2+mc1.21.11.jar`，是 **Sodium 自带 mixin** 的 Mojang/Yarn 映射差异，与本项目无关 |
| `Error loading class: net/minecraft/client/player/LocalPlayer` | 来自第三方 mod 的 Mojang 名 mixin，软告警，非本项目 |
| ALSA / OpenAL / narrator / 401 / Realms 报错 | 无头沙箱没有声卡、显卡、离线账号所致，**非模组 bug** |

真正属于本项目的失效引用只有 `IExplosion.power` 一处。

---

## 三、修复清单

| # | 文件 | 问题 | 修复 |
| --- | --- | --- | --- |
| 1 | `src/main/resources/riyue.mixins.json` | `compatibilityLevel: JAVA_17`，而产物是 Java 21（class version 65）；启动日志 86 条降级警告 | 改为 `JAVA_21`（与 `riyue.sodium.mixins.json` 一致） |
| 2 | 同上 | `accessors.ISimpleRegistry` 同时出现在 `client` 和 `mixins` 列表，107 条注册里重复 1 条 | 从 `client` 移除，仅在 `mixins`（common）保留一次 |
| 3 | `src/main/java/dev/Riyue/asm/accessors/IExplosion.java` | 1.21.11 的 `Explosion` 已改为接口，字段 `power` 不存在；`@Accessor("power")` 导致 `Cannot remap power` | 改为 `@Invoker("getPower")`，调用现存的抽象方法 `float getPower()` |
| 4 | `src/main/resources/fabric.mod.json` | `environment: "*"`，但 `Manager.mc` 等大量代码引用客户端专属类 `MinecraftClient` | 改为 `"client"` |
| 5 | 同上 | `suggests: {"another-mod": "*"}` 是官方模板残留 | 改为实际相关的 `{"sodium": "*", "baritone": "*"}` |
| 6 | 同上 | `minecraft: "1.21.11"` 精确匹配过严；`fabricloader: ">=0.16.0"` 与实际使用的 `0.19.3` 不符 | 改为 `"~1.21.11"` 与 `">=0.19.3"` |
| 7 | `src/main/java/dev/Riyue/core/Manager.java` | `getFolder()` 用 `mc.runDirectory`；而 `mc` 是 `static final MinecraftClient mc = MinecraftClient.getInstance()`，一旦类在窗口创建前初始化就永久为 null，所有配置读写启动期 NPE | 改用 `FabricLoader.getInstance().getGameDir()`，语义等价且不依赖客户端实例 |
| 8 | `src/main/java/dev/Riyue/RiyueClient.java` | `onInitialize()` 里同样用 `MinecraftClient.getInstance().runDirectory` 创建 `riyueclient/cfg` | 改用 `FabricLoader.getInstance().getGameDir()`，并补 import |
| 9 | `README.md` | 标题写 1.21.1、称 `nether-pathfinder` 和 IAS 已禁用，但 `build.gradle` 实际启用了 `nether-pathfinder-1.4.1`（启动日志可见其 native 库加载） | 修正版本描述与依赖清单，与实际构建配置对齐 |

**未改动且有依据的判断**：
- 源码只 import `net.fabricmc.api.*`（由 Fabric Loader 提供），**完全没有引用 Fabric API**，因此不给 `depends` 强行添加 `fabric-api`，避免制造一个并不存在的硬依赖。
- access widener 的 `validateAccessWidener` 任务通过，无需修改。
- `MixinMain` 的 `@Redirect` 看似「无条件把所有 `setProperty` 改掉」，但字节码显示 `Main.<clinit>` 里只有一次 `System.setProperty("java.awt.headless","true")`，改成 `false` 是为 Swing 弹窗服务，属作者有意为之，不改。
- 第三方 Sodium / Baritone jar 内的 mixin 配置不属于本仓库职责，不修改。

---

## 四、验证结果

| 项目 | 修复前 | 修复后 |
| --- | --- | --- |
| `clean build` | 成功，但 6 条 `Cannot remap` | **成功，0 条** `Cannot remap` |
| 本项目 mixin 兼容性警告 | 86 条 `Class version 65 ... JAVA_17` | **0 条** |
| 无头启动 | 进入资源加载阶段 | 进入资源加载阶段，`[RiyueClient] Initialized in 291 ms` |
| `InvalidInjectionException` / `Critical injection` | 0 | **0** |
| `Deferred init failed`（十几个管理器初始化） | 无 | **无** |
| mixin 重复注册 | 1 处 | **0** |
| 成品 jar | 1,333,751 B | 1,333,649 B（598 个 class） |

**成品 jar 内容核对**：
- `fabric.mod.json` 内 `environment: client`、`depends`、`suggests` 均为修复后的值；
- `riyue.mixins.json` 内 `compatibilityLevel: JAVA_21`，`ISimpleRegistry` 只出现一次；
- `IExplosion.class` 的 `@Invoker` 值被 Loom 正确重映射为 `method_55107`（即 `Explosion.getPower()` 的 intermediary 名），证明注入目标真实存在。

**关于剩余的 88 条 DEBUG 警告**：二次启动日志里仍能看到 `Class version 65 ... JAVA_17`，但按 mod 归属统计为 **Sodium 74 条 + Baritone 14 条，`riyue.mixins.json` 0 条**。这是这两个第三方 mod 自己在 mixin 配置里声明了 `JAVA_17`，属于上游问题，不影响本模组运行。

---

## 五、遗留事项（如实说明）

1. **Shader / Satin 相关模块**：原 README 作者明确写了「以下模块仍未修复或存在问题：Shader 和 satin」「Satin 现已完全移除，其相关渲染暂时不会回归」。这是 1.21.11 渲染管线变更造成的**功能缺失**，需要重写 shader 管线，不是崩溃型 bug；本次未触碰。
2. **进入世界后的模块行为**：本次验证覆盖到客户端资源加载与全部 mixin 应用阶段。429 个文件里各玩法模块（战斗、移动、渲染等）的实际效果，需要在有图形环境的真人客户端里逐项测试，无法在 Xvfb 无头沙箱中穷尽。
3. **第三方依赖**：Sodium 与 Baritone 建议与仓库 `lib/` 中的 1.21.11 版本配套使用；版本错配会导致 `NoClassDefFoundError`。

---

## 六、使用与重建

**安装**：将 `build/libs/riyueclient-1.3.3.jar` 放入 `.minecraft/mods/`，需要 Minecraft 1.21.11 + Fabric Loader ≥ 0.19.3 + Java 21；建议同时安装 Sodium `0.8.14-beta.2+mc1.21.11` 与 Baritone `1.21.11`。

**重建**（本沙箱环境中 Gradle/Loom 1.17.x 需 JVM 25，产物仍是 Java 21 字节码）：

```bash
cd /workspace/AlienClient1.21.11
JAVA_HOME=/opt/jdk25 ./gradlew clean build
```

**无头验证**：

```bash
xvfb-run -a env JAVA_HOME=/opt/jdk25 ./gradlew runClient
```

---

## 七、追加修复：实机崩溃 `AbstractMethodError`（不死图腾粒子）

用户提供了一份真实崩溃报告（Windows 7 / Java 21 / Folia 服务器，1.21.11），触发场景是**使用不死图腾**：

```
java.lang.AbstractMethodError: Receiver class net.minecraft.class_734 does not define or
inherit an implementation of the resolved method 'abstract void setColor(float, float, float)'
of abstract class net.minecraft.class_703.
    at net.minecraft.class_734.handler$bfl000$riyueclient$hookInit(class_734.java:529)
```

调用链：`clientbound/minecraft:entity_event` → 添加 `minecraft:totem_of_undying` 粒子 →
`TotemParticle.<init>` → RiyueClient 的 `hookInit` 注入。

### 根因

`class_703` = `Particle`，`class_734` = `TotemParticle`。`MixinParticle`（混入 `Particle` 基类）
里有两个**没有任何 Mixin 注解**的方法：

```java
public abstract void setColor(float red, float green, float blue);
protected void setAlpha(float alpha) {}   // 空实现
```

Mixin 会把它们**并入目标类 `Particle`**。但 1.21.11 的 `Particle` 基类里根本没有这两个方法，
它们定义在子类 `BillboardParticle` 上（`public void setColor(FFF)` / `protected void setAlpha(F)`）。
结果 `Particle` 凭空多出一个 abstract 方法，所有具体粒子子类都没有实现 → 一调用就抛
`AbstractMethodError`；`setAlpha` 则是空实现，从未真正生效过。

影响面**不止图腾粒子**：所有继承 `Particle` 的类都被污染，只是恰好在图腾粒子构造路径上被触发。

### 全量排查（同类隐患）

项目里共有 11 处「无注解的 abstract 方法」（Mixin 惯用法：用来引用目标类里已存在的方法）。
沿继承链用 `javap` 逐一核对后确认：**只有 `MixinParticle.setColor` 失效**，其余 10 处
（`Camera.clipToSpace`、`ChatHud.addMessage`、`ChatInputSuggestor.show`、
`ClientPlayNetworkHandler.sendChatMessage`、`ClientPlayerEntity.getPitch/getYaw`、
`HandledScreen.renderBackground`、`InGameHud.render`、`LivingEntity.remove`、
`PlayerListHud.collectPlayerEntries`）在目标类中均真实存在，属合法用法，无需改动。

### 修复

1. `MixinParticle.java`：删除两个假方法，只保留 `@Shadow` 的 `velocityX/Y/Z` 字段，
   `Particle` 基类不再被污染。
2. `MixinTotemParticle.java`：改为直接调用 1.21.11 真实存在的
   `BillboardParticle.setColor(FFF)`（public），保留自定义 RGB 功能；
   `setAlpha` 因是 protected 且旧实现为空，改为保持粒子默认 alpha。

### 一次被运行时验证拦下的错误尝试

最初尝试用 `@Shadow` 声明来引用父类方法，**构建成功、字节码检查也正常**，但真实启动时 Mixin 报错：

```
InvalidMixinException: @Shadow method setColor(FFF)V ... was not located in the target
class net.minecraft.client.particle.TotemParticle. No refMap loaded.
```

原因是 Mixin 的 `@Shadow` 只在目标类自身查找方法，不会沿父类链找到 `BillboardParticle`。
这印证了「编译通过 + 字节码检查 ≠ 运行期可用」，最终改为普通 `invokevirtual` 调用。
**这次失败是主动做无头启动验证才发现的**，否则会把一个启动即崩的版本交付出去。

### 验证结果

| 检查项 | 结果 |
| --- | --- |
| `clean build` | 成功，`Cannot remap` **0 条** |
| `MixinParticle.class` 字节码 | 只剩 3 个 `@Shadow` 字段，**零个方法** → `Particle` 不再被污染 |
| `hookInit` 调用点字节码 | `checkcast class_3940`（=`BillboardParticle`）+ `invokevirtual class_3940.method_74305:(FFF)V`，指向 **concrete** 方法，不再是 `Particle` 上的 abstract 方法 |
| 无头启动 | 成功，`[RiyueClient] Initialized in 254 ms` |
| `MixinParticle` / `MixinTotemParticle` 应用日志 | 均正常 Mixing，**无异常** |
| `InvalidMixinException` / `AbstractMethodError` / `Mixin apply failed` | **0 条** |

### 实机环境的两点印证

- 用户崩溃报告的 mod 列表里**没有 Sodium / Baritone**，客户端仍正常启动并进入世界，
  印证了把二者放在 `suggests` 而非 `depends` 是正确的（若设为硬依赖，用户会直接被拒绝启动）。
- 缺 Sodium 时 `riyue.sodium.mixins.json` 的 `required: false` 只保证「不崩溃」，
  并不能消除 4 条 `@Mixin target ... was not found` 警告；该警告在第八章用条件化 plugin 彻底解决。

**遗留提示**：`ModuleManager` 会无条件实例化 `BaritoneModule`，该类方法体引用了 `baritone.api.*`。
未安装 Baritone 时类本身可以加载（JVM 延迟解析），但一旦调用到相关方法会 `NoClassDefFoundError`。
若要用 Baritone 相关模块，请同时安装对应版本的 Baritone；本次未改动模块注册逻辑，避免扩大改动面。

---

## 八、第二轮实机日志修复（`接着修`）

### 8.1 日志拆解：四类问题，只有两类属于模组 Java bug

| 现象 | 定性 | 本轮处理 |
| --- | --- | --- |
| 图腾粒子 `AbstractMethodError` 仍复现 | **旧 jar 问题**，非代码问题 | 见 8.5，必须替换 `mods/` 中的 jar |
| 4 条 `@Mixin target ... was not found`（Sodium 未装） | 模组 mixin 配置缺陷 | 8.3 条件化 plugin 修复 |
| `NoSuchFieldException: lastAttackTime` 反复刷堆栈 | 模组反射实现缺陷 | 8.2 修复 |
| `EXCEPTION_ACCESS_VIOLATION` in `atio6axx.dll` | **AMD/ATI OpenGL 驱动 native 崩溃**，非 Java 层 | 8.6 说明处置路径 |
| 401 / Realms / narrator / LAN / `iCCP: known incorrect sRGB` | 离线账号与无头 / 驱动环境噪音 | 不在模组内处理 |

### 8.2 修复 `EntityUtil.resetLastAttackedTicks()`：反射字符串在生产环境必然失效

旧实现：

```java
Field field = LivingEntity.class.getDeclaredField("lastAttackTime");
field.setAccessible(true);
field.setInt(EntityUtil.mc.player, 0);
```

用 Yarn 1.21.11 named jar + `javap -c` 逐字节核对后，确认这是**两个叠加错误**：

1. **映射错误（直接原因）**：`getDeclaredField("lastAttackTime")` 里的字符串是 Yarn named 名，
   反射字符串不会随 Loom / Fabric 重映射。用户生产环境运行时是 intermediary 名（`field_xxxx`），
   因此必然 `NoSuchFieldException`，`AutoCrystal` 每次攻击都打印整条堆栈
   （`AutoCrystal.java:619`、`:677`）。
2. **语义错误（更根本）**：`LivingEntity` 中其实**同时存在**两个相似字段：
   `lastAttackedTime`（被攻击时间戳，有 `getLastAttackedTime()`）与 `lastAttackTime`
   （`onAttacking()` 写入的 `age` 时间戳，有 `getLastAttackTime()`）。两者都不是玩家攻击冷却；
   1.21.11 的攻击冷却由 `PlayerEntity.ticksSinceLastAttack` 控制，`getAttackCooldownProgress()`
   只读该字段，公开重置入口是 `resetTicksSinceLastAttack()`。也就是说，旧代码即使反射成功，
   `resetCD` 也起不到作用。

修复为调用官方 public 方法，并与项目内 `Aura`、`CombatUtil`、`ServerManager` 已有写法统一：

```java
public static void resetLastAttackedTicks() {
    if (EntityUtil.mc.player != null) {
        EntityUtil.mc.player.resetTicksSinceLastAttack();
    }
}
```

移除 `java.lang.reflect.Field` import。最终 jar 字节码核对：
`invokevirtual net/minecraft/class_746.method_75203:()V`
（mappings 中 `method_75203` = `PlayerEntity.resetTicksSinceLastAttack()`，已被正确重映射为
intermediary 名）、`getDeclaredField` 出现 **0 次**。

### 8.3 修复缺 Sodium 时的 4 条类加载警告：`IMixinConfigPlugin` 条件化

`riyue.sodium.mixins.json` 的 4 个 mixin 目标全部来自 Sodium（`remap = false`）。
`required: false` 只能避免启动崩溃，Mixin 仍会尝试解析目标类并打印：

```
Error loading class: net/caffeinemc/mods/sodium/... (ClassNotFoundException)
@Mixin target ... was not found riyue.sodium.mixins.json:...
```

新增 `src/main/java/dev/Riyue/asm/plugin/SodiumMixinPlugin.java`，在 `onLoad` 中
用 `FabricLoader.getInstance().isModLoaded("sodium")` 判定，在 `shouldApplyMixin` 中拒绝或放行，
并在配置里注册 `"plugin": "dev.Riyue.asm.plugin.SodiumMixinPlugin"`。未装 Sodium 时 Mixin
连目标类都不会去加载，日志保持干净；装了 Sodium 时 4 个 mixin 照常应用（Xray 的
Sodium 兼容逻辑保留）。`sodiumLoaded` 默认 `false`，即使加载器尚未就绪也走保守跳过分支。

> **一个编译期坑**：编译 classpath 里的 `sponge-mixin 0.17.3+mixin.0.8.7` 把
> `IMixinConfigPlugin` 的 7 个方法全声明为 `abstract`，而运行时的 `0.17.5` 版本是 `default`。
> 只覆盖 `onLoad` + `shouldApplyMixin` 会报
> `SodiumMixinPlugin is not abstract and does not override abstract method postApply(...)`。
> 最终显式实现全部 7 个方法（空实现走默认行为），两个版本下都能编译运行。

### 8.4 验证结果（两次真实无头启动，覆盖有 / 无 Sodium 两条分支）

缺 Sodium 环境通过**临时**把 `build.gradle` 的
`modImplementation name: 'sodium-fabric-...'` 改为 `modCompileOnly` 构造，验证后已恢复为
`modImplementation` 并重新 `clean build` 生成最终交付 jar。

| 检查项 | 有 Sodium（54 mods） | 缺 Sodium（临时构造） |
| --- | --- | --- |
| plugin 判定输出 | `Sodium compat mixins: enabled` | `Sodium compat mixins: disabled (sodium not installed)` |
| mod 列表含 sodium | 1 | 0 |
| `@Mixin target ... was not found` / `Error loading class: net/caffeinemc` | **0** | **0**（修复前为 4） |
| `InvalidMixinException` / `Mixin apply failed` / `NoSuchFieldException` | 0 | 0 |
| `[RiyueClient] Initialized in ...` | 成功 | 成功 |
| `clean build` | `BUILD SUCCESSFUL`，`Cannot remap` **0 条** | 同左 |
| 最终 jar | `build/libs/riyueclient-1.3.3.jar`，含 `SodiumMixinPlugin.class` 与 `riyue.sodium.mixins.json` | — |

日志里残留的唯一 `Error loading class: net/minecraft/client/player/LocalPlayer` 来自第三方
Mojang 名 mixin，与本项目无关；ALSA / 401 / Realms / narrator 报错是无头沙箱与离线账号噪音。

### 8.5 图腾粒子崩溃为何「修了还复现」：jar 时间线不一致

用户第二份日志覆盖 `11:23～11:28`，图腾崩溃发生在 `11:26:43`；而含第七章粒子修复的 jar
构建于 `11:57`，本轮最终 jar 构建于 `12:35`。**用户当时 `mods/` 里仍是旧 jar**，
所以日志里的 `AbstractMethodError` 与 4 条 Sodium 警告都属于旧版本。
必须用本次交付的 `riyueclient-1.3.3.jar` **覆盖替换** `mods/` 中的旧文件（同名替换，
或删除旧文件后再放入），并完全重启游戏；仅重新登录服务器不会重新加载 mod。替换后启动日志
应出现 `[RiyueClient] Sodium compat mixins: disabled (sodium not installed)`，可作为新版已生效的标志。

### 8.6 `atio6axx.dll` 原生崩溃不属于 Java 模组 bug

```
EXCEPTION_ACCESS_VIOLATION (0xc0000005)
C  [atio6axx.dll+0x3e1e90]
The crash happened outside the Java Virtual Machine in native code.
```

`atio6axx.dll` 是 AMD / ATI OpenGL 驱动的用户态模块，崩溃发生在 JVM 之外的 native 渲染代码，
Java 层 mixin / 反射 / 事件逻辑都无法修复它。建议按顺序排查：更新或回退显卡驱动、
在启动器里关闭或切换 OpenGL 相关兼容选项、检查是否同时加载了注入 OpenGL 的第三方工具。
这类崩溃与本次两处 Java 修复彼此独立，不应通过回退模组代码来「规避」。

### 8.7 仍需实机复测的路径

无头启动只到资源加载阶段，不会自动触发以下路径，请在替换新 jar 后实际验证：

1. 在服务器触发一次不死图腾，确认不再出现 `AbstractMethodError` 且图腾粒子颜色功能正常；
2. 开启 `AutoCrystal`（含 `resetCD`）攻击，确认不再刷 `NoSuchFieldException` 堆栈，且攻击节奏正常；
3. 若安装 Sodium，确认 `Sodium compat mixins: enabled` 且 Xray 渲染兼容无异常；
4. 若仍出现 `atio6axx.dll` 崩溃，按 8.6 走驱动 / 启动器路径，不要回退本次 Java 修复。

---

## 九、第三轮加固（主动排查剩余隐患）

在前面两轮之外又做了一轮全量静态排查，确认以下几点并修掉 4 处遗留问题。
排查结论先说清楚：**没有再发现新的启动期崩溃**，`System.exit(-1)` 全部位于注释块内
（`ThreadManager.java:60–221`、`ConfigManager.java:65–249` 与 `310–484` 三个注释块），
mixin 里其余 9 个裸 `abstract` 方法全部带 `@Shadow`，不存在第二个「假方法污染基类」的
`AbstractMethodError` 炸弹。

| # | 文件 | 问题 | 修复 |
| --- | --- | --- | --- |
| 1 | `asm/mixins/MixinTotemParticle.java` | 粒子在网络包处理线程创建，注入里任何异常都会触发 `Failed to handle packet ..., disconnecting`（直接掉线）；自定义颜色只是装饰功能 | `hookInit` 整体包 `catch (Throwable)`（必须捕获 `Throwable` 才能兜住 `AbstractMethodError` / `NoClassDefFoundError`），失败时降级为原版粒子，并用 `@Unique` 标志只打印一次日志 |
| 2 | `api/utils/path/BaritoneUtil.java` | 用 `Package.getPackages()` 扫描 `baritone.api` 包判断是否安装，在 Fabric 下不可靠：Baritone 装了但入口点未初始化会误判 false，别的 mod 提前加载该包又可能误判 true 导致 `NoClassDefFoundError` | 改用 `FabricLoader.getInstance().isModLoaded("baritone")`，与 Sodium 条件化 plugin 判定方式一致 |
| 3 | `assets/minecraft/textures/blank.png`、`assets/riyueclient/textures/blank.png` | 两张 256×256 贴图都带 `iCCP` chunk，是日志里 `libpng warning: iCCP: known incorrect sRGB profile` 的来源 | 移除 `iCCP` chunk，1040 → 641 bytes，像素数据不变；jar 内复查已无 `iCCP` / `sRGB` |
| 4 | `mod/commands/impl/SaveCommand.java` | 仍用 `SaveCommand.mc.runDirectory` 拼配置目录，是 `Manager.getFolder()` / `RiyueClient.onInitialize()` 之外漏掉的一处；`mc` 是 `static final = MinecraftClient.getInstance()`，窗口创建前可能为 null | 与其余两处统一改用 `FabricLoader.getInstance().getGameDir()` |

### 粒子继承链复核（确认 cast 安全）

```
TotemParticle (class_734)
  └─ AnimatedParticle (abstract)
       └─ BillboardParticle (class_3940, abstract)   ← public void setColor(float,float,float)
            └─ Particle (class_703)                 ← 无 setColor / setAlpha
```

`setColor(FFF)` 是 `BillboardParticle` 上 **public + concrete** 的方法，`setAlpha(F)` 是 protected。
所以 `((BillboardParticle)(Object)this).setColor(...)` 的 `checkcast` 绝对安全，同时也再次印证
第七章的判断：`Particle` 基类本来没有这两个方法，往基类塞裸 `abstract` 声明才导致崩溃。

### 验证结果

| 检查项 | 结果 |
| --- | --- |
| `clean build` | `BUILD SUCCESSFUL`，编译错误 0，`Cannot remap` **0 条** |
| `hookInit` 字节码 | `Exception table: 0–101 → 104, Class java/lang/Throwable`，兜底已编入 |
| 无头启动 | 成功，`Initialized in ...`，`Sodium compat mixins: enabled` |
| `InvalidMixinException` / `Mixin apply failed` / `AbstractMethodError` / `ClassCastException` / `NoSuchFieldException` | 全部 **0** |
| `Totem particle hook ignored`（兜底是否被触发） | **0**，说明正常路径不受影响，兜底只是保险 |
| jar 内 `blank.png` | 641 bytes，`iCCP=False`、`sRGB=False` |

**说明**：第 1 条兜底是防御性加固，不是替代第七章的根因修复；根因（`MixinParticle` 污染基类）
已经在第七章删除。两者叠加后，即便用户环境 MC 小版本与构建映射有差异，也不至于因一个装饰性
粒子把玩家踢出服务器。
