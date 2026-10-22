package com.parfumecreator.presentation.ui.ingredients

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parfumecreator.domain.model.*
import com.parfumecreator.presentation.ui.components.FilterDropdown
import com.parfumecreator.presentation.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IngredientLibraryScreen(
    viewModel: IngredientViewModel,
    onAddToFormula: ((Ingredient) -> Unit)? = null,
    onNavigateToSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val stream = context.contentResolver.openInputStream(it)
            if (stream != null) {
                viewModel.importCsv(stream)
            }
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
                        text = "Ingredient Library",
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = FontFamily.Serif
                    )
                },
                actions = {
                    IconButton(onClick = { csvLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "application/csv")) }) {
                        Icon(Icons.Default.UploadFile, contentDescription = "Import CSV", tint = PerfumePrimary)
                    }
                    if (onNavigateToSettings != null) {
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Pengaturan", tint = PerfumeOutline)
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
                onClick = { viewModel.openAddDialog(true) },
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
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = PerfumeOnPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Bahan Custom",
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
            // Search Bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Cari bahan (e.g. Jasmine, Luzi, Vanilla...)") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = PerfumeOutline) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = PerfumeOutline)
                        }
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PerfumePrimary,
                    unfocusedBorderColor = PerfumeOutline
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Dropdowns Bar (Brand/Source, Note, Family)
            val hasActiveFilter = uiState.selectedBrand != null || uiState.selectedNoteType != null || uiState.selectedFamily != null
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Brand / Source Dropdown
                FilterDropdown(
                    selectedOption = when (uiState.selectedBrand) {
                        "LUZI" -> "Luzi"
                        "PARFAROME" -> "Parfarome"
                        "MACRABAME" -> "Macbrame"
                        "NATURAL" -> "Natural Oil"
                        "CUSTOM" -> "Custom"
                        else -> "Semua Sumber"
                    },
                    options = listOf(
                        null to "Semua Sumber",
                        "NATURAL" to "🌿 Natural Oil",
                        "LUZI" to "Luzi",
                        "PARFAROME" to "Parfarome",
                        "MACRABAME" to "Macbrame",
                        "CUSTOM" to "✨ Custom"
                    ),
                    onOptionSelected = { viewModel.onBrandSelected(it) },
                    modifier = Modifier.weight(1f)
                )

                // Note Dropdown
                FilterDropdown(
                    selectedOption = when (uiState.selectedNoteType) {
                        NoteType.TOP -> "Top Note"
                        NoteType.HEART -> "Heart Note"
                        NoteType.BASE -> "Base Note"
                        else -> "Semua Note"
                    },
                    options = listOf(
                        null to "Semua Note",
                        "TOP" to "Top Note",
                        "HEART" to "Heart / Mid",
                        "BASE" to "Base Note"
                    ),
                    onOptionSelected = {
                        viewModel.onNoteTypeSelected(it?.let { n -> NoteType.valueOf(n) })
                    },
                    modifier = Modifier.weight(1f)
                )

                // Family Dropdown (Standard Michael Edwards & SFP)
                FilterDropdown(
                    selectedOption = when (uiState.selectedFamily) {
                        FragranceFamily.FLORAL -> "Floral"
                        FragranceFamily.WOODY -> "Woody"
                        FragranceFamily.ORIENTAL -> "Amber / Oriental"
                        FragranceFamily.FRESH -> "Fresh"
                        FragranceFamily.CITRUS -> "Citrus"
                        FragranceFamily.FOUGERE -> "Fougère"
                        FragranceFamily.CHYPRE -> "Chypre"
                        FragranceFamily.GOURMAND -> "Gourmand"
                        FragranceFamily.AQUATIC -> "Aquatic"
                        FragranceFamily.SPICY -> "Spicy"
                        FragranceFamily.EARTHY -> "Earthy"
                        FragranceFamily.MUSKY -> "Musky"
                        FragranceFamily.LEATHER -> "Leather"
                        FragranceFamily.FRUITY -> "Fruity"
                        FragranceFamily.GREEN -> "Green"
                        FragranceFamily.ALDEHYDIC -> "Aldehydic"
                        null -> "Semua Aroma"
                    },
                    options = listOf(
                        null to "Semua Aroma",
                        "FLORAL" to "🌸 Floral",
                        "WOODY" to "🌲 Woody",
                        "ORIENTAL" to "🏺 Amber / Oriental",
                        "FRESH" to "🌿 Fresh",
                        "CITRUS" to "🍋 Citrus",
                        "AQUATIC" to "🌊 Aquatic",
                        "FOUGERE" to "🍃 Fougère",
                        "CHYPRE" to "🍂 Chypre",
                        "GOURMAND" to "🍮 Gourmand",
                        "SPICY" to "🌶️ Spicy",
                        "EARTHY" to "🪴 Earthy / Green",
                        "MUSKY" to "☁️ Musky",
                        "LEATHER" to "🧳 Leather / Smoky",
                        "FRUITY" to "🍓 Fruity",
                        "GREEN" to "🌱 Green",
                        "ALDEHYDIC" to "🫧 Aldehydic"
                    ),
                    onOptionSelected = {
                        viewModel.onFamilySelected(it?.let { f -> FragranceFamily.valueOf(f) })
                    },
                    modifier = Modifier.weight(1f)
                )

                // Reset filter button when active
                if (hasActiveFilter) {
                    IconButton(
                        onClick = {
                            viewModel.onBrandSelected(null)
                            viewModel.onNoteTypeSelected(null)
                            viewModel.onFamilySelected(null)
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.FilterAltOff,
                            contentDescription = "Reset Filter",
                            tint = PerfumePrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Ditemukan ${uiState.ingredients.size} bahan",
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 11.sp,
                color = PerfumeOutline
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Ingredient List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                items(uiState.ingredients, key = { it.id }) { ing ->
                    IngredientItemCard(
                        ingredient = ing,
                        onClick = { viewModel.openIngredientDetail(ing) }
                    )
                }
            }
        }
    }

    // Detail Bottom Sheet
    uiState.selectedIngredientForDetail?.let { ing ->
        IngredientDetailBottomSheet(
            ingredient = ing,
            onDismiss = { viewModel.openIngredientDetail(null) },
            onAddToFormula = onAddToFormula
        )
    }

    // Add Custom Dialog
    if (uiState.isAddDialogOpen) {
        AddCustomIngredientDialog(
            onDismiss = { viewModel.openAddDialog(false) },
            onSave = { name, type, brand, note, family, density, desc ->
                viewModel.saveCustomIngredient(name, type, brand, note, family, density, desc)
            }
        )
    }
}

