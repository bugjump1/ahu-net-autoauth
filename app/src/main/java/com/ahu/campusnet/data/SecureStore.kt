package com.ahu.campusnet.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 本地加密存储（对应 Windows 版的 DPAPI）。
 *
 * 用 Android Keystore 里的 AES-256-GCM 密钥加密：密钥由系统 TEE/StrongBox 保管，
 * 不会以明文出现在 App 私有目录，即使 APK 数据被拷走也无法在别的设备解密。
 *
 * 存储格式：
 *   E1:<base64(iv|ciphertext)>  —— 正常加密数据
 *   P1:<base64(明文)>           —— Keystore 不可用时的降级（极少见）
 *   其它                         —— 兼容历史明文
 */
object SecureStore {

    private const val KEY_ALIAS = "campusnet_master_key_v1"
    private const val PROVIDER = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LENGTH = 12
    private const val TAG_BITS = 128

    private const val PREFIX_ENC = "E1:"
    private const val PREFIX_PLAIN = "P1:"

    private var cachedKey: SecretKey? = null

    private fun secretKey(): SecretKey {
        cachedKey?.let { return it }
        val ks = KeyStore.getInstance(PROVIDER).apply { load(null) }
        val existing = ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        val key = existing?.secretKey ?: run {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
            generator.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            generator.generateKey()
        }
        cachedKey = key
        return key
    }

    fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val iv = cipher.iv
            val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            val merged = ByteArray(iv.size + body.size)
            System.arraycopy(iv, 0, merged, 0, iv.size)
            System.arraycopy(body, 0, merged, iv.size, body.size)
            PREFIX_ENC + Base64.encodeToString(merged, Base64.NO_WRAP)
        } catch (e: Exception) {
            // Keystore 异常时降级（只混淆，不是真加密），保证功能不中断
            PREFIX_PLAIN + Base64.encodeToString(plain.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        }
    }

    fun decrypt(stored: String): String {
        if (stored.isEmpty()) return ""
        return try {
            when {
                stored.startsWith(PREFIX_ENC) -> {
                    val raw = Base64.decode(stored.substring(PREFIX_ENC.length), Base64.NO_WRAP)
                    if (raw.size <= IV_LENGTH) return ""
                    val iv = raw.copyOfRange(0, IV_LENGTH)
                    val body = raw.copyOfRange(IV_LENGTH, raw.size)
                    val cipher = Cipher.getInstance(TRANSFORMATION)
                    cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
                    String(cipher.doFinal(body), Charsets.UTF_8)
                }

                stored.startsWith(PREFIX_PLAIN) ->
                    String(Base64.decode(stored.substring(PREFIX_PLAIN.length), Base64.NO_WRAP), Charsets.UTF_8)

                else -> stored // 兼容早期明文
            }
        } catch (e: Exception) {
            ""
        }
    }
}
