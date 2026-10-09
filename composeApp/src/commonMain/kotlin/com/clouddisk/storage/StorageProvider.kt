package com.clouddisk.storage

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/** 传输进度回调：done=已传字节，total=总字节（未知时为 -1）。 */
typealias ProgressListener = (done: Long, total: Long) -> Unit

/** S3 兼容存储的统一操作接口；一套适配器对接阿里云/腾讯/百度/MinIO 等。 */
interface StorageProvider {
    /** 列出某前缀下的直接子项（文件夹 + 文件）。prefix 为空表示根。 */
    suspend fun list(prefix: String): List<StorageItem>

    /** 创建文件夹（写入一个以 "/" 结尾的空对象占位）。 */
    suspend fun createFolder(path: String)

    /** 从本地文件流式上传到指定 key（不整体读入内存）。 */
    suspend fun upload(key: String, localPath: String, onProgress: ProgressListener)

    /** 流式下载到本地临时文件，返回其路径（不整体读入内存）。 */
    suspend fun download(key: String, onProgress: ProgressListener): String

    /** 生成带签名的临时 URL，用于预览或分享；expiry 为有效期（SigV4 上限 7 天）。 */
    suspend fun presignUrl(key: String, expiry: Duration = 1.hours): String

    /** 删除对象；isFolder=true 时删除该前缀下全部对象（含文件夹占位）。 */
    suspend fun delete(key: String, isFolder: Boolean)

    fun close()
}

/** 由平台实现：两个目标都是 JVM，实际实现位于 jvmShared。 */
expect fun createStorage(account: Account): StorageProvider

/** 账号持久化（本地文件，凭据加密存储）。 */
expect fun loadAccounts(): List<Account>
expect fun saveAccounts(accounts: List<Account>)

/** 凭据加密：Android 用 Keystore，桌面用本地密钥文件（AES-GCM）。 */
expect fun encryptBlob(plain: ByteArray): ByteArray
expect fun decryptBlob(cipher: ByteArray): ByteArray
