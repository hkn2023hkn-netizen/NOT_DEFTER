package com.hakan.notdefteri.security

import android.util.Base64
import com.hakan.notdefteri.data.Item
import com.hakan.notdefteri.data.Repository
import com.hakan.notdefteri.data.Settings
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Parola kasası. Parolandan PBKDF2 ile 256 bit anahtar türetilir, kilitli notlar AES-256-GCM ile şifrelenir.
 * Parolanın kendisi hiçbir yerde saklanmaz; yalnızca doğrulama için anahtarın özeti tutulur.
 * Kilit açıldığında anahtar yalnızca bellekte durur.
 */
object Vault {
    private const val ITERATIONS = 120_000
    private var key: SecretKeySpec? = null

    val hasPassword: Boolean get() = Settings.passwordSalt != null && Settings.passwordCheck != null
    val isUnlocked: Boolean get() = key != null

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)

    private fun derive(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }

    private fun check(k: SecretKeySpec) = b64(MessageDigest.getInstance("SHA-256").digest(k.encoded + "dogrulama".toByteArray()))

    /** İlk parolayı belirler (ya da eskisini doğrulayıp değiştirir). Kilitli notlar yeni parolayla yeniden şifrelenir. */
    fun setPassword(old: String?, new: String): Boolean {
        if (hasPassword && (old == null || !unlock(old))) return false
        val oldKey = key
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val newKey = derive(new, salt)
        if (oldKey != null) {
            val reencrypted = Repository.items.value.filter { it.locked }.mapNotNull { item ->
                val (t, c) = decryptWith(oldKey, item.content) ?: return@mapNotNull null
                item.copy(content = encryptWith(newKey, t, c))
            }
            Repository.addAll(reencrypted)
        }
        Settings.passwordSalt = b64(salt)
        Settings.passwordCheck = check(newKey)
        key = newKey
        return true
    }

    fun unlock(password: String): Boolean {
        val salt = Settings.passwordSalt ?: return false
        val k = derive(password, unb64(salt))
        return if (check(k) == Settings.passwordCheck) {
            key = k; true
        } else false
    }

    fun lock() {
        key = null
    }

    private fun encryptWith(k: SecretKeySpec, title: String, content: String): String {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, k, GCMParameterSpec(128, iv))
        val plain = JSONObject().put("t", title).put("c", content).toString().toByteArray()
        return "v1:" + b64(iv) + ":" + b64(cipher.doFinal(plain))
    }

    private fun decryptWith(k: SecretKeySpec, data: String): Pair<String, String>? = try {
        val parts = data.split(":")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, unb64(parts[1])))
        val o = JSONObject(String(cipher.doFinal(unb64(parts[2]))))
        o.optString("t") to o.optString("c")
    } catch (e: Exception) {
        null
    }

    /** Kilit açıkken başlık ve içeriği şifreler. */
    fun encrypt(title: String, content: String): String =
        encryptWith(key ?: error("Kasa kilitli"), title, content)

    /** Kilitli bir öğenin başlık ve içeriğini çözer; kasa kilitliyse null. */
    fun decrypt(item: Item): Pair<String, String>? {
        val k = key ?: return null
        return decryptWith(k, item.content)
    }
}
