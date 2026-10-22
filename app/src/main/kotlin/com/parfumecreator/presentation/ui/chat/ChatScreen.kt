package com.parfumecreator.presentation.ui.chat

import android.content.Intent
import android.net.Uri

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parfumecreator.domain.model.*
import com.parfumecreator.presentation.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateToFormulaWithRecommendation: (RecommendedFormula) -> Unit,
    onNavigateToSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var sessionToDelete by remember { mutableStateOf<ChatSession?>(null) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissErrorMessage()
        }
    }

    LaunchedEffect(uiState.messages.size, uiState.isStreaming) {
        val totalCount = uiState.messages.size + (if (uiState.isStreaming && uiState.streamingContent.isNotBlank()) 1 else 0)
        if (totalCount > 0) {
            listState.animateScrollToItem(totalCount - 1)
        }
    }

    val isImeVisible = WindowInsets.isImeVisible
    LaunchedEffect(isImeVisible) {
        if (isImeVisible) {
            val totalCount = uiState.messages.size + (if (uiState.isStreaming && uiState.streamingContent.isNotBlank()) 1 else 0)
            if (totalCount > 0) {
                listState.animateScrollToItem(totalCount - 1)
            }
        }
    }

    // Dialog: Delete Single Session
    sessionToDelete?.let { session ->
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            containerColor = PerfumeSurface,
            icon = {
                Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = PerfumeError, modifier = Modifier.size(28.dp))
            },
            title = {
                Text(
                    "Hapus Percakapan?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = PerfumeOnBackground
                )
            },
            text = {
                Text(
                    "Apakah Anda yakin ingin menghapus \"${session.title}\"? Riwayat pesan dalam sesi ini akan dihapus secara permanen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PerfumeOutline
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSession(session.id)
                        sessionToDelete = null
                    }
                ) {
                    Text("Hapus", color = PerfumeError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToDelete = null }) {
                    Text("Batal", color = PerfumeOutline)
                }
            }
        )
    }

    // Dialog: Delete All Sessions
    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            containerColor = PerfumeSurface,
            icon = {
                Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = PerfumeError, modifier = Modifier.size(28.dp))
            },
            title = {
                Text(
                    "Hapus Semua Riwayat?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = PerfumeOnBackground
                )
            },
            text = {
                Text(
                    "Semua riwayat percakapan akan dihapus secara permanen dan sesi baru akan dimulai.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PerfumeOutline
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAllSessions()
                        showDeleteAllDialog = false
                    }
                ) {
                    Text("Hapus Semua", color = PerfumeError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text("Batal", color = PerfumeOutline)
                }
            }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = PerfumeSurface,
                drawerContentColor = PerfumeOnBackground,
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .fillMaxHeight(),
                windowInsets = WindowInsets.statusBars
            ) {
                ChatHistoryDrawerContent(
                    sessions = uiState.sessions,
                    currentSessionId = uiState.currentSessionId,
                    activeModelName = uiState.activeModelName,
                    isLocalModelReady = uiState.isLocalModelReady,
                    onSelectSession = { session ->
                        viewModel.selectSession(session.id)
                        scope.launch { drawerState.close() }
                    },
                    onNewChat = {
                        viewModel.createNewSession()
                        scope.launch { drawerState.close() }
                    },
                    onDeleteSession = { session ->
                        sessionToDelete = session
                    },
                    onDeleteAllSessions = {
                        showDeleteAllDialog = true
                    }
                )
            }
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = "Buka Riwayat Percakapan",
                                tint = PerfumeOnBackground
                            )
                        }
                    },
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (uiState.isStreaming) PerfumePrimary else PerfumeSecondary)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "ParfumAI Assistant",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontFamily = FontFamily.Serif
                                )
                                Text(
                                    text = if (uiState.isLocalModelReady) {
                                        "${uiState.activeModelName} • companion lokal siap"
                                    } else {
                                        "Grounded local • web ${if (uiState.allowInternetFallback) "opt-in" else "mati"}"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = PerfumeSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.createNewSession() }) {
                            Icon(
                                Icons.Default.AddBox,
                                contentDescription = "Percakapan Baru",
                                tint = PerfumePrimary
                            )
                        }
                        if (onNavigateToSettings != null) {
                            IconButton(onClick = onNavigateToSettings) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = "Pengaturan",
                                    tint = PerfumeOutline
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = PerfumeBackground,
                        titleContentColor = PerfumeOnBackground
                    )
                )
            },
            contentWindowInsets = WindowInsets.statusBars,
            containerColor = PerfumeBackground,
            modifier = modifier
                ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .consumeWindowInsets(paddingValues)
                    .imePadding()
            ) {
                // Chat Messages List
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp)
                ) {
                    item {
                        CompanionBentoCard(
                            isLocalModelReady = uiState.isLocalModelReady,
                            activeModelName = uiState.activeModelName,
                            retrievalMode = uiState.retrievalMode,
                            activeSources = uiState.activeRagSources,
                            citations = uiState.citations,
                            retrievalReport = uiState.retrievalReport,
                            datasetReadiness = uiState.datasetReadiness,
                            modelReadiness = uiState.modelReadiness,
                            allowInternetFallback = uiState.allowInternetFallback
                        )
                    }

                    if (uiState.messages.isEmpty() && !uiState.isStreaming) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .glassCard()
                                    .padding(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.Psychology,
                                        contentDescription = null,
                                        modifier = Modifier.size(42.dp),
                                        tint = PerfumePrimary
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Teman formulasi parfum",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontFamily = FontFamily.Serif
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Tanya bahan, jenis parfum, dokumen upload, formula, atau minta saya mencari referensi internet saat sumber lokal belum cukup.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = PerfumeOutline,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    FlowRow(
                                        horizontalArrangement = Arrangement.Center,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        listOf(
                                            "apa beda EDP dan EDT",
                                            "buatkan parfum citrus 100g EDP",
                                            "jelaskan bahan dari dokumen"
                                        ).forEach { suggestion ->
                                            AssistChip(
                                                onClick = { viewModel.onInputChanged(suggestion) },
                                                label = { Text(suggestion, fontSize = 11.sp) },
                                                leadingIcon = {
                                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp))
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                items(uiState.messages) { message ->
                    ChatMessageBubble(
                        message = message,
                        onImportFormula = { rec -> onNavigateToFormulaWithRecommendation(rec) }
                    )
                }

                // Live Streaming Bubble
                if (uiState.isStreaming && uiState.streamingContent.isNotBlank()) {
                    item {
                        ChatMessageBubble(
                            message = ChatMessage(
                                sessionId = 0,
                                role = MessageRole.ASSISTANT,
                                content = uiState.streamingContent,
                                isStreaming = true
                            ),
                            onImportFormula = {}
                        )
                    }
                }
            }

            // Chat Input Field
            Surface(
                    color = PerfumeSurface,
                    tonalElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        HorizontalDivider(color = PerfumeSurfaceVariant, thickness = 1.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = uiState.currentInput,
                                onValueChange = { viewModel.onInputChanged(it) },
                                modifier = Modifier.weight(1f),
                                placeholder = {
                                    Text(
                                        "Tanya parfum, dokumen, formula, atau cari internet...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = PerfumeOutline
                                    )
                                },
                                shape = RoundedCornerShape(24.dp),
                                maxLines = 4,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Sentences,
                                    imeAction = ImeAction.Send
                                ),
                                keyboardActions = KeyboardActions(
                                    onSend = {
                                        if (uiState.currentInput.isNotBlank() && !uiState.isStreaming) {
                                            viewModel.sendMessage()
                                        }
                                    }
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PerfumePrimary,
                                    unfocusedBorderColor = PerfumeOutline.copy(alpha = 0.4f),
                                    focusedContainerColor = PerfumeBackground,
                                    unfocusedContainerColor = PerfumeBackground
                                ),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = PerfumeOnBackground)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = { viewModel.sendMessage() },
                                enabled = uiState.currentInput.isNotBlank() && !uiState.isStreaming,
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (uiState.currentInput.isNotBlank() && !uiState.isStreaming)
                                            PerfumePrimary
                                        else
                                            PerfumeSurfaceVariant
                                    )
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send",
                                    tint = if (uiState.currentInput.isNotBlank() && !uiState.isStreaming)
                                        PerfumeOnPrimary
                                    else
                                        PerfumeOutline,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompanionBentoCard(
    isLocalModelReady: Boolean,
    activeModelName: String,
    retrievalMode: CompanionRetrievalMode,
    activeSources: List<String>,
    citations: List<SourceCitation>,
    retrievalReport: RetrievalReport,
    datasetReadiness: DatasetReadiness,
    modelReadiness: ModelReadiness,
    allowInternetFallback: Boolean
) {
    val retrievalBadge = when (retrievalMode) {
        CompanionRetrievalMode.LOCAL_RAG -> "RAG"
        CompanionRetrievalMode.LOCAL_AND_WEB -> "HYBRID"
        CompanionRetrievalMode.WEB_FALLBACK -> "WEB"
    }
    val retrievalBadgeColor = if (retrievalMode == CompanionRetrievalMode.WEB_FALLBACK) {
        PerfumeSecondaryContainer
    } else {
        PerfumePrimaryContainer
    }
    val retrievalBadgeContentColor = if (retrievalMode == CompanionRetrievalMode.WEB_FALLBACK) {
        PerfumeOnSecondaryContainer
    } else {
        PerfumeOnPrimaryContainer
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(cornerRadius = 18)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(PerfumePrimaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Psychology, contentDescription = null, tint = PerfumePrimary, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("AI companion parfum", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Local-first • ${retrievalMode.label}", style = MaterialTheme.typography.bodySmall, color = PerfumeSecondary)
                }
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = retrievalBadgeColor
            ) {
                Text(
                    text = retrievalBadge,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = retrievalBadgeContentColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            CompanionBentoTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Description,
                title = "Dokumen",
                value = if (citations.any { it.sourceType != SourceType.WEB }) {
                    "${citations.count { it.sourceType != SourceType.WEB }} sumber"
                } else if (activeSources.any { !it.startsWith("Internet • ") }) {
                    "Terhubung"
                } else {
                    "Siap upload"
                }
            )
            CompanionBentoTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Public,
                title = "Internet",
                value = if (retrievalMode == CompanionRetrievalMode.WEB_FALLBACK || retrievalMode == CompanionRetrievalMode.LOCAL_AND_WEB) "Dipakai" else "Fallback"
            )
            CompanionBentoTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Memory,
                title = "Inference",
                value = if (isLocalModelReady) activeModelName else "Grounded"
            )
        }
        Text(
            text = "Dataset: ${datasetReadiness.label()} • Model: ${modelReadiness.label()} • Web: ${if (allowInternetFallback) "opt-in" else "mati"}",
            style = MaterialTheme.typography.labelSmall,
            color = PerfumeOutline
        )
        retrievalReport.webError?.let { error ->
            Text(
                text = "WEB_UNAVAILABLE / warning: $error",
                style = MaterialTheme.typography.labelSmall,
                color = PerfumeError
            )
        }
        Text(
            text = "Dokumen upload tetap lokal • hanya query singkat dikirim saat fallback",
            style = MaterialTheme.typography.labelSmall,
            color = PerfumeOutline
        )
    }
}

