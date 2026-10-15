package br.com.portalwifi

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.app.DownloadManager
import android.os.Environment
import android.provider.Settings
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.net.wifi.WifiManager
import android.net.wifi.SoftApConfiguration
import android.net.wifi.WifiSsid
import java.util.concurrent.Executor
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.net.NetworkInterface

/**
 * Dashboard visual. Metrificação limitada ao servidor HTTP; o LocalOnlyHotspot
 * não oferece acesso à internet, lista de clientes ou filtragem de sites externos.
 */
class MainActivity : Activity() {
    private val bg = Color.rgb(7, 13, 28)
    private val panel = Color.rgb(17, 29, 52)
    private val muted = Color.rgb(159, 178, 210)
    private val cyan = Color.rgb(45, 210, 248)
    private val green = Color.rgb(50, 231, 160)
    private val purple = Color.rgb(174, 112, 255)
    private var reservation: WifiManager.LocalOnlyHotspotReservation? = null
    private var server: LocalServer? = null
    private var visits = 0
    private var downloadId: Long? = null
    private var downloadedUri: Uri? = null
    private var downloadButton: Button? = null
    private val downloadPoller by lazy(LazyThreadSafetyMode.NONE) { Handler(mainLooper) }
    private var latestVersion: String? = null
    private val downloadCheck = object : Runnable { override fun run() { pollDownload() } }
    private var startedAt = 0L
    private var busy = false
    private var networkName = "Aguardando inicialização"
    private var networkPassword = "••••••••"
    private var customNetworkRequested = false
    private val prefs by lazy { SettingsStore(this) }
    private val ticker by lazy(LazyThreadSafetyMode.NONE) { Handler(mainLooper) }
    private lateinit var stateLabel: TextView
    private lateinit var subLabel: TextView
    private lateinit var ssidView: TextView
    private lateinit var passwordView: TextView
    private lateinit var counterView: TextView
    private lateinit var uptimeView: TextView
    private lateinit var primary: Button
    private lateinit var qrView: ImageView
    private lateinit var qrHelp: TextView
    private lateinit var portalQrView: ImageView
    private lateinit var portalAddressView: TextView
    private lateinit var portalStatusView: TextView
    private lateinit var portalCopyButton: Button
    private var portalUrl: String? = null
    private val portalSettings by lazy { PortalSettings(this) }
    private lateinit var sitesList: LinearLayout
    private lateinit var speedValue: TextView
    private lateinit var durationValue: TextView
    private var kbps = 128
    private var minutes = 30
    private val tick = object : Runnable {
        override fun run() {
            if (startedAt > 0L) {
                val sec = ((System.currentTimeMillis() - startedAt) / 1000).coerceAtLeast(0)
                uptimeView.text = String.format("%02d:%02d:%02d", sec / 3600, (sec / 60) % 60, sec % 60)
            }
            ticker.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0
        kbps = prefs.getInt("kbps", 128)
        minutes = prefs.getInt("minutes", 30)
        customNetworkRequested = prefs.contains("custom_ssid") && supportsCustomLocalHotspot()
        render()
        ticker.post(tick)
    }

    private fun dp(value: Int): Int = (resources.displayMetrics.density * value + .5f).toInt()
    private fun shape(color: Int, radius: Int = 22, border: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (border != null) setStroke(dp(1), border)
        }
    private fun gradient(a: Int, b: Int, radius: Int = 22): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(a, b)).apply {
            cornerRadius = dp(radius).toFloat()
        }
    private fun label(text: String, size: Float = 15f, color: Int = Color.WHITE, bold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
        }
    private fun column(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun row(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun add(parent: LinearLayout, child: View, top: Int = 0) {
        parent.addView(child, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(top)
        })
    }
    private fun card(): LinearLayout = column().apply {
        setPadding(dp(18), dp(18), dp(18), dp(18))
        background = shape(panel, border = Color.rgb(42, 78, 116))
    }
    private fun action(text: String, a: Int, b: Int): Button = Button(this).apply {
        this.text = text
        textSize = 15f
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = gradient(a, b, 18)
        minimumHeight = dp(56)
        elevation = dp(2).toFloat()
    }

    private fun render() {
        val root = column().apply { setBackgroundColor(bg); setPadding(dp(16), dp(20), dp(16), dp(32)) }
        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false }
        scroll.addView(root)
        setContentView(scroll)
        val header = row()
        val mark = label("◉", 34f, cyan, true).apply { gravity = Gravity.CENTER; background = gradient(Color.rgb(80, 30, 180), Color.rgb(0, 116, 202), 18) }
        header.addView(mark, LinearLayout.LayoutParams(dp(58), dp(58)))
        val heading = column().apply { setPadding(dp(12), 0, 0, 0) }
        add(heading, label("Wi-Fi Captive", 23f, Color.WHITE, true))
        add(heading, label("Portal • Painel de controle", 12f, muted))
        header.addView(heading, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(label("v" + BuildConfig.VERSION_NAME, 11f, muted), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)))
        add(root, header)

