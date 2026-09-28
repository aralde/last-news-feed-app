package com.chronicle.newsfeed.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Source
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chronicle.newsfeed.ui.feed.SortOrder
import com.chronicle.newsfeed.ui.theme.LocalAppStrings

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
    categories: List<String> = emptyList(),
    selectedCategory: String = "Todos",
    onSelectCategory: (String) -> Unit = {},
    sortOrder: SortOrder = SortOrder.NEWEST,
    onToggleSortOrder: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current

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
            selected = !showOnlyUnread && !showOnlyFavorites && selectedCategory == "Todos",
            onClick = {
                onToggleUnread(false)
                onToggleFavorites(false)
                onSelectCategory("Todos")
            },
            label = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(strings.filterAll)
                    Badge(
                        containerColor = if (!showOnlyUnread && !showOnlyFavorites && selectedCategory == "Todos") MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "$allCount",
                            fontSize = 10.sp,
                            color = if (!showOnlyUnread && !showOnlyFavorites && selectedCategory == "Todos") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
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
                    Text(strings.filterUnread)
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
                    Text(strings.filterFavorites)
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

        // Direct Sort Order Pill
        FilterChip(
            selected = sortOrder == SortOrder.NEWEST,
            onClick = onToggleSortOrder,
            leadingIcon = {
                Icon(
                    imageVector = if (sortOrder == SortOrder.NEWEST) Icons.Outlined.Schedule else Icons.Outlined.Source,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
            },
            label = {
                Text(
                    text = if (sortOrder == SortOrder.NEWEST) strings.sortByRecent else strings.sortBySource,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            },
            shape = RoundedCornerShape(16.dp),
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        )

        // Direct Category Chips on Main Screen
        categories.forEach { category ->
            val isSelected = selectedCategory.equals(category, ignoreCase = true)
            FilterChip(
                selected = isSelected,
                onClick = {
                    if (isSelected) onSelectCategory("Todos") else onSelectCategory(category)
                },
                label = { Text(category, fontSize = 12.sp) },
                shape = RoundedCornerShape(16.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }

        VerticalDivider(modifier = Modifier.height(20.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

        // Advanced "Filtros" Button
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
            Text(strings.filters, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            if (activeFiltersCount > 0) {
                Spacer(modifier = Modifier.width(6.dp))
                Badge(containerColor = MaterialTheme.colorScheme.primary) {
                    Text("$activeFiltersCount", color = MaterialTheme.colorScheme.onPrimary, fontSize = 10.sp)
                }
            }
        }

        // "Marcar leídos" Button
        TextButton(
            onClick = onMarkAllRead,
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Icon(imageVector = Icons.Outlined.DoneAll, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(strings.markAllRead, style = MaterialTheme.typography.labelSmall)
        }
    }
}