@Composable
private fun CompanionBentoTile(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String
) {
    Surface(
        modifier = modifier.heightIn(min = 68.dp),
        shape = RoundedCornerShape(12.dp),
        color = PerfumeSurfaceVariant.copy(alpha = 0.82f)
    ) {
        Column(modifier = Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, contentDescription = null, tint = PerfumeSecondary, modifier = Modifier.size(17.dp))
            Text(title, style = MaterialTheme.typography.labelSmall, color = PerfumeOutline)
            Text(value, style = MaterialTheme.typography.labelMedium, color = PerfumeOnBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun DatasetReadiness.label(): String = when (this) {
    DatasetReadiness.SEEDING -> "seeding"
    DatasetReadiness.READY -> "ready"
    DatasetReadiness.FAILED -> "failed"
    DatasetReadiness.EMPTY -> "empty"
}

private fun ModelReadiness.label(): String = when (this) {
    ModelReadiness.SELECTED -> "selected"
    ModelReadiness.DOWNLOADING -> "downloading"
    ModelReadiness.VERIFYING -> "verifying"
    ModelReadiness.READY -> "ready"
    ModelReadiness.UNAVAILABLE -> "unavailable"
    ModelReadiness.FAILED -> "failed"
}

@Composable
private fun ChatMessageBubble(
    message: ChatMessage,
    onImportFormula: (RecommendedFormula) -> Unit
) {
    val context = LocalContext.current
    val isUser = message.role == MessageRole.USER
    val isWarningOrNotice = message.content.startsWith("⚠️") || message.content.contains("Model AI On-Device belum diunduh", ignoreCase = true)
    val parsedFormula = remember(message.content) {
        if (isUser) null else com.parfumecreator.core.llm.StructuredFormulaParser.parseJsonFormula(message.content)
    }

    if (isWarningOrNotice) {
        // Render system notice as full-width banner card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .glassCard(cornerRadius = 14)
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PerfumeOnBackground,
                    lineHeight = 20.sp
                )
            }
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = if (isUser) PerfumePrimaryContainer else PerfumeSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth(if (isUser) 0.82f else 0.90f)
                .widthIn(max = 580.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                FormattedAiContent(
                    text = message.content,
                    isUser = isUser
                )

                // Structured citation cards. Legacy source strings remain
                // readable for messages created before the Room migration.
                if (message.citations.isNotEmpty() || message.ragSources.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    androidx.compose.foundation.layout.FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (message.citations.isNotEmpty()) {
                            message.citations.forEach { citation ->
                                CitationChip(citation = citation) {
                                    openCitation(context, citation)
                                }
                            }
                        } else {
                            message.ragSources.forEach { source ->
                                val isInternetSource = source.startsWith("Internet • ")
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isInternetSource) PerfumePrimaryContainer else PerfumeSecondaryContainer
                                ) {
                                    Text(
                                        text = if (isInternetSource) "🌐 $source" else "📄 $source",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isInternetSource) PerfumeOnPrimaryContainer else PerfumeOnSecondaryContainer,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Formula Recommendation Card & 1-Click Import
        parsedFormula?.let { formula ->
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .widthIn(max = 560.dp)
                    .glassCard(cornerRadius = 14)
                    .padding(16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "✨ Rekomendasi Formula",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = PerfumePrimary
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = PerfumePrimaryContainer
                        ) {
                            Text(
                                text = formula.concentration ?: "EDP",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = PerfumeOnPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formula.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = FontFamily.Serif
                    )
                    formula.totalWeightG?.let { total ->
                        Text(
                            text = "Total formula tervalidasi: ${"%.4f".format(Locale.US, total)} g",
                            style = MaterialTheme.typography.bodySmall,
                            color = PerfumeSecondary
                        )
                    }
                    formula.predictedSpl?.let { spl ->
                        Text(
                            text = "SPL indeks: ${"%.1f".format(Locale.US, spl)} / 100 — bukan hasil uji fisik",
                            style = MaterialTheme.typography.bodySmall,
                            color = PerfumeOutline
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    formula.ingredients.forEach { ing ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "• ${ing.ingredientName}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = if (ing.resolvedIngredientId != null && ing.resolvedIngredientId > 0L) {
                                        "ID katalog: ${ing.resolvedIngredientId}"
                                    } else {
                                        "ID katalog: unresolved"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (ing.resolvedIngredientId != null && ing.resolvedIngredientId > 0L) {
                                        PerfumeSecondary
                                    } else {
                                        PerfumeError
                                    }
                                )
                            }
                            Text(
                                text = "${ing.amount} ${ing.unit}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = PerfumePrimary,
                                fontSize = 13.sp
                            )
                        }
                    }
                    val unresolved = formula.ingredients.filter {
                        it.resolvedIngredientId == null || it.resolvedIngredientId <= 0L
                    }
                    if (unresolved.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "⚠️ ${unresolved.size} bahan belum memiliki ID katalog nyata. Formula tidak dapat diimpor otomatis.",
                            style = MaterialTheme.typography.bodySmall,
                            color = PerfumeError
                        )
                    }
                    Text(
                        text = "⚠️ Verifikasi SDS/IFRA sesuai kategori produk dan lakukan uji stabilitas sebelum digunakan pada kulit.",
                        style = MaterialTheme.typography.labelSmall,
                        color = PerfumeOutline
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { onImportFormula(formula) },
                        enabled = unresolved.isEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PerfumePrimary,
                                disabledContainerColor = PerfumeSurfaceVariant
                            )
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            tint = PerfumeOnPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Import ke Formula Creator",
                            color = PerfumeOnPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CitationChip(citation: SourceCitation, onClick: () -> Unit) {
    val target = citation.url ?: citation.uri
    val isClickable = !target.isNullOrBlank() &&
        (target.startsWith("https://") || target.startsWith("content://"))
    val metadata = remember(citation) {
        buildList {
            add(citation.trustLevel.name)
            citation.pageStart?.let { start ->
                add(if (citation.pageEnd != null && citation.pageEnd != start) {
                    "hal. $start-${citation.pageEnd}"
                } else {
                    "hal. $start"
                })
            }
            citation.contentHash?.takeIf { it.length >= 12 }?.let { add("sha ${it.take(12)}…") }
            add(SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date(citation.retrievedAt)))
        }.joinToString(" • ")
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (citation.sourceType == SourceType.WEB) {
            PerfumePrimaryContainer
        } else {
            PerfumeSecondaryContainer
        },
        modifier = (if (isClickable) Modifier.clickable(onClick = onClick) else Modifier)
            .defaultMinSize(minHeight = 48.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)) {
            Text(
                text = "${if (citation.sourceType == SourceType.WEB) "🌐" else "📄"} ${citation.title}",
                style = MaterialTheme.typography.labelSmall,
                color = if (citation.sourceType == SourceType.WEB) PerfumeOnPrimaryContainer else PerfumeOnSecondaryContainer,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "$metadata • ${citation.excerpt.take(70)}",
                style = MaterialTheme.typography.labelSmall,
                color = PerfumeOutline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun openCitation(context: android.content.Context, citation: SourceCitation) {
    val target = citation.url ?: citation.uri ?: return
    if (!target.startsWith("https://") && !target.startsWith("content://")) return
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(target)).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        )
    }
}

@Composable
fun FormattedAiContent(
    text: String,
    modifier: Modifier = Modifier,
    isUser: Boolean = false
) {
    val cleanText = remember(text) {
        if (isUser) text else com.parfumecreator.core.llm.ChatTextFormatting.visibleText(text)
    }

    val blocks = remember(cleanText) {
        parseMarkdownBlocks(cleanText)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is Block.Heading -> {
                    Text(
                        text = buildAnnotatedStringFromMarkdown(block.text, isUser),
                        style = if (block.level <= 2) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif,
                        color = if (isUser) PerfumeOnPrimaryContainer else PerfumePrimary,
                        lineHeight = 22.sp
                    )
                }
                is Block.NumberedItem -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "${block.number}. ",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isUser) PerfumeOnPrimaryContainer else PerfumePrimary,
                            lineHeight = 22.sp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = buildAnnotatedStringFromMarkdown(block.text, isUser),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isUser) PerfumeOnPrimaryContainer else PerfumeOnBackground,
                            lineHeight = 22.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                is Block.BulletItem -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "• ",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isUser) PerfumeOnPrimaryContainer else PerfumeSecondary,
                            lineHeight = 22.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = buildAnnotatedStringFromMarkdown(block.text, isUser),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isUser) PerfumeOnPrimaryContainer else PerfumeOnBackground,
                            lineHeight = 22.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                is Block.Paragraph -> {
                    Text(
                        text = buildAnnotatedStringFromMarkdown(block.text, isUser),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isUser) PerfumeOnPrimaryContainer else PerfumeOnBackground,
                        lineHeight = 22.sp
                    )
                }
            }
        }
    }
}

