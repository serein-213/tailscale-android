# Tailscale Android · 自用增强版

基于 [Tailscale Android](https://github.com/tailscale/tailscale-android) 的非官方 fork，主要面向自托管 Headscale 用户：在手机上连接网络、管理设备、审批路由和编辑访问策略，同时提供简体中文、主题和界面定制。

**本项目不是 Tailscale 官方发行版。** 官方商店及 [Tailscale Packages](https://pkgs.tailscale.com/stable/#android) 提供的是官方客户端，不包含本 fork 的定制功能。

当前发布 **1.104.6.mod.1**：同步官方 Android 1.104.6，升级 Tailscale 核心至 1.104.6 及对应 WireGuard 依赖，保留本 fork 的 Headscale 管理与界面定制。

## 这个 fork 有什么不同

### Headscale 应用内管理

无需切换到浏览器，即可通过 Headscale HTTP API 管理 tailnet：

- **设备**：按名称、IP、用户和标签搜索，按在线状态、用户和标签组合筛选，按名称或最后在线时间排序；支持重命名、修改标签、过期和删除。批量操作展示失败设备与原因，可仅重试失败项。
- **用户**：查看用户及其设备，新建和删除用户；点击用户下的设备可跳转至设备详情。
- **预授权密钥**：创建密钥，配置有效期、可复用和临时节点选项，按用户和使用状态筛选；用户选择支持搜索。密钥默认脱敏，查看与复制均需显式操作。
- **路由审批**：查看设备通告的子网路由和出口节点路由，筛选待审批设备，调整批准状态；提交状态按设备显示。
- **访问策略**：结构化浏览与搜索，新增、编辑和删除条目，包括访问规则与 SSH 规则；也可直接编辑 huJSON 原文。结构化编辑保留原文格式和注释，失败会明确提示。保存前显示行级差异，并检查服务端策略是否已被修改，最终由服务端校验。

各管理数据集独立显示加载、更新时间和可展开的错误信息；刷新失败保留已有内容。标签栏支持横向滚动。

管理 API 的接口形状和测试样例以 **Headscale v0.29.3** 为依据；其他版本的兼容性需实际验证。这不是 Tailscale 官方 SaaS 管理 API 的客户端，也不是完整的 Web 管理控制台替代品。

### 日常使用与界面

- 自定义协调服务器和管理控制台 URL，方便接入自托管环境。
- 补全简体中文翻译，默认语言资源为英文。
- 主题设置与 Komi Manga 风格主题。
- 在设备列表、搜索和设备详情中展示标签。
- 按应用分流的筛选和本地化优化。
- 设置项支持长按隐藏；长按「关于」显示隐藏项，点击对应项目恢复，再次长按「关于」退出。
- 在「关于」中检查本仓库 GitHub Releases 的更新，并下载安装包。

### 兼容性与构建

- 包含面向 16 KB 内存页设备的原生库链接与 APK 对齐处理，以及 JNI/Go 返回值对齐问题的修复。
- 保留按 ABI 拆分及通用 APK 构建，提供本地签名脚本和 Nix 开发环境。

这些是本分支的改动方向，不代表已在所有 Android 版本和设备上验证通过。具体历史可查看 [提交记录](https://github.com/serein-213/tailscale-android/commits)。

## 下载与安装

前往 [本 fork 的 Releases](https://github.com/serein-213/tailscale-android/releases)，选择对应版本的 APK：

- 大多数现代手机选择 `arm64-v8a`。
- 不确定设备架构时选择 `universal`，体积会更大。
- 当前构建配置最低支持 **Android 8.0（API 26）**。

本 fork 沿用 `com.tailscale.ipn` 包名。**签名不同的官方版、第三方构建和自编译版本不能直接覆盖安装，也不能作为不同应用并存。** 如果必须卸载旧版，请先记录服务器地址及必要配置；卸载会清除应用数据，之后需要重新登录。后续覆盖升级也需要保持签名一致。

应用内更新需要访问 GitHub，安装时可能需要授予此应用「安装未知应用」权限。

## 连接与管理 Headscale

1. 在自定义服务器设置中填写协调服务器 URL，按服务端配置完成登录。
2. 如需应用内管理，进入「Tailnet 管理」，填写 Headscale 地址和 **Headscale 管理 API 密钥**，点击「保存并测试」。
3. 按需使用设备、用户、密钥、路由和策略页面。

**管理 API 密钥与用于设备入网的预授权密钥不是同一种密钥。** 普通 VPN 连接不需要管理 API 密钥。API 密钥和地址使用 Android `EncryptedSharedPreferences` 保存在本机；管理密钥具有高权限，只应配置在可信设备上，并使用 HTTPS。

保存策略会影响整个 tailnet，批量过期或删除设备也会中断连接。操作前请保留策略备份，并确保仍有独立访问 Headscale 服务端的方式；语法校验不能保证访问规则符合你的意图。

策略更新接口的兼容性依据为 [Headscale v0.29.3 的协议定义](https://github.com/juanfont/headscale/blob/v0.29.3/proto/headscale/v1/policy.proto)。该协议的写入请求只有策略正文，没有版本前置条件；保存前检查不能消除检查与写入之间的并发修改窗口。多位管理员同时操作时，应协调编辑，不能将客户端检查视为原子冲突保护。

## 从源码构建

### 环境准备

需要 Go 引导运行时、JDK 17 或兼容版本、Android SDK，以及构建所需的 SDK/NDK 组件。项目通过 `tool/go` 和 `go.toolchain.rev` 管理 Go 工具链，具体 Android 组件以 `Makefile` 为准。

使用 Android Studio 时，在 SDK Manager 中安装 Android SDK Command-line Tools，然后在仓库根目录执行：

```sh
make androidsdk
```

SDK 不在默认路径时，设置 `ANDROID_SDK_ROOT`。可运行 `make androidpath` 查看工具路径设置。

### 调试版

在仓库根目录执行：

```sh
make tailscale-debug
make install
```

调试 APK 输出到 `./tailscale-debug.apk`。安装步骤需要连接设备并启用 USB 调试。

### Nix 开发环境

```sh
nix --extra-experimental-features 'nix-command flakes' develop
make androidsdk
make tailscale-debug
```

Flake 提供 Java、`make`、`curl`、`git` 等宿主工具，并使用仓库内的 `./android-sdk`；SDK 目录不会提交到 Git。

已生成 `android/libs/libtailscale.aar` 后，只改 Kotlin 时可缩短迭代流程：

```sh
nix develop --command bash -lc 'cd android && ./gradlew ktfmtCheck compileDebugKotlin'
```

这条命令不会重建 Go/JNI 库。

### 签名发布 APK

先准备自己的签名库，密钥别名使用 `tailscale`。在仓库根目录创建已被 Git 忽略的 `.release.local`：

```sh
JKS_PATH=/absolute/path/to/your-release.jks
JKS_PASSWORD='your-keystore-password'
```

从干净源码构建时，先生成 Go/JNI 库和版本信息，再运行签名脚本：

```sh
make libtailscale version
./build-release-apk.sh
```

脚本读取 `.release.local` 并运行 Gradle `assembleRelease`，APK 位于 `android/build/outputs/apk/release/`，包括按 ABI 拆分和通用版本。它本身不会重新构建 Go/JNI 库。

不要提交签名库或密码；请妥善备份自己的签名密钥，否则无法为已有安装提供同签名升级。

### 其他构建方式

- Docker：`make docker-shell` 进入构建环境。
- AAB：`make release` / `make release-tv`，需要设置 `JKS_PATH` 和 `JKS_PASSWORD`。
- AAB 转 APK：`./convert-to-apk.sh`，同样需要签名环境变量；具体依赖和处理流程见脚本。

## 问题反馈与上游

- 本 fork 的功能、安装包和回归问题：[本仓库 Issues](https://github.com/serein-213/tailscale-android/issues)。
- 已确认属于上游的问题：[Tailscale Issues](https://github.com/tailscale/tailscale/issues)。

反馈时请附应用版本、Android 版本、设备架构、复现步骤；涉及 Headscale 时附服务端版本。日志和截图请先移除 API 密钥、预授权密钥及其他敏感信息。

## 许可证与致谢

基于 Tailscale Android 开源客户端开发，沿用 [BSD 3-Clause 许可证](LICENSE)，另见 [PATENTS](PATENTS)。感谢 Tailscale 及上游贡献者。

WireGuard 是 Jason A. Donenfeld 的注册商标。
