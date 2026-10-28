package de.crunchymate.sync

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

data class HttpResponse(
    val statusCode: Int,
    val headers: Map<String, List<String>> = emptyMap(),
    val body: String = ""
) {
    fun getHeader(name: String): String? {
        val entry = headers.entries.find { it.key.equals(name, ignoreCase = true) }
        return entry?.value?.firstOrNull()
    }
}

interface HttpTransport {
    fun request(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: String? = null
    ): HttpResponse
}

class DefaultHttpTransport(
    private val connectTimeoutMs: Int = 15000,
    private val readTimeoutMs: Int = 30000
) : HttpTransport {
    override fun request(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: String?
    ): HttpResponse {
        val u = URL(url)
        val conn = u.openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = connectTimeoutMs
            conn.readTimeout = readTimeoutMs
            conn.requestMethod = method
            conn.instanceFollowRedirects = true

            for ((key, value) in headers) {
                conn.setRequestProperty(key, value)
            }

            if (body != null && (method == "POST" || method == "PUT" || method == "PATCH")) {
                conn.doOutput = true
                val bytes = body.toByteArray(StandardCharsets.UTF_8)
                conn.outputStream.use { out ->
                    out.write(bytes)
                    out.flush()
                }
            }

            val statusCode = conn.responseCode
            val headerFields = conn.headerFields ?: emptyMap()

            val stream = if (statusCode in 200..299) conn.inputStream else conn.errorStream

            val responseBody = stream?.use { s ->
                BufferedReader(InputStreamReader(s, StandardCharsets.UTF_8)).readText()
            } ?: ""

            return HttpResponse(statusCode, headerFields, responseBody)
        } finally {
            conn.disconnect()
        }
    }
}
