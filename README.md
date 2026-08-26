# MiaoAssistant（喵喵输入助手）

一个在 Android 上本地运行的全局文本改写工具。它不再只针对 QQ，而是通过当前聚焦的无障碍可编辑节点，尽可能覆盖普通 `EditText`、提供无障碍语义的 Compose 输入框和网页输入框。

> 包名：`com.mazha0309.miaoassistant`
>
> 仓库：<https://github.com/Mazha0309/MiaoAssistant>

## 功能

- 尽可能覆盖所有普通输入框，不再绑定 QQ 包名
- 标点触发（推荐）与实时替换两种模式
- 默认提供可编辑、可删除的 `我=本喵` 与 `你=主人`，并支持更多多行自定义规则
- 自定义断句追加内容、内置或自定义颜文字
- 可搜索已安装应用，并选择“除所选应用外启用”或“仅在所选应用启用”
- 应用内可直接选择无障碍、Shizuku 或 Root 文本写回方式
- 本应用、系统界面和密码输入框始终跳过
- MiuiX 分页界面，提供标准/悬浮底栏与 KernelSU 同源液态玻璃效果
- 跟随系统、浅色与深色主题；莫奈取色可选且默认关闭
- 可选前台服务保活、HyperOS 后台设置引导，以及默认关闭的 Root 强保活
- 纯本地处理；Manifest 不申请联网权限，也不会记录输入文本

## 覆盖范围与限制

“所有输入框”在 Android 上不存在一个普通应用可无条件访问的统一接口，因此这里采用尽可能覆盖、默认保守的策略：

| 输入场景 | 预期支持情况 |
| --- | --- |
| 标准 Android `EditText` | 通常可用 |
| 正确暴露无障碍语义的 Compose 输入框 | 通常可用，取决于目标应用 |
| WebView / 浏览器网页输入框 | 取决于浏览器的无障碍桥接 |
| 游戏、Canvas、自绘或主动屏蔽无障碍的输入框 | 通常不可用 |
| 密码、可见密码、数字密码框 | 主动跳过，不提供关闭选项 |

服务只处理事件来源或当前获得输入焦点的节点，不会遍历窗口并猜测输入框。应用范围页只读取具有启动入口的应用，可按应用图标和名称勾选排除列表或仅限列表。

液态玻璃依赖 Android 13（API 33）及以上的运行时着色器；低版本仍可使用标准底栏或不带折射效果的悬浮底栏。

## 无障碍、Shizuku 与 Root

“范围与运行 → 文本写回方式”可以直接选择：

- **无障碍（默认）**：使用 `ACTION_SET_TEXT` 写回，改动最小，但微信等主动限制无障碍节点写入的应用可能无法使用。
- **Shizuku**：选择时在应用内请求 Shizuku 授权，作为旧系统的兼容写回通道。
- **Root**：选择时在应用内请求 KernelSU/Magisk 等 root 管理器授权，作为旧系统的兼容写回通道。

Shizuku/Root 只替换“写回”通道，仍需开启无障碍服务来识别当前输入事件、应用范围与密码框。Android 13 及以上通过系统提供给无障碍服务的 `InputConnection` 直接提交 Unicode 文本，不读写剪贴板；连接暂不可用时也不会暗中退回剪贴板。Android 12L 及以下没有这套接口，选择 Shizuku/Root 时才使用临时剪贴板兼容写回并在完成后恢复原内容。实时模式仍可能受具体输入法的候选区语义影响。

三种方式严格按应用内选择执行，不会在未明确选择时自动升级到 Shizuku 或 Root。

## 保活说明

“常驻通知保活”默认关闭。开启后会启动用户可见、可随时停止的低优先级前台服务，并在收到开机完成或应用更新广播后尝试恢复。Android 13 及以上必须先授予通知权限；通知权限被撤回时，保活配置会自动关闭。“HyperOS 后台设置”集中提供自启动、后台耗电策略和应用详情入口，并提示手动锁定最近任务。

前台服务只能提高同进程的存活优先级，不能绕过“强行停止”。部分 HyperOS 版本会把最近任务上划清理记为 `SwipeUpClean / FORCE STOP`，此时 Android 会禁止普通服务自行恢复。

“Root 强保活”是独立、默认关闭的高级选项。用户确认并授权后，应用会在 `/data/adb/service.d/miaoassistant-keepalive.sh` 安装一个每 30 秒检查一次的守护脚本，用于在系统清理或重启后恢复可见前台服务与无障碍组件。关闭选项会移除脚本；卸载应用或清除应用数据后，守护也会检测并自行退出。该选项会覆盖用户手动关闭无障碍的操作，因此需要先关闭 Root 强保活，再手动关闭无障碍。

## 使用

1. 安装 APK，打开应用。
2. 点击“管理无障碍服务”，启用“喵喵全局输入服务”。
3. 配置触发方式、替换规则、追加内容与应用生效范围。
4. 若微信等应用拒绝无障碍写回，在“范围与运行”中切换为 Shizuku 或 Root 并完成授权。
5. 如设备后台策略较激进，可打开常驻通知保活，并按“HyperOS 后台设置”完成自启动、后台无限制和最近任务锁定。
6. 只有在理解其行为且确实需要绕过 `SwipeUpClean` 时，再单独开启 Root 强保活。

建议先使用“标点触发”。实时模式会在每次文本变化时回写，对某些输入法的候选区或撤销逻辑更敏感。

## 构建

环境：JDK 17+、Android SDK Platform 37。应用最低支持 Android 7.0（API 24，与 Shizuku API 13 对齐）。

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

调试 APK：`app/build/outputs/apk/debug/app-debug.apk`

主要技术栈：Kotlin、Jetpack Compose、[MiuiX](https://github.com/compose-miuix-ui/miuix)、[Shizuku API](https://github.com/RikkaApps/Shizuku-API)、Gradle 9.7.1、AGP 9.3.1。MiuiX 版本与当前 KernelSU Manager 使用的 0.9.x 代际保持一致。

应用内“关于 → 第三方使用许可”与 [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) 列出了界面及构建依赖的来源和许可证。

## 权限与隐私

- `BIND_ACCESSIBILITY_SERVICE`：读取并回写当前聚焦的普通输入节点
- Shizuku：仅在应用内选择 Shizuku 写回时请求授权
- Root：仅在选择 Root 写回或明确开启 Root 强保活时触发 root 管理器授权
- `POST_NOTIFICATIONS`、前台服务权限：仅用于用户主动开启的可见保活
- `RECEIVE_BOOT_COMPLETED`：仅在保活已经开启时尝试恢复
- 无 `INTERNET` 或悬浮窗权限；Shizuku/Root 均为默认不启用的可选写回方式
- 应用备份与设备迁移均显式禁用，避免配置意外离开设备

## 项目来源

本仓库 fork 自 [QiCaiJie114514/QQMiaoAssistant](https://github.com/QiCaiJie114514/QQMiaoAssistant)。上游工程说明其由“QQ 喵喵助手”APK 逆向重建，原作者未知；如涉及原作者权益，请按上游仓库说明联系处理。

本次重构已更换应用 ID、全局输入引擎、配置模型、界面、主题、图标、构建链与测试，不代表原应用作者。
