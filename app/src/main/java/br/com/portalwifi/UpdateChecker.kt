package br.com.portalwifi

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/** GitHub /releases/latest omite prereleases e retorna 404 quando só há previews. */
object UpdateChecker {
    sealed interface Result {
        data class NewVersion(val version: String, val url: String): Result
        data object UpToDate: Result
        data class Error(val message: String): Result
    }
    fun checkAsync(url: String, installed: String, callback: (Result) -> Unit) {
        thread(name = "portal-update-check") {
            val result: Result = try {
                val endpoint = url.removeSuffix("/latest") + "?per_page=30"
                val con = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10000
                    readTimeout = 10000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "WiFi-Captive-Portal")
                }
                try {
                    if (con.responseCode != 200) {
                        Result.Error("Não foi possível consultar atualizações (HTTP ${con.responseCode})")
                    } else {
                        val releases = JSONArray(con.inputStream.bufferedReader().use { it.readText() })
                        val candidates = mutableListOf<Pair<String, String>>()
                        for (index in 0 until releases.length()) {
                            val release = releases.optJSONObject(index) ?: continue
                            if (release.optBoolean("draft", true)) continue
                            val version = release.optString("tag_name").removePrefix("v")
                            if (!Regex("^\\d+\\.\\d+\\.\\d+$").matches(version)) continue
                            val assets = release.optJSONArray("assets") ?: continue
                            for (j in 0 until assets.length()) {
                                val asset = assets.optJSONObject(j) ?: continue
                                val fileName = asset.optString("name")
                                val download = asset.optString("browser_download_url")
                                if (fileName.endsWith(".apk", ignoreCase = true) &&
                                    download.startsWith("https://github.com/AmaroPSJunior/WiFi-Captive-Portal/releases/download/")) {
                                    candidates.add(version to download)
                                    break
                                }
                            }
                        }
                        val newer = candidates.filter { VersionComparator.isNewer(it.first, installed) }
                        val best = newer.maxWithOrNull(compareBy<Pair<String, String>> {
                            it.first.split(".").getOrNull(0)?.toIntOrNull() ?: 0
                        }.thenBy {
                            it.first.split(".").getOrNull(1)?.toIntOrNull() ?: 0
                        }.thenBy {
                            it.first.split(".").getOrNull(2)?.toIntOrNull() ?: 0
                        })
                        if (best == null) Result.UpToDate else Result.NewVersion(best.first, best.second)
                    }
                } finally { con.disconnect() }
            } catch (e: Exception) { Result.Error(e.localizedMessage ?: "Verifique sua conexão com a internet") }
            callback(result)
        }
    }
}
