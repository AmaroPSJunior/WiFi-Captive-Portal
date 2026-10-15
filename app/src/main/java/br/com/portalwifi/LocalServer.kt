package br.com.portalwifi
import java.io.BufferedInputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min
/** Limites apenas para HTTP local; não atua sobre todo o tráfego Wi-Fi. */
class LocalServer(private val titleProvider: () -> String, private val onVisit: (String) -> Unit, private val onListening: (Boolean) -> Unit = {}) {
    private val running = AtomicBoolean(false)
    private val executor = Executors.newCachedThreadPool()
    @Volatile private var listener: ServerSocket? = null
    @Volatile var isListening: Boolean = false
        private set
    @Volatile var bytesPerSecond: Int = 128 * 1024
    @Volatile var maxMinutes: Int = 30
    private var startedAt = 0L
    fun start() {
        if (!running.compareAndSet(false, true)) return
        startedAt = System.currentTimeMillis()
        executor.execute {
            try {
                ServerSocket(8080).use { ss ->
                    listener = ss
                    isListening = true
                    onListening(true)
                    while (running.get()) {
                        val client = ss.accept()
                        executor.execute { serve(client) }
                    }
                }
            } catch (_: SocketException) {
            } catch (_: Exception) {
                running.set(false)
            } finally { listener = null; isListening = false; onListening(false) }
        }
    }
    private fun serve(socket: Socket) {
        socket.use { client ->
            client.soTimeout = 4000
            try {
                val input = BufferedInputStream(client.getInputStream())
                val first = readLine(input) ?: return
                val path = first.split(' ').getOrNull(1) ?: "/"
                for (i in 0..31) { if (readLine(input).isNullOrEmpty()) break }
                val expired = System.currentTimeMillis() - startedAt > maxMinutes.toLong() * 60000
                val probe = path.substringBefore('?') in setOf("/generate_204", "/gen_204", "/hotspot-detect.html", "/library/test/success.html", "/connecttest.txt", "/ncsi.txt")
                // Os probes so funcionam se a requisicao chegar ao servidor local.
                val permitted = path == "/" || path == "/index.html" || path == "/health" || probe
                val code = if (expired) 403 else if (!permitted) 404 else if (probe) 302 else 200
                if (code == 200 && path != "/health") onVisit(client.inetAddress.hostAddress ?: "?")
                val body = when {
                    expired -> "<h1>Sessão encerrada</h1>"
                    !permitted -> "<h1>Página não disponível</h1>"
                    path == "/health" -> "OK"
                    probe -> ""
                    else -> landingPage(titleProvider())
                }
                val bytes = body.toByteArray(Charsets.UTF_8)
                val reason = when (code) { 200 -> "OK"; 302 -> "Found"; 403 -> "Forbidden"; else -> "Not Found" }
                val location = if (code == 302) "Location: /\r\n" else ""
                val header = "HTTP/1.1 $code $reason\r\n${location}Content-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\nCache-Control: no-store\r\n\r\n"
                val out = client.getOutputStream()
                out.write(header.toByteArray(Charsets.UTF_8))
                val limit = bytesPerSecond.coerceAtLeast(1024)
                val startNanos = System.nanoTime()
                var sent = 0
                while (sent < bytes.size) {
                    val next = min(sent + 1024, bytes.size)
                    out.write(bytes, sent, next - sent)
                    sent = next
                    val delay = sent.toLong() * 1000000000 / limit - (System.nanoTime() - startNanos)
                    if (delay > 0) Thread.sleep(delay / 1000000)
                }
                out.flush()
            } catch (_: Exception) {}
        }
    }
    private fun landingPage(title: String): String {
        val safe = title.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&#39;")
        return """<!doctype html><html lang="pt-br"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>Portal Wi-Fi</title><style>
            body{margin:0;background:#070e21;color:white;font:18px system-ui;padding:24px}
            main{max-width:540px;margin:8vh auto;padding:32px;border:1px solid #246383;
            border-radius:24px;background:linear-gradient(130deg,#093c44,#1c1b50)}
            h1{color:#5ff0c7}p{line-height:1.5}small{color:#b5c5d8}
            </style></head><body><main><h1>$safe</h1>
            <p>Bem-vindo! Seu dispositivo está conectado ao Wi-Fi.</p><p>Em breve, contratação de acesso à internet por Pix.</p>
            <small>Protótipo: nenhuma cobrança ou liberação de internet está ativa. Abra o endereço local manualmente; a abertura automática depende de integração com DNS e gateway.</small>
            </main></body></html>"""
    }
    private fun readLine(input: BufferedInputStream): String? {
        val s = StringBuilder()
        while (s.length < 4096) {
            val x = input.read()
            if (x < 0) return null
            if (x == 10) return s.toString().trimEnd('\r')
            s.append(x.toChar())
        }
        return null
    }
    fun stop() { running.set(false); listener?.close(); executor.shutdownNow() }
}
