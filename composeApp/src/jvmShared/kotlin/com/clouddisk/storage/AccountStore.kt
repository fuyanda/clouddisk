package com.clouddisk.storage

import com.clouddisk.platform.platformDataDir
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

private fun accountsFile(): File = File(platformDataDir(), "accounts.enc")
private fun legacyPlainFile(): File = File(platformDataDir(), "accounts.json")

actual fun loadAccounts(): List<Account> {
    val enc = accountsFile()
    if (enc.exists()) {
        return runCatching { json.decodeFromString<List<Account>>(decryptBlob(enc.readBytes()).decodeToString()) }
            .getOrElse { emptyList() }
    }
    // 兼容旧版明文：读到就迁移为加密存储，删除明文
    val legacy = legacyPlainFile()
    if (legacy.exists()) {
        val list = runCatching { json.decodeFromString<List<Account>>(legacy.readText()) }.getOrElse { emptyList() }
        if (list.isNotEmpty()) {
            saveAccounts(list)
            legacy.delete()
        }
        return list
    }
    return emptyList()
}

actual fun saveAccounts(accounts: List<Account>) {
    accountsFile().writeBytes(encryptBlob(json.encodeToString(accounts).encodeToByteArray()))
}
