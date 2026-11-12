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
- **Windows 7 用户注意**：AMD 主线驱动从 `21.6.1` 起已不再支持 Win7，`22.5.1` 官方只支持
  Win10/11（别装错）。Win7 能装到的最后一版是专门的 `Adrenalin 22.6.1 for Windows 7`，
  详见文末「附录：Windows 7 + AMD 核显」；
- Win10/11 上部分玩家反馈旧版 `22.5.1`（其内置 `atio6axx` 版本不同）也能规避，可作为降级尝试；
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

## 附录：Windows 7 + AMD 核显怎么装驱动

### A. 先确认你的核显型号（决定能不能装）

Win7 下任选一种方式查：

- `Win + R` → 输入 `dxdiag` → 「显示」选项卡，看**名称 / 芯片类型 / 驱动程序版本**；
- 或「设备管理器」→「显示适配器」；
- 或命令行（Win7 自带 `wmic`）：

```bat
wmic path win32_VideoController get name,driverversion,videoprocessor /format:list
wmic os get caption,version,osarchitecture /format:list
```

按名称对号入座：

| 你看到的名字 | 属于 | Win7 官方驱动 |
| --- | --- | --- |
| `AMD Radeon R2/R3/R4/R5/R7/R9 Graphics`、`Radeon HD 7xxx/8xxx`、`Radeon HD 8xxxG` | 老 APU 核显（GCN 及以前） | ✅ 有：`Adrenalin 22.6.1 for Windows 7` |
| `AMD Radeon(TM) Graphics`、`Radeon Vega 8/11`、`Radeon 680M/780M` | Ryzen APU 核显 | ❌ **官方没有 Win7 驱动** |
| `Microsoft 基本显示适配器` / 黄色感叹号 / `Standard VGA Graphics Adapter` | 压根没装 AMD 驱动 | 按下面 C 装 |

> 用户日志里 `atio6axx.dll` 存在，说明 AMD 的 OpenGL ICD 已装；但如果是 Windows Update
> 自动塞进去的旧版，文件日期可能停在 2015–2018 年——那正是 `atio6axx` 崩溃高发的版本。

### B. 硬性限制（先说清楚，避免白折腾）

1. AMD **从 `21.6.1` 起停止 Win7 支持**；`22.5.1` 官方只支持 Win10/11 64 位
   （见其 Release Notes 的 Compatible Operating Systems），**别把它往 Win7 上装**。
2. `Adrenalin 22.6.1 for Windows 7`（2022-06-23）是一次计划外更新，
   **也是最后一份 Win7 官方驱动**，之后再没更新。所以「更新到最新驱动」在 Win7 上的天花板就是它。
   驱动版本 `21.09.12.01`，WDDM `27.20.20912.1002`。
3. 该包兼容列表：`RX 400/500/Vega/5000/6000`、`Radeon 500`、`HD 7000/8000`，
   以及 **AMD Processors with Radeon R2/R3/R4/R5/R7/R9 系列 Graphics**；
   **不包含 Ryzen 带 Vega / RDNA 的核显**。
4. Win7 是 WDDM 1.1 + 老 OpenGL ICD，`GL_ARB_buffer_storage` 这类 UBO 路径在旧驱动上本来就不稳，
   这正是该组合特别容易撞上 `atio6axx.dll` 的原因。
5. 顺带一提：Minecraft 1.21.11 + Java 21 本身也不是 Win7 的官方支持组合
   （日志里是 `Microsoft Build of OpenJDK 21.0.7`）。能跑起来不代表底层稳妥。
   **最省事的根治是换 Windows 10/11**：驱动、OpenGL、Java 21 一次性全部回到受支持状态。

### C. 老 APU 核显：装 22.6.1 for Win7 的具体步骤

1. **下载离线完整包**（不要在线安装器），AMD 官方直链：

   ```text
   https://drivers.amd.com/drivers/radeon-software-adrenalin-2020-22.6.1-win7-64bit-june23-2022.exe
   ```

   （只有 RX 6400 / 6500 XT 需要用同页的另一个专用包。）
2. **前置条件**：Win7 **SP1 64 位**；装好 `.NET Framework 4.8`（DDU 与 Radeon 设置需要）
   与 VC++ 运行库；若安装程序提示缺系统更新，再补 Win7 Platform Update（KB2670838）。
   全程以**管理员**身份运行。
