package com.clouddisk.storage

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.CompleteMultipartUploadRequest
import aws.sdk.kotlin.services.s3.model.CompletedMultipartUpload
import aws.sdk.kotlin.services.s3.model.CompletedPart
import aws.sdk.kotlin.services.s3.model.CreateMultipartUploadRequest
import aws.sdk.kotlin.services.s3.model.DeleteObjectRequest
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.model.ListObjectsV2Request
import aws.sdk.kotlin.services.s3.model.ListPartsRequest
import aws.sdk.kotlin.services.s3.model.PutObjectRequest
import aws.sdk.kotlin.services.s3.model.UploadPartRequest
import aws.sdk.kotlin.services.s3.presigners.presignGetObject
import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.content.writeToOutputStream
import aws.smithy.kotlin.runtime.http.engine.AlpnId
import aws.smithy.kotlin.runtime.io.SdkBuffer
import aws.smithy.kotlin.runtime.io.SdkSource
import aws.smithy.kotlin.runtime.net.url.Url
import kotlin.time.Duration.Companion.seconds
import com.clouddisk.platform.platformDataDir
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FilterOutputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

private const val PROGRESS_STEP = 256L * 1024 // 每 256KB 上报一次进度
private const val PART_SIZE = 8L * 1024 * 1024 // 分片大小 8MB（S3 要求每片 ≥5MB，末片除外）

/**
 * 可重放的文件分片 ByteStream：isOneShot=false，每次 readFrom() 重新打开并 seek 到 offset。
 * S3 SigV4 需先读一遍算哈希、发送再读一遍，一次性流会报 "Stream closed"，故必须可重放。
 * onRead 回调给出本分片内已读的累计字节（重放时会从 0 重新累计）。
 */
private class FileSliceByteStream(
    private val file: File,
    private val offset: Long,
    private val length: Long,
    private val onRead: (cum: Long) -> Unit,
) : ByteStream.SourceStream() {
    override val contentLength: Long = length
    override val isOneShot: Boolean = false
    override fun readFrom(): SdkSource = FileSliceSource(file, offset, length, onRead)
}

private class FileSliceSource(
    file: File,
    private val offset: Long,
    private val length: Long,
    private val onRead: (cum: Long) -> Unit,
) : SdkSource {
    private val raf = RandomAccessFile(file, "r").apply { seek(offset) }
    private val buffer = ByteArray(64 * 1024)
    private var cum = 0L
    private var reported = -1L

    override fun read(sink: SdkBuffer, limit: Long): Long {
        val remaining = length - cum
        if (remaining <= 0) return -1L
        val toRead = minOf(minOf(limit, buffer.size.toLong()), remaining).toInt()
        if (toRead <= 0) return 0L
        val n = raf.read(buffer, 0, toRead)
        if (n < 0) return -1L
        sink.write(buffer, 0, n)
        cum += n
        if (crossedStep(reported, cum, PROGRESS_STEP) || cum == length) {
            reported = cum
            onRead(cum)
        }
        return n.toLong()
    }

    override fun close() = raf.close()
}

private class CountingOutputStream(
    sink: OutputStream,
    private val total: Long,
    private val onProgress: ProgressListener,
) : FilterOutputStream(sink) {
    private var count = 0L
    private var reported = -1L

    private fun advance(n: Int) {
        count += n
        if (crossedStep(reported, count, PROGRESS_STEP) || count == total) {
            reported = count
            onProgress(count, total)
        }
    }

    override fun write(b: Int) {
        out.write(b)
        advance(1)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        out.write(b, off, len)
        advance(len)
    }
}

/** 分片上传的断点续传会话：sessionKey -> uploadId。无敏感信息，明文存储即可。 */
private object UploadSessions {
    private val json = Json { ignoreUnknownKeys = true }
    private fun file() = File(platformDataDir(), "upload-sessions.json")

    @Synchronized
    private fun load(): MutableMap<String, String> {
        val f = file()
        if (!f.exists()) return mutableMapOf()
        return runCatching { json.decodeFromString<Map<String, String>>(f.readText()).toMutableMap() }
            .getOrElse { mutableMapOf() }
    }

    @Synchronized fun get(key: String): String? = load()[key]

    @Synchronized fun put(key: String, uploadId: String) {
        val m = load(); m[key] = uploadId; file().writeText(json.encodeToString(m))
    }

    @Synchronized fun remove(key: String) {
        val m = load(); m.remove(key); file().writeText(json.encodeToString(m))
    }
}

private class S3StorageProvider(private val account: Account) : StorageProvider {

    private val client: S3Client = S3Client {
        region = account.region.ifBlank { "us-east-1" }
        endpointUrl = Url.parse(account.endpoint)
        forcePathStyle = account.pathStyle
        credentialsProvider = StaticCredentialsProvider {
            accessKeyId = account.accessKey
            secretAccessKey = account.secretKey
        }
        // 强制 HTTP/1.1：MinIO 大文件(分片)上传在 HTTP/2 下常见 StreamResetException(PROTOCOL_ERROR)。
        // 同时放宽读写超时，避免大分片在慢速链路上被默认超时掐断。
        httpClient {
            tlsContext { alpn = listOf(AlpnId.HTTP1_1) }
            socketWriteTimeout = 120.seconds
            socketReadTimeout = 120.seconds
        }
    }

