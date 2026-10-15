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
        return """<!doctype html><html lang="pt-BR"><head>
            <meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            <meta name="color-scheme" content="dark"><title>Portal Wi-Fi • Pix</title>
            <style>
            *{box-sizing:border-box}body{margin:0;background:#080f20;color:#f6f9ff;font:16px system-ui,-apple-system,sans-serif;padding:20px}
            main{max-width:510px;margin:5vh auto}header{display:flex;justify-content:space-between;align-items:center;margin-bottom:26px}
            .brand{font-weight:800;font-size:19px}.pill{border:1px solid #27625b;color:#81f0c2;border-radius:40px;padding:7px 12px;font-size:12px}
            .card{background:#121d33;border:1px solid #2e4666;border-radius:22px;padding:25px;margin:14px 0}
            h1{font-size:28px;line-height:1.15;margin:8px 0 12px}h2{font-size:18px;margin:0 0 12px}p{color:#b9c8df;line-height:1.55}
            .eyebrow{color:#82e7d5;font-weight:700;font-size:12px;letter-spacing:.12em}
            .steps{display:grid;gap:12px;margin-top:18px}.step{display:flex;gap:12px;align-items:start}
            .number{display:grid;place-items:center;flex-shrink:0;background:#24465d;border-radius:12px;width:32px;height:32px;color:#8af3d5;font-weight:800}
            .step div:last-child{padding-top:4px}.price{font-size:29px;font-weight:800;color:white}
            button{width:100%;border:0;border-radius:14px;padding:16px;background:#405168;color:#cbd5e1;font-weight:750;font-size:16px}
            small{display:block;color:#a4b3cb;line-height:1.55;margin-top:13px}
            .warning{border-left:3px solid #f9bd70;padding-left:12px;color:#ffd9a6}
            footer{text-align:center;color:#8194ad;font-size:12px;margin:24px 0}
            </style></head><body><main>
            <header><div class="brand">◉ Portal Wi-Fi</div><span class="pill">✓ Rede conectada</span></header>
            <section class="card"><div class="eyebrow">BEM-VINDO</div>
            <h1>$safe</h1><p>Você já está conectado ao Wi-Fi local. Aqui você poderá contratar acesso à internet com pagamento Pix.</p></section>
            <section class="card"><h2>Internet via Pix</h2>
            <p>Plano e valor serão apresentados antes de qualquer cobrança.</p>
            <div class="price">Em configuração</div>
            <div class="steps">
            <div class="step"><span class="number">1</span><div>Escolha o plano e confira o valor.</div></div>
            <div class="step"><span class="number">2</span><div>Pague por Pix com código Copia e Cola ou QR Code.</div></div>
            <div class="step"><span class="number">3</span><div>O pagamento será confirmado pelo servidor antes de qualquer liberação.</div></div>
            </div><p class="warning">Pagamento ainda indisponível nesta versão de teste.</p>
            <button type="button" disabled aria-disabled="true">Gerar Pix — em breve</button>
            <small>Este hotspot é local e não fornece internet. O acesso externo dependerá de um mecanismo de compartilhamento validado no Xiaomi. Nenhum pagamento é cobrado agora.</small>
            </section><footer>Portal Wi-Fi • Conexão local segura</footer></main></body></html>"""
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
