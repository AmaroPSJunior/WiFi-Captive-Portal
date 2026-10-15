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
        data class BuildAvailable(val version: String, val url: String): Result
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
                        if (best == null) latestSuccessfulBuild(installed) ?: Result.UpToDate else Result.NewVersion(best.first, best.second)
                    }
                } finally { con.disconnect() }
            } catch (e: Exception) { Result.Error(e.localizedMessage ?: "Verifique sua conexão com a internet") }
            callback(result)
        }
    }
    /**
     * Artifacts are temporary workflow outputs, not signed releases.
     * Never offer an artifact URL to DownloadManager as an APK.
     */
    private fun latestSuccessfulBuild(installed: String): Result? {
        val url = "https://api.github.com/repos/AmaroPSJunior/WiFi-Captive-Portal/actions/workflows/android.yml/runs?per_page=30"
        val con = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 10000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "WiFi-Captive-Portal")
        }
        return try {
            if (con.responseCode != 200) return null
            val runs = JSONObject(con.inputStream.bufferedReader().use { it.readText() })
                .optJSONArray("workflow_runs") ?: return null
            var bestVersion: String? = null
            var bestUrl: String? = null
            for (index in 0 until runs.length()) {
                val run = runs.optJSONObject(index) ?: continue
                if (run.optString("conclusion") != "success") continue
                if (run.optString("event") !in setOf("push", "pull_request", "workflow_dispatch")) continue
                val number = run.optInt("run_number", -1)
                if (number < 0) continue
                val version = "0.1.$number"
                if (!VersionComparator.isNewer(version, installed)) continue
                if (bestVersion == null || VersionComparator.isNewer(version, bestVersion)) {
                    val page = run.optString("html_url")
                    if (!page.startsWith("https://github.com/AmaroPSJunior/WiFi-Captive-Portal/actions/runs/")) continue
                    bestVersion = version
                    bestUrl = page
                }
            }
            if (bestVersion != null && bestUrl != null) Result.BuildAvailable(bestVersion, bestUrl) else null
        } finally { con.disconnect() }
    }

}
