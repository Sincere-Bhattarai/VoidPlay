package com.theveloper.voidplay.data.netease

import android.net.Uri
import com.theveloper.voidplay.data.stream.CloudStreamSecurity
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.engine.*
import io.ktor.server.cio.*
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.header
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local HTTP proxy server for streaming Netease Cloud Music audio.
 *
 * Resolves `netease://{songId}` URIs by fetching temporary streaming URLs
 * from the Netease API and proxying the audio data to ExoPlayer.
 *
 * Follows the same architectural pattern as [TelegramStreamProxy] using Ktor CIO.
 */
@Singleton
class NeteaseStreamProxy @Inject constructor(
    private val repository: NeteaseRepository,
    private val okHttpClient: OkHttpClient
) {
    private companion object {
        val ALLOWED_REMOTE_HOST_SUFFIXES = setOf(
            "music.126.net",
            "music.163.com",
            "126.net",
            "163.com"
        )
    }

    private var server: ApplicationEngine? = null
    private var actualPort: Int = 0
    private val proxyScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var startJob: Job? = null

    // Cache of resolved streaming URLs (they expire, so we track timestamp)
    private val urlCache = ConcurrentHashMap<Long, CachedUrl>()

    private data class CachedUrl(val url: String, val timestamp: Long) {
        // Netease URLs typically expire in ~20 minutes, re-fetch after 15
        fun isExpired(): Boolean = System.currentTimeMillis() - timestamp > 15 * 60 * 1000
    }

    fun isReady(): Boolean = actualPort > 0

    /**
     * Suspends until the proxy server is ready (port bound).
     * @param timeoutMs Maximum time to wait
     * @return true if ready, false if timed out
     */
    suspend fun awaitReady(timeoutMs: Long = 10_000L): Boolean {
        if (isReady()) return true

        val stepMs = 50L
        var elapsed = 0L
        while (elapsed < timeoutMs) {
            if (isReady()) return true
            delay(stepMs)
            elapsed += stepMs
        }
        return false
    }

    fun getProxyUrl(songId: Long): String {
        if (actualPort == 0) {
            Timber.w("NeteaseStreamProxy: getProxyUrl called but actualPort is 0")
            return ""
        }
        if (!CloudStreamSecurity.validateNeteaseSongId(songId)) {
            Timber.w("NeteaseStreamProxy: getProxyUrl rejected invalid songId: $songId")
            return ""
        }
        return "http://127.0.0.1:$actualPort/netease/$songId"
    }

    /**
     * Parse a `netease://` URI and return the proxy URL.
     * Returns null if the URI is not a valid Netease URI.
     */
    fun resolveNeteaseUri(uriString: String): String? {
        val uri = Uri.parse(uriString)
        if (uri.scheme != "netease") return null
        val songId = uri.host?.toLongOrNull() ?: return null
        if (!CloudStreamSecurity.validateNeteaseSongId(songId)) return null
        return getProxyUrl(songId)
    }

    fun start() {
        startJob?.cancel()
        startJob = proxyScope.launch {
            try {
                val freePort = ServerSocket(0).use { it.localPort }
                val createdServer = createServer(freePort)
                createdServer.start(wait = false)
                server = createdServer
                actualPort = freePort
                Timber.d("NeteaseStreamProxy started on port $actualPort")
            } catch (e: CancellationException) {
                Timber.d("NeteaseStreamProxy start cancelled")
            } catch (e: Exception) {
                Timber.e(e, "Failed to start NeteaseStreamProxy")
            }
        }
    }

    fun stop() {
        startJob?.cancel()
        startJob = null
        proxyScope.coroutineContext.cancelChildren()
        server?.stop(1000, 2000)
        server = null
        actualPort = 0
        urlCache.clear()
        Timber.d("NeteaseStreamProxy stopped")
    }

    private fun createServer(port: Int): ApplicationEngine {
        return embeddedServer(CIO, host = "127.0.0.1", port = port) {
            routing {
                get("/netease/{songId}") {
                    val songId = call.parameters["songId"]?.toLongOrNull()
                    if (songId == null || !CloudStreamSecurity.validateNeteaseSongId(songId)) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid Song ID")
                        return@get
                    }

                    try {
                        val rangeValidation = CloudStreamSecurity.validateRangeHeader(call.request.headers["Range"])
                        if (!rangeValidation.isValid) {
                            call.respond(HttpStatusCode(416, "Range Not Satisfiable"), "Invalid range header")
                            return@get
                        }

                        val streamUrl = getOrFetchStreamUrl(songId)
                        if (streamUrl == null) {
                            call.respond(HttpStatusCode.NotFound, "No stream URL available")
                            return@get
                        }
                        if (!CloudStreamSecurity.isSafeRemoteStreamUrl(
                                url = streamUrl,
                                allowedHostSuffixes = ALLOWED_REMOTE_HOST_SUFFIXES,
                                allowHttpForAllowedHosts = true
                            )
                        ) {
                            call.respond(HttpStatusCode.BadGateway, "Rejected upstream stream URL")
                            return@get
                        }

                        // Proxy the audio stream
                        val requestBuilder = Request.Builder().url(streamUrl)
                        rangeValidation.normalizedHeader?.let {
                            requestBuilder.header("Range", it)
                        }

                        val response = withContext(Dispatchers.IO) {
                            okHttpClient.newCall(requestBuilder.build()).execute()
                        }

                        response.use { upstream ->
                            if (upstream.code != 200 && upstream.code != 206) {
                                call.respond(
                                    CloudStreamSecurity.mapUpstreamStatusToProxyStatus(upstream.code),
                                    "Upstream stream request failed"
                                )
                                return@get
                            }

                            val body = upstream.body

                            val contentTypeHeader = upstream.header("Content-Type")
                            if (!CloudStreamSecurity.isSupportedAudioContentType(contentTypeHeader)) {
                                call.respond(HttpStatusCode.BadGateway, "Unsupported stream content type")
                                return@get
                            }

                            val contentLength = upstream.header("Content-Length")
                            if (!CloudStreamSecurity.isAcceptableContentLength(contentLength)) {
                                call.respond(HttpStatusCode(413, "Payload Too Large"), "Stream content too large")
                                return@get
                            }

                            val contentRange = upstream.header("Content-Range")
                            val acceptRanges = upstream.header("Accept-Ranges")
                            val responseContentType = contentTypeHeader
                                ?.substringBefore(';')
                                ?.trim()
                                ?.let { raw -> runCatching { ContentType.parse(raw) }.getOrNull() }
                                ?: ContentType.Audio.Any

                            if (upstream.code == 206) {
                                call.response.status(HttpStatusCode.PartialContent)
                            } else {
                                call.response.status(HttpStatusCode.OK)
                            }
                            call.response.header("Accept-Ranges", acceptRanges ?: "bytes")
                            contentLength?.let { call.response.header("Content-Length", it) }
                            contentRange?.let { call.response.header("Content-Range", it) }

                            call.respondBytesWriter(contentType = responseContentType) {
                                withContext(Dispatchers.IO) {
                                    body.byteStream().use { input ->
                                        val buffer = ByteArray(64 * 1024)
                                        var bytesRead: Int
                                        while (input.read(buffer).also { bytesRead = it } != -1) {
                                            writeFully(buffer, 0, bytesRead)
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        val msg = e.toString()
                        if (msg.contains("ChannelWriteException") ||
                            msg.contains("ClosedChannelException") ||
                            msg.contains("Broken pipe") ||
                            msg.contains("JobCancellationException")) {
                            // Client disconnected, normal behavior
                        } else {
                            Timber.e(e, "Error streaming Netease song $songId")
                        }
                    }
                }
            }
        }
    }

    private suspend fun getOrFetchStreamUrl(songId: Long): String? {
        // Check cache first
        urlCache[songId]?.let { cached ->
            if (!cached.isExpired()) return cached.url
        }

        // Fetch fresh URL
        val result = repository.getSongUrl(songId)
        return result.getOrNull()?.also { url ->
            urlCache[songId] = CachedUrl(url, System.currentTimeMillis())
        }
    }
}
