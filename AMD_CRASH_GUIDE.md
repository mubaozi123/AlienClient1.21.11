# AMD 显卡 `atio6axx.dll` 崩溃解决指南

适用环境：Windows + AMD 显卡 + Minecraft 1.21.11（Fabric），日志出现

```text
# A fatal error has been detected by the Java Runtime Environment:
#  EXCEPTION_ACCESS_VIOLATION (0xc0000005) ...
# Problematic frame:
# C  [atio6axx.dll+0x3e1e90]
# The crash happened outside the Java Virtual Machine in native code.
```

## 一句话结论

`atio6axx.dll` 是 **AMD 的 OpenGL 驱动模块**（OpenGL ICD）。崩溃发生在 JVM 之外的 native 代码里，
Java 层模组**不可能修复驱动本身的 bug**，但可以去掉触发条件。要彻底解决，仍需更新 / 重装显卡驱动；
本模组已在代码侧做了能做的最大缓解（见下）。

## 这次日志里到底哪些是问题

| 日志条目 | 性质 | 是否模组崩溃 |
| --- | --- | --- |
| `Compatibility level set to JAVA_21`、`Sodium compat mixins: disabled`、`Initialized in ...` | 模组正常初始化 | 否，是修复已生效的证据 |
| `Failed to fetch user properties` / `Status: 401` / `Failed to retrieve profile key pair` / Realms `SignedJWT` / 「你正在使用盗版 Minecraft」 | 离线（盗版）账号无法访问 Mojang 服务 | 否，与模组无关 |
| `Error while loading the narrator` / `SP_VOICE returned code -2147221164` | Windows SAPI 语音组件不可用 | 否，与模组无关 |
| `Unable to start LAN server detection: Cannot assign requested address: setsocketopt` | 本机网络环境（无可用广播地址） | 否，与模组无关 |
| `libpng warning: iCCP: known incorrect sRGB profile` | 某张 PNG 带错误的 sRGB profile | 否，**本模组 jar 内只有 2 张 PNG 且已确认无 `iCCP`/`sRGB`**，这条来自服务器资源包或其他 mod 加载的图片 |
| `Too many temp register is used in Vertex shader`（云着色器） | AMD 驱动对 MC 云 shader 的编译警告 | 否，是驱动特性 |
| `Resizing Dynamic Transforms UBO` / `Resizing Chunk Sections UBO` 从 2 一路涨到 1024 | 单帧渲染对象过多导致 UBO 扩容 | 否，是**渲染压力的表现**，也是压垮驱动的诱因 |
| `EXCEPTION_ACCESS_VIOLATION` in `atio6axx.dll` | AMD 驱动 native 崩溃 | **是，也是本次唯一致命项** |

值得单独说：日志里 `Dynamic Transforms UBO`、`Chunk Sections UBO` 在**单帧内**从 2 翻倍到 1024，
说明渲染负载极高。本模组原本默认开启 `FuckFPSLimit`（解除帧率上限），会让 GPU 全速渲染，
把 AMD 驱动直接推到这个崩溃点上——这是模组侧实实在在能修的部分。

## 模组侧已经做掉的修复（换上新 jar 即生效）

1. **`FuckFPSLimit` 默认值 `true` → `false`**：默认恢复原版 `RenderSystem.limitDisplayFPS()`，
   不再无条件解除帧率上限。需要解锁帧率的用户仍可在客户端设置里手动开启。
2. **AMD 驱动自动保护**：`MixinMinecraftClient.fpsHook()` 首帧读取 `GL_VENDOR` / `GL_RENDERER`，
   命中 `ATI Technologies` / `AMD` / `Radeon` 时，**无论配置里存了什么都强制保留原版帧率限制**，
   并打印一行 `[RiyueClient] AMD/ATI OpenGL driver detected: keeping vanilla FPS limit ...`。
   这条专门用来兜住「老配置文件里已经存了 `FuckFPSLimit=true`」的情况（只改默认值救不了老配置）。
   检测只做一次并缓存，且用 `catch(Throwable)` 包住，检测失败时完全不干预用户设置，零回归风险。
