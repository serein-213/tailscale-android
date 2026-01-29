# Tailscale Android 16 (16KB Page Size) 稳定性修复总结

本文档总结了为使 Tailscale 适配 Android 16 (16KB Page Size 环境) 所做的技术修改及其背后的原因。

## 1. 崩溃点分析

### 1.1 ELF 段对齐问题 (16KB Page Support)
*   **现象**：App 启动即闪退，系统日志显示无法加载 `libgojni.so`。
*   **原因**：Android 16 引入了对 16KB 页面大小硬件的支持。传统的 Android 动态库（`.so`）通常以 4KB 边界对齐。当该动态库加载到 16KB 页面的内核中时，由于段边界未对齐，系统会拒绝映射内存，导致 `dlopen` 失败。
*   **关联工具**：`zipalign`, `linker flags`。

### 1.2 Go Runtime 内存对齐错误 (CGO 8-byte Alignment)
*   **现象**：App 在进行网络请求或调用 LocalAPI 时偶发性崩溃，日志显示：
    `fatal error: bulkBarrierPreWrite: unaligned arguments`。
*   **原因**：这是 Go 1.25.x 编译器在 arm64 架构下的一个已知 Bug。
    *   在通过 CGO/JNI 返回包含指针的数据结构（如 Go 的 `[]byte` 切片，本质是一个包含指针、长度、容量的结构体）时，gomobile 生成的 C 语言中间代码在 C 栈上分配的内存可能仅为 4 字节对齐。
    *   Go 的垃圾回收器（GC）在执行“写屏障（Write Barrier）”时，强制要求目标内存地址必须是 8 字节对齐的。
    *   当地址未对齐时，Go 运行时为了内存安全性会主动抛出 fatal error 并中断进程。

---

## 2. 修复方案

### 2.1 强制 16KB 链接与对齐
*   **编译期修复**：在 `Makefile` 构建命令中，为 Go 链接器增加了 `ldflags`:
    `-linkmode=external -extldflags=-Wl,-z,max-page-size=16384`
    这确保生成的 `libgojni.so` 内部 ELF 段符合 16KB 边界要求。
*   **打包期修复**：引入了 `scripts/align-apk.sh`，在 APK 生成后使用 `zipalign -p 16` 处理。这保证了 APK 压缩包内的 `.so` 文件在解压到内存时，物理地址依然保持 16KB 对齐。

### 2.2 Go 接口重构：返回指针避开写屏障
*   **思路**：避开在 JNI 边界直接通过“值传递”返回复杂结构体。
*   **实施细节**：
    *   **Go 层**：修改 `libtailscale/interfaces.go`。将本会崩溃的 `BodyBytes() ([]byte, error)` 修改为返回结构体指针：`BodyBytes() *BodyResult`。
    *   **原理**：Go 的堆分配器（Heap Allocation）确保所有对象起始地址都是 8 字节对齐的。返回指针时，JNI 仅传递一个 64 位整数（地址），不涉及对未对齐内存地址的指针写入操作，从而完美绕过了 Go 运行时的写屏障校验。
    *   **Kotlin 层**：适配 `Client.kt`，从返回的 `BodyResult` 对象中提取字节数组 `b` 和错误信息 `e`。

### 2.3 异常防御 (Panic Hardening)
*   **代码加固**：在 `libtailscale` 的关键入口（如 `multitun.go`, `log.go`, `localapi.go`）增加了大量 `recover()` 逻辑，并重定向到 `log.Printf`。
*   **日志可见性**：禁用了 `log.go` 中可能导致 Android 16 安全审计拦截的 `syscall.Dup3` 重定向，确保崩溃前的最后日志能通过 `adb logcat` 被捕获到。

---

## 3. 打包流程规范

由于 16KB 对齐会破坏原有的数字签名，必须严格遵守以下构建顺序：

1.  **Build**: 编译 AAB 或通用 APK。
2.  **Align**: 使用 `zipalign -p 16` 进行对齐（这是为了 Android 16 兼容性）。
3.  **Sign**: 使用 `apksigner` 进行签名（这是为了系统能识别证书）。

目前的构建脚本 `make release && ./convert-to-apk.sh` 已自动整合上述流程。

---

## 4. 修改文件清单

*   [Makefile](Makefile): 增加 16KB 链接参数。
*   [libtailscale/interfaces.go](libtailscale/interfaces.go): 修改 LocalAPI 接口，由值返回改为指针返回。
*   [libtailscale/localapi.go](libtailscale/localapi.go): 实现新的内存对齐逻辑。
*   [android/src/main/java/com/tailscale/ipn/ui/localapi/Client.kt](android/src/main/java/com/tailscale/ipn/ui/localapi/Client.kt): 适配指针形式的 JNI 响应。
*   [scripts/align-apk.sh](scripts/align-apk.sh): APK 对齐工具。
*   [convert-to-apk.sh](convert-to-apk.sh): 自动化 AAB 转换、对齐、签名脚本。
