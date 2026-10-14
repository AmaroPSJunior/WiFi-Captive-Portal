package br.com.portalwifi

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.*

class MainActivity : Activity() {
    private var reservation: WifiManager.LocalOnlyHotspotReservation? = null
    private var server: LocalServer? = null
    private lateinit var status: TextView
    private lateinit var visits: TextView
    private var visitCount = 0
    private val prefs by lazy { getSharedPreferences("portal", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        val title = TextView(this).apply { text = "Wi-Fi Captive Portal · Administração"; textSize = 22f }
        val version = TextView(this).apply { text = "Versão instalada: ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})" }
        status = TextView(this).apply { text = "Desligado"; setPadding(0, 20, 0, 20) }
        visits = TextView(this).apply { text = "Acessos HTTP: 0" }
        val speed = EditText(this).apply {
            hint = "Limite do portal (KB/s)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(prefs.getInt("kbps",128).toString())
        }
        val minutes = EditText(this).apply {
            hint = "Tempo da sessão (minutos)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(prefs.getInt("minutes",30).toString())
        }
        val update = Button(this).apply { text = "Verificar atualizações" }
        val start = Button(this).apply { text = "Iniciar hotspot local" }
        val stop = Button(this).apply { text = "Encerrar portal" }
        listOf(title,version,update,status,speed,minutes,start,stop,visits).forEach {
            layout.addView(it,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        setContentView(ScrollView(this).apply { addView(layout) })
        update.setOnClickListener {
            update.isEnabled = false
            UpdateChecker.checkAsync(BuildConfig.RELEASES_API_URL,BuildConfig.VERSION_NAME) { result ->
                runOnUiThread {
                    update.isEnabled = true
                    when(result) {
                        is UpdateChecker.Result.NewVersion ->
                            AlertDialog.Builder(this)
                              .setTitle("Nova versão: ${result.version}")
                              .setMessage("O download será aberto no navegador. A instalação precisa da confirmação do Android.")
                              .setPositiveButton("Baixar APK") { _,_ -> startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(result.url))) }
                              .setNegativeButton("Depois",null).show()
                        UpdateChecker.Result.UpToDate -> Toast.makeText(this,"Versão atualizada",Toast.LENGTH_LONG).show()
                        is UpdateChecker.Result.Error -> Toast.makeText(this,result.message,Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        start.setOnClickListener {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),100)
            } else {
                val kb = speed.text.toString().toIntOrNull()?.coerceIn(1,10000) ?: 128
                val mins = minutes.text.toString().toIntOrNull()?.coerceIn(1,1440) ?: 30
                prefs.edit().putInt("kbps",kb).putInt("minutes",mins).apply()
                launch(kb,mins)
            }
        }
        stop.setOnClickListener { shutdown(); status.text = "Desligado" }
    }

    @Suppress("DEPRECATION")
    private fun launch(kb:Int, minutes:Int) {
        if (reservation != null) return
        val manager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        status.text = "Iniciando rede Wi-Fi local..."
        try {
            manager.startLocalOnlyHotspot(object:WifiManager.LocalOnlyHotspotCallback() {
                override fun onStarted(r:WifiManager.LocalOnlyHotspotReservation) {
                    reservation = r
                    val ssid = r.wifiConfiguration?.SSID ?: "Não disponível"
                    val key = r.wifiConfiguration?.preSharedKey ?: "Não disponível"
                    server = LocalServer { ip ->
                        runOnUiThread {
                            visitCount++
                            visits.text = "Acessos HTTP: $visitCount · Último: $ip"
                        }
                    }.apply {
                        bytesPerSecond = kb * 1024
                        maxMinutes = minutes
                        start()
                    }
                    status.text = "Rede: $ssid\nSenha: $key\nPortal: http://[IP local do celular]:8080\n\nAtenção: esta rede não compartilha internet. Consulte o endereço IP do hotspot nas configurações do aparelho."
                }
                override fun onStopped() {
                    shutdown()
                    status.text = "Hotspot encerrado pelo Android"
                }
                override fun onFailed(reason:Int) {
                    shutdown()
                    status.text = "Falha ao iniciar hotspot: $reason"
                }
            },android.os.Handler(mainLooper))
        } catch(e:Exception) { status.text = "Erro: ${e.message}" }
    }

    private fun shutdown() {
        server?.stop()
        server = null
        reservation?.close()
        reservation = null
    }
    override fun onDestroy() { shutdown(); super.onDestroy() }
}
