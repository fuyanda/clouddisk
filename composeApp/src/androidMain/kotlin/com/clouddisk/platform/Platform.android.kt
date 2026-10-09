package com.clouddisk.platform

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.clouddisk.AndroidApp
import java.io.File

actual fun platformDataDir(): String = AndroidApp.instance.filesDir.absolutePath

actual fun openExternal(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    AndroidApp.instance.startActivity(intent)
}

@Composable
actual fun rememberUploadPicker(onPicked: (fileName: String, localPath: String) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val ctx = AndroidApp.instance
        val name = ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        } ?: uri.lastPathSegment ?: "upload.bin"
        // SAF 只给 Uri，先流式拷到 cache 临时文件，再交给共享层上传（不整体读入内存）
        val temp = File.createTempFile("upload-", "-$name", ctx.cacheDir)
        ctx.contentResolver.openInputStream(uri)?.use { input -> temp.outputStream().use { input.copyTo(it) } }
            ?: return@rememberLauncherForActivityResult
        onPicked(name, temp.absolutePath)
    }
    return { launcher.launch("*/*") }
}

@Composable
actual fun rememberFileSaver(): FileSaver {
    val pendingPath = remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val path = pendingPath.value
        pendingPath.value = null
        val temp = path?.let { File(it) }
        if (uri != null && temp != null) {
            AndroidApp.instance.contentResolver.openOutputStream(uri)?.use { out -> temp.inputStream().use { it.copyTo(out) } }
        }
        temp?.delete()
    }
    return remember {
        object : FileSaver {
            override fun save(fileName: String, localPath: String) {
                pendingPath.value = localPath
                launcher.launch(fileName)
            }
        }
    }
}
