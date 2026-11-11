package com.nuvio.app.lumio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nuvio.app.features.details.MetaDetails
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Blue = Color(0xFF3D8BFF)
private val Green = Color(0xFF30D158)
private val Orange = Color(0xFFFF9F0A)

private fun MetaDetails.lumioType(): String =
    if (type.equals("series", true) || type.equals("tv", true)) "tv" else "movie"

private fun MetaDetails.lumioTmdb(): Int? =
    id.takeIf { it.startsWith("tmdb:") }?.removePrefix("tmdb:")?.substringBefore(':')?.toIntOrNull()

private fun MetaDetails.lumioImdb(): String? =
    imdbId?.takeIf { it.startsWith("tt") } ?: id.takeIf { it.startsWith("tt") }?.substringBefore(':')

/**
 * Lumio : sous les boutons de la fiche, « Demander » quand le titre n'est pas sur le serveur,
 * sinon l'état de la demande (en attente, téléchargement en cours…). Rien si le titre est déjà disponible.
 */
@Composable
fun LumioRequestBar(meta: MetaDetails, modifier: Modifier = Modifier) {
    if (!LumioMode.ENABLED) return
    val scope = rememberCoroutineScope()
    var media by remember(meta.id) { mutableStateOf<LumioMedia?>(null) }
    var reload by remember(meta.id) { mutableIntStateOf(0) }
    var busy by remember(meta.id) { mutableStateOf(false) }
    var message by remember(meta.id) { mutableStateOf<String?>(null) }
    var selected by remember(meta.id) { mutableStateOf(emptySet<Int>()) }
    val type = meta.lumioType()

    LaunchedEffect(meta.id, reload) {
        media = runCatching { LumioClient.media(type, meta.lumioTmdb(), meta.lumioImdb()) }.getOrNull() ?: media
    }
    // Demande en attente (1) ou validée (2)
    val active = media?.requests?.firstOrNull { it.status == 1 || it.status == 2 }
    // Suivi en direct pendant un téléchargement
    LaunchedEffect(meta.id, active?.id) {
        if (active == null) return@LaunchedEffect
        while (true) {
            delay(6_000)
            reload++
        }
    }

    // Dès que le titre arrive dans Jellyfin : on vide le cache des recherches serveur pour que « Lire » le trouve tout de suite
    val ready = media?.status == LumioStatus.AVAILABLE || media?.status == LumioStatus.PARTIALLY_AVAILABLE
    var waited by remember(meta.id) { mutableStateOf(false) }
    LaunchedEffect(ready, active?.id) {
        if (active != null && !ready) waited = true
        if (ready && waited) {
            com.nuvio.app.features.servers.ServerMatcher.shared.clear()
            waited = false
        }
    }

    // « Lire » un titre absent (admin) : streaming par le serveur (TorBox), le fichier arrive en arrière-plan
    val navigate = LocalLumioNavigate.current
    var watchNow by remember { mutableStateOf(false) }
    var watching by remember(meta.id) { mutableStateOf(false) }
    LaunchedEffect(Unit) { watchNow = LumioClient.watchNowEnabled() }

    val m = media ?: return
    if (m.status == LumioStatus.AVAILABLE) return
    val missingSeasons = m.seasons.filter { it.status == LumioStatus.UNKNOWN }.map { it.number }
    val canRequest = if (type == "tv") (missingSeasons.isNotEmpty() || (m.seasons.isEmpty() && m.status == LumioStatus.UNKNOWN)) else m.status == LumioStatus.UNKNOWN

    // Séries : on choisit la ou les saisons à demander (une seule saison manquante : déjà cochée)
    val pickSeasons = type == "tv" && missingSeasons.size > 1
    val chosen = when {
        pickSeasons -> missingSeasons.filter { it in selected }
        else -> missingSeasons
    }

    Column(modifier = modifier.widthIn(max = 420.dp).fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Série : premier épisode de la première saison absente (la suite arrive dans Jellyfin par le téléchargement)
        val watchSeason = if (type == "tv") (missingSeasons.filter { it > 0 }.minOrNull() ?: 1) else null
        if (watchNow && (type != "tv" || m.status != LumioStatus.AVAILABLE)) Button(
            onClick = {
                if (watching) return@Button
                watching = true
                scope.launch {
                    message = runCatching {
                        val tmdbId = meta.lumioTmdb() ?: meta.lumioImdb()?.let { LumioClient.tmdbIdFor(type, it) }
                            ?: throw LumioException("Titre non reconnu par TMDB")
                        val w = LumioClient.watchNow(type, tmdbId, watchSeason, if (type == "tv") 1 else null, meta.name, meta.releaseInfo?.take(4), meta.poster, meta.background)
                        val launch = com.nuvio.app.features.player.PlayerLaunch(
                            profileId = com.nuvio.app.features.profiles.ProfileRepository.activeProfileId,
                            title = meta.name,
                            sourceUrl = w.url,
                            logo = meta.logo,
                            poster = meta.poster,
                            background = meta.background,
                            seasonNumber = watchSeason,
                            episodeNumber = if (type == "tv") 1 else null,
                            streamTitle = listOfNotNull("Lumio", w.quality, w.language).joinToString(" · "),
                            providerName = "Lumio",
                            contentType = meta.type,
                            videoId = if (type == "tv") "${meta.id}:$watchSeason:1" else meta.id,
                            parentMetaId = meta.id,
                            parentMetaType = meta.type,
                        )
                        navigate(com.nuvio.app.navigation.PlayerRoute(launchId = com.nuvio.app.features.player.PlayerLaunchStore.put(launch), title = meta.name))
                        if (w.downloading) "Téléchargement lancé sur le serveur ✓" else null
                    }.getOrElse { it.message ?: "Aucun lien pour l'instant" }
                    watching = false
                    reload++
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(40.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                text = when {
                    watching -> "  Recherche d'un lien…"
                    watchSeason != null -> "  Lire S${watchSeason}E1"
                    else -> "  Lire"
                },
                style = MaterialTheme.typography.titleSmall,
            )
        }
        if (active != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val downloading = active.progress != null
            Icon(
                if (downloading) Icons.Default.HourglassTop else Icons.Default.CheckCircle,
                contentDescription = null,
                tint = if (downloading) Blue else Orange,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = when {
                    downloading -> "Téléchargement : ${active.progress} %"
                    active.status == 1 -> "Demande en attente de validation"
                    else -> "Demandé : recherche en cours"
                },
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        // Une série peut recevoir une autre saison pendant qu'une première est en cours ; un film, non
        if (canRequest && (active == null || pickSeasons)) {
            if (pickSeasons) {
                Text(
                    text = "Quelle saison veux-tu ?",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    missingSeasons.forEach { n ->
                        SeasonChip("Saison $n", n in selected) { selected = if (n in selected) selected - n else selected + n }
                    }
                    val all = chosen.size == missingSeasons.size
                    SeasonChip("Toutes", all) { selected = if (all) emptySet() else missingSeasons.toSet() }
                }
            }
            val nothing = pickSeasons && chosen.isEmpty()
            Button(
                onClick = {
                    if (busy || nothing) return@Button
                    busy = true
                    val sent = chosen
                    scope.launch {
                        message = runCatching {
                            LumioClient.request(type, meta.lumioTmdb(), meta.lumioImdb(), meta.name, meta.releaseInfo?.take(4), meta.poster, sent)
                            when {
                                type != "tv" || sent.isEmpty() -> "Demande envoyée ✓"
                                sent.size == 1 -> "Saison ${sent[0]} demandée ✓"
                                else -> "${sent.size} saisons demandées ✓"
                            }
                        }.getOrElse { it.message ?: "Erreur" }
                        selected = emptySet()
                        busy = false
                        reload++
                    }
                },
                enabled = !nothing,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(40.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Blue,
                    contentColor = Color.White,
                    disabledContainerColor = Blue.copy(alpha = 0.35f),
                    disabledContentColor = Color.White.copy(alpha = 0.7f),
                ),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    text = when {
                        busy -> "  Envoi…"
                        nothing -> "  Choisis une saison"
                        type == "tv" && chosen.size == 1 -> "  Demander la saison ${chosen[0]}"
                        type == "tv" && chosen.size > 1 -> "  Demander ${chosen.size} saisons"
                        else -> "  Demander"
                    },
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
        message?.let { Text(it, color = if (it.endsWith("✓")) Green else Orange, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun SeasonChip(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (on) Blue else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            color = if (on) Color.White else MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
