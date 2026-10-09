package com.clouddisk.storage

import kotlinx.serialization.Serializable

/** 一个已配置的存储账号。accessKey/secretKey 为连接必需，持久化在本地，禁止写入日志。 */
@Serializable
data class Account(
    val id: String,
    val name: String,
    val providerId: String,
    val endpoint: String,
    val region: String = "",
    val bucket: String,
    val accessKey: String,
    val secretKey: String,
    val pathStyle: Boolean = true,
)

/** 远端对象或"文件夹"（S3 以 prefix + delimiter 模拟目录）。 */
data class StorageItem(
    val name: String,
    val key: String,
    val isFolder: Boolean,
    val size: Long = 0,
    val lastModified: String? = null,
)

/** 厂商预设：端点模板中的 {region} 会被实际区域替换。 */
data class ProviderPreset(
    val id: String,
    val displayName: String,
    val endpointTemplate: String,
    val defaultRegion: String = "",
    val pathStyle: Boolean = false,
    val custom: Boolean = false,
)

val providerPresets: List<ProviderPreset> = listOf(
    ProviderPreset("aliyun", "阿里云 OSS", "https://oss-{region}.aliyuncs.com", "cn-hangzhou", pathStyle = false),
    ProviderPreset("tencent", "腾讯云 COS", "https://cos.{region}.myqcloud.com", "ap-guangzhou", pathStyle = false),
    ProviderPreset("baidu", "百度云 BOS", "https://s3.{region}.bcebos.com", "bj", pathStyle = false),
    ProviderPreset("minio", "MinIO / 自定义 S3", "http://127.0.0.1:9000", "us-east-1", pathStyle = true, custom = true),
)

fun resolveEndpoint(preset: ProviderPreset, region: String): String =
    preset.endpointTemplate.replace("{region}", region.ifBlank { preset.defaultRegion })

fun presetOf(providerId: String): ProviderPreset =
    providerPresets.firstOrNull { it.id == providerId } ?: providerPresets.last()
