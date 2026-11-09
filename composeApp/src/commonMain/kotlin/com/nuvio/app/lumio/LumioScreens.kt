package com.nuvio.app.lumio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.nuvio.app.core.ui.NuvioScreen
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.core.ui.ScreenActivityEffect
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.navigation.AppRoute
import com.nuvio.app.navigation.LocalUseNativeNavigation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** Ouvre une page Lumio (plateforme, recherche) depuis n'importe quel écran. */
val LocalLumioNavigate = compositionLocalOf<(AppRoute) -> Unit> { {} }

// ——— Onglet « Demandes » ———

@Composable
fun LumioRequestsScreen(
    modifier: Modifier = Modifier,
    onPosterClick: ((MetaPreview) -> Unit)?,
) {
    var state by remember { mutableStateOf<LumioLoad<List<LumioListedRequest>>>(LumioLoad.Loading) }
    var retry by remember { mutableStateOf(0) }
    var filterName by rememberSaveable { mutableStateOf(LumioRequestFilter.ONGOING.name) }

    // Rafraîchi toutes les 10 s tant que l'onglet est affiché (progression des téléchargements)
    ScreenActivityEffect(retry) { active ->
        if (!active) return@ScreenActivityEffect
        while (true) {
            val next = try {
                LumioLoad.Ready(LumioClient.requests())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LumioLoad.Failed(e.message ?: "Demandes indisponibles")
            }
            if (next is LumioLoad.Ready || state !is LumioLoad.Ready) state = next
            delay(10_000)
        }
    }

    NuvioScreen(modifier = modifier.fillMaxSize(), horizontalPadding = 0.dp) {
        item(key = "lumio-requests-header") {
            NuvioScreenHeader(title = "Demandes", modifier = Modifier.padding(horizontal = 16.dp))
        }
        lumioRequestsContent(
            state = state,
            filter = runCatching { LumioRequestFilter.valueOf(filterName) }.getOrDefault(LumioRequestFilter.ONGOING),
            onFilterChange = { filterName = it.name },
            onPosterClick = onPosterClick,
            onRetry = {
                state = LumioLoad.Loading
                retry++
            },
        )
    }
}

// ——— Onglet « Calendrier » (sorties numériques) ———

private enum class CalendarFilter(val label: String) {
    OUT("Sorties"),
    SOON("À venir"),
}

@Composable
fun LumioCalendarScreen(
    modifier: Modifier = Modifier,
    onPosterClick: ((MetaPreview) -> Unit)?,
) {
    var state by remember { mutableStateOf<LumioLoad<LumioReleases.Result>>(LumioLoad.Loading) }
    var retry by remember { mutableStateOf(0) }
    var filterName by rememberSaveable { mutableStateOf(CalendarFilter.OUT.name) }

    var loadedFor by remember { mutableStateOf(-1) }
    ScreenActivityEffect(retry) { active ->
        // Chargé une fois ; « Réessayer » force un nouveau chargement
        if (!active || (state is LumioLoad.Ready && loadedFor == retry)) return@ScreenActivityEffect
        state = LumioLoad.Loading
        state = try {
            LumioLoad.Ready(LumioReleases.load(force = retry > 0)).also { loadedFor = retry }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LumioLoad.Failed(e.message ?: "Calendrier indisponible")
        }
    }

    val filter = runCatching { CalendarFilter.valueOf(filterName) }.getOrDefault(CalendarFilter.OUT)
    NuvioScreen(modifier = modifier.fillMaxSize(), horizontalPadding = 0.dp) {
        item(key = "lumio-calendar-header") {
            NuvioScreenHeader(title = "Calendrier", modifier = Modifier.padding(horizontal = 16.dp))
        }
        when (val s = state) {
            LumioLoad.Loading -> item(key = "lumio-calendar-loading") { LumioCenteredLoader("Recherche des sorties…") }
            is LumioLoad.Failed -> item(key = "lumio-calendar-error") { LumioMessage(s.message, onRetry = { retry++ }) }
            is LumioLoad.Ready -> {
                val out = (s.value.moviesOut + s.value.seriesOut).sortedByDescending { it.releaseInfo.orEmpty() }
                val soon = (s.value.moviesSoon + s.value.seriesSoon).sortedBy { it.releaseInfo.orEmpty() }
                item(key = "lumio-calendar-filter") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CalendarFilter.entries.forEach { f ->
                            val count = if (f == CalendarFilter.OUT) out.size else soon.size
                            LumioPill(text = "${f.label} · $count", selected = f == filter, onClick = { filterName = f.name })
                        }
                    }
                }
                calendarDays(if (filter == CalendarFilter.OUT) out else soon, onPosterClick)
            }
        }
    }
}

