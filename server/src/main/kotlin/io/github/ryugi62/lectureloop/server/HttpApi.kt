package io.github.ryugi62.lectureloop.server

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.github.ryugi62.lectureloop.adapters.gemini.CardJson
import io.github.ryugi62.lectureloop.domain.AudioRef
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.InetSocketAddress
import java.time.DateTimeException
import java.time.ZoneId
import java.util.concurrent.Executors

/**
 * `POST /v1/cards` — body: the recording; headers: `X-App-User-Id` (RevenueCat app user ID), `X-Audio-Seconds`,
 * `X-Time-Zone` (optional). 200 card JSON · 402 weekly limit · 422 card rejected twice · 429 AI busy · 502 AI unreachable.
 */
class HttpApi(private val service: CardService, port: Int, private val tmpDir: File, private val maxBytes: Long = 200L * 1024 * 1024) {
    private val server = HttpServer.create(InetSocketAddress(port), 0)
    val port: Int get() = server.address.port

    init {
        server.executor = Executors.newFixedThreadPool(4)
        server.createContext("/healthz") { ex -> ex.reply(200, """{"ok":true}""") }
        server.createContext("/v1/cards") { ex -> cards(ex) }
    }

    fun start() = server.start()
    fun stop() = server.stop(0)

    private fun cards(ex: HttpExchange) {
        if (ex.requestMethod != "POST") return ex.reply(405, """{"error":"method"}""")
        val user = ex.requestHeaders.getFirst("X-App-User-Id")?.takeIf { it.isNotBlank() && it.length <= 200 }
            ?: return ex.reply(400, """{"error":"X-App-User-Id required"}""")
        val seconds = ex.requestHeaders.getFirst("X-Audio-Seconds")?.toIntOrNull()?.takeIf { it in 1..6 * 3600 }
            ?: return ex.reply(400, """{"error":"X-Audio-Seconds required"}""")
        val zone = try {
            ZoneId.of(ex.requestHeaders.getFirst("X-Time-Zone") ?: "UTC")
        } catch (e: DateTimeException) {
            ZoneId.of("UTC")
        }
        val mime = ex.requestHeaders.getFirst("Content-Type")?.substringBefore(';')?.takeIf { it.startsWith("audio/") } ?: "audio/mp4"
        tmpDir.mkdirs()
        val file = File.createTempFile("rec-", ".audio", tmpDir)
        try {
            var total = 0L
            ex.requestBody.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > maxBytes) return ex.reply(413, """{"error":"too large"}""")
                        out.write(buf, 0, n)
                    }
                }
            }
            val served = runBlocking { service.serve(user, zone, AudioRef(file.absolutePath, mime, seconds)) }
            when (served) {
                is Served.Card -> ex.reply(200, CardJson.encode(served.card))
                is Served.Limit -> ex.reply(
                    402,
                    """{"error":"weekly_limit","used":${served.access.used},"limit":${served.access.limit},"resetsAt":"${served.access.resetsAt}"}""",
                )
                is Served.Rejected -> ex.reply(422, """{"error":"card_rejected"}""")
                Served.Busy -> ex.reply(429, """{"error":"busy"}""")
                is Served.Unreachable -> ex.reply(502, """{"error":"ai_unreachable"}""")
            }
        } finally {
            file.delete()
        }
    }

    private fun HttpExchange.reply(code: Int, json: String) {
        val bytes = json.toByteArray()
        responseHeaders.add("Content-Type", "application/json")
        sendResponseHeaders(code, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }
}
