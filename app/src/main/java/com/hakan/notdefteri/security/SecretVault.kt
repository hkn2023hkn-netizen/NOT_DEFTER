package com.hakan.notdefteri.security

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Gizli bölüm. Uygulamanın kilidinden ve kilitli notlardan tamamen ayrı, kendi parolası olan bir kasa.
 *
 * - Anahtar parolandan PBKDF2-HMAC-SHA256 (600.000 tur, 16 bayt rastgele tuz) ile türetilir.
 * - Tüm içerik tek dosyada AES-256-GCM ile şifrelenir; her kayıtta yeni rastgele IV kullanılır.
 *   GCM, dosyaya dokunulursa (bozulursa) bunu fark eder.
 * - Parola hiçbir yerde saklanmaz. Doğru parola yalnızca şifre çözme başarılı olunca anlaşılır.
 * - Üst üste 3 yanlış parolada dosya rastgele baytlarla ezilip silinir. Sayaç uygulama kapatılsa da korunur.
 * - Uygulama arka plana geçince kasa hemen kapanır, anahtar bellekten silinir.
 * - Parolayla şifrelenen içerik bir kez de telefonun güvenlik çipindeki anahtarla (DeviceKey) şifrelenir.
 *   Dosya başka cihaza kopyalanırsa açılamaz; gizli bölüm telefon değişince taşınamaz.
 */
object SecretVault {
    data class Note(val id: Long, val title: String, val content: String, val updatedAt: Long)

    sealed class OpenResult {
        object Ok : OpenResult()
        data class Wrong(val remaining: Int) : OpenResult()
        object Wiped : OpenResult()
        /** Dosya bu telefonun güvenlik anahtarıyla açılamıyor (başka cihazdan gelmiş ya da telefon sıfırlanmış). */
        object OtherDevice : OpenResult()
    }

    const val MAX_TRIES = 3
    private const val ITERATIONS = 600_000
    private const val MAGIC = "gk1"

    private lateinit var dir: File
    private lateinit var prefs: android.content.SharedPreferences
    private val file get() = File(dir, "kasa.bin")

    private var key: SecretKeySpec? = null
    private var salt: ByteArray? = null
    private val _notes = MutableStateFlow<List<Note>?>(null)
    /** null: kasa kapalı. */
    val notes: StateFlow<List<Note>?> = _notes

    fun init(context: Context) {
        dir = File(context.filesDir, "gizli").apply { mkdirs() }
        prefs = context.getSharedPreferences("gizli", Context.MODE_PRIVATE)
    }

