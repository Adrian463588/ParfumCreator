package com.parfumecreator.presentation.ui.download

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parfumecreator.domain.model.DownloadBundle
import com.parfumecreator.domain.model.DownloadStatus
import com.parfumecreator.presentation.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(
    viewModel: DownloadViewModel,
    onNavigateToSettings: (() -> Unit)? = null,
    onNavigateToChat: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            val persisted = try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                context.contentResolver.persistedUriPermissions.any { permission ->
                    permission.uri == it && permission.isReadPermission && permission.isWritePermission
                }
            } catch (e: Exception) {
                Log.w("DownloadScreen", "SAF permission could not be persisted", e)
                false
            }
            if (persisted) {
                viewModel.setSafFolder(it)
            } else {
                viewModel.rejectSafFolder()
            }
        }
    }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissUserMessage()
        }
    }

    val llmBundles = remember(uiState.bundles) {
        uiState.bundles.filter { it.id.startsWith("llm_") }
    }
    val embeddingBundles = remember(uiState.bundles) {
        uiState.bundles.filter { !it.id.startsWith("llm_") }
    }

    val recommendedBundle = llmBundles.find { it.id == "llm_gemma_2b" } ?: llmBundles.firstOrNull()
    val hasAnyCompletedLlm = llmBundles.any { it.status == DownloadStatus.COMPLETED }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "AI Model Bundles",
                            style = MaterialTheme.typography.titleLarge,
                            fontFamily = FontFamily.Serif
                        )
                        Text(
                            text = "On-Device Google Gemma 4 LLM & RAG",
                            style = MaterialTheme.typography.bodyMedium,
                            color = PerfumeSecondary,
                            fontSize = 11.sp
                        )
                    }
                },
                actions = {
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
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = PerfumeBackground,
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 90.dp)
        ) {
            // 1. SMART GUIDANCE BANNER (Penunjuk Model yang Harus Diunduh)
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = PerfumePrimary.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, PerfumePrimary.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = PerfumePrimaryContainer,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = PerfumePrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Panduan Memulai AI Chatbot",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = PerfumePrimary
                                )
                                Text(
                                    text = "Offline setelah model lokal siap",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp,
                                    color = PerfumeOutline
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Untuk mengaktifkan inference LLM penuh secara offline, unduh salah satu model LLM di bawah. Tanpa model, chatbot tetap memberi resep yang grounded dari dataset template.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 12.sp,
                            color = PerfumeOnBackground.copy(alpha = 0.9f),
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (hasAnyCompletedLlm && onNavigateToChat != null) {
                                Button(
                                    onClick = onNavigateToChat,
                                    colors = ButtonDefaults.buttonColors(containerColor = PerfumePrimary),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.ChatBubble, contentDescription = null, tint = PerfumeOnPrimary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Buka AI Chatbot", color = PerfumeOnPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            } else if (recommendedBundle != null && recommendedBundle.status == DownloadStatus.NOT_DOWNLOADED) {
                                Button(
                                    onClick = { viewModel.startOrResumeDownload(recommendedBundle) },
                                    colors = ButtonDefaults.buttonColors(containerColor = PerfumePrimary),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, tint = PerfumeOnPrimary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Unduh Model Rekomendasi (2.4 GB)", color = PerfumeOnPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // 2. SAF FOLDER CARD
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassCard()
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Folder, contentDescription = null, tint = PerfumeSecondary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Lokasi Penyimpanan SAF",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (uiState.safFolderUri != null) "Folder SAF Terpilih (Aman saat reinstall)" else "Penyimpanan Internal Aplikasi",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (uiState.safFolderUri != null) PerfumeSecondary else PerfumeOutline,
                                fontSize = 11.sp
                            )
                        }
                        OutlinedButton(
                            onClick = { folderPickerLauncher.launch(null) },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, PerfumePrimary.copy(alpha = 0.5f)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Pilih Folder", color = PerfumePrimary, fontSize = 11.sp)
                        }
                    }
                }
            }

            // 3. SECTION 1: LLM CHATBOT MODELS
            item {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Psychology, contentDescription = null, tint = PerfumePrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Model AI Chatbot (LLM)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Pilih dan unduh salah satu model di bawah untuk mengaktifkan AI Chatbot.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PerfumeOutline,
                        fontSize = 11.sp
                    )
                }
            }

            items(llmBundles, key = { it.id }) { bundle ->
                val isRecommended = bundle.id == "llm_gemma_2b"
                val isActiveLlm = uiState.activeModelId == bundle.id &&
                    uiState.modelReadiness[bundle.id] == true

                LlmBundleCard(
                    bundle = bundle,
                    isRecommended = isRecommended,
                    isActiveLlm = isActiveLlm,
                    onSelectActiveModel = { viewModel.selectActiveModel(bundle) },
                    onStart = { viewModel.startOrResumeDownload(bundle) },
                    onPause = { viewModel.pauseDownload(bundle.id) },
                    onCancel = { viewModel.cancelDownload(bundle.id) },
                    onOpenChat = onNavigateToChat
                )
            }

            // 4. SECTION 2: VECTOR EMBEDDING / RAG
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountTree, contentDescription = null, tint = PerfumeSecondary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Model Vektor & RAG (Basis Data Bawaan)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Model semantik pendukung pencarian dokumen & katalog bahan baku parfum.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PerfumeOutline,
                        fontSize = 11.sp
                    )
                }
            }

            items(embeddingBundles, key = { it.id }) { bundle ->
                EmbeddingBundleCard(bundle = bundle)
            }
        }
    }
}