3. 前四轮的修复（事件总线异常隔离、`AbstractMethodError`、`NoSuchFieldException`、
   Sodium 条件化、配置路径等）继续生效。

## 你需要做的，按优先级

### 1. 更新或彻底重装 AMD 驱动（最有效，社区已验证）

- 用 AMD 官方 **Auto-Detect and Install** / Adrenalin 更新到最新版；
- 若更新后仍崩，用 **DDU（Display Driver Uninstaller）在安全模式彻底卸载后重装**——
  `atio6axx.dll` 残留旧版本是这类崩溃的常见根因；
- 部分玩家反馈旧版 Adrenalin 22.5.1（其内置的 `atio6axx` 版本不同）也能规避，可作为降级尝试；
- **不要从 `dll-files.com` 之类站点下载 `atio6axx.dll`**，也不要用 `regsvr32` 手动注册它：
  该 DLL 无 `DllRegisterServer` 导出，强制注册会直接触发 `STATUS_ACCESS_VIOLATION`，
  且第三方下载样本带恶意代码的比例很高。

### 2. 双显卡机型：让游戏走另一张卡

Windows 设置 → 系统 → 屏幕 → 显示卡（图形设置）→ 添加 `javaw.exe`（路径见启动器的 Java 设置）
→ 指定「高性能 GPU」。若有 N 卡，选 N 卡可完全绕开 AMD 的 OpenGL ICD。

### 3. 降低渲染压力（对本次日志特别对症）

- 视频设置里把**渲染距离**降到 8 或更低（社区明确反馈可降低该崩溃概率）；
- 关闭**云**、**平滑光照**、**实体阴影**等；
- 关闭本模组里会大量绘制的模块：`Xray`、`ESP` / `Chams` / `PopChams`、`HoleESP`、
  `BreakESP`、`Tracers`、`Shader` 等，减少 `MixinWorldRenderer` 每帧的 3D 渲染注入量；
- 保持 `FuckFPSLimit` 关闭（新 jar 默认已关）。

### 4. 进阶（前三项无效时再试）

- 换用 Mesa / Zink 的 `opengl32.dll` 作为 OpenGL 实现，绕开 AMD ICD；
- 关闭启动器里的「OpenGL 兼容扩展」类选项，避免走 `GL_ARB_buffer_storage` 的 UBO 路径；
- 换成官方启动器 / 其他 Java 21 发行版排除启动器层面的注入差异。

## 换 jar 后怎么确认

1. 删除 `mods/` 里的旧 jar，放入本次交付的 `riyueclient-1.3.3.jar`；
2. 进同一个服（`crystalpvp.xin`）并待到与之前相同的时间点；
3. 日志出现 `AMD/ATI OpenGL driver detected: keeping vanilla FPS limit ...` 说明保护已生效；
4. 若仍生成 `hs_err_pid*.log` 且 `Problematic frame` 仍是 `atio6axx.dll`，
   说明是驱动层问题，请按上面第 1～3 项处理，并把 `hs_err_pid*.log` 一起发来。

**说明**：这个崩溃与 Java 层的 `AbstractMethodError` / `NoSuchFieldException` 是两类完全不同的问题。
前者在驱动 native 代码里，后者在模组 Java 代码里；后者已经在前四轮修完并在这份日志中确认消失。

## 参考资料

- [Minecraft 的疑难杂症 #14：AMD 显卡驱动触发 atio6axx.dll 崩溃](https://www.bilibili.com/opus/838099774752161833)
- [AMD Community: AMD driver conflicts with Java and causes crash (atio6axx.dll)](https://community.amd.com/t5/drivers-software/amd-driver-conflicts-with-java-and-causes-crash-atio6axx-dll/td-p/193499)
- [Minecraft Forum: [SOLVED] EXCEPTION_ACCESS_VIOLATION crash (atio6axx.dll)](https://www.minecraftforum.net/forums/support/java-edition-support/3178072-solve)
