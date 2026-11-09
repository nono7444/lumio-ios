package com.nuvio.app.lumio

import com.nuvio.app.features.addons.httpGetText
import com.nuvio.app.features.catalog.CatalogTarget
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.tmdb.TmdbSettingsRepository
import com.nuvio.app.features.tmdb.buildTmdbUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Lumio iOS : catalogue des sorties numériques (VOD, plateformes), avec les dates exactes de TMDB.
 * - films : sortie de type 4 (« numérique ») en France, sinon aux États-Unis ;
 * - séries : nouvelles saisons sur les plateformes de streaming en France.
 * Même principe que le calendrier de l'appli Lumio.
 */
object LumioReleases {
    data class Result(
        val moviesOut: List<MetaPreview>,
        val moviesSoon: List<MetaPreview>,
        val seriesOut: List<MetaPreview>,
        val seriesSoon: List<MetaPreview>,
    ) {
        /** Rangées à afficher (les vides sont retirées). */
        fun sections(): List<HomeCatalogSection> = listOf(
            section("out-movies", "Films · sortis en numérique", moviesOut),
            section("out-series", "Séries · nouvelles saisons", seriesOut),
            section("soon-movies", "Films · bientôt en numérique", moviesSoon),
            section("soon-series", "Séries · bientôt", seriesSoon),
        ).filter { it.items.isNotEmpty() }

        private fun section(key: String, title: String, items: List<MetaPreview>) = HomeCatalogSection(
            key = "lumio-releases-$key",
            title = title,
            subtitle = "Sorties numériques",
            addonName = "TMDB",
            target = CatalogTarget.Addon(manifestUrl = "lumio:releases", contentType = "movie", catalogId = key),
            items = items,
        )
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()
    private var cache: Pair<Long, Result>? = null

    // Talk-shows, actualités, feuilletons quotidiens : pas des « sorties »
    private const val EXCLUDED_TV_GENRES = "10767|10763|10766"
    private const val DAY_MS = 86_400_000L

    suspend fun load(force: Boolean = false): Result = lock.withLock {
        val now = nowMs()
        cache?.let { (at, value) -> if (!force && now - at < 3_600_000L) return@withLock value }
        LumioTmdb.ensureKey()
        val key = TmdbSettingsRepository.effectiveApiKey()
        if (key.isBlank()) throw LumioException("Sorties indisponibles : serveur Lumio injoignable.")
        val today = isoDay(now)
        val from = isoDay(now - 45 * DAY_MS)
        val to = isoDay(now + 30 * DAY_MS)
        val (movies, series) = coroutineScope {
            val m = async { digitalMovies(key, from, to) }
            val s = async { newSeasons(key, from, to) }
            m.await() to s.await()
        }
        if (movies.isEmpty() && series.isEmpty()) throw LumioException("Aucune sortie trouvée pour le moment.")
        val result = Result(
            moviesOut = movies.filter { it.first <= today }.sortedByDescending { it.first }.map { it.second },
            moviesSoon = movies.filter { it.first > today }.sortedBy { it.first }.map { it.second },
            seriesOut = series.filter { it.first <= today }.sortedByDescending { it.first }.map { it.second },
            seriesSoon = series.filter { it.first > today }.sortedBy { it.first }.map { it.second },
        )
        cache = now to result
        result
    }

    private suspend fun get(key: String, endpoint: String, query: Map<String, String>): String =
        httpGetText(buildTmdbUrl(endpoint = endpoint, apiKey = key, query = query))

    private suspend fun <T, R> pool(items: List<T>, n: Int, fn: suspend (T) -> R?): List<R> = coroutineScope {
        val gate = Semaphore(n)
        items.map { item ->
            async {
                gate.withPermit {
                    try {
                        fn(item)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                }
            }
        }.awaitAll().filterNotNull()
    }

    private suspend fun discoverIds(key: String, endpoint: String, query: Map<String, String>, pages: Int, max: Int): List<Int> {
        val ids = pool((1..pages).toList(), pages) { page ->
            json.decodeFromString<Page>(get(key, endpoint, query + ("page" to page.toString()))).results.map { it.id }
        }.flatten()
        return ids.filter { it > 0 }.distinct().take(max)
    }

    /** Films sortis (ou qui sortent) en numérique entre [from] et [to] : (date ISO, affiche). */
    private suspend fun digitalMovies(key: String, from: String, to: String): List<Pair<String, MetaPreview>> {
        val ids = discoverIds(
            key, "discover/movie",
            mapOf(
                "region" to "FR",
                "with_release_type" to "4",
                "release_date.gte" to from,
                "release_date.lte" to to,
                "sort_by" to "popularity.desc",
                "include_adult" to "false",
                "language" to "fr-FR",
            ),
            pages = 3, max = 60,
        )
        return pool(ids, 6) { id ->
            val m = json.decodeFromString<MovieDetail>(
                get(key, "movie/$id", mapOf("language" to "fr-FR", "append_to_response" to "release_dates")),
            )
            fun pick(country: String) = m.releaseDates?.results
                ?.firstOrNull { it.country == country }
                ?.dates?.filter { it.type == 4 }
                ?.mapNotNull { it.date?.take(10)?.takeIf { d -> d.length == 10 } }
                ?.minOrNull()
            val date = pick("FR") ?: pick("US") ?: return@pool null
            if (date < from || date > to || m.title.isNullOrBlank()) return@pool null
            date to MetaPreview(
                id = "tmdb:${m.id}",
                type = "movie",
                name = m.title,
                poster = m.posterPath?.let { "https://image.tmdb.org/t/p/w342$it" },
                banner = m.backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" },
                description = m.overview?.takeIf { it.isNotBlank() },
                releaseInfo = date,
                rawReleaseDate = date,
            )
        }
    }

    /** Nouvelles saisons sur les plateformes en France entre [from] et [to] (une affiche par série). */
    private suspend fun newSeasons(key: String, from: String, to: String): List<Pair<String, MetaPreview>> {
        val ids = discoverIds(
            key, "discover/tv",
            mapOf(
                "watch_region" to "FR",
                "with_watch_monetization_types" to "flatrate",
                "air_date.gte" to from,
                "air_date.lte" to to,
                "without_genres" to EXCLUDED_TV_GENRES,
                "sort_by" to "popularity.desc",
                "language" to "fr-FR",
            ),
            pages = 2, max = 40,
        )
        return pool(ids, 6) { id ->
            val t = json.decodeFromString<TvDetail>(get(key, "tv/$id", mapOf("language" to "fr-FR")))
            val season = t.seasons
                .filter { it.number > 0 }
                .mapNotNull { s -> s.airDate?.take(10)?.takeIf { it.length == 10 && it in from..to }?.let { it to s } }
                .maxByOrNull { it.first } ?: return@pool null
            if (t.name.isNullOrBlank()) return@pool null
            val (date, s) = season
            date to MetaPreview(
                id = "tmdb:${t.id}",
                type = "series",
                name = t.name,
                poster = (s.posterPath ?: t.posterPath)?.let { "https://image.tmdb.org/t/p/w342$it" },
                banner = t.backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" },
                description = listOfNotNull(
                    if (s.number == 1) "Nouvelle série" else "Saison ${s.number}",
                    t.overview?.takeIf { it.isNotBlank() },
                ).joinToString(" · "),
                releaseInfo = date,
                rawReleaseDate = date,
            )
        }
    }

    // ——— Dates sans bibliothèque externe ———
    internal fun nowMs(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()

    /** Jour (UTC) au format AAAA-MM-JJ. */
    internal fun isoDay(epochMs: Long): String {
        // Algorithme « civil from days » (Howard Hinnant)
        val z = floorDiv(epochMs, DAY_MS) + 719_468
        val era = floorDiv(z, 146_097)
        val doe = z - era * 146_097
        val yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = doy - (153 * mp + 2) / 5 + 1
        val m = if (mp < 10) mp + 3 else mp - 9
        val y = yoe + era * 400 + if (m <= 2) 1 else 0
        return "${y.toString().padStart(4, '0')}-${m.toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}"
    }

    private fun floorDiv(a: Long, b: Long): Long {
        val q = a / b
        return if ((a % b != 0L) && ((a < 0) != (b < 0))) q - 1 else q
    }

    @Serializable
    private data class Page(val results: List<Id> = emptyList())

    @Serializable
    private data class Id(val id: Int = 0)

    @Serializable
    private data class MovieDetail(
        val id: Int = 0,
        val title: String? = null,
        val overview: String? = null,
        @SerialName("poster_path") val posterPath: String? = null,
        @SerialName("backdrop_path") val backdropPath: String? = null,
        @SerialName("release_dates") val releaseDates: ReleaseDates? = null,
    )

    @Serializable
    private data class ReleaseDates(val results: List<CountryDates> = emptyList())

    @Serializable
    private data class CountryDates(
        @SerialName("iso_3166_1") val country: String = "",
        @SerialName("release_dates") val dates: List<ReleaseDate> = emptyList(),
    )

    @Serializable
    private data class ReleaseDate(
        val type: Int = 0,
        @SerialName("release_date") val date: String? = null,
    )

    @Serializable
    private data class TvDetail(
        val id: Int = 0,
        val name: String? = null,
        val overview: String? = null,
        @SerialName("poster_path") val posterPath: String? = null,
        @SerialName("backdrop_path") val backdropPath: String? = null,
        val seasons: List<Season> = emptyList(),
    )

    @Serializable
    private data class Season(
        @SerialName("season_number") val number: Int = 0,
        @SerialName("air_date") val airDate: String? = null,
        @SerialName("poster_path") val posterPath: String? = null,
    )
}
