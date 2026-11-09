package com.nuvio.app.lumio

import com.nuvio.app.features.addons.httpGetText
import com.nuvio.app.features.catalog.CatalogTarget
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.servers.ServerRepository
import com.nuvio.app.features.tmdb.TmdbSettingsRepository
import com.nuvio.app.features.tmdb.buildTmdbUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Lumio iOS : recherche TMDB (films et séries mélangés, comme sur Lumio TV).
 * La clé TMDB vient du serveur Lumio, elle reste en mémoire.
 */
object LumioTmdb {
    private val json = Json { ignoreUnknownKeys = true }
    private val keyLock = Mutex()

    /** Récupère la clé TMDB du serveur Lumio si on ne l'a pas encore. */
    suspend fun ensureKey(): Boolean = keyLock.withLock {
        if (TmdbSettingsRepository.effectiveApiKey().isNotBlank()) return@withLock true
        val key = try {
            LumioClient.tmdbKey()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        if (key != null) TmdbSettingsRepository.lumioServerKey = key
        key != null
    }

    /** Au démarrage et à chaque changement de serveur : on essaie d'avoir la clé. */
    suspend fun keepKeyFresh() {
        ServerRepository.ensureLoaded()
        ServerRepository.uiState.collect { ensureKey() }
    }

    suspend fun searchSection(query: String): HomeCatalogSection {
        ensureKey()
        val key = TmdbSettingsRepository.effectiveApiKey()
        require(key.isNotBlank()) { "Recherche indisponible : serveur Lumio injoignable." }
        val url = buildTmdbUrl(
            endpoint = "search/multi",
            apiKey = key,
            query = mapOf("query" to query, "language" to "fr-FR", "include_adult" to "false", "region" to "FR"),
        )
        val results = json.decodeFromString<Page>(httpGetText(url)).results
        val items = results
            .filter { (it.mediaType == "movie" || it.mediaType == "tv") && it.id > 0 }
            .sortedByDescending { (if (it.posterPath != null) 1_000_000.0 else 0.0) + (it.popularity ?: 0.0) }
            .map { r ->
                val date = r.releaseDate ?: r.firstAirDate
                MetaPreview(
                    id = "tmdb:${r.id}",
                    type = if (r.mediaType == "tv") "series" else "movie",
                    name = r.title ?: r.name ?: "",
                    poster = r.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" },
                    banner = r.backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" },
                    description = r.overview?.takeIf { it.isNotBlank() },
                    releaseInfo = date?.take(4)?.takeIf { it.length == 4 },
                    rawReleaseDate = date,
                    popularity = r.popularity,
                    voteCount = r.voteCount,
                )
            }
            .filter { it.name.isNotBlank() }
        require(items.isNotEmpty()) { "Aucun résultat sur TMDB" }
        return HomeCatalogSection(
            key = "lumio-tmdb-search",
            title = "Films et séries",
            subtitle = "Pas sur ton serveur ? Ouvre la fiche et fais « Demander »",
            addonName = "TMDB",
            target = CatalogTarget.Addon(manifestUrl = "lumio:tmdb", contentType = "movie", catalogId = "search"),
            items = items,
        )
    }

    @Serializable
    private data class Page(val results: List<Result> = emptyList())

    @Serializable
    private data class Result(
        val id: Int = 0,
        @SerialName("media_type") val mediaType: String? = null,
        val title: String? = null,
        val name: String? = null,
        val overview: String? = null,
        @SerialName("poster_path") val posterPath: String? = null,
        @SerialName("backdrop_path") val backdropPath: String? = null,
        @SerialName("release_date") val releaseDate: String? = null,
        @SerialName("first_air_date") val firstAirDate: String? = null,
        val popularity: Double? = null,
        @SerialName("vote_count") val voteCount: Int? = null,
    )
}
