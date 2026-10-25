package com.parfumecreator.presentation.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parfumecreator.domain.model.MeasurementUnit
import com.parfumecreator.domain.model.ModelReadiness
import com.parfumecreator.presentation.ui.components.FilterDropdown
import com.parfumecreator.presentation.ui.components.MeasurementUnitDropdown
import com.parfumecreator.presentation.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: (() -> Unit)? = null,
    onOpenTemplates: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val exportPersonaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.exportPersonaToUri(context, it) }
    }

    val importPersonaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importPersonaFromUri(context, it) }
    }

    val openFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { viewModel.onSafFolderSelected(context, it) }
    }

    LaunchedEffect(uiState.isSavedMessageVisible) {
        if (uiState.isSavedMessageVisible) {
            snackbarHostState.showSnackbar("Pengaturan berhasil disimpan!")
            viewModel.dismissSavedMessage()
        }
    }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissUserMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Pengaturan",
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Kembali",
                                tint = PerfumeOnBackground
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(modifier = Modifier.height(2.dp))

            // 1. ACTIVE AI MODEL CARD
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(cornerRadius = 14)
                    .padding(14.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Psychology,
                            contentDescription = null,
                            tint = PerfumePrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Model AI Chatbot",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Pilih model aktif untuk inferensi offline lokal.",
                        style = MaterialTheme.typography.bodySmall,
                        color = PerfumeOutline,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    val modelOptions = listOf(
                        "llm_gemma_2b" to "Gemma 4 E2B-it (LiteRT-LM)",
                        "llm_gemma_4b" to "Gemma 4 E4B-it (High-Capability)"
                    )

                    modelOptions.forEach { (id, label) ->
                        val isReady = uiState.modelReadiness[id] == true
                        val isSelected = uiState.activeModelId == id
                        val status = uiState.modelStatuses[id] ?: if (isReady) {
                            ModelReadiness.VERIFYING
                        } else ModelReadiness.UNAVAILABLE
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) PerfumePrimaryContainer.copy(alpha = 0.6f) else PerfumeSurfaceVariant,
                            border = if (isSelected) BorderStroke(1.dp, PerfumePrimary) else BorderStroke(1.dp, PerfumeOutline.copy(alpha = 0.15f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clickable { viewModel.selectActiveModel(id) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (isSelected) PerfumePrimary else PerfumeOutline,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isSelected) PerfumeOnPrimaryContainer else PerfumeOnBackground,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        fontSize = 13.sp
                                    )
                                }
                                if (isSelected || isReady || status != ModelReadiness.UNAVAILABLE) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (status == ModelReadiness.READY) PerfumePrimary else PerfumeSurfaceVariant
                                    ) {
                                        Text(
                                            text = when (status) {
                                                ModelReadiness.READY -> if (isSelected) "AKTIF" else "SIAP"
                                                ModelReadiness.VERIFYING -> "VERIFIKASI"
                                                ModelReadiness.DOWNLOADING -> "UNDUH"
                                                ModelReadiness.FAILED -> "GAGAL"
                                                ModelReadiness.SELECTED -> "DIPILIH"
                                                ModelReadiness.UNAVAILABLE -> "BELUM SIAP"
                                            },
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (status == ModelReadiness.READY) PerfumeOnPrimary else PerfumePrimary,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (!uiState.isActiveModelReady) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Model yang dipilih perlu verifikasi runtime LiteRT sebelum dipakai. Selesaikan download lalu tekan pilihan model.",
                            style = MaterialTheme.typography.bodySmall,
                            color = PerfumeOutline,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // 2. LOCAL-FIRST INTERNET FALLBACK CARD
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(cornerRadius = 14)
                    .padding(14.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Public,
                                contentDescription = null,
                                tint = PerfumePrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Internet Fallback",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Switch(
                            checked = uiState.allowInternetFallback,
                            onCheckedChange = viewModel::setAllowInternetFallback
                        )
                    }
                    Text(
                        text = if (uiState.allowInternetFallback) {
                            "Aktif • otomatis mencari saat sumber lokal belum cukup, tanpa dialog per pertanyaan."
                        } else {
                            "Mati • chatbot hanya membaca dataset dan dokumen lokal."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = PerfumeOutline,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Mencari di Wikipedia dan sumber resmi, dengan sitasi yang dapat dibuka. Dokumen dan riwayat chat tetap lokal. Referensi web bukan verifikasi keamanan bahan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = PerfumeSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }

            // 3. AI PERFUMER PERSONA CARD
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(cornerRadius = 14)
                    .padding(14.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Tune,
                                contentDescription = null,
                                tint = PerfumePrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Persona AI",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        TextButton(
                            onClick = { viewModel.resetSystemPrompt() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text("Reset Default", color = PerfumePrimary, fontSize = 11.sp)
                        }
                    }

                    Text(
                        text = "System prompt karakter asisten formulasi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = PerfumeOutline,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Persona Preset Dropdown
                    val presetOptions = PersonaPreset.entries.map { it.name to it.title }
                    val currentPreset = PersonaPreset.entries.find { it.prompt == uiState.systemPrompt }
                    val currentPresetTitle = currentPreset?.title ?: "Custom Persona"
                    FilterDropdown(
                        selectedOption = currentPresetTitle,
                        options = presetOptions,
                        onOptionSelected = { key ->
                            key?.let { name ->
                                val preset = PersonaPreset.valueOf(name)
                                viewModel.applyPreset(preset)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = uiState.systemPrompt,
                        onValueChange = { viewModel.updateSystemPrompt(it) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 6,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PerfumePrimary,
                            unfocusedBorderColor = PerfumeOutline.copy(alpha = 0.3f),
                            focusedContainerColor = PerfumeSurfaceVariant.copy(alpha = 0.5f),
                            unfocusedContainerColor = PerfumeSurfaceVariant.copy(alpha = 0.3f)
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Storage Folder Row
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = PerfumeSurfaceVariant,
                        border = BorderStroke(1.dp, PerfumeOutline.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = PerfumeSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = uiState.safFolderName ?: "Pilih folder backup...",
                                    fontSize = 11.sp,
                                    color = if (uiState.safFolderName != null) PerfumeOnBackground else PerfumeOutline,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            TextButton(
                                onClick = { openFolderLauncher.launch(null) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Pilih Folder", fontSize = 11.sp, color = PerfumePrimary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Action Buttons (Simpan Persona, Export, Import)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { viewModel.savePersonaPrompt(context) },
                            modifier = Modifier.weight(1.3f),
                            colors = ButtonDefaults.buttonColors(containerColor = PerfumePrimary),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp), tint = PerfumeOnPrimary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Simpan Persona", color = PerfumeOnPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { exportPersonaLauncher.launch("persona_parfum_ai.json") },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PerfumePrimary),
                            border = BorderStroke(1.dp, PerfumeOutline.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp), tint = PerfumePrimary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Export", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick = { importPersonaLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PerfumePrimary),
                            border = BorderStroke(1.dp, PerfumeOutline.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(14.dp), tint = PerfumePrimary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Import", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            onOpenTemplates?.let { openTemplates ->
                Surface(
                    onClick = openTemplates,
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Template Resep", style = MaterialTheme.typography.titleMedium)
                        Text("Natural & bibit sintetis · preview, pilih bahan, gunakan di editor",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // 4. MEASUREMENT UNIT CARD
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(cornerRadius = 14)
                    .padding(14.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Scale,
                            contentDescription = null,
                            tint = PerfumePrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Satuan Ukur Default",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Satuan saat menambahkan bahan formula baru.",
                        style = MaterialTheme.typography.bodySmall,
                        color = PerfumeOutline,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    MeasurementUnitDropdown(
                        selectedUnit = uiState.defaultUnit,
                        onUnitSelected = { viewModel.updateDefaultUnit(it) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(90.dp))
        }
    }
}
