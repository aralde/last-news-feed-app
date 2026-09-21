package com.chronicle.newsfeed.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryFilterBar(
    allCount: Int,
    unreadCount: Int,
    favoritesCount: Int,
    showOnlyUnread: Boolean,
    onToggleUnread: (Boolean) -> Unit,
    showOnlyFavorites: Boolean,
    onToggleFavorites: (Boolean) -> Unit,
    activeFiltersCount: Int,
    onOpenFiltersModal: () -> Unit,
    onMarkAllRead: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // "Todos" Pill with Count
        FilterChip(
            selected = !showOnlyUnread && !showOnlyFavorites,
            onClick = {
                onToggleUnread(false)
                onToggleFavorites(false)
            },
            label = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Todos")
                    Badge(
                        containerColor = if (!showOnlyUnread && !showOnlyFavorites) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "$allCount",
                            fontSize = 10.sp,
                            color = if (!showOnlyUnread && !showOnlyFavorites) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            shape = RoundedCornerShape(16.dp),
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
            )
        )

        // "No leídos" Pill with Count
        FilterChip(
            selected = showOnlyUnread,
            onClick = {
                onToggleUnread(!showOnlyUnread)
                if (!showOnlyUnread) onToggleFavorites(false)
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.FiberManualRecord,
                    contentDescription = null,
                    modifier = Modifier.size(10.dp),
                    tint = if (showOnlyUnread) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                )
            },
            label = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("No leídos")
                    Badge(
                        containerColor = if (showOnlyUnread) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "$unreadCount",
                            fontSize = 10.sp,
                            color = if (showOnlyUnread) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            shape = RoundedCornerShape(16.dp),
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
            )
        )

        // "Favoritos" Pill with Count
        FilterChip(
            selected = showOnlyFavorites,
            onClick = {
                onToggleFavorites(!showOnlyFavorites)
                if (!showOnlyFavorites) onToggleUnread(false)
            },
            leadingIcon = {
                Icon(
                    imageVector = if (showOnlyFavorites) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
            },
            label = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Favoritos")
                    Badge(
                        containerColor = if (showOnlyFavorites) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "$favoritesCount",
                            fontSize = 10.sp,
                            color = if (showOnlyFavorites) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            shape = RoundedCornerShape(16.dp),
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
            )
        )

        VerticalDivider(modifier = Modifier.height(20.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

        // "Filtros" Button with Indicator Badge (replicates Chronicle web button)
        FilledTonalButton(
            onClick = onOpenFiltersModal,
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = if (activeFiltersCount > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Icon(imageVector = Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Filtros", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            if (activeFiltersCount > 0) {
                Spacer(modifier = Modifier.width(6.dp))
                Badge(containerColor = MaterialTheme.colorScheme.primary) {
                    Text("$activeFiltersCount", color = MaterialTheme.colorScheme.onPrimary, fontSize = 10.sp)
                }
            }
        }

        // "Marcar leídos" Button (replicates Chronicle web)
        TextButton(
            onClick = onMarkAllRead,
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Icon(imageVector = Icons.Outlined.DoneAll, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Marcar todo leído", style = MaterialTheme.typography.labelSmall)
        }
    }
}
