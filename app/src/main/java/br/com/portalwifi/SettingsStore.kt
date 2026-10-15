package br.com.portalwifi

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Room is the source of truth after first launch. Existing SharedPreferences
 * values are imported once without deleting them.
 */
class SettingsStore(context: Context) {
    private val legacy = context.getSharedPreferences("portal", Context.MODE_PRIVATE)
    private val dao = PortalDatabase.get(context).dao()

    private fun <T> io(action: suspend () -> T): T =
        runBlocking { withContext(Dispatchers.IO) { action() } }

    init {
        io {
            if (!dao.hasSetting("__import_complete")) {
                for ((key, value) in legacy.all) {
                    if (value is String || value is Int || value is Long ||
                        value is Float || value is Boolean) {
                        if (!dao.hasSetting(key)) dao.putSetting(AppSetting(key, value.toString()))
                    }
                }
                dao.putSetting(AppSetting("__import_complete", "1"))
            }
        }
    }

    fun contains(key: String) = io { dao.hasSetting(key) }
    fun getString(key: String, default: String?): String? = io { dao.setting(key) } ?: default
    fun getInt(key: String, default: Int) = getString(key, null)?.toIntOrNull() ?: default
    fun getBoolean(key: String, default: Boolean) =
        getString(key, null)?.toBooleanStrictOrNull() ?: default

    fun edit() = Editor()
    inner class Editor {
        private val pending = linkedMapOf<String, String>()
        fun putString(key: String, value: String) = apply { pending[key] = value }
        fun putInt(key: String, value: Int) = apply { pending[key] = value.toString() }
        fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value.toString() }
        fun apply() {
            io { for ((key, value) in pending) dao.putSetting(AppSetting(key, value)) }
        }
    }
}
