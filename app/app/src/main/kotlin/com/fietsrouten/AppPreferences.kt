package com.fietsrouten

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import org.json.JSONObject

/** Small SharedPreferences-backed store for the settings ProfileFragment exposes. */
object AppPreferences {
    private const val PREFS_NAME = "fietsrouten_prefs"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_VOICE_GUIDANCE = "voice_guidance_enabled"
    private const val KEY_HOME = "favorite_home"
    private const val KEY_WORK = "favorite_work"

    data class FavoriteLocation(val lat: Double, val lon: Double, val label: String)

    enum class ThemeMode(val nightMode: Int) {
        SYSTEM(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
        LIGHT(AppCompatDelegate.MODE_NIGHT_NO),
        DARK(AppCompatDelegate.MODE_NIGHT_YES)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getThemeMode(context: Context): ThemeMode {
        val name = prefs(context).getString(KEY_THEME, ThemeMode.SYSTEM.name)
        return runCatching { ThemeMode.valueOf(name ?: ThemeMode.SYSTEM.name) }.getOrDefault(ThemeMode.SYSTEM)
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        prefs(context).edit().putString(KEY_THEME, mode.name).apply()
        AppCompatDelegate.setDefaultNightMode(mode.nightMode)
    }

    /** Apply the persisted theme; call once as early as possible (e.g. Activity.onCreate before setContentView). */
    fun applyPersistedTheme(context: Context) {
        AppCompatDelegate.setDefaultNightMode(getThemeMode(context).nightMode)
    }

    fun isVoiceGuidanceEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_VOICE_GUIDANCE, true)

    fun setVoiceGuidanceEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_VOICE_GUIDANCE, enabled).apply()
    }

    fun getHome(context: Context): FavoriteLocation? = getFavorite(context, KEY_HOME)
    fun setHome(context: Context, location: FavoriteLocation) = setFavorite(context, KEY_HOME, location)
    fun getWork(context: Context): FavoriteLocation? = getFavorite(context, KEY_WORK)
    fun setWork(context: Context, location: FavoriteLocation) = setFavorite(context, KEY_WORK, location)

    private fun getFavorite(context: Context, key: String): FavoriteLocation? {
        val raw = prefs(context).getString(key, null) ?: return null
        return runCatching {
            val o = JSONObject(raw)
            FavoriteLocation(o.getDouble("lat"), o.getDouble("lon"), o.getString("label"))
        }.getOrNull()
    }

    private fun setFavorite(context: Context, key: String, location: FavoriteLocation) {
        val o = JSONObject().apply {
            put("lat", location.lat)
            put("lon", location.lon)
            put("label", location.label)
        }
        prefs(context).edit().putString(key, o.toString()).apply()
    }
}