private sealed class Block {
    data class Heading(val level: Int, val text: String) : Block()
    data class NumberedItem(val number: String, val text: String) : Block()
    data class BulletItem(val text: String) : Block()
    data class Paragraph(val text: String) : Block()
}

private fun parseMarkdownBlocks(text: String): List<Block> {
    val lines = text.lines()
    val blocks = mutableListOf<Block>()
    val paragraphBuffer = mutableListOf<String>()

    fun flushParagraph() {
        if (paragraphBuffer.isNotEmpty()) {
            val content = paragraphBuffer.joinToString(" ").trim()
            if (content.isNotEmpty()) {
                blocks.add(Block.Paragraph(content))
            }
            paragraphBuffer.clear()
        }
    }

    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) {
            flushParagraph()
            continue
        }

        // Headings
        val headingMatch = Regex("""^(#{1,4})\s+(.*)$""").find(trimmed)
        if (headingMatch != null) {
            flushParagraph()
            val level = headingMatch.groupValues[1].length
            val headingText = headingMatch.groupValues[2]
            blocks.add(Block.Heading(level, headingText))
            continue
        }

        // Numbered list: "1. text" or "1) text"
        val numberedMatch = Regex("""^(\d+)[.)]\s+(.*)$""").find(trimmed)
        if (numberedMatch != null) {
            flushParagraph()
            val num = numberedMatch.groupValues[1]
            val itemText = numberedMatch.groupValues[2]
            blocks.add(Block.NumberedItem(num, itemText))
            continue
        }

        // Bullet list: "- text" or "• text" or "* text" (only if not an italic whole-sentence)
        val bulletMatch = Regex("""^(?:[\-•]|\*(?!\*))\s+(.*)$""").find(trimmed)
        val isEntireItalic = trimmed.startsWith("*") && trimmed.endsWith("*") && !trimmed.startsWith("**") && trimmed.count { it == '*' } == 2
        if (bulletMatch != null && !isEntireItalic) {
            flushParagraph()
            val itemText = bulletMatch.groupValues[1]
            blocks.add(Block.BulletItem(itemText))
            continue
        }

        // Normal line in paragraph
        paragraphBuffer.add(trimmed)
    }

    flushParagraph()
    return blocks
}

