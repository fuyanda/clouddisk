# CloudDisk

对接多个对象存储的跨平台客户端。一套 S3 兼容接口覆盖阿里云 OSS、腾讯云 COS、百度云 BOS，以及 MinIO 等自定义端点。

## 功能

- 配置多个存储账号（厂商预设或自定义端点）
- 浏览目录、新建文件夹、上传、下载、删除（文件夹会删掉前缀下全部对象）
- 列表 / 缩略图两种视图
- 图片预览：旋转、缩放、上一张 / 下一张
- 视频预览：播放、进度、倍速、放大（桌面依赖本机 VLC）
- 分享链接，有效期可选（最长 7 天，受 SigV4 限制）
- 大文件分片上传，失败后可断点续传
- 账号凭据本地加密保存（Android 用 Keystore，桌面用本机密钥文件）

## 技术栈

Kotlin Multiplatform + Compose Multiplatform。存储层用 aws-sdk-kotlin，走 S3 兼容 API。

| 目标 | 状态 |
| --- | --- |
| Android（minSdk 24） | 可打 APK |
| macOS | 可打 DMG |
| Windows | 配置了 MSI，必须在 Windows 上构建 |
| Linux | 配置了 Deb，必须在 Linux 上构建 |

桌面安装包由 `jpackage` 生成，只能在对应操作系统上打包，不能在 macOS 上交叉编译 Windows / Linux 安装包。

## 环境

- JDK 21（建议用 [SDKMAN](https://sdkman.io/)：`sdk install java 21.0.11-amzn`）
- Android 端另需 Android SDK，路径写在本机的 `local.properties`（不要提交）

## 构建

```bash
# 桌面直接运行（当前系统）
./gradlew :composeApp:run

# macOS 安装包
./gradlew :composeApp:packageDmg

# Android 调试包
./gradlew :composeApp:assembleDebug

# Android 正式包（需本机 keystore.properties + release.keystore，见下）
./gradlew :composeApp:assembleRelease
```

Windows 上打 MSI：`gradlew.bat :composeApp:packageMsi`。

产物位置：

- DMG：`composeApp/build/compose/binaries/main/dmg/`
- APK：`composeApp/build/outputs/apk/`

## 桌面视频预览

在线播放使用 vlcj，回调渲染依赖 `jdk.unsupported`（已在打包配置里保留）。macOS / Windows / Linux 都需要先安装 [VLC](https://www.videolan.org/)。未检测到 VLC 时会提示用外部播放器打开。

## 正式签名（不要提交）

Android release 从项目根目录的 `keystore.properties` 读取签名信息，文件不存在时跳过签名。示例：

```properties
storeFile=release.keystore
storePassword=你的口令
keyAlias=clouddisk
keyPassword=你的口令
```

`keystore.properties`、`*.keystore`、`local.properties` 已在 `.gitignore` 中排除。密钥丢失后无法给同一应用发更新，请自行离线备份。