@Composable
private fun IngredientItemCard(
    ingredient: Ingredient,
    onClick: () -> Unit
) {
    val familyColor = remember(ingredient.fragranceFamily) {
        try {
            Color(android.graphics.Color.parseColor(ingredient.fragranceFamily.hexColor))
        } catch (_: Exception) {
            PerfumePrimary
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(cornerRadius = 14)
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(36.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(familyColor)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = ingredient.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = PerfumeSecondary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = ingredient.noteType.displayName,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = PerfumeSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = familyColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = ingredient.fragranceFamily.displayName,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = familyColor,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        ingredient.brand?.let { brandName ->
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = PerfumeSurfaceVariant
                            ) {
                                Text(
                                    text = brandName,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = PerfumeOutline
                                )
                            }
                        }
                    }

                    ingredient.inspiredBy?.let { inspired ->
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Inspired by: $inspired",
                            style = MaterialTheme.typography.bodySmall,
                            color = PerfumePrimary,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = PerfumeSurfaceVariant
            ) {
                Text(
                    text = "${ingredient.densityGml} g/ml",
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp,
                    color = PerfumeOutline
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IngredientDetailBottomSheet(
    ingredient: Ingredient,
    onDismiss: () -> Unit,
    onAddToFormula: ((Ingredient) -> Unit)? = null
) {
    val familyColor = remember(ingredient.fragranceFamily) {
        try {
            Color(android.graphics.Color.parseColor(ingredient.fragranceFamily.hexColor))
        } catch (_: Exception) {
            PerfumePrimary
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = PerfumeSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = PerfumeOutline.copy(alpha = 0.5f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Badges Row
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Family Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = familyColor.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, familyColor.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(familyColor)
                        )
                        Text(
                            text = ingredient.fragranceFamily.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = familyColor
                        )
                    }
                }

                // Note Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = PerfumeSecondary.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, PerfumeSecondary.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = ingredient.noteType.displayName,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = PerfumeSecondary
                    )
                }

                // Type Badge (Synthetic / Natural)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = PerfumeSurfaceVariant
                ) {
                    Text(
                        text = ingredient.type.name,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = PerfumeOutline
                    )
                }
            }

            // Material Name & Subtitle
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = ingredient.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = PerfumeOnBackground,
                    lineHeight = 22.sp
                )
                Text(
                    text = "Brand / Produsen: ${ingredient.brand ?: "Natural Essential Oil"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = PerfumeOutline
                )
            }

            HorizontalDivider(color = PerfumeSurfaceVariant, thickness = 1.dp)

            // Specification Card
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = PerfumeSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Karakteristik Fisik & Regulasi",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = PerfumePrimary
                    )
                    DetailRow(label = "Densitas", value = "${ingredient.densityGml} g/ml")
                    DetailRow(label = "CAS Number", value = ingredient.casNumber ?: "N/A")
                DetailRow(
                    label = "IFRA / safety",
                    value = ingredient.ifraLimit?.let {
                        "Metadata $it% tanpa kategori/sumber terverifikasi; cek standar IFRA supplier"
                    } ?: "Tidak tersedia; cek kategori produk dan standar IFRA supplier"
                )
                    DetailRow(label = "Klasifikasi Piramida", value = ingredient.noteType.displayName)
                }
            }

            // Olfactory & Source Card (if available)
            if (!ingredient.inspiredBy.isNullOrBlank() || !ingredient.sourcePlant.isNullOrBlank() || !ingredient.description.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = PerfumeSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Profil Olfaktori & Asal-usul",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = PerfumePrimary
                        )
                        ingredient.inspiredBy?.takeIf { it.isNotBlank() }?.let {
                            DetailRow(label = "Inspirasi Aroma", value = it)
                        }
                        ingredient.sourcePlant?.takeIf { it.isNotBlank() }?.let {
                            DetailRow(label = "Sumber Botani", value = it)
                        }
                        ingredient.description?.takeIf { it.isNotBlank() }?.let {
                            DetailRow(label = "Deskripsi", value = it)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (onAddToFormula != null) {
                Button(
                    onClick = {
                        onAddToFormula(ingredient)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PerfumePrimary)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = PerfumeOnPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Tambah ke Formulasi Aktif",
                        color = PerfumeOnPrimary,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = PerfumeOutline,
            modifier = Modifier.weight(0.42f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = PerfumeOnBackground,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.58f)
        )
    }
}

