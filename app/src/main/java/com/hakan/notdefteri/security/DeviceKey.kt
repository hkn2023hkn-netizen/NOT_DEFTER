package com.hakan.notdefteri.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Telefonun güvenlik çipinde (Android Keystore; varsa StrongBox) duran, dışarı çıkarılamayan AES-256 anahtarı.
 * Gizli bölümün dosyası parolayla şifrelendikten sonra bir kez de bu anahtarla şifrelenir. Böylece dosya
 * başka bir cihaza kopyalansa bile açılamaz, orada parola denemesi de yapılamaz.
 */
object DeviceKey {
    private const val ALIAS = "notdefteri_gizli_cihaz"
    const val PREFIX = "gk2"

    private fun store() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun existing(): SecretKey? = try {
        (store().getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
    } catch (e: Exception) {
        null
    }

    val exists: Boolean get() = existing() != null

    private fun generate(strongBox: Boolean): SecretKey {
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .apply { if (strongBox && Build.VERSION.SDK_INT >= 28) setIsStrongBoxBacked(true) }
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run { init(spec); generateKey() }
    }

    /** Anahtarı döndürür; yoksa üretir (önce StrongBox, olmazsa normal güvenli donanım). */
    private fun getOrCreate(): SecretKey? = existing() ?: try {
        generate(strongBox = true)
    } catch (e: Exception) {
        try { generate(strongBox = false) } catch (e2: Exception) { null }
    }

    /** Cihaz anahtarıyla şifreler. Telefon Keystore desteklemiyorsa null. */
    fun wrap(data: String): String? = try {
        val key = getOrCreate() ?: return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val ct = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
        "$PREFIX:" + Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(ct, Base64.NO_WRAP)
    } catch (e: Exception) {
        null
    }

    /** Cihaz anahtarıyla çözer. Anahtar bu telefonda yoksa (başka cihaz / sıfırlama) null. */
    fun unwrap(data: String): String? = try {
        val parts = data.split(":")
        val key = existing() ?: return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, Base64.decode(parts[1], Base64.NO_WRAP)))
        String(cipher.doFinal(Base64.decode(parts[2], Base64.NO_WRAP)), Charsets.UTF_8)
    } catch (e: Exception) {
        null
    }

    /** Anahtarı siler: bu anahtarla şifrelenmiş her şey kalıcı olarak okunamaz olur. */
    fun destroy() {
        try { store().deleteEntry(ALIAS) } catch (e: Exception) {}
    }
}