3. **断网**（拔网线或禁用网卡），防止 Windows Update 在装机过程中自动塞回旧驱动。
4. 下载 **DDU**（支持 Win7，需 .NET 4.8），进**安全模式**运行 → 选 `AMD` →
   「清除并重启（Clean and restart）」。
5. 正常启动后运行 22.6.1 安装包 → 选「完整安装 / Full Install」→ 装完重启。
6. **验证**：

   ```bat
   wmic path win32_VideoController get name,driverversion /format:list
   ```

   - 设备管理器里显示真实 AMD 核显型号，不再是「基本显示适配器」；
   - 驱动版本应为 `21.09.12.01`（WDDM `27.20.20912.1002`）；
   - `C:\Windows\System32\atio6axx.dll` 存在，且文件日期 / 版本已更新。
7. **关闭驱动自动更新**，否则 Win7 可能把旧驱动推回来：控制面板 → 系统 →
   高级系统设置 → 硬件 → 设备安装设置 → 选「否，让我选择要执行的操作」→
   勾选「从不安装来自 Windows Update 的驱动程序软件」。

> 22.6.1 修复的正是「AMD Radeon 软件在某些 AMD 显卡上安装失败」这个 Win7 特有问题，
> 所以如果你之前装驱动失败过，这一版尤其值得一试。

### D. Ryzen APU 核显（Vega / RDNA）：官方无解

- 22.6.1 for Win7 的兼容列表里没有它，**别硬装**（可能装不上或黑屏）。
- 可选但都不理想：找笔记本 / 主板 **OEM 提供的 Win7 驱动**（仅较老机型有）、
  第三方修改版驱动（有安全与稳定性风险，且新核显在 Win7 上 OpenGL 支持残缺）。
- **实际建议**：换 Windows 10/11；暂时不能换就用下面的 E 方案绕开 AMD ICD。

### E. Win7 专属兜底：用 Mesa 的 `opengl32.dll` 绕开 `atio6axx.dll`

把 Mesa（llvmpipe 软件渲染）的 64 位 `opengl32.dll` 放到 **`javaw.exe` 所在目录**
（即 JRE 的 `bin`，Windows 会优先加载 exe 同目录的 DLL），这样 OpenGL 调用
**不再经过 AMD 的 `atio6axx.dll`**，native 崩溃自然消失。

- 只从 Mesa 官方 / MSYS2 等可信来源获取，且位数要与 64 位 Java 一致；
- 代价是纯 CPU 渲染、帧率很低，只适合「先能进服不崩」；
- 配合本模组的帧率限制、渲染距离 ≤ 8、关闭 `Xray` / `ESP` 使用。

### F. 换完驱动怎么确认

进同一个服，日志出现
`[RiyueClient] AMD/ATI OpenGL driver detected: keeping vanilla FPS limit ...`
说明本模组保护已生效；不再生成 `hs_err_pid*.log` 即问题解决。
若仍崩且 `Problematic frame` 还是 `atio6axx.dll`，说明已经碰到 Win7 + 旧 OpenGL ICD 的天花板，
按 B 第 5 条考虑升级系统，并把 `dxdiag` 信息与 `hs_err_pid*.log` 一起发来。

## 参考资料

- [Minecraft 的疑难杂症 #14：AMD 显卡驱动触发 atio6axx.dll 崩溃](https://www.bilibili.com/opus/838099774752161833)
- [AMD Community: AMD driver conflicts with Java and causes crash (atio6axx.dll)](https://community.amd.com/t5/drivers-software/amd-driver-conflicts-with-java-and-causes-crash-atio6axx-dll/td-p/193499)
- [Minecraft Forum: [SOLVED] EXCEPTION_ACCESS_VIOLATION crash (atio6axx.dll)](https://www.minecraftforum.net/forums/support/java-edition-support/3178072-solve)
- [AMD: Adrenalin 22.6.1 for Windows 7 Release Notes（最后一版 Win7 驱动、兼容产品列表、官方下载直链）](https://www.amd.com/en/support/kb/release-notes/rn-rad-win-22-6-1-win7)
- [AMD: Adrenalin 22.5.1 Release Notes（Compatible OS 仅 Win10 / Win11）](https://www.amd.com/en/resources/support-articles/release-notes/RN-RAD-WIN-22-5-1.html)
