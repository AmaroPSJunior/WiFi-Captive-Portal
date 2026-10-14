package br.com.portalwifi

import android.content.Context
import org.json.JSONArray

class PortalSettings(context: Context) {
    private val prefs = context.getSharedPreferences("portal", Context.MODE_PRIVATE)
    var title: String
        get() = prefs.getString("landing_title", "Bem-vindo ao Portal Wi-Fi") ?: "Bem-vindo ao Portal Wi-Fi"
        set(value) { prefs.edit().putString("landing_title", value.trim().take(80)).apply() }

    /** Informational allowlist only: LocalOnlyHotspot has no internet or gateway. */
    fun sites(): List<String> = try {
        val array = JSONArray(prefs.getString("allowed_sites", "[]") ?: "[]")
        (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
    } catch (_: Exception) { emptyList() }

    fun addSite(raw: String): Boolean {
        val value = raw.trim().lowercase().removePrefix("https://").removePrefix("http://").substringBefore('/').substringBefore(':')
        if (!Regex("^(?=.{4,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$").matches(value)) return false
        val next = (sites() + value).distinct().take(50)
        prefs.edit().putString("allowed_sites", JSONArray(next).toString()).apply()
        return true
    }
    fun removeSite(value: String) {
        prefs.edit().putString("allowed_sites", JSONArray(sites().filterNot { it == value }).toString()).apply()
    }
}