private fun LazyListScope.calendarDays(items: List<MetaPreview>, onPosterClick: ((MetaPreview) -> Unit)?) {
    if (items.isEmpty()) {
        item(key = "lumio-calendar-empty") { LumioMessage("Rien de prévu pour le moment.", onRetry = null) }
        return
    }
    val today = LumioReleases.isoDay(LumioReleases.nowMs())
    items.groupBy { it.releaseInfo.orEmpty() }.forEach { (day, entries) ->
        item(key = "lumio-day-$day") {
            Text(
                text = frenchDayLabel(day, today),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 4.dp),
            )
        }
        items(entries, key = { "lumio-day-$day-${it.type}-${it.id}" }) { m ->
            CalendarRow(m, onClick = onPosterClick?.let { { it(m) } })
        }
    }
}

@Composable
private fun CalendarRow(m: MetaPreview, onClick: (() -> Unit)?) {
    val kind = if (m.type == "series") {
        "Série · " + (m.description?.substringBefore(" · ")?.takeIf { it.isNotBlank() } ?: "nouvelle saison")
    } else {
        "Film · sortie numérique"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(54.dp)
                .height(81.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Color.White.copy(alpha = 0.08f)),
        ) {
            if (m.poster != null) {
                AsyncImage(model = m.poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = m.name,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = kind,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ——— Page d'une plateforme (Netflix, Disney+…) ———

private enum class PlatformFilter(val label: String) {
    ALL("Tout"),
    MOVIES("Films"),
    SERIES("Séries"),
}

@Composable
fun LumioPlatformScreen(
    platformId: String,
    platformName: String,
    onPosterClick: (MetaPreview) -> Unit,
) {
    var state by remember(platformId) { mutableStateOf<LumioLoad<List<LumioPlatformTitle>>>(LumioLoad.Loading) }
    var retry by remember { mutableStateOf(0) }
    var filterName by rememberSaveable(platformId) { mutableStateOf(PlatformFilter.ALL.name) }
    val native = LocalUseNativeNavigation.current

    LaunchedEffect(platformId, retry) {
        state = LumioLoad.Loading
        state = try {
            LumioLoad.Ready(LumioPlatformsCache.latest(platformId, force = retry > 0))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LumioLoad.Failed(e.message ?: "Dernières sorties indisponibles")
        }
    }

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val filter = runCatching { PlatformFilter.valueOf(filterName) }.getOrDefault(PlatformFilter.ALL)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val columns = (maxWidth / 118.dp).toInt().coerceAtLeast(3)
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = statusTop + if (native) 52.dp else 16.dp,
                bottom = 40.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!native) {
                item(key = "title", span = { GridItemSpan(maxLineSpan) }) {
                    Text(platformName, color = MaterialTheme.colorScheme.onSurface, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                }
            }
            when (val s = state) {
                LumioLoad.Loading -> item(key = "loading", span = { GridItemSpan(maxLineSpan) }) {
                    LumioCenteredLoader("Dernières sorties $platformName…")
                }
                is LumioLoad.Failed -> item(key = "error", span = { GridItemSpan(maxLineSpan) }) {
                    LumioMessage(s.message, onRetry = { retry++ })
                }
                is LumioLoad.Ready -> {
                    val all = s.value
                    val shown = when (filter) {
                        PlatformFilter.ALL -> all
                        PlatformFilter.MOVIES -> all.filter { it.type == "movie" }
                        PlatformFilter.SERIES -> all.filter { it.type == "series" }
                    }
                    item(key = "filter", span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            PlatformFilter.entries.forEach { f ->
                                val count = when (f) {
                                    PlatformFilter.ALL -> all.size
                                    PlatformFilter.MOVIES -> all.count { it.type == "movie" }
                                    PlatformFilter.SERIES -> all.count { it.type == "series" }
                                }
                                LumioPill(text = "${f.label} · $count", selected = f == filter, onClick = { filterName = f.name })
                            }
                        }
                    }
                    if (shown.isEmpty()) {
                        item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                            LumioMessage("Rien de neuf sur $platformName.", onRetry = null)
                        }
                    } else {
                        items(shown, key = { "${it.type}-${it.tmdbId}" }) { t ->
                            PlatformPoster(t, onClick = { onPosterClick(t.toPreview()) })
                        }
                    }
                }
            }
        }
    }
}