private fun buildAnnotatedStringFromMarkdown(input: String, isUser: Boolean): AnnotatedString {
    return buildAnnotatedString {
        val tokenRegex = Regex("""(\*\*.*?\*\*|\*[^*]+?\*|`.*?`|[^\*\`]+)""")
        val matches = tokenRegex.findAll(input)

        for (match in matches) {
            val token = match.value
            when {
                token.startsWith("**") && token.endsWith("**") && token.length >= 4 -> {
                    val inner = token.substring(2, token.length - 2)
                    withStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Bold,
                            color = if (isUser) PerfumeOnPrimaryContainer else PerfumePrimary
                        )
                    ) {
                        append(inner)
                    }
                }
                token.startsWith("*") && token.endsWith("*") && token.length >= 2 -> {
                    val inner = token.substring(1, token.length - 1)
                    withStyle(
                        SpanStyle(
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Normal
                        )
                    ) {
                        append(inner)
                    }
                }
                token.startsWith("`") && token.endsWith("`") && token.length >= 2 -> {
                    val inner = token.substring(1, token.length - 1)
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            background = PerfumeBackground.copy(alpha = 0.5f)
                        )
                    ) {
                        append(" $inner ")
                    }
                }
                else -> {
                    append(token)
                }
            }
        }
    }
}