@Composable
private fun LlmBundleCard(
    bundle: DownloadBundle,
    isRecommended: Boolean,
    isActiveLlm: Boolean,
    onSelectActiveModel: () -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onCancel: () -> Unit,
    onOpenChat: (() -> Unit)?
) {
    val progress = if (bundle.totalBytes > 0) {
        (bundle.downloadedBytes.toFloat() / bundle.totalBytes.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val totalMb = bundle.totalBytes / (1024 * 1024)
    val downloadedMb = bundle.downloadedBytes / (1024 * 1024)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(cornerRadius = 14)
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = bundle.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    if (isRecommended) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = PerfumePrimary.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, PerfumePrimary.copy(alpha = 0.4f)),
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = PerfumePrimary, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "DIREKOMENDASIKAN (Paling Cepat & Hemat RAM)",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PerfumePrimary
                                )
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = PerfumeSecondary.copy(alpha = 0.15f),
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            Text(
                                "⚡ HIGH SPEC (Disarankan RAM 8GB+)",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PerfumeSecondary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = bundle.description,
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 11.sp,
                        color = PerfumeOutline
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when (bundle.status) {
                        DownloadStatus.COMPLETED -> PerfumeSecondaryContainer
                        DownloadStatus.DOWNLOADING -> PerfumePrimaryContainer
                        DownloadStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
                        else -> PerfumeSurfaceVariant
                    }
                ) {
                    Text(
                        text = when (bundle.status) {
                            DownloadStatus.COMPLETED -> "TERUNDUH"
                            DownloadStatus.DOWNLOADING -> "MENGUNDUH"
                            DownloadStatus.PAUSED -> "JEDA"
                            DownloadStatus.FAILED -> "GAGAL"
                            else -> "BELUM DIUNDUH"
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = when (bundle.status) {
                            DownloadStatus.COMPLETED -> PerfumeOnSecondaryContainer
                            DownloadStatus.DOWNLOADING -> PerfumeOnPrimaryContainer
                            DownloadStatus.FAILED -> MaterialTheme.colorScheme.onErrorContainer
                            else -> PerfumeOnBackground
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Progress Bar
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = PerfumePrimary,
                trackColor = PerfumeSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (bundle.status == DownloadStatus.COMPLETED) {
                        "$totalMb MB (100% Siap Offline)"
                    } else {
                        "$downloadedMb MB / $totalMb MB (${(progress * 100).toInt()}%)"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 11.sp,
                    color = PerfumeOutline
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (bundle.status) {
                        DownloadStatus.NOT_DOWNLOADED, DownloadStatus.FAILED, DownloadStatus.PAUSED -> {
                            Button(
                                onClick = onStart,
                                colors = ButtonDefaults.buttonColors(containerColor = PerfumePrimary),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, tint = PerfumeOnPrimary, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    if (bundle.status == DownloadStatus.PAUSED) "Lanjut" else "Unduh ($totalMb MB)",
                                    color = PerfumeOnPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        DownloadStatus.DOWNLOADING -> {
                            IconButton(onClick = onPause, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Pause, contentDescription = "Pause", tint = PerfumePrimary)
                            }
                            IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel", tint = PerfumeError)
                            }
                        }
                        DownloadStatus.COMPLETED -> {
                            if (isActiveLlm) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = PerfumePrimaryContainer,
                                        border = BorderStroke(1.dp, PerfumePrimary.copy(alpha = 0.6f))
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Default.Star, contentDescription = null, tint = PerfumePrimary, modifier = Modifier.size(13.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                "MODEL AKTIF",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = PerfumeOnPrimaryContainer,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                    if (onOpenChat != null) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Button(
                                            onClick = onOpenChat,
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = PerfumePrimary),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            modifier = Modifier.height(30.dp)
                                        ) {
                                            Text("Chat 💬", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            } else {
                                OutlinedButton(
                                    onClick = onSelectActiveModel,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PerfumePrimary),
                                    border = BorderStroke(1.dp, PerfumePrimary.copy(alpha = 0.5f)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Default.RadioButtonUnchecked, contentDescription = null, tint = PerfumePrimary, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Pilih Model Ini", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }
}

@Composable
private fun EmbeddingBundleCard(bundle: DownloadBundle) {
    val totalMb = bundle.totalBytes / (1024 * 1024)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(cornerRadius = 14)
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = bundle.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = PerfumeSecondaryContainer,
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Text(
                            "BAWAAN APLIKASI (Pre-Installed)",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = PerfumeOnSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = "Model vektor 384 dimensi bawaan aplikasi (${totalMb} MB) untuk pencarian cerdas semantik pada dokumen PDF & katalog bahan baku.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 11.sp,
                        color = PerfumeOutline
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = PerfumeSecondaryContainer
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = PerfumeSecondary, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("RAG Ready", style = MaterialTheme.typography.labelSmall, color = PerfumeOnSecondaryContainer, fontSize = 10.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = { 1.0f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = PerfumeSecondary,
                trackColor = PerfumeSurfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "$totalMb MB / $totalMb MB (100% Terpasang • Tidak Membutuhkan Unduhan)",
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 11.sp,
                color = PerfumeSecondary
            )
        }
    }
}
