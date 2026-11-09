package com.nuvio.app.lumio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow

/** Lumio : tri de la grille d'une bibliothèque du serveur. */
enum class LumioLibrarySort(val label: String, val sortBy: String, val sortOrder: String) {
    RECENT("Ajout récent", "DateCreated,SortName", "Descending"),
    NAME("A-Z", "SortName", "Ascending"),
    YEAR("Année", "ProductionYear,PremiereDate,SortName", "Descending"),
    RATING("Note", "CommunityRating,SortName", "Descending");

    companion object {
        val current = MutableStateFlow(RECENT)
    }
}

@Composable
fun LumioSortChips(onChange: () -> Unit) {
    val selected by LumioLibrarySort.current.collectAsState()
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LumioLibrarySort.entries.forEach { sort ->
            val on = sort == selected
            Text(
                text = sort.label,
                color = if (on) Color.Black else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (on) Color.White else Color.White.copy(alpha = 0.12f))
                    .clickable {
                        if (!on) {
                            LumioLibrarySort.current.value = sort
                            onChange()
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}
