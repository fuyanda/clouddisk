package com.clouddisk.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.net.URI

actual fun platformDataDir(): String {
    val dir = File(System.getProperty("user.home"), ".clouddisk")
    dir.mkdirs()
    return dir.absolutePath
}

actual fun openExternal(url: String) {
    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
        Desktop.getDesktop().browse(URI(url))
    }
}

@Composable
actual fun rememberUploadPicker(onPicked: (fileName: String, localPath: String) -> Unit): () -> Unit = {
    val dialog = FileDialog(null as Frame?, "选择要上传的文件", FileDialog.LOAD)
    dialog.isVisible = true
    val dir = dialog.directory
    val file = dialog.file
    if (dir != null && file != null) {
        val f = File(dir, file)
        onPicked(f.name, f.absolutePath)
    }
}

@Composable
actual fun rememberFileSaver(): FileSaver = remember {
    object : FileSaver {
        override fun save(fileName: String, localPath: String) {
            val temp = File(localPath)
            val dialog = FileDialog(null as Frame?, "保存文件", FileDialog.SAVE)
            dialog.file = fileName
            dialog.isVisible = true
            val dir = dialog.directory
            val file = dialog.file
            if (dir != null && file != null) temp.copyTo(File(dir, file), overwrite = true)
            temp.delete()
        }
    }
}