        val hero = card().apply { background = gradient(Color.rgb(12, 80, 80), Color.rgb(23, 26, 75)) }
        stateLabel = label("PORTAL DESLIGADO", 24f, green, true)
        subLabel = label("Pronto para iniciar uma rede local", 13f, muted)
        add(hero, label("◉  CENTRAL WI-FI", 12f, cyan, true))
        add(hero, stateLabel, 14)
        add(hero, subLabel, 4)
        val credentials = column()
        ssidView = label("Rede: $networkName", 15f, Color.WHITE, true)
        passwordView = label("Senha: $networkPassword", 15f, Color.WHITE, true)
        add(credentials, ssidView)
        add(credentials, passwordView, 10)
        add(hero, credentials, 20)
        val copies = row()
        val copyWifi = action("Copiar rede", Color.rgb(27, 84, 130), Color.rgb(30, 50, 110))
        val copyPass = action("Copiar senha", Color.rgb(77, 42, 137), Color.rgb(56, 45, 115))
        copies.addView(copyWifi, LinearLayout.LayoutParams(0, dp(52), 1f))
        copies.addView(copyPass, LinearLayout.LayoutParams(0, dp(52), 1f).apply { leftMargin = dp(8) })
        copyWifi.setOnClickListener { copy("Nome da rede", networkName) }
        copyPass.setOnClickListener { copy("Senha", if (reservation != null) networkPassword else "") }
        add(hero, copies, 14)
        qrHelp = label("① Conectar no Wi-Fi — escaneie com a câmera.", 14f, Color.WHITE, true)
        add(hero, qrHelp, 18)
        qrView = ImageView(this).apply {
            contentDescription = "QR Code para conectar ao Wi-Fi"
            setBackgroundColor(Color.WHITE)
            visibility = View.GONE
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        hero.addView(qrView, LinearLayout.LayoutParams(dp(240), dp(240)).apply {
            gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(12)
        })
        add(hero, label("O QR Code usa sempre as credenciais confirmadas pelo Android.", 12f, muted), 10)
        add(hero, label("② Abrir portal — escaneie depois de conectar ao Wi-Fi.", 14f, Color.WHITE, true), 18)
        portalQrView = ImageView(this).apply {
            contentDescription = "QR Code com casinha para abrir o portal HTTP"
            setBackgroundColor(Color.WHITE)
            visibility = View.GONE
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        hero.addView(portalQrView, LinearLayout.LayoutParams(dp(240), dp(240)).apply {
            gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(12)
        })
        portalStatusView = label("Servidor HTTP: desligado", 13f, muted)
        add(hero, portalStatusView, 12)
        portalAddressView = label("Endereço: indisponível", 14f, Color.WHITE, true)
        add(hero, portalAddressView, 6)
        portalCopyButton = action("Copiar endereço do portal", Color.rgb(30, 100, 136), Color.rgb(54, 56, 125)).apply {
            isEnabled = false
            setOnClickListener { portalUrl?.let { copy("Endereço do portal", it) } }
        }
        add(hero, portalCopyButton, 10)
        val editNetwork = action("✎  Editar nome e senha do Wi-Fi", Color.rgb(42, 77, 121), Color.rgb(64, 47, 117))
        add(hero, editNetwork, 10)
        editNetwork.setOnClickListener { editWifiConfiguration() }

        val internetMode = Switch(this).apply {
            text = "Habilitar internet (hotspot do Android)"
            textSize = 15f
            setTextColor(Color.WHITE)
            isChecked = prefs.getBoolean("internet_mode", false)
        }
        add(hero, internetMode, 16)
        add(hero, label("No modo internet, abra as configurações do Xiaomi para ativar o hotspot. A lista de sites NÃO filtra o acesso: todos os sites poderão abrir.", 12f, muted), 6)
        internetMode.setOnCheckedChangeListener { button, enabled ->
            if (enabled) {
                AlertDialog.Builder(this)
                    .setTitle("Compartilhamento sem filtro")
                    .setMessage("O Android não permite que este aplicativo ative e filtre automaticamente o hotspot nativo. Ao ativá-lo nas configurações, os dispositivos poderão acessar qualquer site, não apenas os cadastrados. Deseja selecionar esse modo?")
                    .setPositiveButton("Selecionar modo") { _, _ ->
                        if (reservation != null) {
                            shutdown()
                            setState("PORTAL DESLIGADO", "Rede local encerrada para alternar o modo", false)
                        }
                        prefs.edit().putBoolean("internet_mode", true).apply()
                        openTetherSettings()
                    }
                    .setNegativeButton("Cancelar") { _, _ -> button.isChecked = false }
                    .setOnCancelListener { button.isChecked = false }
                    .show()
            } else {
                prefs.edit().putBoolean("internet_mode", false).apply()
                Toast.makeText(this, "Modo local selecionado. Desative o hotspot nativo nas configurações, se estiver ativo.", Toast.LENGTH_LONG).show()
            }
        }
        add(hero, label("ⓘ Rede local isolada • Não compartilha internet", 12f, muted), 16)
        primary = action("▶  INICIAR PORTAL", Color.rgb(19, 125, 232), Color.rgb(76, 60, 236))
        add(hero, primary, 18)
        primary.setOnClickListener { if (internetMode.isChecked) openTetherSettings() else if (reservation == null) startPortal() else { shutdown(); setState("PORTAL DESLIGADO", "Rede local encerrada", false) } }
        add(root, hero, 20)

        val settings = card()
        add(settings, label("⚡  CONTROLES RÁPIDOS", 16f, Color.WHITE, true))
        speedValue = label("$kbps KB/s", 23f, cyan, true)
        add(settings, label("Velocidade do servidor local", 13f, muted), 18)
        add(settings, speedValue, 4)
        val speedBar = SeekBar(this).apply {
            max = 2047
            progress = (kbps - 1).coerceIn(0, 2047)
            progressTintList = android.content.res.ColorStateList.valueOf(cyan)
            thumbTintList = android.content.res.ColorStateList.valueOf(cyan)
        }
        add(settings, speedBar, 4)
        speedBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                kbps = progress + 1
                speedValue.text = "$kbps KB/s"
                prefs.edit().putInt("kbps", kbps).apply()
                server?.bytesPerSecond = kbps * 1024
            }
            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) {}
        })
        add(settings, label("Duração da sessão HTTP", 13f, muted), 12)
        durationValue = label("$minutes min", 23f, purple, true)
        add(settings, durationValue, 4)
        val chips = row()
        for (m in listOf(30, 60, 120, 240)) {
            val chip = action(if (m < 60) "$m min" else "${m / 60} h", Color.rgb(70, 41, 137), Color.rgb(52, 65, 122))
            chip.textSize = 12f
            chips.addView(chip, LinearLayout.LayoutParams(0, dp(50), 1f).apply { rightMargin = dp(4) })
            chip.setOnClickListener {
                minutes = m
                durationValue.text = if (m < 60) "$m min" else "${m / 60} hora(s)"
                prefs.edit().putInt("minutes", minutes).apply()
                server?.maxMinutes = minutes
            }
        }
        add(settings, chips, 12)
        add(root, settings, 14)

        val metrics = row()
        val visitsCard = card()
        counterView = label("$visits", 26f, green, true)
        add(visitsCard, label("◎ ACESSOS HTTP", 12f, muted))
        add(visitsCard, counterView, 8)
        metrics.addView(visitsCard, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val timeCard = card()
        uptimeView = label("00:00:00", 25f, cyan, true)
        add(timeCard, label("◷ TEMPO ONLINE", 12f, muted))
        add(timeCard, uptimeView, 8)
        metrics.addView(timeCard, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(8) })
        add(root, metrics, 14)

        val info = card()
        add(info, label("🌐  ACESSO À REDE", 17f, Color.WHITE, true))
        add(info, label("Este modo permite apenas páginas hospedadas pelo aplicativo. Lista de sites externos e controle de internet exigem outro modo de rede.", 13f, muted), 10)
        add(info, label("A quantidade de dispositivos conectados não está disponível nesta API.", 12f, muted), 10)
        add(root, info, 14)
        val landing = card()
        add(landing, label("🏠  PÁGINA DE BOAS-VINDAS", 17f, Color.WHITE, true))
        val titleEntry = EditText(this).apply {
            setSingleLine(true)
            setText(portalSettings.title)
            setTextColor(Color.WHITE)
            setHintTextColor(muted)
            hint = "Título da página local"
        }
        add(landing, titleEntry, 10)
        val saveLanding = action("Salvar página padrão", Color.rgb(26, 106, 151), Color.rgb(68, 52, 144))
        add(landing, saveLanding, 8)
        saveLanding.setOnClickListener {
            val title = titleEntry.text.toString().trim()
            if (title.isBlank()) { titleEntry.error = "Informe um título"; return@setOnClickListener }
            portalSettings.title = title
            Toast.makeText(this, "Página local atualizada", Toast.LENGTH_SHORT).show()
        }
        add(landing, label("A página será exibida ao visitar o servidor HTTP. Abertura automática ao conectar depende de infraestrutura de portal cativo e não está disponível no hotspot local.", 12f, muted), 10)
        add(root, landing, 14)

        val allow = card()
        add(allow, label("🌐  SITES DESEJADOS", 17f, Color.WHITE, true))
        add(allow, label("Lista salva para um futuro modo gateway. A rede local atual não fornece internet; estes domínios ainda não serão acessíveis.", 12f, muted), 8)
        sitesList = column()
        add(allow, sitesList, 12)
        val siteEntry = EditText(this).apply {
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            hint = "exemplo.com.br"
            setTextColor(Color.WHITE)
            setHintTextColor(muted)
        }
        add(allow, siteEntry, 6)
        val addSite = action("+ Adicionar domínio", Color.rgb(29, 101, 132), Color.rgb(66, 54, 146))
        add(allow, addSite, 6)
        addSite.setOnClickListener {
            if (portalSettings.addSite(siteEntry.text.toString())) {
                siteEntry.text.clear()
                showSites()
            } else siteEntry.error = "Informe um domínio válido"
        }
        showSites()
        add(root, allow, 14)


        val tools = card()
        add(tools, label("⚙  APLICATIVO", 16f, Color.WHITE, true))
        val update = action("↻  Verificar atualizações", Color.rgb(49, 77, 133), Color.rgb(67, 51, 130))
        add(tools, update, 12)
        val diagnostic = action("◉  Diagnóstico do hotspot", Color.rgb(30, 98, 120), Color.rgb(62, 52, 120))
        add(tools, diagnostic, 10)
        diagnostic.setOnClickListener { showNetworkDiagnosis() }
        val laboratory = action("🔬  Laboratório Wi-Fi", Color.rgb(29, 111, 134), Color.rgb(76, 51, 148))
        add(tools, laboratory, 10)
        laboratory.setOnClickListener { showWifiLaboratory() }
        update.setOnClickListener { checkUpdate(update) }
        add(root, tools, 14)
    }

    /**
     * Diagnóstico somente leitura. Não ativa tethering, não altera firewall ou Wi-Fi.
     * Permissões privilegiadas e regras de tráfego precisam de confirmação externa via ADB.
     */
    private fun showNetworkDiagnosis() {
        val connectivity = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val active = connectivity.activeNetwork
        val caps = connectivity.getNetworkCapabilities(active)
        val wifi = getSystemService(Context.WIFI_SERVICE) as WifiManager
        val permissionNames = listOf(
            "android.permission.TETHER_PRIVILEGED",
            "android.permission.NETWORK_SETTINGS",
            "android.permission.NETWORK_STACK",
            "android.permission.CONNECTIVITY_INTERNAL",
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_WIFI_STATE
        )
        val report = buildString {
            appendLine("DIAGNÓSTICO SOMENTE LEITURA — Wi-Fi Captive Portal")
            appendLine("Android API: ${Build.VERSION.SDK_INT}")
            appendLine("Fabricante: ${Build.MANUFACTURER}; Modelo: ${Build.MODEL}")
            appendLine("Aplicativo: ${BuildConfig.VERSION_NAME}")
            appendLine("Hotspot local do app: ${if (reservation != null) "ativo" else "inativo"}")
            appendLine("Wi-Fi do aparelho: ${if (wifi.isWifiEnabled) "ativado" else "desativado"}")
            appendLine("Rede padrão com internet validada: ${caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true}")
            appendLine("Rede padrão por celular: ${caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) == true}")
            appendLine("Rede padrão por Wi-Fi: ${caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true}")
            appendLine("Permissões do aplicativo:")
            for (name in permissionNames) {
                val granted = packageManager.checkPermission(name, packageName) == PackageManager.PERMISSION_GRANTED
                appendLine("  $name: ${if (granted) "concedida" else "não concedida"}")
            }
            appendLine("Modo atual: LocalOnlyHotspot, SEM tethering de internet.")
            appendLine("Filtragem do tráfego dos clientes: NÃO implementada.")
            appendLine("Este relatório NÃO comprova que ADB ou firewall estão disponíveis.")
            appendLine("Próximo teste: consultar shell/iptables/nft no próprio Xiaomi via ADB; sem alterar regras.")
        }
        AlertDialog.Builder(this)
            .setTitle("Diagnóstico do Xiaomi")
            .setMessage(report)
            .setPositiveButton("Copiar relatório") { _, _ -> copy("Diagnóstico do hotspot", report) }
            .setNegativeButton("Fechar", null)
            .show()
    }

    private fun showWifiLaboratory() {
        val lab = WifiLaboratory(this)
        val options = arrayOf("Iniciar investigação", "Histórico e copiar relatório", "Limpar histórico")
        AlertDialog.Builder(this).setTitle("Laboratório Wi-Fi • POCO X6 Pro")
            .setItems(options) { _, index ->
                when (index) {
                    0 -> {
                        Thread {
                            try {
                                val result = lab.investigate()
                                runOnUiThread {
                                    AlertDialog.Builder(this)
                                        .setTitle("Investigação salva")
                                        .setMessage(result.report)
                                        .setPositiveButton("Copiar relatório") { _, _ -> copy("Relatório Wi-Fi", result.report) }
                                        .setNegativeButton("Fechar", null).show()
                                }
                            } catch (e: Exception) {
                                runOnUiThread {
                                    AlertDialog.Builder(this).setTitle("Investigação não concluída")
                                        .setMessage(e.javaClass.simpleName + ": " + (e.localizedMessage ?: "erro desconhecido"))
                                        .setPositiveButton("OK", null).show()
                                }
                            }
                        }.start()
                    }
                    1 -> Thread {
                        val items = try { lab.history() } catch (_: Exception) { emptyList() }
                        runOnUiThread {
                            if (items.isEmpty()) {
                                Toast.makeText(this, "Nenhuma investigação salva", Toast.LENGTH_LONG).show()
                            } else {
                                val fmt = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
                                val labels = items.map { fmt.format(java.util.Date(it.createdAt)) }.toTypedArray()
                                AlertDialog.Builder(this).setTitle("Histórico de investigações")
                                    .setItems(labels) { _, selected ->
                                        val report = items[selected].report
                                        AlertDialog.Builder(this).setTitle(labels[selected])
                                            .setMessage(report)
                                            .setPositiveButton("Copiar relatório") { _, _ -> copy("Relatório Wi-Fi", report) }
                                            .setNegativeButton("Fechar", null).show()
                                    }.setNegativeButton("Fechar", null).show()
                            }
                        }
                    }.start()
                    2 -> AlertDialog.Builder(this).setTitle("Limpar histórico?")
                        .setMessage("Isso remove apenas os relatórios do laboratório. As configurações do portal serão mantidas.")
                        .setPositiveButton("Limpar") { _, _ ->
                            Thread {
                                lab.clear()
                                runOnUiThread { Toast.makeText(this, "Histórico limpo", Toast.LENGTH_SHORT).show() }
                            }.start()
                        }
                        .setNegativeButton("Cancelar", null).show()
                }
            }.setNegativeButton("Fechar", null).show()
    }

    private fun openTetherSettings() {
        AlertDialog.Builder(this)
            .setTitle("Ativar internet no Xiaomi")
            .setMessage("O próximo passo é ativar manualmente o hotspot Wi-Fi do Android. Neste modo não há bloqueio por sites autorizados. O app não afirma que a internet está ativa sem confirmação do sistema.")
            .setPositiveButton("Abrir configurações") { _, _ ->
                try {
                    startActivity(Intent("android.settings.TETHER_SETTINGS"))
                } catch (_: Exception) {
                    startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun supportsCustomLocalHotspot(): Boolean = try {
        Build.VERSION.SDK_INT >= 36 &&
            android.net.wifi.SoftApConfiguration.Builder::class.java.getMethod(
                "setWifiSsid", WifiSsid::class.java
            ) != null &&
            android.net.wifi.SoftApConfiguration.Builder::class.java.getMethod(
                "setPassphrase", String::class.java, Integer.TYPE
            ) != null &&
            WifiManager::class.java.getMethod(
                "startLocalOnlyHotspotWithConfiguration",
                SoftApConfiguration::class.java,
                Executor::class.java,
                WifiManager.LocalOnlyHotspotCallback::class.java
            ) != null
    } catch (_: Exception) { false }

    private fun editWifiConfiguration() {
        if (!supportsCustomLocalHotspot()) {
            AlertDialog.Builder(this).setTitle("Personalização indisponível")
                .setMessage("Esta versão do Android não expõe a configuração personalizada para hotspot local. Nome e senha são gerados pelo sistema. O QR Code continuará exibindo os dados reais.")
                .setPositiveButton("OK", null).show()
            return
        }
        val content = column().apply { setPadding(dp(20), dp(12), dp(20), 0) }
        val name = EditText(this).apply {
            hint = "Nome da rede (SSID)"
            setSingleLine(true)
            setText(prefs.getString("custom_ssid", networkName.takeIf { reservation != null } ?: "MeuPortal"))
        }
        val pass = EditText(this).apply {
            hint = "Senha WPA2 (8 a 63 caracteres)"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(prefs.getString("custom_pass", ""))
        }
        add(content, name)
        add(content, pass, 8)
        val dialog = AlertDialog.Builder(this).setTitle("Configuração do Wi-Fi")
            .setMessage("Ao aplicar, o hotspot será reiniciado. Outros dispositivos precisarão reconectar.")
            .setView(content)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Aplicar", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val ssid = name.text.toString().trim()
                val password = pass.text.toString()
                if (ssid.isBlank() || ssid.toByteArray(Charsets.UTF_8).size > 32) {
                    name.error = "SSID deve ter de 1 a 32 bytes UTF-8"
                    return@setOnClickListener
                }
                if (password.length !in 8..63 || password.any { it.code !in 32..126 }) {
                    pass.error = "Senha deve conter 8–63 caracteres ASCII"
                    return@setOnClickListener
                }
                prefs.edit().putString("custom_ssid", ssid).putString("custom_pass", password).apply()
                customNetworkRequested = true
                dialog.dismiss()
                shutdown()
                setState("PORTAL DESLIGADO", "Nova configuração salva. Inicie novamente o hotspot.", false)
                Toast.makeText(this, "Toque em Iniciar Portal para aplicar a nova rede", Toast.LENGTH_LONG).show()
            }
        }
        dialog.show()
    }

    /** Usa o IPv4 da interface ap0 real, nunca um gateway presumido. */
    private fun updatePortalAddress(listening: Boolean = server?.isListening == true) {
        if (!::portalQrView.isInitialized) return
        val ip = try {
            NetworkInterface.getByName("ap0")?.inetAddresses?.toList()
                ?.filterIsInstance<java.net.Inet4Address>()
                ?.firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                ?.hostAddress
        } catch (_: Exception) { null }
        val ready = reservation != null && listening && !ip.isNullOrBlank()
        portalUrl = if (ready) "http://$ip:8080/" else null
        portalStatusView.text = when {
            reservation == null -> "Servidor HTTP: desligado"
            !listening -> "Servidor HTTP: aguardando porta 8080"
            ip == null -> "Servidor HTTP ativo, mas IP de ap0 indisponível"
            else -> "Servidor HTTP: ativo na porta 8080"
        }
        portalAddressView.text = "Endereço: ${portalUrl ?: "indisponível"}"
        portalCopyButton.isEnabled = ready
        portalQrView.visibility = if (ready) View.VISIBLE else View.GONE
        if (ready) try {
            portalQrView.setImageBitmap(WifiQr.urlBitmap(portalUrl!!))
        } catch (_: Exception) {
            portalQrView.visibility = View.GONE
            portalStatusView.text = "QR indisponível; use o endereço em texto"
        }
    }

    private fun refreshQr() {
        if (reservation == null || networkName == "Aguardando inicialização" ||
            networkPassword.length < 8 || networkPassword == "Consulte as configurações") {
            qrView.visibility = View.GONE
            qrHelp.text = "Inicie a rede para visualizar o QR Code de conexão."
            return
        }
        try {
            qrView.setImageBitmap(WifiQr.bitmap(networkName, networkPassword))
            qrView.visibility = View.VISIBLE
            qrHelp.text = "Aponte a câmera de outro dispositivo para conectar"
        } catch (_: Exception) {
            qrView.visibility = View.GONE
            qrHelp.text = "Não foi possível gerar o QR Code desta rede."
        }
    }

    private fun showSites() {
        sitesList.removeAllViews()
        val sites = portalSettings.sites()
        if (sites.isEmpty()) add(sitesList, label("Nenhum domínio cadastrado", 13f, muted))
        for (site in sites) {
            val line = row()
            line.addView(label("•  $site", 14f, Color.WHITE), LinearLayout.LayoutParams(0, dp(48), 1f))
            val remove = action("Remover", Color.rgb(107, 40, 78), Color.rgb(73, 37, 72))
            line.addView(remove, LinearLayout.LayoutParams(dp(105), dp(48)))
            remove.setOnClickListener { portalSettings.removeSite(site); showSites() }
            add(sitesList, line, 4)
        }
    }

    private fun copy(name: String, value: String) {
        if (value.isBlank() || value.startsWith("Aguardando")) {
            Toast.makeText(this, "Inicie o portal primeiro", Toast.LENGTH_SHORT).show(); return
        }
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText(name, value))
        Toast.makeText(this, "$name copiado", Toast.LENGTH_SHORT).show()
    }
    private fun setState(title: String, subtitle: String, active: Boolean) {
        stateLabel.text = title
        stateLabel.setTextColor(if (active) green else Color.rgb(255, 179, 100))
        subLabel.text = subtitle
        primary.text = if (active) "■  ENCERRAR PORTAL" else "▶  INICIAR PORTAL"
        primary.background = if (active) gradient(Color.rgb(240, 52, 105), Color.rgb(173, 20, 76)) else gradient(Color.rgb(19, 125, 232), Color.rgb(76, 60, 236))
        primary.isEnabled = true
        busy = false
    }
    private fun startPortal() {
        if (busy || reservation != null) return
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        val missing = permissions.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), 100)
            Toast.makeText(this, "Autorize as permissões e toque em iniciar novamente", Toast.LENGTH_LONG).show()
            return
        }
        launch()
    }
    @Suppress("DEPRECATION")
    private fun launch() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
            (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED)) return
        busy = true
        primary.isEnabled = false
        stateLabel.text = "INICIANDO..."
        subLabel.text = "Preparando hotspot local seguro"
        val manager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        try {
            val callback = object : WifiManager.LocalOnlyHotspotCallback() {
                override fun onStarted(r: WifiManager.LocalOnlyHotspotReservation) {
                    reservation = r
                    val config = if (Build.VERSION.SDK_INT >= 30) r.softApConfiguration else null
                    networkName = (config?.ssid ?: r.wifiConfiguration?.SSID ?: "Rede do Android").trim('"')
                    networkPassword = config?.passphrase ?: r.wifiConfiguration?.preSharedKey ?: "Consulte as configurações"
                    ssidView.text = "Rede: $networkName"
                    passwordView.text = "Senha: $networkPassword"
                    refreshQr()
                    startedAt = System.currentTimeMillis()
                    server = LocalServer({ portalSettings.title }, { _ ->
                        runOnUiThread { visits++; counterView.text = visits.toString() }
                    }, { listening ->
                        runOnUiThread { updatePortalAddress(listening) }
                    }).apply {
                        bytesPerSecond = kbps * 1024
                        maxMinutes = minutes
                        start()
                    }
                    setState("PORTAL ATIVO", "Rede local disponível • HTTP na porta 8080", true)
                }
                override fun onStopped() {
                    shutdown()
                    setState("PORTAL DESLIGADO", "Hotspot encerrado pelo Android", false)
                }
                override fun onFailed(reason: Int) {
                    shutdown()
                    val message = when (reason) {
                        WifiManager.LocalOnlyHotspotCallback.ERROR_INCOMPATIBLE_MODE -> "Desative o hotspot convencional ou outra função Wi-Fi conflitante."
                        WifiManager.LocalOnlyHotspotCallback.ERROR_NO_CHANNEL -> "Nenhum canal Wi-Fi disponível no momento."
                        WifiManager.LocalOnlyHotspotCallback.ERROR_GENERIC -> "O Android não conseguiu iniciar a rede local."
                        else -> "Não foi possível iniciar a rede (código $reason)."
                    }
                    setState("NÃO FOI POSSÍVEL INICIAR", message, false)
                    AlertDialog.Builder(this@MainActivity).setTitle("Hotspot indisponível")
                        .setMessage(message + "\n\nTente desligar o compartilhamento de internet nas configurações do aparelho.")
                        .setPositiveButton("Entendi", null).show()
                }
            }
            if (customNetworkRequested && supportsCustomLocalHotspot()) {
                val ssid = prefs.getString("custom_ssid", null) ?: ""
                val password = prefs.getString("custom_pass", null) ?: ""
                val configBuilder = SoftApConfiguration.Builder()
                val wifiSsid = WifiSsid::class.java.getMethod("fromBytes", ByteArray::class.java)
                    .invoke(null, ssid.toByteArray(Charsets.UTF_8)) as WifiSsid
                configBuilder.javaClass.getMethod("setWifiSsid", WifiSsid::class.java)
                    .invoke(configBuilder, wifiSsid)
                configBuilder.javaClass.getMethod("setPassphrase", String::class.java, Integer.TYPE)
                    .invoke(configBuilder, password, SoftApConfiguration.SECURITY_TYPE_WPA2_PSK)
                val config = configBuilder.build()
                manager.javaClass.getMethod("startLocalOnlyHotspotWithConfiguration",
                    SoftApConfiguration::class.java, Executor::class.java,
                    WifiManager.LocalOnlyHotspotCallback::class.java)
                    .invoke(manager, config, mainExecutor, callback)
            } else {
                manager.startLocalOnlyHotspot(callback, Handler(mainLooper))
            }
        } catch (e: SecurityException) {
            setState("PERMISSÃO NECESSÁRIA", "Conceda as permissões de Wi-Fi nas configurações", false)
        } catch (e: Exception) {
            setState("ERRO AO INICIAR", e.localizedMessage ?: "Tente iniciar sem personalização ou consulte as configurações de Wi-Fi.", false)
        }
    }
    private fun refreshQrOff() {
        qrView.visibility = View.GONE
        qrHelp.text = "QR Code disponível quando a rede estiver ativa"
    }
    private fun shutdown() {
        val oldServer = server
        server = null
        oldServer?.stop()
        val oldReservation = reservation
        reservation = null
        oldReservation?.close()
        startedAt = 0L
        if (::qrView.isInitialized) refreshQrOff()
        if (::portalQrView.isInitialized) updatePortalAddress(false)
        networkName = "Aguardando inicialização"
        networkPassword = "••••••••"
        if (::ssidView.isInitialized) ssidView.text = "Rede: $networkName"
        if (::passwordView.isInitialized) passwordView.text = "Senha: $networkPassword"
    }
    private fun checkUpdate(button: Button) {
        button.isEnabled = false
        button.text = "Buscando atualização..."
        UpdateChecker.checkAsync(BuildConfig.RELEASES_API_URL, BuildConfig.VERSION_NAME) { result ->
            runOnUiThread {
                button.isEnabled = true
                button.text = "↻  Verificar atualizações"
                when (result) {
                    is UpdateChecker.Result.NewVersion -> AlertDialog.Builder(this)
                        .setTitle("Atualização ${result.version} disponível")
                        .setMessage("Deseja baixar o APK agora? Após o download, o Android solicitará confirmação para instalar. Se esta versão estiver assinada com outra chave, pode ser necessário desinstalar a anterior.")
                        .setPositiveButton("Baixar APK") { _, _ -> downloadApk(result.version, result.url, button) }
                        .setNegativeButton("Agora não", null).show()
                    is UpdateChecker.Result.BuildAvailable -> AlertDialog.Builder(this)
                        .setTitle("Build ${result.version} disponível")
                        .setMessage("Encontramos uma compilação de teste mais recente no GitHub Actions. Ela ainda não é uma release assinada. O arquivo vem na seção Artifacts e pode exigir extração do ZIP; a assinatura de teste pode impedir instalar sobre a versão atual.")
                        .setPositiveButton("Abrir build no GitHub") { _, _ ->
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result.url)))
                        }
                        .setNegativeButton("Agora não", null).show()
                    UpdateChecker.Result.UpToDate -> Toast.makeText(this, "Nenhuma atualização publicada ou build mais recente encontrado", Toast.LENGTH_LONG).show()
                    is UpdateChecker.Result.Error -> AlertDialog.Builder(this)
                        .setTitle("Falha ao verificar atualização")
                        .setMessage(result.message)
                        .setPositiveButton("OK", null).show()
                }
            }
        }
    }

    private fun downloadApk(version: String, url: String, button: Button) {
        if (downloadId != null) {
            Toast.makeText(this, "Download em andamento", Toast.LENGTH_LONG).show()
            return
        }
        try {
            val target = Uri.parse(url)
            if (target.scheme != "https" || target.host != "github.com") {
                Toast.makeText(this, "Endereço de atualização inválido", Toast.LENGTH_LONG).show()
                return
            }
            val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            val fileName = "WiFi-Captive-Portal-$version.apk"
            val request = DownloadManager.Request(target)
                .setTitle("Wi-Fi Captive Portal $version")
                .setDescription("Baixando atualização do aplicativo")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            downloadId = manager.enqueue(request)
            latestVersion = version
            downloadButton = button
            button.text = "Baixando atualização..."
            button.isEnabled = false
            downloadPoller.postDelayed(downloadCheck, 1000)
            Toast.makeText(this, "Download iniciado", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            downloadId = null
            button.isEnabled = true
            button.text = "↻  Verificar atualizações"
            AlertDialog.Builder(this).setTitle("Falha ao baixar APK")
                .setMessage(e.localizedMessage ?: "Tente novamente")
                .setPositiveButton("OK", null).show()
        }
    }

    private fun pollDownload() {
        val id = downloadId ?: return
        val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        try {
            manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
                if (!cursor.moveToFirst()) {
                    finishDownload("Download não encontrado")
                    return
                }
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    val uri = manager.getUriForDownloadedFile(id)
                    finishDownload(null)
                    if (uri != null) {
                        downloadedUri = uri
                        promptInstall(uri)
                    } else {
                        Toast.makeText(this, "APK baixado, abra a pasta Downloads", Toast.LENGTH_LONG).show()
                    }
                } else if (status == DownloadManager.STATUS_FAILED) {
                    val errorCode = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    finishDownload("Download falhou (código $errorCode)")
                } else {
                    val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    val done = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    if (total > 0) {
                        val percent = (done * 100 / total).coerceIn(0, 100)
                        downloadButton?.text = "Baixando... $percent%"
                    }
                    downloadPoller.postDelayed(downloadCheck, 1000)
                }
            }
        } catch (e: Exception) {
            finishDownload(e.localizedMessage ?: "Falha ao consultar download")
        }
    }

    private fun finishDownload(error: String?) {
        downloadPoller.removeCallbacks(downloadCheck)
        downloadId = null
        downloadButton?.isEnabled = true
        downloadButton?.text = if (error == null) "✓  Instalar APK baixado" else "↻  Verificar atualizações"
        if (error == null) downloadButton?.setOnClickListener {
            downloadedUri?.let { uri -> promptInstall(uri) } ?: Toast.makeText(this, "APK não encontrado", Toast.LENGTH_LONG).show()
        }
        if (error != null) AlertDialog.Builder(this).setTitle("Download interrompido")
            .setMessage(error).setPositiveButton("OK", null).show()
    }

    private fun promptInstall(uri: Uri) {
        if (Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(this)
                .setTitle("Permitir instalação")
                .setMessage("Autorize instalações de aplicativos desta origem e volte para tocar em 'Instalar APK baixado'.")
                .setPositiveButton("Abrir configurações") { _, _ ->
                    startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:$packageName")))
                }
                .setNegativeButton("Cancelar", null).show()
            return
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            AlertDialog.Builder(this).setTitle("Não foi possível abrir o instalador")
                .setMessage("Abra o APK na pasta Downloads para concluir a instalação. ${e.localizedMessage ?: ""}")
                .setPositiveButton("OK", null).show()
        }
    }

    override fun onDestroy() {
        ticker.removeCallbacks(tick)
        downloadPoller.removeCallbacks(downloadCheck)
        shutdown()
        super.onDestroy()
    }
}
