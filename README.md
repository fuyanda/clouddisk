# CloudDisk

跨平台云盘客户端，用一套 S3 兼容接口对接阿里云 OSS、腾讯云 COS、百度云 BOS、MinIO 以及自定义端点。

技术栈：Kotlin Multiplatform + Compose Multiplatform。Android 与桌面（macOS / Windows / Linux）共用业务代码。

## 功能

- 配置多个存储账号（厂商预设或自定义 Endpoint）
- 凭据本地加密保存（Android Keystore / 桌面 AES-GCM）
- 浏览、新建文件夹、上传、下载、删除
- 大文件分片上传，支持断点续传
- 列表 / 缩略图两种视图
- 图片预览：旋转、缩放、上一张 / 下一张
- 视频应用内播放：进度、倍速、放大（桌面需本机安装 [VLC](https://www.videolan.org/)）
- 生成带有效期的分享链接

## 环境

- JDK 21（建议用 [SDKMAN](https://sdkman.io/) 管理）
- Android SDK（仅打 Android 包时需要，路径写在本机 `local.properties`）

## 构建

```bash
# 桌面直接运行（当前系统）
./gradlew :composeApp:run

# Android debug APK
./gradlew :composeApp:assembleDebug

# 安装包只能在对应系统上打
./gradlew :composeApp:packageDmg    # macOS
./gradlew :composeApp:packageMsi    # Windows
./gradlew :composeApp:packageDeb    # Linux
./gradlew :composeApp:assembleRelease
```

Windows 安装包不能在 macOS 上交叉编译，需要在 Windows 上执行 `packageMsi`。

发布签名读取仓库根目录的 `keystore.properties`（不入库）。没有该文件时 release 包不签名。
