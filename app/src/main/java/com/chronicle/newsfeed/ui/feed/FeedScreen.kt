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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chronicle.newsfeed.data.model.Article
import com.chronicle.newsfeed.ui.components.CategoryFilterBar
import com.chronicle.newsfeed.ui.components.FloatingAudioPlayer
import com.chronicle.newsfeed.ui.components.NewsCard
import com.chronicle.newsfeed.ui.digest.DailyDigestDialog
import com.chronicle.newsfeed.ui.sources.SourcesSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    onArticleClick: (Article) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: FeedViewModel = viewModel()
) {
    val articles by viewModel.articles.collectAsState()
    val sources by viewModel.sources.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val selectedSources by viewModel.selectedSources.collectAsState()
    val limitPerSource by viewModel.limitPerSource.collectAsState()
    val blockedTags by viewModel.blockedTags.collectAsState()
    val showOnlyUnread by viewModel.showOnlyUnread.collectAsState()
    val showOnlyFavorites by viewModel.showOnlyFavorites.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val isAddingSource by viewModel.isAddingSource.collectAsState()
    val synthesizingArticleId by viewModel.synthesizingArticleId.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val dailyDigest by viewModel.dailyDigest.collectAsState()
    val isGeneratingDigest by viewModel.isGeneratingDigest.collectAsState()
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
                                text = "CHRONICLE",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    letterSpacing = 1.sp,
                                    fontWeight = FontWeight.Black
                                ),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Lector Editorial & Locutor de IA",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { isSearchActive = !isSearchActive }) {
                            Icon(
                                imageVector = if (isSearchActive) Icons.Default.Close else Icons.Outlined.Search,
                                contentDescription = "Buscar"
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
                            Icon(imageVector = Icons.Default.RssFeed, contentDescription = "Fuentes")
                        }

                        IconButton(onClick = onNavigateToSettings) {
                            Icon(imageVector = Icons.Default.Settings, contentDescription = "Ajustes")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )

                // Search Bar
                AnimatedVisibility(visible = isSearchActive) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text("Buscar en titulares o fuentes…") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Limpiar")
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

                // Status Bar with Counts & Filter Modal Trigger (matching Chronicle web)
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
                    onMarkAllRead = {
                        viewModel.markAllRead()
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
                        Text("Activos:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

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
                            Text("Limpiar", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        },
        bottomBar = {
            FloatingAudioPlayer(
                playbackState = playbackState,
                onTogglePlayPause = { (viewModel.getApplication() as com.chronicle.newsfeed.ChronicleApplication).ttsManager.togglePlayPause() },
                onCycleSpeed = { (viewModel.getApplication() as com.chronicle.newsfeed.ChronicleApplication).ttsManager.cycleSpeed() },
                onClose = { (viewModel.getApplication() as com.chronicle.newsfeed.ChronicleApplication).ttsManager.stop() }
            )
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
                // DAILY DIGEST HERO BANNER
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showDailyDigestDialog = true
                                if (dailyDigest == null) {
                                    viewModel.generateDailyDigest()
                                }
                            },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
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
                                        text = "Boletín de Noticias de Hoy",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Informativo radiofónico sintetizado por IA con las noticias clave de la jornada.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

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
                                Icon(imageVector = Icons.Default.Headphones, contentDescription = "Escuchar Boletín")
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
                                    text = "No hay noticias con los filtros actuales.",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Prueba a refrescar o a reiniciar los filtros.",
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
                        isSynthesizing = synthesizingArticleId == article.id
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

    // Daily Digest Modal
    if (showDailyDigestDialog) {
        DailyDigestDialog(
            digest = dailyDigest,
            isLoading = isGeneratingDigest,
            isPlaying = playbackState.isPlaying && playbackState.title == "Boletín de Noticias de Hoy",
            onDismiss = { showDailyDigestDialog = false },
            onTogglePlay = {
                if (playbackState.isPlaying && playbackState.title == "Boletín de Noticias de Hoy") {
                    (viewModel.getApplication() as com.chronicle.newsfeed.ChronicleApplication).ttsManager.stop()
                } else {
                    viewModel.playDailyDigest()
                }
            }
        )
    }
}