    val exists: Boolean get() = file.exists()
    val isOpen: Boolean get() = key != null
    val failedTries: Int get() = prefs.getInt("hata", 0)

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)

    private fun derive(password: String, salt: ByteArray): SecretKeySpec {
        val chars = password.toCharArray()
        val spec = PBEKeySpec(chars, salt, ITERATIONS, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
            Arrays.fill(chars, '\u0000')
        }
    }

    /** Güçlü parola kuralı: en az 10 karakter; harf ve rakam içermeli. Uygunsa null, değilse açıklama döner. */
    fun weakness(password: String): String? = when {
        password.length < 10 -> "En az 10 karakter olmalı"
        password.none { it.isLetter() } -> "En az bir harf içermeli"
        password.none { it.isDigit() } -> "En az bir rakam içermeli"
        password.isBlank() -> "Boş olamaz"
        else -> null
    }

    /** Yeni, boş bir gizli kasa kurar. (Uzun sürer: arka planda çağırın.) */
    fun create(password: String) {
        val s = ByteArray(16).also { SecureRandom().nextBytes(it) }
        key = derive(password, s)
        salt = s
        prefs.edit().putInt("hata", 0).commit()
        _notes.value = emptyList()
        write(emptyList())
    }

    /** Parolayla açar. Yanlışsa kalan hakkı döner; hak biterse kasayı siler. (Uzun sürer: arka planda çağırın.) */
    fun open(password: String): OpenResult {
        if (!exists) return OpenResult.Wiped
        val raw = file.readText()
        // Yeni biçim: önce cihaz anahtarıyla çözülür. Bu telefonda anahtar yoksa parola hiç denenmez.
        val inner = if (raw.startsWith(DeviceKey.PREFIX + ":")) DeviceKey.unwrap(raw) ?: return OpenResult.OtherDevice else raw
        val migrate = !raw.startsWith(DeviceKey.PREFIX + ":")
        val notes = try {
            val parts = inner.split(":")
            require(parts.size == 4 && parts[0] == MAGIC)
            val s = unb64(parts[1])
            val k = derive(password, s)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, unb64(parts[2])))
            val plain = String(cipher.doFinal(unb64(parts[3])), Charsets.UTF_8)
            key = k
            salt = s
            parse(plain)
        } catch (e: Exception) {
            null
        }
        if (notes == null) {
            val tries = failedTries + 1
            // Sayaç dosyadan önce diske yazılır ki uygulamayı kapatıp tekrar denemek işe yaramasın
            prefs.edit().putInt("hata", tries).commit()
            if (tries >= MAX_TRIES) {
                wipe()
                return OpenResult.Wiped
            }
            return OpenResult.Wrong(MAX_TRIES - tries)
        }
        prefs.edit().putInt("hata", 0).commit()
        _notes.value = notes
        // Eski biçimdeki kasa, içerik kaybolmadan cihaz anahtarlı yeni biçime taşınır
        if (migrate) write(notes)
        return OpenResult.Ok
    }

    fun close() {
        key = null
        salt = null
        _notes.value = null
    }

    /** Dosyanın üzerine rastgele veri yazıp siler; geri getirilemez. */
    fun wipe() {
        close()
        try {
            if (file.exists()) {
                val len = file.length().toInt().coerceAtLeast(64)
                val junk = ByteArray(len)
                repeat(2) {
                    SecureRandom().nextBytes(junk)
                    file.outputStream().use { it.write(junk); it.fd.sync() }
                }
            }
        } catch (e: Exception) {
        }
        file.delete()
        File(dir, "kasa.tmp").delete()
        DeviceKey.destroy()
        prefs.edit().putInt("hata", 0).commit()
    }

    fun upsert(note: Note) {
        val cur = _notes.value ?: return
        val next = listOf(note) + cur.filter { it.id != note.id }
        _notes.value = next
        write(next)
    }

    fun delete(id: Long) {
        val cur = _notes.value ?: return
        val next = cur.filter { it.id != id }
        _notes.value = next
        write(next)
    }

    /** Parolayı değiştirir: eski parola doğrulanır, her şey yeni anahtarla yeniden şifrelenir. */
    fun changePassword(old: String, new: String): Boolean {
        val cur = _notes.value ?: return false
        val s = salt ?: return false
        if (!derive(old, s).encoded.contentEquals(key?.encoded)) return false
        val newSalt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        key = derive(new, newSalt)
        salt = newSalt
        write(cur)
        return true
    }

    private fun write(notes: List<Note>) {
        val k = key ?: return
        val s = salt ?: return
        val arr = JSONArray()
        notes.forEach {
            arr.put(JSONObject().put("i", it.id).put("t", it.title).put("c", it.content).put("u", it.updatedAt))
        }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, k, GCMParameterSpec(128, iv))
        val ct = cipher.doFinal(arr.toString().toByteArray(Charsets.UTF_8))
        // Önce geçici dosyaya yaz, sonra yer değiştir: yarıda kalan kayıt kasayı bozmasın
        val tmp = File(dir, "kasa.tmp")
        val inner = "$MAGIC:${b64(s)}:${b64(iv)}:${b64(ct)}"
        // Keystore'u olmayan (çok nadir) telefonlarda yalnızca parola katmanı kalır
        tmp.writeText(DeviceKey.wrap(inner) ?: inner)
        if (!tmp.renameTo(file)) {
            file.delete(); tmp.renameTo(file)
        }
    }

    private fun parse(json: String): List<Note> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Note(o.getLong("i"), o.optString("t"), o.optString("c"), o.optLong("u"))
        }.sortedByDescending { it.updatedAt }
    }
}
