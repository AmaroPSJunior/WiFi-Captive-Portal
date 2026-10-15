package br.com.portalwifi

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Read-only probes. Never changes hotspot, routes, kernel, or system configuration. */
class WifiLaboratory(private val context: Context) {
    private val dao = PortalDatabase.get(context).dao()
    private fun <T> db(block: suspend () -> T) = runBlocking { withContext(Dispatchers.IO) { block() } }
    private fun probe(name: String, test: () -> Any?): String =
        try { "[OK] $name: ${test() ?: "indisponível"}" }
        catch (e: SecurityException) { "[SEM PERMISSÃO] $name: ${e.javaClass.simpleName}" }
        catch (e: Exception) { "[ERRO] $name: ${e.javaClass.simpleName}" }

    fun investigate(): WifiInvestigation {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val tests = listOf(
            probe("Fabricante") { Build.MANUFACTURER },
            probe("Modelo") { Build.MODEL },
            probe("Dispositivo") { Build.DEVICE },
            probe("Placa") { Build.BOARD },
            probe("Hardware") { Build.HARDWARE },
            probe("SoC") { if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else "API anterior a 31" },
            probe("Android API") { Build.VERSION.SDK_INT },
            probe("Android release") { Build.VERSION.RELEASE },
            probe("Build fingerprint") { Build.FINGERPRINT },
            probe("Kernel") { System.getProperty("os.version") },
            probe("Wi-Fi ligado") { wifi.isWifiEnabled },
            probe("Wi-Fi 5 GHz disponível") { wifi.is5GHzBandSupported },
            probe("Wi-Fi 6 GHz disponível") { if (Build.VERSION.SDK_INT >= 30) wifi.is6GHzBandSupported else "API anterior a 30" },
            probe("Rede ativa validada") {
                val n = connectivity.activeNetwork
                connectivity.getNetworkCapabilities(n)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ?: false
            },
            probe("Transporte celular") {
                connectivity.getNetworkCapabilities(connectivity.activeNetwork)
                    ?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ?: false
            },
            probe("Interface hotspot ap0 visível") { NetworkInterface.getByName("ap0") != null },
            probe("Interface hotspot ap0 ativa") { NetworkInterface.getByName("ap0")?.isUp ?: false },
            probe("Interface secundária ap1 visível") { NetworkInterface.getByName("ap1") != null },
            probe("Interface secundária ap1 ativa") { NetworkInterface.getByName("ap1")?.isUp ?: false },
            probe("Redes visíveis ao aplicativo (quantidade)") { connectivity.allNetworks.size },
            probe("Rede padrão com capacidade INTERNET") {
                connectivity.getNetworkCapabilities(connectivity.activeNetwork)
                    ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: false
            },
            probe("Interfaces de rede (nomes)") {
                NetworkInterface.getNetworkInterfaces()?.toList()?.map { it.name }?.sorted()?.joinToString(", ")
            },
            probe("CHANGE_WIFI_STATE") { context.checkSelfPermission(android.Manifest.permission.CHANGE_WIFI_STATE) == PackageManager.PERMISSION_GRANTED },
            probe("ACCESS_WIFI_STATE") { context.checkSelfPermission(android.Manifest.permission.ACCESS_WIFI_STATE) == PackageManager.PERMISSION_GRANTED },
            probe("TETHER_PRIVILEGED") { context.packageManager.checkPermission("android.permission.TETHER_PRIVILEGED", context.packageName) == PackageManager.PERMISSION_GRANTED },
            probe("NETWORK_SETTINGS") { context.packageManager.checkPermission("android.permission.NETWORK_SETTINGS", context.packageName) == PackageManager.PERMISSION_GRANTED },
            probe("NETWORK_STACK") { context.packageManager.checkPermission("android.permission.NETWORK_STACK", context.packageName) == PackageManager.PERMISSION_GRANTED }
        )
        val now = System.currentTimeMillis()
        val report = buildString {
            appendLine("LABORATÓRIO WI-FI — INVESTIGAÇÃO SOMENTE LEITURA")
            appendLine("Data: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date(now)))
            appendLine("App: " + BuildConfig.VERSION_NAME)
            appendLine("Testes: " + tests.size)
            appendLine()
            tests.forEach { appendLine(it) }
            appendLine()
            appendLine("NOTA: ap0/ap1 são nomes comuns do chipset, não prova de compartilhamento ativo.")
            appendLine("NOTA: detecção de interface e rede não equivale a permissão de firewall ou tethering.")
            appendLine("LIMITAÇÕES: sem root, sem modificar rede, sem acesso privilegiado.")
            appendLine("Resultado positivo de capacidade NÃO comprova controle de tethering ou filtragem.")
            appendLine("Dados de SSID, senhas, IPs, MACs e identificadores pessoais não são coletados.")
        }
        return WifiInvestigation(UUID.randomUUID().toString(), now, report).also { db { dao.insertInvestigation(it) } }
    }
    fun history() = db { dao.investigations() }
    fun clear() = db { dao.clearInvestigations() }
}
