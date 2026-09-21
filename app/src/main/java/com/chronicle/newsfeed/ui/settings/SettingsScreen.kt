package com.chronicle.newsfeed.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chronicle.newsfeed.data.model.PromptSettings
import com.chronicle.newsfeed.data.model.TtsEngineType
import com.chronicle.newsfeed.domain.llm.LlmStatus
import com.chronicle.newsfeed.domain.llm.NewsPrompts
import com.chronicle.newsfeed.domain.tts.PiperVoiceInfo
import com.chronicle.newsfeed.domain.tts.getVoicesForLanguage
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val llmSettings by viewModel.llmSettings.collectAsState()
    val ttsSettings by viewModel.ttsSettings.collectAsState()
    val promptSettings by viewModel.promptSettings.collectAsState()
    val llmStatus = viewModel.getLlmStatus()

    val isImportingModel by viewModel.isImportingModel.collectAsState()
    val importProgress by viewModel.importProgress.collectAsState()

    val downloadingVoiceId by viewModel.downloadingVoiceId.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val downloadStatusMessage by viewModel.downloadStatusMessage.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Modelo & IA", "Prompts", "Locución & TTS")
    val tabIcons = listOf(Icons.Default.Memory, Icons.Default.EditNote, Icons.Default.RecordVoiceOver)

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    val modelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importModelFromUri(it) }
    }

    val voicePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importPiperVoiceFromUri(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
                TopAppBar(
                    title = {
                        Text(
                            "Configuración",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )

                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = {
                                Text(
                                    title,
                                    fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp
                                )
                            },
                            icon = {
                                Icon(
                                    imageVector = tabIcons[index],
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTabIndex) {
                0 -> ModelAiTab(
                    llmSettings = llmSettings,
                    llmStatus = llmStatus,
                    isImportingModel = isImportingModel,
                    importProgress = importProgress,
                    onSelectModel = { modelPickerLauncher.launch(arrayOf("*/*")) },
                    onLanguageChange = { viewModel.setLanguage(it) },
                    onTemperatureChange = { viewModel.updateLlmSettings(llmSettings.copy(temperature = it)) },
                    onTokensChange = { viewModel.updateLlmSettings(llmSettings.copy(maxTokens = it)) }
                )
                1 -> SystemPromptsTab(
                    promptSettings = promptSettings,
                    currentLanguage = llmSettings.language,
                    onSavePrompts = { viewModel.updatePromptSettings(it) },
                    onResetPrompts = { viewModel.resetPromptSettings() }
                )
                2 -> TtsVoiceTab(
                    ttsSettings = ttsSettings,
                    downloadingVoiceId = downloadingVoiceId,
                    downloadProgress = downloadProgress,
                    downloadStatusMessage = downloadStatusMessage,
                    isVoiceDownloaded = { viewModel.piperDownloader.isVoiceDownloaded(it) },
                    onDownloadVoice = { viewModel.downloadPiperVoice(it) },
                    onImportCustomVoice = { voicePickerLauncher.launch(arrayOf("*/*")) },
                    onUpdateTts = { viewModel.updateTtsSettings(it) },
                    onVoiceRegionChange = { viewModel.setVoiceRegion(it) },
                    onTestVoice = { viewModel.testTtsVoice(it) }
                )
            }
        }
    }
}

// ==========================================
// PESTAÑA 1: MODELO & IA (GEMMA ON-DEVICE)
// ==========================================
@Composable
private fun ModelAiTab(
    llmSettings: com.chronicle.newsfeed.data.model.LlmSettings,
    llmStatus: LlmStatus,
    isImportingModel: Boolean,
    importProgress: Float,
    onSelectModel: () -> Unit,
    onLanguageChange: (String) -> Unit,
    onTemperatureChange: (Float) -> Unit,
    onTokensChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Motor On-Device: Google Gemma 4 E2B",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Status Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when (llmStatus) {
                        is LlmStatus.Ready -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        is LlmStatus.Loading -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                        is LlmStatus.Error -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                        LlmStatus.Uninitialized -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val icon = when (llmStatus) {
                            is LlmStatus.Ready -> Icons.Default.CheckCircle
                            is LlmStatus.Loading -> Icons.Default.HourglassTop
                            is LlmStatus.Error -> Icons.Default.Error
                            LlmStatus.Uninitialized -> Icons.Default.Info
                        }
                        val color = when (llmStatus) {
                            is LlmStatus.Ready -> MaterialTheme.colorScheme.primary
                            is LlmStatus.Loading -> MaterialTheme.colorScheme.tertiary
                            is LlmStatus.Error -> MaterialTheme.colorScheme.error
                            LlmStatus.Uninitialized -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                        Text(
                            text = when (llmStatus) {
                                is LlmStatus.Ready -> "Modelo cargado y listo (GPU LiteRT)"
                                is LlmStatus.Loading -> "Inicializando motor en GPU..."
                                is LlmStatus.Error -> "Error: ${llmStatus.message}"
                                LlmStatus.Uninitialized -> "Sin modelo cargado. Selecciona un archivo abajo."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = color
                        )
                    }
                }

                // Current file
                if (llmSettings.modelPath.isNotBlank()) {
                    val file = File(llmSettings.modelPath)
                    Text(
                        text = "Archivo activo: ${file.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Button to select file
                Button(
                    onClick = onSelectModel,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isImportingModel
                ) {
                    Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (llmSettings.modelPath.isBlank()) "Buscar archivo de modelo (.litertlm)" else "Cambiar archivo de modelo")
                }

                // Import progress
                if (isImportingModel) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Importando modelo al almacenamiento privado (${(importProgress * 100).toInt()}%)...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        LinearProgressIndicator(
                            progress = { importProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // Parameters Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Parámetros de Inferencia",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // Language
                Text("Idioma base de generación", style = MaterialTheme.typography.bodyMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilterChip(
                        selected = llmSettings.language == "es",
                        onClick = { onLanguageChange("es") },
                        label = { Text("Español 🇪🇸 / 🇦🇷") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = llmSettings.language == "en",
                        onClick = { onLanguageChange("en") },
                        label = { Text("English 🇺🇸 / 🇬🇧") },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Temperature
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Temperatura", style = MaterialTheme.typography.bodyMedium)
                    Text("${"%.1f".format(llmSettings.temperature)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = llmSettings.temperature,
                    onValueChange = onTemperatureChange,
                    valueRange = 0.1f..1.0f,
                    steps = 8
                )

                // Max Tokens
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Límite de Tokens", style = MaterialTheme.typography.bodyMedium)
                    Text("${llmSettings.maxTokens}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = llmSettings.maxTokens.toFloat(),
                    onValueChange = { onTokensChange(it.toInt()) },
                    valueRange = 128f..1024f,
                    steps = 6
                )
            }
        }
    }
}

// ==========================================
// PESTAÑA 2: PROMPTS DEL SISTEMA EDITABLES
// ==========================================
@Composable
private fun SystemPromptsTab(
    promptSettings: PromptSettings,
    currentLanguage: String,
    onSavePrompts: (PromptSettings) -> Unit,
    onResetPrompts: () -> Unit
) {
    var selectedPromptType by remember { mutableIntStateOf(0) } // 0 = Resumen de Noticia, 1 = Boletín Diario
    var isEditingEnglish by remember(currentLanguage) { mutableStateOf(currentLanguage == "en") }

    var articlePromptEs by remember(promptSettings) {
        mutableStateOf(promptSettings.articleSummaryPromptEs.ifBlank { NewsPrompts.DEFAULT_ARTICLE_SUMMARY_ES })
    }
    var articlePromptEn by remember(promptSettings) {
        mutableStateOf(promptSettings.articleSummaryPromptEn.ifBlank { NewsPrompts.DEFAULT_ARTICLE_SUMMARY_EN })
    }
    var digestPromptEs by remember(promptSettings) {
        mutableStateOf(promptSettings.dailyDigestPromptEs.ifBlank { NewsPrompts.DEFAULT_DAILY_DIGEST_ES })
    }
    var digestPromptEn by remember(promptSettings) {
        mutableStateOf(promptSettings.dailyDigestPromptEn.ifBlank { NewsPrompts.DEFAULT_DAILY_DIGEST_EN })
    }

    val currentPromptText = when {
        selectedPromptType == 0 && !isEditingEnglish -> articlePromptEs
        selectedPromptType == 0 && isEditingEnglish -> articlePromptEn
        selectedPromptType == 1 && !isEditingEnglish -> digestPromptEs
        else -> digestPromptEn
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Selector of Prompt Type
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedPromptType == 0,
                onClick = { selectedPromptType = 0 },
                label = { Text("Resumen de Noticia", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = selectedPromptType == 1,
                onClick = { selectedPromptType = 1 },
                label = { Text("Boletín Diario", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Podcasts, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.weight(1f)
            )
        }

        // Language toggle for prompts
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Idioma del Prompt:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = !isEditingEnglish,
                    onClick = { isEditingEnglish = false },
                    label = { Text("Español 🇪🇸/🇦🇷") }
                )
                FilterChip(
                    selected = isEditingEnglish,
                    onClick = { isEditingEnglish = true },
                    label = { Text("English 🇺🇸/🇬🇧") }
                )
            }
        }

        // Variable Hints Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Variables dinámicas que sustituirá Gemma:",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (selectedPromptType == 0) {
                    Text("• {title} : Titular de la noticia", style = MaterialTheme.typography.bodySmall)
                    Text("• {content} : Contenido o descripción del artículo", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("• {articles} : Lista numerada de las noticias destacadas de la jornada", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Prompt Editor
        OutlinedTextField(
            value = currentPromptText,
            onValueChange = { newValue ->
                when {
                    selectedPromptType == 0 && !isEditingEnglish -> articlePromptEs = newValue
                    selectedPromptType == 0 && isEditingEnglish -> articlePromptEn = newValue
                    selectedPromptType == 1 && !isEditingEnglish -> digestPromptEs = newValue
                    else -> digestPromptEn = newValue
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 280.dp, max = 450.dp),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            )
        )

        // Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = {
                    when {
                        selectedPromptType == 0 && !isEditingEnglish -> articlePromptEs = NewsPrompts.DEFAULT_ARTICLE_SUMMARY_ES
                        selectedPromptType == 0 && isEditingEnglish -> articlePromptEn = NewsPrompts.DEFAULT_ARTICLE_SUMMARY_EN
                        selectedPromptType == 1 && !isEditingEnglish -> digestPromptEs = NewsPrompts.DEFAULT_DAILY_DIGEST_ES
                        else -> digestPromptEn = NewsPrompts.DEFAULT_DAILY_DIGEST_EN
                    }
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(imageVector = Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Por Defecto", fontSize = 12.sp)
            }

            Button(
                onClick = {
                    onSavePrompts(
                        PromptSettings(
                            articleSummaryPromptEs = articlePromptEs,
                            articleSummaryPromptEn = articlePromptEn,
                            dailyDigestPromptEs = digestPromptEs,
                            dailyDigestPromptEn = digestPromptEn
                        )
                    )
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Guardar", fontSize = 12.sp)
            }
        }
    }
}

// ==========================================
// PESTAÑA 3: LOCUTOR & TTS (PIPER / SISTEMA)
// ==========================================
@Composable
private fun TtsVoiceTab(
    ttsSettings: com.chronicle.newsfeed.data.model.TtsSettings,
    downloadingVoiceId: String?,
    downloadProgress: Float,
    downloadStatusMessage: String,
    isVoiceDownloaded: (String) -> Boolean,
    onDownloadVoice: (PiperVoiceInfo) -> Unit,
    onImportCustomVoice: () -> Unit,
    onUpdateTts: (com.chronicle.newsfeed.data.model.TtsSettings) -> Unit,
    onVoiceRegionChange: (String) -> Unit,
    onTestVoice: (String) -> Unit
) {
    val isEnglish = ttsSettings.language == "en"
    val availableVoices = remember(ttsSettings.language) {
        getVoicesForLanguage(ttsSettings.language)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Engine Selection Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Motor de Síntesis de Voz",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilterChip(
                        selected = ttsSettings.engineType == TtsEngineType.PIPER_ONNX,
                        onClick = { onUpdateTts(ttsSettings.copy(engineType = TtsEngineType.PIPER_ONNX)) },
                        label = { Text("Piper TTS (Neural)") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = ttsSettings.engineType == TtsEngineType.SYSTEM_TTS,
                        onClick = { onUpdateTts(ttsSettings.copy(engineType = TtsEngineType.SYSTEM_TTS)) },
                        label = { Text("Sistema Android") },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Regional Dialect Selector adapted to the language
                Text(
                    text = if (isEnglish) "Acento / Dialecto en Inglés" else "Acento / Dialecto en Español",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isEnglish) {
                        FilterChip(
                            selected = ttsSettings.voiceRegion == "US",
                            onClick = { onVoiceRegionChange("US") },
                            label = { Text("Estados Unidos 🇺🇸") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = ttsSettings.voiceRegion == "GB" || ttsSettings.voiceRegion == "UK",
                            onClick = { onVoiceRegionChange("GB") },
                            label = { Text("Reino Unido 🇬🇧") },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        FilterChip(
                            selected = ttsSettings.voiceRegion == "AR",
                            onClick = { onVoiceRegionChange("AR") },
                            label = { Text("Argentina 🇦🇷") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = ttsSettings.voiceRegion == "ES",
                            onClick = { onVoiceRegionChange("ES") },
                            label = { Text("España 🇪🇸") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Piper Voices Catalog (if Piper is selected)
        if (ttsSettings.engineType == TtsEngineType.PIPER_ONNX) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Voces de Piper (${if (isEnglish) "Inglés" else "Español"})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = onImportCustomVoice) {
                            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Importar .onnx", fontSize = 12.sp)
                        }
                    }

                    availableVoices.forEach { voice ->
                        val isDownloaded = isVoiceDownloaded(voice.id)
                        val isSelected = ttsSettings.selectedVoiceId == voice.id
                        val isDownloading = downloadingVoiceId == voice.id

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isDownloaded) {
                                        onUpdateTts(ttsSettings.copy(selectedVoiceId = voice.id))
                                    }
                                }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(voice.flag, fontSize = 22.sp)
                                        Column {
                                            Text(
                                                text = voice.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = if (isSelected) "Voz activa" else if (isDownloaded) "Descargada y lista" else "No descargada",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    if (isDownloading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.5.dp
                                        )
                                    } else if (!isDownloaded) {
                                        IconButton(onClick = { onDownloadVoice(voice) }) {
                                            Icon(
                                                imageVector = Icons.Default.Download,
                                                contentDescription = "Descargar voz",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    } else if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                if (isDownloading) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = { downloadProgress },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Text(
                                        text = downloadStatusMessage,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Voice Controls & Test Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Ajustes de Reproducción",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // Speech Rate
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Velocidad de habla", style = MaterialTheme.typography.bodyMedium)
                    Text("${"%.2f".format(ttsSettings.speechRate)}x", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = ttsSettings.speechRate,
                    onValueChange = { onUpdateTts(ttsSettings.copy(speechRate = it)) },
                    valueRange = 0.75f..1.75f,
                    steps = 8
                )

                // Pitch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Tono de voz", style = MaterialTheme.typography.bodyMedium)
                    Text("${"%.2f".format(ttsSettings.pitch)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = ttsSettings.pitch,
                    onValueChange = { onUpdateTts(ttsSettings.copy(pitch = it)) },
                    valueRange = 0.8f..1.3f,
                    steps = 5
                )

                // Test Voice Button
                Button(
                    onClick = {
                        val testText = if (isEnglish) {
                            if (ttsSettings.voiceRegion == "GB") {
                                "Welcome to Chronicle. This is a voice test of our British English narrator."
                            } else {
                                "Welcome to Chronicle. This is a voice test of our American English narrator."
                            }
                        } else {
                            if (ttsSettings.voiceRegion == "AR" || ttsSettings.selectedVoiceId.contains("daniela")) {
                                "Hola, bienvenidos a Chronicle. Esta es una prueba de la locución con acento argentino para tus noticias."
                            } else {
                                "Hola, bienvenidos a Chronicle. Esta es una prueba de la locución en español para tus noticias."
                            }
                        }
                        onTestVoice(testText)
                    },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Probar Voz")
                }
            }
        }
    }
}
