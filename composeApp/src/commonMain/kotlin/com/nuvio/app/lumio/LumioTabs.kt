package com.nuvio.app.lumio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.components.HomeCatalogRowSection

/** État de chargement d'un onglet Lumio. */
sealed interface LumioLoad<out T> {
    data object Loading : LumioLoad<Nothing>
    data class Ready<T>(val value: T) : LumioLoad<T>
    data class Failed(val message: String) : LumioLoad<Nothing>
}

/** Filtre de l'onglet Demandes. */
enum class LumioRequestFilter(val label: String) {
    ONGOING("En cours"),
    DONE("Terminées"),
}

fun LumioListedRequest.isDone(): Boolean =
    status == LumioRequestStatus.COMPLETED ||
        status == LumioRequestStatus.DECLINED ||
        status == LumioRequestStatus.FAILED ||
        mediaStatus == LumioStatus.AVAILABLE

private fun LumioListedRequest.toPreview(): MetaPreview? = tmdbId?.let {
    MetaPreview(
        id = "tmdb:$it",
        type = if (type == "tv") "series" else "movie",
        name = title,
        poster = poster,
        releaseInfo = year,
    )
}

// ——— Onglet « Sorties » ———

fun LazyListScope.lumioReleasesContent(
    state: LumioLoad<LumioReleases.Result>,
    onPosterClick: ((MetaPreview) -> Unit)?,
    onRetry: () -> Unit,
) {
    when (state) {
        LumioLoad.Loading -> item(key = "lumio-releases-loading") { LumioCenteredLoader("Recherche des dernières sorties…") }
        is LumioLoad.Failed -> item(key = "lumio-releases-error") { LumioMessage(state.message, onRetry) }
        is LumioLoad.Ready -> {
            item(key = "lumio-releases-intro") {
                Text(
                    text = "Films et séries arrivés sur les plateformes (VOD, Netflix, Prime Video…). Ouvre une fiche pour regarder ou faire « Demander ».",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            state.value.sections().forEach { section ->
                item(key = section.key) {
                    HomeCatalogRowSection(
                        section = section,
                        modifier = Modifier.padding(bottom = 12.dp),
                        sectionPadding = 16.dp,
                        onPosterClick = onPosterClick,
                    )
                }
            }
        }
    }
}

// ——— Onglet « Demandes » ———

fun LazyListScope.lumioRequestsContent(
    state: LumioLoad<List<LumioListedRequest>>,
    filter: LumioRequestFilter,
    onFilterChange: (LumioRequestFilter) -> Unit,
    onPosterClick: ((MetaPreview) -> Unit)?,
    onRetry: () -> Unit,
) {
    when (state) {
        LumioLoad.Loading -> item(key = "lumio-requests-loading") { LumioCenteredLoader("Chargement des demandes…") }
        is LumioLoad.Failed -> item(key = "lumio-requests-error") { LumioMessage(state.message, onRetry) }
        is LumioLoad.Ready -> {
            val all = state.value
            val shown = all.filter { it.isDone() == (filter == LumioRequestFilter.DONE) }
            item(key = "lumio-requests-filter") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LumioRequestFilter.entries.forEach { f ->
                        val count = all.count { it.isDone() == (f == LumioRequestFilter.DONE) }
                        LumioPill(text = "${f.label} · $count", selected = f == filter, onClick = { onFilterChange(f) })
                    }
                }
            }
            if (shown.isEmpty()) {
                item(key = "lumio-requests-empty") {
                    LumioMessage(
                        if (filter == LumioRequestFilter.ONGOING) {
                            "Aucune demande en cours. Cherche un film ou une série et touche « Demander »."
                        } else {
                            "Aucune demande terminée pour l'instant."
                        },
                        onRetry = null,
                    )
                }
            } else {
                items(shown, key = { "lumio-request-${it.id}" }) { r ->
                    LumioRequestRow(r, onClick = r.toPreview()?.let { p -> onPosterClick?.let { { it(p) } } })
                }
            }
        }
    }
}

@Composable
private fun LumioRequestRow(r: LumioListedRequest, onClick: (() -> Unit)?) {
    val (label, color) = when {
        r.status == LumioRequestStatus.DECLINED -> "Refusée" to Color(0xFFE5484D)
        r.status == LumioRequestStatus.FAILED -> "Échec du téléchargement" to Color(0xFFE5484D)
        r.status == LumioRequestStatus.COMPLETED || r.mediaStatus == LumioStatus.AVAILABLE -> "Disponible" to Color(0xFF30A46C)
        r.status == LumioRequestStatus.PENDING -> "En attente de validation" to Color(0xFFF5A524)
        r.downloadState == "importing" -> "Ajout dans la bibliothèque…" to Color(0xFF3E8BFF)
        r.progress != null -> "Téléchargement · ${r.progress} %" to Color(0xFF3E8BFF)
        else -> "Recherche d'une version…" to Color(0xFF3E8BFF)
    }
    val kind = if (r.type == "tv") {
        if (r.seasons.isEmpty()) "Série" else "Série · " + r.seasons.sorted().joinToString(", ") { "S$it" }
    } else {
        "Film"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(60.dp)
                .height(90.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.08f)),
        ) {
            if (r.poster != null) {
                AsyncImage(
                    model = r.poster,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = r.title + (r.year?.let { " ($it)" } ?: ""),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = kind + (r.requestedBy?.let { " · demandé par $it" } ?: ""),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(color))
                Spacer(Modifier.width(6.dp))
                Text(text = label, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            val p = r.progress
            if (p != null && !r.isDone() && r.status == LumioRequestStatus.APPROVED) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (p.coerceIn(0, 100)) / 100f },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)),
                    color = color,
                    trackColor = Color.White.copy(alpha = 0.12f),
                )
            }
        }
    }
}

// ——— Petits éléments communs ———

@Composable
fun LumioPill(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        color = if (selected) Color.Black else Color.White,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Color.White else Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
internal fun LumioCenteredLoader(text: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
        Spacer(Modifier.height(12.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
    }
}

@Composable
internal fun LumioMessage(text: String, onRetry: (() -> Unit)?) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
        if (onRetry != null) {
            Spacer(Modifier.height(14.dp))
            LumioPill(text = "Réessayer", selected = true, onClick = onRetry)
        }
    }
}
