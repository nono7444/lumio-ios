package com.nuvio.app.lumio

import com.nuvio.app.core.network.createApiHttpClient
import com.nuvio.app.core.network.readBoundedResponseBody
import com.nuvio.app.features.servers.ServerRepository
import com.nuvio.app.features.servers.ServerSession
import io.ktor.client.request.header
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import io.ktor.http.contentLength
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** Statuts du serveur Lumio (façon Seerr). */
object LumioStatus {
    const val UNKNOWN = 1
    const val PENDING = 2
    const val PROCESSING = 3
    const val PARTIALLY_AVAILABLE = 4
    const val AVAILABLE = 5
}

data class LumioSeason(val number: Int, val status: Int)
data class LumioRequestInfo(val id: String, val status: Int, val progress: Int?)
data class LumioMedia(val status: Int, val seasons: List<LumioSeason>, val requests: List<LumioRequestInfo>)

class LumioException(message: String) : Exception(message)

/**
 * Lumio iOS : demandes de films et séries au serveur Lumio, avec le compte Jellyfin connecté
 * (même fonctionnement que l'appli Lumio et Lumio TV). Aucune clé dans l'appli : le jeton Jellyfin suffit.
 */
object LumioClient {
    private val http by lazy { createApiHttpClient() }
    private val json = Json { ignoreUnknownKeys = true }
    private var resolvedBase: String? = null

    private fun jellyfin(): ServerSession? {
        val conn = ServerRepository.enabledConnections().firstOrNull { it.providerId == "jellyfin" } ?: return null
        return ServerRepository.session(conn.id)
    }

    /** Adresses possibles du serveur Lumio, devinées depuis celle de Jellyfin (jellyfin.x.lol → lumio.x.lol). */
    private fun candidates(jellyfinAddress: String): List<String> {
        val out = mutableListOf<String>()
        runCatching {
            val url = Url(jellyfinAddress.trim())
            val host = url.host
            if (Regex("""\d+\.\d+\.\d+\.\d+""").matches(host) || host == "localhost") {
                out += "http://$host:3456"
            } else {
                val parts = host.split('.')
                if (parts.size >= 3) out += "https://lumio." + parts.drop(1).joinToString(".")
                out += "https://lumio.$host"
                out += "${url.protocol.name}://$host:3456"
            }
        }
        out += "https://lumio.wawanono.lol"
        return out.distinct()
    }

    private suspend fun send(method: HttpMethod, url: String, token: String?, body: String? = null): Pair<Int, String> =
        http.prepareRequest(url) {
            this.method = method
            header("Accept", "application/json")
            token?.let { header("X-Jellyfin-Token", it) }
            if (body != null) {
                header("Content-Type", "application/json")
                setBody(body)
            }
        }.execute { r -> r.status.value to readBoundedResponseBody(r.bodyAsChannel(), r.contentLength()) }

    private suspend fun base(session: ServerSession): String {
        resolvedBase?.let { return it }
        for (c in candidates(session.connection.address)) {
            val ok = try {
                val (code, text) = send(HttpMethod.Get, "$c/health", null)
                code in 200..299 && json.parseToJsonElement(text).jsonObject["features"]?.jsonArray
                    ?.any { it.jsonPrimitive.content == "requests" } == true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            if (ok) {
                resolvedBase = c
                return c
            }
        }
        throw LumioException("Serveur Lumio introuvable")
    }

    private suspend fun call(method: HttpMethod, path: String, body: String? = null): JsonObject {
        val session = jellyfin() ?: throw LumioException("Connecte d'abord ton serveur Jellyfin (Profil → Serveurs).")
        val (code, text) = send(method, base(session) + path, session.token, body)
        val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
        if (code !in 200..299) {
            throw LumioException(obj?.get("error")?.jsonPrimitive?.content ?: "Erreur $code")
        }
        return obj ?: JsonObject(emptyMap())
    }

    /** Statut d'un titre sur le serveur (disponible, demandé, en cours…). */
    suspend fun media(type: String, tmdbId: Int?, imdbId: String?): LumioMedia {
        val q = buildString {
            append("/api/media?type=").append(type)
            tmdbId?.let { append("&tmdbId=").append(it) }
            imdbId?.let { append("&imdbId=").append(it) }
        }
        val o = call(HttpMethod.Get, q)
        fun JsonObject.int(k: String) = this[k]?.jsonPrimitive?.intOrNull
        return LumioMedia(
            status = o.int("status") ?: LumioStatus.UNKNOWN,
            seasons = (o["seasons"] as? JsonArray).orEmpty().map { it.jsonObject }
                .map { LumioSeason(it.int("seasonNumber") ?: 0, it.int("status") ?: LumioStatus.UNKNOWN) },
            requests = (o["requests"] as? JsonArray).orEmpty().map { it.jsonObject }.map {
                val dl = it["download"] as? JsonObject
                LumioRequestInfo(
                    id = it["id"]?.jsonPrimitive?.content.orEmpty(),
                    status = it.int("status") ?: LumioStatus.PENDING,
                    progress = dl?.get("progress")?.jsonPrimitive?.intOrNull,
                )
            },
        )
    }

    /** Envoie une demande (toutes les saisons manquantes pour une série). Renvoie le message du serveur. */
    suspend fun request(type: String, tmdbId: Int?, imdbId: String?, title: String, year: String?, poster: String?, seasons: List<Int>): Int {
        val body = buildJsonObject {
            put("type", type)
            tmdbId?.let { put("tmdbId", it) }
            imdbId?.let { put("imdbId", it) }
            put("title", title)
            year?.let { put("year", it) }
            poster?.takeIf { it.startsWith("https://") }?.let { put("poster", it) }
            if (seasons.isNotEmpty()) putJsonArray("seasons") { seasons.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }
        }
        return call(HttpMethod.Post, "/api/requests", body.toString())["status"]?.jsonPrimitive?.intOrNull ?: LumioStatus.PENDING
    }
}
