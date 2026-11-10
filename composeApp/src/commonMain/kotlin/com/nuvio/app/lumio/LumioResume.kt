package com.nuvio.app.lumio

import androidx.compose.runtime.Composable
import com.nuvio.app.core.ui.ScreenActivityEffect
import com.nuvio.app.features.servers.ServerItemRef
import com.nuvio.app.features.servers.ServerMediaKind
import com.nuvio.app.features.servers.ServerRepository
import com.nuvio.app.features.servers.ServerResumeProgress
import com.nuvio.app.features.watchprogress.WatchProgressClock
import com.nuvio.app.features.watchprogress.WatchProgressEntry
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Lumio : la rangée « Continuer à regarder » de l'appli suit Jellyfin, donc ce qui a été commencé
 * sur la TV ou un autre appareil apparaît ici aussi (une seule rangée au lieu de deux).
 */
internal object LumioResume {
    private const val LIMIT = 60
    private const val GRACE_MS = 3 * 60_000L

    suspend fun sync() {
        if (!LumioMode.ENABLED) return
        ServerRepository.enabledConnections().forEach { connection ->
            val items = try {
                ServerRepository.call(connection.id) { provider, session -> provider.resumeProgress(session, LIMIT) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@forEach
            }
            val label = ServerRepository.sourceLabel(connection)
            WatchProgressRepository.applyExternalProgress(
                items
                    .filter { !it.state.played && it.state.positionMs > 0L && it.state.durationMs > 0L && it.state.lastPlayedEpochMs != null }
                    .map { it.toEntry(label, connection.id) },
            )

            // Titre terminé ou remis à zéro sur un autre appareil : il sort aussi de la rangée ici.
            // On ne touche qu'aux lectures franchement entamées (Jellyfin ne liste pas les débuts ni les fins),
            // et pas à celles qui viennent d'être mises à jour sur cet appareil.
            if (items.size >= LIMIT) return@forEach
            val onServer = items.mapTo(mutableSetOf()) { it.state.videoId }
            val now = WatchProgressClock.nowEpochMs()
            WatchProgressRepository.uiState.value.entries
                .filter { entry ->
                    ServerItemRef.parse(entry.videoId)?.connectionId == connection.id &&
                        entry.videoId !in onServer &&
                        !entry.isCompleted &&
                        entry.durationMs > 10 * 60_000L &&
                        entry.lastPositionMs.toDouble() / entry.durationMs.toDouble() in 0.08..0.85 &&
                        now - entry.lastUpdatedEpochMs > GRACE_MS
                }
                .forEach { WatchProgressRepository.clearProgress(listOf(it.videoId), it.parentMetaId) }
        }
    }

    private fun ServerResumeProgress.toEntry(label: String, connectionId: String): WatchProgressEntry {
        val isSeries = preview.type == ServerMediaKind.SERIES.contentType
        return WatchProgressEntry(
            contentType = preview.type,
            parentMetaId = preview.id,
            parentMetaType = preview.type,
            videoId = state.videoId,
            title = preview.name,
            logo = preview.logo,
            poster = preview.poster,
            background = preview.banner,
            seasonNumber = state.season.takeIf { isSeries },
            episodeNumber = state.episode.takeIf { isSeries },
            episodeTitle = state.title.takeIf { isSeries },
            lastPositionMs = state.positionMs,
            durationMs = state.durationMs,
            lastUpdatedEpochMs = state.lastPlayedEpochMs ?: 0L,
            providerName = label,
            providerAddonId = "server:$connectionId",
        )
    }
}

/** À poser sur l'accueil : synchronise à l'affichage, puis toutes les minutes tant que l'accueil est visible. */
@Composable
internal fun LumioResumeSyncEffect() {
    ScreenActivityEffect { active ->
        if (!active) return@ScreenActivityEffect
        while (true) {
            LumioResume.sync()
            delay(60_000)
        }
    }
}
