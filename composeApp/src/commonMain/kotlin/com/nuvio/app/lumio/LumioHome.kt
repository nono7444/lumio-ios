package com.nuvio.app.lumio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nuvio.app.features.catalog.CatalogTarget
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.components.HomeCatalogRowSection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

// ——— Pastille « serveur allumé / éteint » ———

/** État du serveur Lumio, partagé par tout l'écran d'accueil. null = vérification en cours. */
object LumioServerState {
    val online = MutableStateFlow<Boolean?>(null)
}

/** Petite icône serveur en haut de l'accueil : point vert = en ligne, rouge = éteint ou injoignable. */
@Composable
fun LumioServerBadge(modifier: Modifier = Modifier) {
    val online by LumioServerState.online.collectAsState()
    LaunchedEffect(Unit) {
        while (true) {
            LumioServerState.online.value = LumioClient.isOnline()
            delay(if (LumioServerState.online.value == true) 30_000 else 10_000)
        }
    }
    val dot = when (online) {
        true -> Color(0xFF30D158)
        false -> Color(0xFFFF453A)
        null -> Color(0xFF8E8E93)
    }
    Box(modifier = modifier.size(38.dp)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
                .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Dns,
                contentDescription = when (online) {
                    true -> "Serveur en ligne"
                    false -> "Serveur hors ligne"
                    null -> "Vérification du serveur"
                },
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 1.dp, y = (-1).dp)
                .size(12.dp)
                .clip(CircleShape)
                .background(Color.Black)
                .padding(2.dp)
                .clip(CircleShape)
                .background(dot),
        )
    }
}

// ——— Barre des plateformes (Netflix, Disney+…) ———

private object LumioPlatformsCache {
    var platforms: List<LumioPlatform>? = null
    val latest = mutableMapOf<String, Pair<Long, List<LumioPlatformTitle>>>()
}

/**
 * Accueil Lumio : logos des plateformes, puis les dernières sorties de celle choisie
 * (une rangée Films, une rangée Séries, de la plus récente à la plus ancienne).
 */
@Composable
fun LumioPlatformsSection(
    sectionPadding: Dp,
    onPosterClick: ((MetaPreview) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var platforms by remember { mutableStateOf(LumioPlatformsCache.platforms) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var titles by remember { mutableStateOf<List<LumioPlatformTitle>?>(null) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (platforms == null) {
            platforms = try {
                LumioClient.platforms().also { LumioPlatformsCache.platforms = it }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
    }
    val list = platforms ?: return
    if (list.isEmpty()) return
    val selected = list.firstOrNull { it.id == selectedId } ?: list.first()

    LaunchedEffect(selected.id) {
        failed = false
        val cached = LumioPlatformsCache.latest[selected.id]
        val now = LumioReleases.nowMs()
        if (cached != null && now - cached.first < 15 * 60_000L) {
            titles = cached.second
            return@LaunchedEffect
        }
        titles = null
        titles = try {
            LumioClient.platformLatest(selected.id).also { LumioPlatformsCache.latest[selected.id] = now to it }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            failed = true
            emptyList()
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Plateformes",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = sectionPadding, vertical = 6.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = sectionPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(list, key = { it.id }) { p ->
                PlatformLogo(p, selected = p.id == selected.id, onClick = { selectedId = p.id })
            }
        }
        val current = titles
        when {
            current == null -> Text(
                text = "Chargement des dernières sorties ${selected.name}…",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = sectionPadding, vertical = 16.dp),
            )
            current.isEmpty() -> Text(
                text = if (failed) "Dernières sorties indisponibles pour le moment." else "Rien de neuf sur ${selected.name}.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = sectionPadding, vertical = 16.dp),
            )
            else -> {
                val movies = current.filter { it.type == "movie" }.map { it.toPreview() }
                val series = current.filter { it.type == "series" }.map { it.toPreview() }
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    if (movies.isNotEmpty()) {
                        HomeCatalogRowSection(
                            section = platformSection(selected, "movie", "${selected.name} · Films", movies),
                            modifier = Modifier.padding(bottom = 12.dp),
                            sectionPadding = sectionPadding,
                            onPosterClick = onPosterClick,
                        )
                    }
                    if (series.isNotEmpty()) {
                        HomeCatalogRowSection(
                            section = platformSection(selected, "series", "${selected.name} · Séries", series),
                            sectionPadding = sectionPadding,
                            onPosterClick = onPosterClick,
                        )
                    }
                }
            }
        }
    }
}

private fun platformSection(p: LumioPlatform, type: String, title: String, items: List<MetaPreview>) = HomeCatalogSection(
    key = "lumio-platform-${p.id}-$type",
    title = title,
    subtitle = "Dernières sorties",
    addonName = p.name,
    target = CatalogTarget.Addon(manifestUrl = "lumio:platform:${p.id}", contentType = type, catalogId = "latest"),
    items = items,
)

private fun LumioPlatformTitle.toPreview() = MetaPreview(
    id = "tmdb:$tmdbId",
    type = type,
    name = title,
    poster = poster,
    releaseInfo = date ?: year,
    rawReleaseDate = date,
)

@Composable
private fun PlatformLogo(p: LumioPlatform, selected: Boolean, onClick: () -> Unit) {
    val brand = remember(p.color) { parseHexColor(p.color) ?: Color(0xFF2C2C2E) }
    val shape = RoundedCornerShape(16.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(70.dp).clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .clip(shape)
                .background(brand)
                .border(if (selected) 2.5.dp else 1.dp, if (selected) Color.White else Color.White.copy(alpha = 0.12f), shape),
            contentAlignment = Alignment.Center,
        ) {
            val image = p.icon ?: p.logo
            if (image != null) {
                AsyncImage(
                    model = image,
                    contentDescription = p.name,
                    contentScale = if (p.icon != null) ContentScale.Crop else ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(if (p.icon != null) 0.dp else 10.dp),
                )
            } else {
                Text(p.name.take(1), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(
            text = p.name,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 5.dp).height(16.dp),
        )
    }
}

private fun parseHexColor(hex: String?): Color? {
    val h = hex?.removePrefix("#")?.takeIf { it.length == 6 } ?: return null
    return h.toLongOrNull(16)?.let { Color(0xFF000000 or it) }
}
