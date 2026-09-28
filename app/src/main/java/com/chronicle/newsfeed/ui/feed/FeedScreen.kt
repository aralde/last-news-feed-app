package com.chronicle.newsfeed.ui.feed

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chronicle.newsfeed.data.model.Article
import com.chronicle.newsfeed.ui.components.CategoryFilterBar
import com.chronicle.newsfeed.ui.components.FloatingAudioPlayer
import com.chronicle.newsfeed.ui.components.NewsCard
import com.chronicle.newsfeed.ui.digest.DailyDigestDialog
import com.chronicle.newsfeed.ui.sources.SourcesSheet
import com.chronicle.newsfeed.ui.theme.LocalAppStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    onArticleClick: (Article) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: FeedViewModel = viewModel()
) {
    val strings = LocalAppStrings.current

    val articles by viewModel.articles.collectAsState()
    val sources by viewModel.sources.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val selectedSources by viewModel.selectedSources.collectAsState()
    val limitPerSource by viewModel.limitPerSource.collectAsState()
    val blockedTags by viewModel.blockedTags.collectAsState()
    val showOnlyUnread by viewModel.showOnlyUnread.collectAsState()
    val showOnlyFavorites by viewModel.showOnlyFavorites.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val isAddingSource by viewModel.isAddingSource.collectAsState()
    val synthesizingArticleId by viewModel.synthesizingArticleId.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val dailyDigest by viewModel.dailyDigest.collectAsState()
    val isGeneratingDigest by viewModel.isGeneratingDigest.collectAsState()
    val isCustomBulletinMode by viewModel.isCustomBulletinMode.collectAsState()
    val selectedArticleIdsForDigest by viewModel.selectedArticleIdsForDigest.collectAsState()
    val queueArticles by viewModel.queueArticles.collectAsState()
    val currentQueueIndex by viewModel.currentQueueIndex.collectAsState()
    val engineInitState by viewModel.engineInitState.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val allCount by viewModel.allCount.collectAsState()
    val unreadCount by viewModel.unreadCount.collectAsState()
    val favoritesCount by viewModel.favoritesCount.collectAsState()

    var showSourcesSheet by remember { mutableStateOf(false) }
    var showFilterModal by remember { mutableStateOf(false) }
    var showDailyDigestDialog by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }

    val categories = remember(sources) {
        sources.map { it.category }.distinct().filter { it.isNotBlank() }
    }

    val activeFiltersCount = remember(selectedCategory, selectedSources, blockedTags) {
        var count = 0
        if (selectedCategory != "Todos") count++
        count += selectedSources.size
        count += blockedTags.size
        count
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = strings.appName,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    letterSpacing = 1.sp,
                                    fontWeight = FontWeight.Black
                                ),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = strings.appTagline,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { isSearchActive = !isSearchActive }) {
                            Icon(
                                imageVector = if (isSearchActive) Icons.Default.Close else Icons.Outlined.Search,
                                contentDescription = strings.searchPlaceholder
                            )
                        }

                        IconButton(onClick = { viewModel.syncFeeds() }, enabled = !isSyncing) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Sincronizar")
                            }
                        }

                        IconButton(onClick = { showSourcesSheet = true }) {
                            Icon(imageVector = Icons.Default.RssFeed, contentDescription = strings.sources)
                        }

                        IconButton(onClick = onNavigateToSettings) {
                            Icon(imageVector = Icons.Default.Settings, contentDescription = strings.settings)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )

                // Engine Initialization Banner (Startup loading feedback for LLM & Voices)
                AnimatedVisibility(
                    visible = engineInitState.isLlmLoading || engineInitState.isTtsLoading,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(15.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = engineInitState.message.ifBlank { strings.loadingModels },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }


                // Search Bar
                AnimatedVisibility(visible = isSearchActive) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text(strings.searchPlaceholder) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(Icons.Default.Close, contentDescription = strings.clear)
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Status Bar with Counts, Direct Categories & Quick Sort Toggle
                CategoryFilterBar(
                    allCount = allCount,
                    unreadCount = unreadCount,
                    favoritesCount = favoritesCount,
                    showOnlyUnread = showOnlyUnread,
                    onToggleUnread = { viewModel.toggleUnread(it) },
                    showOnlyFavorites = showOnlyFavorites,
                    onToggleFavorites = { viewModel.toggleFavorites(it) },
                    activeFiltersCount = activeFiltersCount,
                    onOpenFiltersModal = { showFilterModal = true },
                    onMarkAllRead = { viewModel.markAllRead() },
                    categories = categories,
                    selectedCategory = selectedCategory,
                    onSelectCategory = { viewModel.setCategory(it) },
                    sortOrder = sortOrder,
                    onToggleSortOrder = {
                        val next = if (sortOrder == SortOrder.NEWEST) SortOrder.BY_SOURCE else SortOrder.NEWEST
                        viewModel.setSortOrder(next)
                    }
                )

                // Active Filters Removable Chips Row
                if (activeFiltersCount > 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(strings.activeFilters, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        if (selectedCategory != "Todos") {
                            InputChip(
                                selected = true,
                                onClick = { viewModel.setCategory("Todos") },
                                label = { Text(selectedCategory) },
                                trailingIcon = { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(12.dp)) }
                            )
                        }

                        selectedSources.forEach { sourceId ->
                            val sourceName = sources.find { it.id == sourceId }?.name ?: sourceId
                            InputChip(
                                selected = true,
                                onClick = { viewModel.toggleSourceFilter(sourceId) },
                                label = { Text(sourceName) },
                                trailingIcon = { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(12.dp)) }
                            )
                        }

                        blockedTags.forEach { tag ->
                            InputChip(
                                selected = true,
                                onClick = { viewModel.removeBlockedTag(tag) },
                                label = { Text("🚫 #$tag") },
                                trailingIcon = { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(12.dp)) },
                                colors = InputChipDefaults.inputChipColors(labelColor = MaterialTheme.colorScheme.error)
                            )
                        }

                        TextButton(
                            onClick = { viewModel.resetAllFilters() },
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Text(strings.clear, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (isCustomBulletinMode) {
                // Floating Bottom Bar when selecting stories to listen
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = { viewModel.exitCustomBulletinMode() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = strings.cancel,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (selectedArticleIdsForDigest.isEmpty()) {
                                    strings.customBulletinHeader
                                } else {
                                    String.format(strings.storiesSelectedCount, selectedArticleIdsForDigest.size)
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = strings.customBulletinSelectPrompt,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        TextButton(
                            onClick = {
                                if (selectedArticleIdsForDigest.size == articles.size && articles.isNotEmpty()) {
                                    viewModel.clearDigestSelection()
                                } else {
                                    viewModel.selectAllForDigest(articles.map { it.id })
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (selectedArticleIdsForDigest.size == articles.size && articles.isNotEmpty()) strings.clearSelection else strings.selectAll,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }

                        Button(
                            onClick = { viewModel.playSelectedArticles(articles) },
                            enabled = selectedArticleIdsForDigest.isNotEmpty(),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(imageVector = Icons.Default.Headphones, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (selectedArticleIdsForDigest.isNotEmpty()) {
                                    "${strings.customBulletinGenerate} (${selectedArticleIdsForDigest.size})"
                                } else {
                                    strings.customBulletinGenerate
                                },
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            } else {
                val queueText = if (queueArticles.isNotEmpty() && currentQueueIndex >= 0) {
                    String.format(strings.queueStoryProgress, currentQueueIndex + 1, queueArticles.size)
                } else null

                FloatingAudioPlayer(
                    playbackState = playbackState,
                    onTogglePlayPause = { (viewModel.getApplication() as com.chronicle.newsfeed.ChronicleApplication).ttsManager.togglePlayPause() },
                    onCycleSpeed = { (viewModel.getApplication() as com.chronicle.newsfeed.ChronicleApplication).ttsManager.cycleSpeed() },
                    onClose = { viewModel.stopAudio() },
                    onSkipNext = { viewModel.skipQueueNext() },
                    hasSkipNext = queueArticles.isNotEmpty() && currentQueueIndex < queueArticles.size - 1,
                    queuePositionText = queueText
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp)
            ) {
                // DAILY DIGEST & CUSTOM BULLETIN HERO BANNER
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = strings.dailyDigest,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                FilledIconButton(
                                    onClick = {
                                        showDailyDigestDialog = true
                                        if (dailyDigest == null) {
                                            viewModel.generateDailyDigest()
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Icon(imageVector = Icons.Default.Headphones, contentDescription = strings.listenArticle)
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = strings.dailyDigestDesc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Action Row: Listen Daily Digest & Custom Bulletin Toggle
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        showDailyDigestDialog = true
                                        if (dailyDigest == null) {
                                            viewModel.generateDailyDigest()
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Headphones,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = strings.dailyDigestBtn,
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }

                                OutlinedButton(
                                    onClick = { viewModel.toggleCustomBulletinMode() },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (isCustomBulletinMode) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isCustomBulletinMode) Icons.Default.ChecklistRtl else Icons.Default.Checklist,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = strings.customBulletinBtn,
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // EMPTY STATE
                if (articles.isEmpty() && !isSyncing) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.RssFeed,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = strings.emptyFeedTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = strings.emptyFeedSubtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // ARTICLES LIST
                items(articles, key = { it.id }) { article ->
                    NewsCard(
                        article = article,
                        onClick = { onArticleClick(article) },
                        onNarrate = { viewModel.narrateArticle(article) },
                        onToggleFavorite = { viewModel.toggleArticleFavorite(article) },
                        onToggleRead = { viewModel.toggleArticleRead(article) },
                        isSynthesizing = synthesizingArticleId == article.id,
                        isSelectionMode = isCustomBulletinMode,
                        isSelectedForDigest = selectedArticleIdsForDigest.contains(article.id),
                        onToggleSelectForDigest = { viewModel.toggleArticleForDigest(article.id) }
                    )
                }
            }
        }
    }

    // Sources Bottom Sheet
    if (showSourcesSheet) {
        SourcesSheet(
            sources = sources,
            onToggleSource = { id, enabled -> viewModel.toggleSource(id, enabled) },
            onDeleteSource = { id -> viewModel.deleteSource(id) },
            onAddSource = { url, category -> viewModel.addSource(url, category) },
            isAdding = isAddingSource,
            onDismiss = { showSourcesSheet = false }
        )
    }

    // Content Filters Modal Sheet (matching Chronicle web)
    if (showFilterModal) {
        FilterModalSheet(
            sources = sources,
            selectedSources = selectedSources,
            onToggleSource = { viewModel.toggleSourceFilter(it) },
            categories = categories,
            selectedCategory = selectedCategory,
            onSelectCategory = { viewModel.setCategory(it) },
            limitPerSource = limitPerSource,
            onSelectLimit = { viewModel.setLimitPerSource(it) },
            blockedTags = blockedTags,
            onAddBlockedTag = { viewModel.addBlockedTag(it) },
            onRemoveBlockedTag = { viewModel.removeBlockedTag(it) },
            onResetFilters = { viewModel.resetAllFilters() },
            onDismiss = { showFilterModal = false }
        )
    }

    // Daily Digest Modal (Automatic Top 10)
    if (showDailyDigestDialog) {
        DailyDigestDialog(
            digest = dailyDigest,
            isLoading = isGeneratingDigest,
            isPlaying = playbackState.isPlaying && playbackState.title.contains("Boletín de Noticias de Hoy", ignoreCase = true) || playbackState.title.contains("Today's News Bulletin", ignoreCase = true),
            isCustom = false,
            onDismiss = { showDailyDigestDialog = false },
            onTogglePlay = {
                if (playbackState.isPlaying) {
                    (viewModel.getApplication() as com.chronicle.newsfeed.ChronicleApplication).ttsManager.stop()
                } else {
                    viewModel.playDailyDigest()
                }
            }
        )
    }
}
