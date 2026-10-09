package com.clouddisk.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** 应用本地数据目录（存账号配置等）。 */
expect fun platformDataDir(): String

/** 用系统默认程序打开 URL（视频/其他文件的预览走外部播放器/浏览器）。 */
expect fun openExternal(url: String)

/** 选择本地文件上传：点击后弹系统选择框，回调给出文件名与可读的本地路径。 */
@Composable
expect fun rememberUploadPicker(onPicked: (fileName: String, localPath: String) -> Unit): () -> Unit

/** 保存已下载到本地临时文件（localPath）的内容到用户选定位置；完成后临时文件会被清理。 */
@Composable
expect fun rememberFileSaver(): FileSaver

interface FileSaver {
    fun save(fileName: String, localPath: String)
}

/** 应用内视频播放器：直接播放签名 URL。桌面若缺少本机 VLC 会回退到外部播放器。rate 为倍速。 */
@Composable
expect fun VideoPlayer(url: String, rate: Float, modifier: Modifier)