private fun LumioPlatformTitle.toPreview() = MetaPreview(
    id = "tmdb:$tmdbId",
    type = type,
    name = title,
    poster = poster,
    releaseInfo = date ?: year,
    rawReleaseDate = date,
)

@Composable
private fun PlatformPoster(t: LumioPlatformTitle, onClick: () -> Unit) {
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.08f)),
        ) {
            if (t.poster != null) {
                AsyncImage(model = t.poster, contentDescription = t.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            if (t.available) {
                // Déjà dans la bibliothèque Lumio
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF30A46C)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = "Dans Lumio", tint = Color.White, modifier = Modifier.size(15.dp))
                }
            }
        }
        Text(
            text = t.title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        val sub = t.date?.let { frenchShortDate(it) } ?: t.year
        if (sub != null) {
            Text(text = sub, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1)
        }
    }
}

// ——— Recherche ouverte depuis la loupe ———

/** Laisse la place à la barre de navigation iOS au-dessus de l'écran de recherche. */
@Composable
fun LumioSearchPage(content: @Composable () -> Unit) {
    val native = LocalUseNativeNavigation.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = if (native) 44.dp else 0.dp),
    ) {
        content()
    }
}

// ——— Dates en français ———

private val MONTHS = listOf(
    "janvier", "février", "mars", "avril", "mai", "juin",
    "juillet", "août", "septembre", "octobre", "novembre", "décembre",
)
private val MONTHS_SHORT = listOf(
    "janv.", "févr.", "mars", "avr.", "mai", "juin",
    "juil.", "août", "sept.", "oct.", "nov.", "déc.",
)

// Le 1er janvier 1970 était un jeudi
private val WEEKDAYS_FROM_THURSDAY = listOf("Jeudi", "Vendredi", "Samedi", "Dimanche", "Lundi", "Mardi", "Mercredi")

private data class Ymd(val y: Int, val m: Int, val d: Int)

private fun parseYmd(iso: String): Ymd? {
    if (iso.length < 10) return null
    val y = iso.substring(0, 4).toIntOrNull() ?: return null
    val m = iso.substring(5, 7).toIntOrNull()?.takeIf { it in 1..12 } ?: return null
    val d = iso.substring(8, 10).toIntOrNull()?.takeIf { it in 1..31 } ?: return null
    return Ymd(y, m, d)
}

/** Nombre de jours depuis le 1er janvier 1970 (algorithme « days from civil »). */
private fun epochDay(v: Ymd): Long {
    val y = (if (v.m <= 2) v.y - 1 else v.y).toLong()
    val era = (if (y >= 0) y else y - 399) / 400
    val yoe = y - era * 400
    val mp = (v.m + 9) % 12
    val doy = (153 * mp + 2) / 5 + v.d - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146_097 + doe - 719_468
}

internal fun frenchDayLabel(iso: String, todayIso: String): String {
    val v = parseYmd(iso) ?: return iso
    val t = parseYmd(todayIso)
    val day = epochDay(v)
    if (t != null) {
        when (day - epochDay(t)) {
            0L -> return "Aujourd'hui"
            1L -> return "Demain"
            -1L -> return "Hier"
        }
    }
    val weekday = WEEKDAYS_FROM_THURSDAY[(((day % 7) + 7) % 7).toInt()]
    val dayNumber = if (v.d == 1) "1er" else v.d.toString()
    val year = if (t != null && t.y != v.y) " ${v.y}" else ""
    return "$weekday $dayNumber ${MONTHS[v.m - 1]}$year"
}

internal fun frenchShortDate(iso: String): String {
    val v = parseYmd(iso) ?: return iso
    return "${v.d} ${MONTHS_SHORT[v.m - 1]} ${v.y}"
}