    override suspend fun list(prefix: String): List<StorageItem> {
        val resp = client.listObjectsV2(
            ListObjectsV2Request {
                bucket = account.bucket
                this.prefix = prefix
                delimiter = "/"
            },
        )
        val folders = resp.commonPrefixes.orEmpty().mapNotNull { cp ->
            val p = cp.prefix ?: return@mapNotNull null
            StorageItem(name = displayName(p), key = p, isFolder = true)
        }
        val files = resp.contents.orEmpty().mapNotNull { obj ->
            val k = obj.key ?: return@mapNotNull null
            if (k == prefix || k.endsWith("/")) return@mapNotNull null // 跳过文件夹占位对象
            StorageItem(
                name = displayName(k),
                key = k,
                isFolder = false,
                size = obj.size ?: 0L,
                lastModified = obj.lastModified?.toString(),
            )
        }
        return folders.sortedBy { it.name } + files.sortedBy { it.name }
    }

    override suspend fun createFolder(path: String) {
        val key = if (path.endsWith("/")) path else "$path/"
        client.putObject(
            PutObjectRequest {
                bucket = account.bucket
                this.key = key
                body = ByteStream.fromBytes(ByteArray(0))
            },
        )
    }

    override suspend fun upload(key: String, localPath: String, onProgress: ProgressListener) {
        val file = File(localPath)
        val size = file.length()
        if (size <= PART_SIZE) {
            client.putObject(
                PutObjectRequest {
                    bucket = account.bucket
                    this.key = key
                    body = FileSliceByteStream(file, 0, size) { cum -> onProgress(cum, size) }
                },
            )
        } else {
            multipartUpload(key, file, size, onProgress)
        }
    }

    /** 大文件分片上传，支持断点续传：失败后保留 uploadId，重传同一文件（大小+修改时间一致）可跳过已完成分片。 */
    private suspend fun multipartUpload(key: String, file: File, size: Long, onProgress: ProgressListener) {
        val sessionKey = "${account.bucket}/$key:$size:${file.lastModified()}"
        val partCount = partCountOf(size, PART_SIZE)
        val completed = linkedMapOf<Int, String>() // partNumber -> eTag

        var uploadId = UploadSessions.get(sessionKey)
        if (uploadId != null) {
            runCatching {
                client.listParts(
                    ListPartsRequest { bucket = account.bucket; this.key = key; this.uploadId = uploadId },
                ).parts.orEmpty().forEach { p ->
                    val n = p.partNumber; val e = p.eTag
                    if (n != null && e != null) completed[n] = e
                }
            }.onFailure { uploadId = null } // 会话已失效，重新开始
        }
        if (uploadId == null) {
            uploadId = client.createMultipartUpload(
                CreateMultipartUploadRequest { bucket = account.bucket; this.key = key },
            ).uploadId!!
            UploadSessions.put(sessionKey, uploadId)
            completed.clear()
        }

        var base = completed.keys.sumOf { partLengthOf(it, size, PART_SIZE) }
        onProgress(base, size)

        for (n in 1..partCount) {
            if (completed.containsKey(n)) continue
            val offset = (n - 1).toLong() * PART_SIZE
            val len = partLengthOf(n, size, PART_SIZE)
            val etag = client.uploadPart(
                UploadPartRequest {
                    bucket = account.bucket
                    this.key = key
                    this.uploadId = uploadId
                    partNumber = n
                    body = FileSliceByteStream(file, offset, len) { cum -> onProgress(base + cum, size) }
                },
            ).eTag!!
            completed[n] = etag
            base += len
        }

        client.completeMultipartUpload(
            CompleteMultipartUploadRequest {
                bucket = account.bucket
                this.key = key
                this.uploadId = uploadId
                multipartUpload = CompletedMultipartUpload {
                    parts = (1..partCount).map { n -> CompletedPart { partNumber = n; eTag = completed[n] } }
                }
            },
        )
        UploadSessions.remove(sessionKey)
    }

    override suspend fun download(key: String, onProgress: ProgressListener): String {
        val temp = File.createTempFile("clouddisk-", ".tmp")
        client.getObject(GetObjectRequest { bucket = account.bucket; this.key = key }) { resp ->
            val total = resp.contentLength ?: -1L
            CountingOutputStream(temp.outputStream().buffered(), total, onProgress).use { out ->
                resp.body?.writeToOutputStream(out)
            }
        }
        return temp.absolutePath
    }

    override suspend fun presignUrl(key: String, expiry: Duration): String {
        val presigned = client.presignGetObject(
            GetObjectRequest { bucket = account.bucket; this.key = key },
            expiry.coerceIn(Duration.ZERO, 7.days),
        )
        return presigned.url.toString()
    }

    override suspend fun delete(key: String, isFolder: Boolean) {
        if (!isFolder) {
            client.deleteObject(DeleteObjectRequest { bucket = account.bucket; this.key = key })
            return
        }
        // 文件夹：不带 delimiter 递归列出前缀下所有对象再逐个删除
        // ponytail: 逐个 deleteObject，天花板是 N 次往返；对象很多时可改 deleteObjects 批量(每批 ≤1000)
        var token: String? = null
        do {
            val resp = client.listObjectsV2(
                ListObjectsV2Request {
                    bucket = account.bucket
                    prefix = key
                    continuationToken = token
                },
            )
            resp.contents.orEmpty().forEach { obj ->
                obj.key?.let { k -> client.deleteObject(DeleteObjectRequest { bucket = account.bucket; this.key = k }) }
            }
            token = resp.nextContinuationToken
        } while (token != null)
    }

    override fun close() = client.close()
}

actual fun createStorage(account: Account): StorageProvider = S3StorageProvider(account)
