package br.com.portalwifi
import java.io.BufferedInputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min
/** Limites apenas para HTTP local; não atua sobre todo o tráfego Wi-Fi. */
class LocalServer(private val onVisit: (String) -> Unit) {
    private val running = AtomicBoolean(false)
    private val executor = Executors.newCachedThreadPool()
    private var listener: ServerSocket? = null
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
                    while (running.get()) {
                        val client = ss.accept()
                        executor.execute { serve(client) }
                    }
                }
            } catch (_: SocketException) {
            } catch (_: Exception) {
                running.set(false)
            } finally { listener = null }
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
                val permitted = path == "/" || path == "/index.html" || path == "/health"
                val code = if (expired) 403 else if (permitted) 200 else 404
                if (code == 200 && path != "/health") onVisit(client.inetAddress.hostAddress ?: "?")
                val body = when {
                    expired -> "<h1>Sessão encerrada</h1>"
                    !permitted -> "<h1>Página não disponível</h1>"
                    path == "/health" -> "OK"
                    else -> "<!doctype html><html lang='pt-br'><meta name='viewport' content='width=device-width,initial-scale=1'><meta charset='utf-8'><title>Portal Wi-Fi</title><style>body{font:18px system-ui;background:#111827;color:white;padding:32px}main{max-width:540px;margin:auto;padding:24px;border-radius:16px;background:#253348}</style><main><h1>Bem-vindo!</h1><p>Seu acesso ao portal local está ativo.</p><small>Esta rede não oferece internet.</small></main></html>"
                }
                val bytes = body.toByteArray(Charsets.UTF_8)
                val header = "HTTP/1.1 $code OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\nCache-Control: no-store\r\n\r\n"
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