@Composable
fun ChatHistoryDrawerContent(
    sessions: List<ChatSession>,
    currentSessionId: Long?,
    activeModelName: String,
    isLocalModelReady: Boolean,
    onSelectSession: (ChatSession) -> Unit,
    onNewChat: () -> Unit,
    onDeleteSession: (ChatSession) -> Unit,
    onDeleteAllSessions: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PerfumeSurface)
            .padding(vertical = 16.dp, horizontal = 14.dp)
    ) {
        // 1. App / Drawer Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(PerfumePrimaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Psychology,
                    contentDescription = null,
                    tint = PerfumePrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Riwayat Chat",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    color = PerfumeOnBackground
                )
                Text(
                    text = "ParfumAI Assistant",
                    style = MaterialTheme.typography.bodySmall,
                    color = PerfumeOutline,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. "+ Chat Baru" Action Button (ChatGPT / Gemini style)
        Button(
            onClick = onNewChat,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PerfumePrimary,
                contentColor = PerfumeOnPrimary
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = PerfumeOnPrimary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Percakapan Baru",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = PerfumeOnPrimary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Section Label with session count
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SESI PERCAKAPAN",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = PerfumeOutline,
                letterSpacing = 1.sp
            )
            Text(
                text = "${sessions.size}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = PerfumeSecondary
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 4. Sessions List (Scrollable)
        if (sessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.ChatBubbleOutline,
                        contentDescription = null,
                        tint = PerfumeOutline.copy(alpha = 0.5f),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Belum ada riwayat",
                        style = MaterialTheme.typography.bodySmall,
                        color = PerfumeOutline
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(sessions, key = { it.id }) { session ->
                    val isSelected = session.id == currentSessionId
                    DrawerSessionItem(
                        session = session,
                        isSelected = isSelected,
                        onClick = { onSelectSession(session) },
                        onDelete = { onDeleteSession(session) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = PerfumeSurfaceVariant, thickness = 1.dp)
        Spacer(modifier = Modifier.height(10.dp))

        // 5. Footer Actions: Delete All + Model Info
        if (sessions.isNotEmpty()) {
            OutlinedButton(
                onClick = onDeleteAllSessions,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = PerfumeError
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    PerfumeError.copy(alpha = 0.4f)
                )
            ) {
                Icon(
                    Icons.Default.DeleteSweep,
                    contentDescription = null,
                    tint = PerfumeError,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Hapus Semua Riwayat",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = PerfumeError
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        // Bottom model status
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(PerfumeSecondary)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isLocalModelReady) {
                    "$activeModelName • On-Device AI"
                } else {
                    "Dataset formula • LLM lokal belum tersedia"
                },
                style = MaterialTheme.typography.labelSmall,
                color = PerfumeOutline,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun DrawerSessionItem(
    session: ChatSession,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) PerfumePrimaryContainer.copy(alpha = 0.85f) else PerfumeSurfaceVariant.copy(alpha = 0.45f),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, PerfumePrimary.copy(alpha = 0.6f)) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isSelected) Icons.AutoMirrored.Filled.Chat else Icons.Default.ChatBubbleOutline,
                contentDescription = null,
                tint = if (isSelected) PerfumePrimary else PerfumeOutline,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) PerfumeOnPrimaryContainer else PerfumeOnBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatSessionTime(session.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) PerfumePrimary else PerfumeOutline,
                    fontSize = 11.sp
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = "Hapus Sesi",
                    tint = if (isSelected) PerfumeOnPrimaryContainer.copy(alpha = 0.7f) else PerfumeOutline.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private fun formatSessionTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val oneDay = 24 * 60 * 60 * 1000L
    val sdfTime = SimpleDateFormat("HH:mm", Locale.getDefault())
    val sdfDate = SimpleDateFormat("dd MMM", Locale.getDefault())

    return when {
        diff < oneDay && SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(now) == SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(timestamp) -> {
            "Hari ini, ${sdfTime.format(Date(timestamp))}"
        }
        diff < 2 * oneDay -> {
            "Kemarin, ${sdfTime.format(Date(timestamp))}"
        }
        else -> {
            sdfDate.format(Date(timestamp))
        }
    }
}
