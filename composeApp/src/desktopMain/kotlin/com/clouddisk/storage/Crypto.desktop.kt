package com.clouddisk.storage

import com.clouddisk.platform.platformDataDir
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private const val IV_LEN = 12
private const val TAG_BITS = 128

// ponytail: 桌面主密钥存本地文件（权限 0600），仅防止随手翻看/云同步泄露，挡不住本机有权限的攻击者。
// 升级路径：macOS Keychain（security 命令）/ Windows DPAPI / Linux libsecret。
private fun masterKey(): SecretKeySpec {
    val f = File(platformDataDir(), "master.key")
    val bytes = if (f.exists()) {
        f.readBytes()
    } else {
        ByteArray(32).also { SecureRandom().nextBytes(it) }.also { key ->
            f.writeBytes(key)
            runCatching {
                Files.setPosixFilePermissions(f.toPath(), PosixFilePermissions.fromString("rw-------"))
            } // Windows 无 POSIX 权限，忽略
        }
    }
    return SecretKeySpec(bytes, "AES")
}

actual fun encryptBlob(plain: ByteArray): ByteArray {
    val iv = ByteArray(IV_LEN).also { SecureRandom().nextBytes(it) }
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, masterKey(), GCMParameterSpec(TAG_BITS, iv))
    return iv + cipher.doFinal(plain)
}

actual fun decryptBlob(cipher: ByteArray): ByteArray {
    val c = Cipher.getInstance("AES/GCM/NoPadding")
    c.init(Cipher.DECRYPT_MODE, masterKey(), GCMParameterSpec(TAG_BITS, cipher, 0, IV_LEN))
    return c.doFinal(cipher, IV_LEN, cipher.size - IV_LEN)
}
