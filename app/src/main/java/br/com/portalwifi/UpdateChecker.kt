package br.com.portalwifi
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread
object UpdateChecker {
    sealed interface Result {
        data class NewVersion(val version: String, val url: String): Result
        data object UpToDate: Result
        data class Error(val message: String): Result
    }
    fun checkAsync(url: String, installed: String, callback: (Result) -> Unit) {
        thread(name = "portal-update-check") {
            val result = try {
                val con = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000; readTimeout = 8000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "WiFi-Captive-Portal")
                }
                try {
                    if (con.responseCode != 200) Result.Error("HTTP ${con.responseCode}")
                    else {
                        val obj = JSONObject(con.inputStream.bufferedReader().use { it.readText() })
                        val version = obj.optString("tag_name").removePrefix("v")
                        val assets = obj.optJSONArray("assets")
                        var apk: String? = null
                        if (assets != null) for (i in 0 until assets.length()) {
                            val a = assets.optJSONObject(i) ?: continue
                            val link = a.optString("browser_download_url")
                            if (a.optString("name").endsWith(".apk") && link.startsWith("https://github.com/")) { apk = link; break }
                        }
                        if (VersionComparator.isNewer(version, installed)) {
                            if (apk == null) Result.Error("Release sem APK") else Result.NewVersion(version, apk)
                        } else Result.UpToDate
                    }
                } finally { con.disconnect() }
            } catch (e: Exception) { Result.Error(e.localizedMessage ?: "sem conexão") }
            callback(result)
        }
    }
}
