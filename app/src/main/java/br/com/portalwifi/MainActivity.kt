package br.com.portalwifi

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.net.wifi.WifiManager
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
    private var startedAt = 0L
    private var busy = false
    private var networkName = "Aguardando inicialização"
    private var networkPassword = "••••••••"
    private val prefs by lazy { getSharedPreferences("portal", MODE_PRIVATE) }
    private val ticker = Handler(mainLooper)
    private lateinit var stateLabel: TextView
    private lateinit var subLabel: TextView
    private lateinit var ssidView: TextView
    private lateinit var passwordView: TextView
    private lateinit var counterView: TextView
    private lateinit var uptimeView: TextView
    private lateinit var primary: Button
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
        add(hero, label("ⓘ Rede local isolada • Não compartilha internet", 12f, muted), 16)
        primary = action("▶  INICIAR PORTAL", Color.rgb(19, 125, 232), Color.rgb(76, 60, 236))
        add(hero, primary, 18)
        primary.setOnClickListener { if (reservation == null) startPortal() else { shutdown(); setState("PORTAL DESLIGADO", "Rede local encerrada", false) } }
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

        val tools = card()
        add(tools, label("⚙  APLICATIVO", 16f, Color.WHITE, true))
        val update = action("↻  Verificar atualizações", Color.rgb(49, 77, 133), Color.rgb(67, 51, 130))
        add(tools, update, 12)
        update.setOnClickListener { checkUpdate(update) }
        add(root, tools, 14)
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
            manager.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                override fun onStarted(r: WifiManager.LocalOnlyHotspotReservation) {
                    reservation = r
                    networkName = r.wifiConfiguration?.SSID ?: "Rede do Android"
                    networkPassword = r.wifiConfiguration?.preSharedKey ?: "Consulte as configurações"
                    ssidView.text = "Rede: $networkName"
                    passwordView.text = "Senha: $networkPassword"
                    startedAt = System.currentTimeMillis()
                    server = LocalServer { _ ->
                        runOnUiThread { visits++; counterView.text = visits.toString() }
                    }.apply {
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
            }, Handler(mainLooper))
        } catch (e: SecurityException) {
            setState("PERMISSÃO NECESSÁRIA", "Conceda as permissões de Wi-Fi nas configurações", false)
        } catch (e: Exception) {
            setState("ERRO AO INICIAR", e.localizedMessage ?: "Tente novamente", false)
        }
    }
    private fun shutdown() {
        val oldServer = server
        server = null
        oldServer?.stop()
        val oldReservation = reservation
        reservation = null
        oldReservation?.close()
        startedAt = 0L
        networkName = "Aguardando inicialização"
        networkPassword = "••••••••"
        if (::ssidView.isInitialized) ssidView.text = "Rede: $networkName"
        if (::passwordView.isInitialized) passwordView.text = "Senha: $networkPassword"
    }
    private fun checkUpdate(button: Button) {
        button.isEnabled = false
        UpdateChecker.checkAsync(BuildConfig.RELEASES_API_URL, BuildConfig.VERSION_NAME) { result ->
            runOnUiThread {
                button.isEnabled = true
                when (result) {
                    is UpdateChecker.Result.NewVersion -> AlertDialog.Builder(this)
                        .setTitle("Versão ${result.version} disponível")
                        .setMessage("O APK será aberto no navegador. A instalação depende da confirmação do Android.")
                        .setPositiveButton("Baixar") { _, _ -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result.url))) }
                        .setNegativeButton("Agora não", null).show()
                    UpdateChecker.Result.UpToDate -> Toast.makeText(this, "Nenhuma atualização estável disponível", Toast.LENGTH_LONG).show()
                    is UpdateChecker.Result.Error -> Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    override fun onDestroy() {
        ticker.removeCallbacks(tick)
        shutdown()
        super.onDestroy()
    }
}
