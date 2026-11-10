package com.nuvio.app.lumio

import androidx.compose.runtime.Composable
import com.nuvio.app.core.ui.ScreenActivityEffect
import com.nuvio.app.features.library.LibraryItem
import com.nuvio.app.features.library.LibraryRepository
import com.nuvio.app.features.library.LibrarySourceMode
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Lumio : les favoris de l'appli sont la liste « À voir » du serveur Lumio, la même que sur Lumio TV.
 * - un favori ajouté ou retiré ici est envoyé tout de suite au serveur ;
 * - à l'accueil (puis toutes les minutes), la liste du serveur est recopiée ici : ce qui a été ajouté
 *   sur la TV apparaît, ce qui y a été retiré disparaît.
 * Seuls les films et séries reconnus par TMDB sont partagés.
 */
internal object LumioFavorites {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val tmdbByImdb = mutableMapOf<String, Int?>() // protégé par mutex

    /** Titres que la synchro est en train de recopier : à ne pas renvoyer au serveur. */
    @Volatile
    private var applying: Set<String> = emptySet()

    private fun LibraryItem.lumioType(): String? = when (type.lowercase()) {
        "movie" -> "movie"
        "series", "tv" -> "tv"
        else -> null
    }

    private fun LibraryItem.directTmdb(): Int? =
        id.takeIf { it.startsWith("tmdb:") }?.removePrefix("tmdb:")?.substringBefore(':')?.toIntOrNull() ?: tmdbId

    private fun LibraryItem.imdb(): String? =
        imdbId?.takeIf { it.startsWith("tt") } ?: id.takeIf { it.startsWith("tt") }?.substringBefore(':')

    private suspend fun LibraryItem.toWatchItem(): LumioWatchItem? {
        val type = lumioType() ?: return null
        val tmdb = directTmdb() ?: imdb()?.let { imdb ->
            mutex.withLock { if (tmdbByImdb.containsKey(imdb)) return@let tmdbByImdb[imdb] }
            val found = try {
                LumioClient.tmdbIdFor(type, imdb)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                return null // serveur injoignable : on réessaiera
            }
            mutex.withLock { tmdbByImdb[imdb] = found }
            found
        } ?: return null
        return LumioWatchItem(
            type = type,
            tmdbId = tmdb,
            title = name,
            year = releaseInfo?.take(4)?.takeIf { year -> year.length == 4 && year.all { it.isDigit() } },
            poster = poster,
            backdrop = banner,
        )
    }

    private fun LumioWatchItem.toLibraryItem() = LibraryItem(
        id = "tmdb:$tmdbId",
        type = if (type == "tv") "series" else "movie",
        name = title,
        poster = poster,
        banner = backdrop,
        releaseInfo = year,
        tmdbId = tmdbId,
        savedAtEpochMs = 0L,
    )

    /** Appelé par la bibliothèque quand un favori est ajouté ou retiré dans l'appli. */
    fun onLocalChange(item: LibraryItem, saved: Boolean) {
        if (!LumioMode.ENABLED) return
        val direct = item.lumioType()?.let { type -> item.directTmdb()?.let { "$type:$it" } }
        if (direct != null && direct in applying) return
        scope.launch {
            try {
                val watch = item.toWatchItem() ?: return@launch
                if (watch.key in applying) return@launch
                if (saved) LumioClient.watchlistAdd(watch) else LumioClient.watchlistRemove(watch.type, watch.tmdbId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Serveur injoignable : la prochaine synchro rattrapera un ajout
            }
        }
    }

    private val syncMutex = Mutex()

    suspend fun sync() {
        if (!LumioMode.ENABLED) return
        syncMutex.withLock {
            try {
                syncLocked()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun syncLocked() {
        val remote = LumioClient.watchlist()
        LibraryRepository.ensureLoaded()
        val state = LibraryRepository.uiState.value
        if (state.sourceMode != LibrarySourceMode.LOCAL || !state.isLoaded) return

        val local = mutableMapOf<String, Pair<LibraryItem, LumioWatchItem>>()
        for (item in state.items) {
            val watch = item.toWatchItem() ?: continue
            if (watch.key !in local) local[watch.key] = item to watch
        }
        val remoteKeys = remote.items.map { it.key }.toSet()

        // Sur le serveur mais pas ici : ajouté sur un autre appareil
        val toAdd = remote.items.filter { it.key !in local }
        // Ici mais pas sur le serveur : retiré ailleurs depuis, ou pas encore envoyé
        val toRemove = mutableListOf<LibraryItem>()
        val toPush = mutableListOf<LumioWatchItem>()
        val removed = remote.removed
        if (removed != null) {
            for ((key, pair) in local) {
                if (key in remoteKeys) continue
                val removedAt = removed[key]
                if (removedAt != null && removedAt > pair.first.savedAtEpochMs) toRemove += pair.first else toPush += pair.second
            }
        }

        applying = toAdd.map { it.key }.toSet() + toRemove.mapNotNull { local.entries.firstOrNull { e -> e.value.first === it }?.key }
        try {
            // Du plus ancien au plus récent, pour garder l'ordre du serveur (le plus récent en premier)
            toAdd.asReversed().forEach { LibraryRepository.save(it.toLibraryItem()) }
            toRemove.forEach { LibraryRepository.remove(it.id) }
        } finally {
            applying = emptySet()
        }
        toPush.asReversed().forEach { LumioClient.watchlistAdd(it) }
    }
}

/** À poser sur l'accueil : synchronise à l'affichage, puis toutes les minutes tant que l'accueil est visible. */
@Composable
internal fun LumioFavoritesSyncEffect() {
    ScreenActivityEffect { active ->
        if (!active) return@ScreenActivityEffect
        while (true) {
            LumioFavorites.sync()
            delay(60_000)
        }
    }
}
