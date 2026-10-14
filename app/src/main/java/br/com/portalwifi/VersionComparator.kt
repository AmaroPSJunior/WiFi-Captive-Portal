package br.com.portalwifi
object VersionComparator {
    fun isNewer(candidate: String, installed: String): Boolean {
        val regex = Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)(?:[-+].*)?$")
        val c = regex.matchEntire(candidate)?.groupValues?.drop(1)?.mapNotNull { it.toIntOrNull() } ?: return false
        val i = regex.matchEntire(installed)?.groupValues?.drop(1)?.mapNotNull { it.toIntOrNull() } ?: return false
        for (p in 0..2) if (c[p] != i[p]) return c[p] > i[p]
        return installed.contains("-dev") && !candidate.contains("-dev")
    }
}
