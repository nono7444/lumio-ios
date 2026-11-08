package com.nuvio.app.lumio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassTop
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

    val m = media ?: return
    if (m.status == LumioStatus.AVAILABLE) return
    val partial = m.status == LumioStatus.PARTIALLY_AVAILABLE
    val missingSeasons = m.seasons.filter { it.status == LumioStatus.UNKNOWN }.map { it.number }
    val canRequest = if (type == "tv") (missingSeasons.isNotEmpty() || (m.seasons.isEmpty() && m.status == LumioStatus.UNKNOWN)) else m.status == LumioStatus.UNKNOWN

    Column(modifier = modifier.widthIn(max = 420.dp).fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when {
            canRequest && active == null -> Button(
                onClick = {
                    if (busy) return@Button
                    busy = true
                    scope.launch {
                        message = runCatching {
                            LumioClient.request(type, meta.lumioTmdb(), meta.lumioImdb(), meta.name, meta.releaseInfo?.take(4), meta.poster, missingSeasons)
                            if (type == "tv" && partial) "Saisons manquantes demandées ✓" else "Demande envoyée ✓"
                        }.getOrElse { it.message ?: "Erreur" }
                        busy = false
                        reload++
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(40.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Blue, contentColor = Color.White),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    text = when {
                        busy -> "  Envoi…"
                        type == "tv" && partial -> "  Demander les saisons manquantes"
                        else -> "  Demander"
                    },
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            active != null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        }
        message?.let { Text(it, color = if (it.endsWith("✓")) Green else Orange, style = MaterialTheme.typography.bodySmall) }
    }
}
