package app.orchard.tv.network

import kotlinx.coroutines.CancellationException
import java.net.HttpURLConnection
import java.net.URL

/** Small HTTP boundary for optional personal-build downloads. Revocation closes in-flight reads. */
internal class RevocableHttp {
    private val lock = Any()
    private var enabled = false
    private var generation = 0L
    private val active = mutableSetOf<HttpURLConnection>()

    // Replaced only by focused tests; production always opens a real HTTPS connection.
    internal var openConnection: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }

    fun setEnabled(on: Boolean) {
        val closing = synchronized(lock) {
            if (enabled == on) return
            enabled = on
            generation++
            if (on) emptyList() else active.toList().also { active.clear() }
        }
        closing.forEach { runCatching { it.disconnect() } }
    }

    fun read(address: String, connectTimeout: Int, readTimeout: Int): ByteArray {
        val url = URL(address)
        require(url.protocol == "https") { "Personal downloads require HTTPS" }
        val (connection, ticket) = synchronized(lock) {
            if (!enabled) throw CancellationException("Download disabled")
            openConnection(url).also { active.add(it) } to generation
        }
        try {
            connection.connectTimeout = connectTimeout
            connection.readTimeout = readTimeout
            // Fail closed on redirects; do not let a client follow a redirect to cleartext.
            connection.instanceFollowRedirects = false
            synchronized(lock) {
                if (!enabled || generation != ticket) throw CancellationException("Download revoked")
            }
            val status = connection.responseCode
            if (status !in 200..299) error("HTTP $status")
            val bytes = connection.inputStream.use { it.readBytes() }
            synchronized(lock) {
                if (!enabled || generation != ticket) throw CancellationException("Download revoked")
            }
            return bytes
        } finally {
            synchronized(lock) { active.remove(connection) }
            connection.disconnect()
        }
    }
}
