package com.hakan.notdefteri.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode(val label: String) { SYSTEM("Sistem ayarı"), LIGHT("Açık"), DARK("Koyu") }

/** Uygulama ayarları (telefonda saklanır). */
object Settings {
    private lateinit var prefs: SharedPreferences
    private val _theme = MutableStateFlow(ThemeMode.SYSTEM)
    val theme: StateFlow<ThemeMode> = _theme

    fun init(context: Context) {
        prefs = context.getSharedPreferences("ayarlar", Context.MODE_PRIVATE)
        _theme.value = runCatching { ThemeMode.valueOf(prefs.getString("theme", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM)
    }

    fun setTheme(mode: ThemeMode) {
        prefs.edit().putString("theme", mode.name).apply()
        _theme.value = mode
    }

    var appLock: Boolean
        get() = prefs.getBoolean("app_lock", false)
        set(v) = prefs.edit().putBoolean("app_lock", v).apply()

    // Parola doğrulama verileri (parolanın kendisi saklanmaz)
    var passwordSalt: String?
        get() = prefs.getString("pw_salt", null)
        set(v) = prefs.edit().putString("pw_salt", v).apply()

    var passwordCheck: String?
        get() = prefs.getString("pw_check", null)
        set(v) = prefs.edit().putString("pw_check", v).apply()
}