@Composable
private fun AddCustomIngredientDialog(
    onDismiss: () -> Unit,
    onSave: (String, IngredientType, String?, NoteType, FragranceFamily, Double, String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var density by remember { mutableStateOf("0.95") }
    var description by remember { mutableStateOf("") }
    var selectedNote by remember { mutableStateOf(NoteType.MIDDLE) }
    var selectedFamily by remember { mutableStateOf(FragranceFamily.FRESH) }
    var selectedType by remember { mutableStateOf(IngredientType.SYNTHETIC) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PerfumeSurface,
        title = {
            Text(
                text = "Tambah Bahan Custom",
                style = MaterialTheme.typography.titleLarge,
                fontFamily = FontFamily.Serif
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama Bahan *") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PerfumePrimary,
                        unfocusedBorderColor = PerfumeOutline
                    )
                )
                OutlinedTextField(
                    value = brand,
                    onValueChange = { brand = it },
                    label = { Text("Brand / Produsen") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PerfumePrimary,
                        unfocusedBorderColor = PerfumeOutline
                    )
                )
                OutlinedTextField(
                    value = density,
                    onValueChange = { density = it },
                    label = { Text("Densitas (g/ml)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PerfumePrimary,
                        unfocusedBorderColor = PerfumeOutline
                    )
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Deskripsi Aroma") },
                    maxLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PerfumePrimary,
                        unfocusedBorderColor = PerfumeOutline
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        name,
                        selectedType,
                        brand.ifBlank { null },
                        selectedNote,
                        selectedFamily,
                        density.replace(',', '.').toDoubleOrNull() ?: Double.NaN,
                        description.ifBlank { null }
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = PerfumePrimary)
            ) {
                Text("Simpan", color = PerfumeOnPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal", color = PerfumeOutline)
            }
        }
    )
}
