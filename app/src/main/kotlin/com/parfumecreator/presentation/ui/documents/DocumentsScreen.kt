package com.parfumecreator.presentation.ui.documents

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parfumecreator.domain.model.RagDocument
import com.parfumecreator.domain.model.DocumentAvailability
import com.parfumecreator.presentation.ui.theme.*

private val ALLOWED_MIME_TYPES = arrayOf(
    "application/pdf",
    "text/plain",
    "text/*",
    "application/octet-stream",
    "*/*"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsScreen(
    viewModel: DocumentsViewModel,
    onNavigateToSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var docToDelete by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var selectedDocForDetail by remember { mutableStateOf<RagDocument?>(null) }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val persisted = try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                context.contentResolver.persistedUriPermissions.any { permission ->
                    permission.uri == it && permission.isReadPermission
                }
            } catch (e: Exception) {
                Log.w("DocumentsScreen", "SAF URI tidak memberi izin baca permanen", e)
                false
            }
            if (persisted) viewModel.indexDocumentFromUri(it) else viewModel.rejectDocumentPermission()
        }
    }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissUserMessage()
        }
    }

    // Clean Delete Confirmation Dialog
    docToDelete?.let { (docId, fileName) ->
        Dialog(
            onDismissRequest = { docToDelete = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = PerfumeSurface,
                border = BorderStroke(1.dp, PerfumeError.copy(alpha = 0.4f)),
                shadowElevation = 16.dp,
                modifier = Modifier.fillMaxWidth(0.88f)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = PerfumeError.copy(alpha = 0.15f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = null,
                                    tint = PerfumeError,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Text(
                            text = "Hapus Dokumen?",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = PerfumeOnBackground
                        )
                    }

                    Text(
                        text = "Dokumen '$fileName' dan seluruh chunk embedding vektor terkait akan dihapus permanen dari basis data AI lokal.",
                        style = MaterialTheme.typography.bodySmall,
                        color = PerfumeOutline,
                        lineHeight = 18.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { docToDelete = null },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, PerfumeOutline.copy(alpha = 0.4f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PerfumeOutline)
                        ) {
                            Text("Batal", fontSize = 13.sp)
                        }

                        Button(
                            onClick = {
                                viewModel.deleteDocument(docId)
                                docToDelete = null
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PerfumeError)
                        ) {
                            Text("Hapus", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }

    // Document Info Modal
    selectedDocForDetail?.let { doc ->
        Dialog(
            onDismissRequest = { selectedDocForDetail = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = PerfumeSurface,
                border = BorderStroke(1.dp, PerfumePrimary.copy(alpha = 0.35f)),
                shadowElevation = 16.dp,
                modifier = Modifier.fillMaxWidth(0.90f)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = PerfumePrimary.copy(alpha = 0.15f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = null,
                                        tint = PerfumePrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Detail Dokumen",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = PerfumeOnBackground
                            )
                        }
                        IconButton(
                            onClick = { selectedDocForDetail = null },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Tutup",
                                tint = PerfumeOutline,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = doc.fileName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = PerfumePrimary
                        )
                        Text(
                            text = "Format: ${if (doc.fileName.endsWith(".pdf", ignoreCase = true)) "Adobe PDF Document" else "Plain Text Document"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = PerfumeOutline
                        )
                        Text(
                            text = "Ukuran: ${doc.sizeBytes / 1024} KB • Total Chunks: ${doc.chunkCount} potongan vektor",
                            style = MaterialTheme.typography.bodySmall,
                            color = PerfumeSecondary
                        )
                        Text(
                            text = "Dimensi Vektor: 384 dimensions (Normalized Dense Embedding)",
                            style = MaterialTheme.typography.bodySmall,
                            color = PerfumeOutline
                        )
                        Text(
                            text = "Sumber: ${doc.sourceType.name} • Trust: ${doc.trustLevel.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = PerfumeSecondary
                        )
                        doc.contentHash?.let { hash ->
                            Text(
                                text = "SHA-256: ${hash.take(16)}…",
                                style = MaterialTheme.typography.bodySmall,
                                color = PerfumeOutline
                            )
                        }
                        if (doc.availability == DocumentAvailability.UNAVAILABLE) {
                            Text(
                                text = "URI SAF tidak tersedia; citation tetap tersimpan, dokumen asli perlu dipilih ulang.",
                                style = MaterialTheme.typography.bodySmall,
                                color = PerfumeError
                            )
                        }
                    }

                    Button(
                        onClick = { selectedDocForDetail = null },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PerfumePrimary)
                    ) {
                        Text("Tutup", color = PerfumeOnPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "RAG Documents",
                            style = MaterialTheme.typography.titleLarge,
                            fontFamily = FontFamily.Serif
                        )
                        Text(
                            text = "Knowledge Base & Referensi AI",
                            style = MaterialTheme.typography.bodyMedium,
                            color = PerfumeSecondary,
                            fontSize = 11.sp
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            documentPickerLauncher.launch(ALLOWED_MIME_TYPES)
                        }
                    ) {
                        Icon(
                            Icons.Default.AddCircle,
                            contentDescription = "Upload Dokumen",
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
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    documentPickerLauncher.launch(ALLOWED_MIME_TYPES)
                },
                containerColor = PerfumePrimary,
                contentColor = PerfumeOnPrimary,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.UploadFile,
                        contentDescription = null,
                        tint = PerfumeOnPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Upload Dokumen",
                        fontWeight = FontWeight.Bold,
                        color = PerfumeOnPrimary,
                        fontSize = 14.sp
                    )
                }
            }
        },
        containerColor = PerfumeBackground,
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // Indexing Status Card
            if (uiState.isIndexing) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = PerfumeSurfaceVariant,
                    border = BorderStroke(1.dp, PerfumePrimary.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = PerfumePrimary,
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Sedang Memproses Chunking & Embedding...",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = PerfumePrimary
                            )
                            Text(
                                text = uiState.indexingFileName ?: "Mengekstrak teks...",
                                style = MaterialTheme.typography.bodySmall,
                                color = PerfumeOutline,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Overview Banner Card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = PerfumeSurfaceVariant,
                border = BorderStroke(1.dp, PerfumeOutline.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Basis Data Pengetahuan Parfum",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "File PDF/TXT dipecah menjadi vektor 384 dimensi dan menjadi rujukan otomatis AI Chatbot.",
                        style = MaterialTheme.typography.bodySmall,
                        color = PerfumeOutline,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            documentPickerLauncher.launch(ALLOWED_MIME_TYPES)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PerfumePrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.UploadFile,
                            contentDescription = null,
                            tint = PerfumeOnPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Pilih & Index Dokumen (PDF / TXT)",
                            color = PerfumeOnPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Document List Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Daftar Dokumen (${uiState.documents.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (uiState.documents.isNotEmpty()) {
                    val totalChunks = uiState.documents.sumOf { it.chunkCount }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = PerfumeSecondaryContainer
                    ) {
                        Text(
                            text = "$totalChunks Total Chunks",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = PerfumeOnSecondaryContainer,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Empty State with Built-in Standard Guides Quick-Add
            if (uiState.documents.isEmpty() && !uiState.isIndexing) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = PerfumeSurfaceVariant,
                    border = BorderStroke(1.dp, PerfumeOutline.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = PerfumePrimary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Belum Ada Dokumen Terindeks",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Unggah file buku racikan parfum, formulasi, atau standar IFRA untuk memperkaya pengetahuan AI Chatbot.",
                            style = MaterialTheme.typography.bodySmall,
                            color = PerfumeOutline,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Quick Seed Buttons
                        Text(
                            text = "Atau tambahkan standar referensi cepat:",
                            style = MaterialTheme.typography.labelSmall,
                            color = PerfumeSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.indexBuiltInGuide("ifra") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, PerfumePrimary.copy(alpha = 0.4f)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text("+ Panduan verifikasi IFRA", fontSize = 11.sp, color = PerfumePrimary)
                            }
                            OutlinedButton(
                                onClick = { viewModel.indexBuiltInGuide("accords") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, PerfumeSecondary.copy(alpha = 0.4f)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text("+ Panduan Accords", fontSize = 11.sp, color = PerfumeSecondary)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 90.dp)
                ) {
                    items(uiState.documents, key = { it.id }) { doc ->
                        val isPdf = doc.fileName.endsWith(".pdf", ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = PerfumeSurfaceVariant,
                            border = BorderStroke(1.dp, PerfumeOutline.copy(alpha = 0.2f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedDocForDetail = doc }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isPdf) PerfumePrimaryContainer else PerfumeSecondaryContainer,
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                if (isPdf) Icons.Default.PictureAsPdf else Icons.Default.Description,
                                                contentDescription = null,
                                                tint = if (isPdf) PerfumeOnPrimaryContainer else PerfumeOnSecondaryContainer,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = doc.fileName,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "${doc.chunkCount} chunks",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontSize = 11.sp,
                                                color = PerfumePrimary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = " • ${(doc.sizeBytes / 1024).coerceAtLeast(1)} KB",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontSize = 11.sp,
                                                color = PerfumeOutline
                                            )
                                            Text(
                                                text = " • ${when (doc.availability) {
                                                    DocumentAvailability.AVAILABLE -> "Terindeks"
                                                    DocumentAvailability.UNAVAILABLE -> "URI tidak tersedia"
                                                    DocumentAvailability.UNKNOWN -> "Status tidak diketahui"
                                                }}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontSize = 11.sp,
                                                color = if (doc.availability == DocumentAvailability.UNAVAILABLE) {
                                                    PerfumeError
                                                } else PerfumeSecondary
                                            )
                                        }
                                    }
                                }
                                IconButton(
                                    onClick = { docToDelete = doc.id to doc.fileName },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "Hapus Dokumen",
                                        tint = PerfumeError,
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
}
