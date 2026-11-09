package com.nuvio.app.lumio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.servers.ServerMediaKind

/** Lumio : grande carte d'une bibliothèque Jellyfin (image de fond, icône, nom, nombre de titres). */
@Composable
fun LumioLibraryCard(
    name: String,
    kind: ServerMediaKind,
    total: Int?,
    items: List<MetaPreview>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val image = remember(items) {
        items.firstNotNullOfOrNull { it.banner ?: it.landscapePoster } ?: items.firstNotNullOfOrNull { it.poster }
    }
    val isKids = remember(name) { name.lowercase().let { "kid" in it || "enfant" in it || "jeunesse" in it } }
    val icon = when {
        isKids -> Icons.Rounded.ChildCare
        kind == ServerMediaKind.MOVIE -> Icons.Rounded.Movie
        kind == ServerMediaKind.SERIES -> Icons.Rounded.Tv
        else -> Icons.Rounded.VideoLibrary
    }
    val countLabel = total?.let { n ->
        when (kind) {
            ServerMediaKind.MOVIE -> if (n > 1) "$n films" else "$n film"
            ServerMediaKind.SERIES -> if (n > 1) "$n séries" else "$n série"
            else -> if (n > 1) "$n titres" else "$n titre"
        }
    }
    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.62f)
            .clip(shape)
            .background(Color(0xFF15161A))
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
            .clickable(onClick = onClick),
    ) {
        if (image != null) {
            AsyncImage(
                model = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.85f),
                    ),
                ),
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (countLabel != null) {
                    Text(text = countLabel, color = Color.White.copy(alpha = 0.75f), fontSize = 15.sp)
                }
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(30.dp),
            )
        }
    }
}
