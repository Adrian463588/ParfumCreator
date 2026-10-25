package com.parfumecreator.presentation.ui.formula

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parfumecreator.domain.model.*
import com.parfumecreator.presentation.ui.components.*
import com.parfumecreator.presentation.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormulaScreen(
    viewModel: FormulaViewModel,
    onNavigateToSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val calc = uiState.calculation
    val snackbarHostState = remember { SnackbarHostState() }

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
                    Column {
                        Text(
                            text = "Formula Creator",
                            style = MaterialTheme.typography.titleLarge,
                            fontFamily = FontFamily.Serif
                        )
                        Text(
                            text = uiState.formulaName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = PerfumePrimary,
                            maxLines = 1
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.showTemplatesDialog(true) }) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = "Template Resep Natural dan Bibit",
                            tint = PerfumeSecondary
                        )
                    }
                    IconButton(onClick = { viewModel.showSavedFormulasSheet(true) }) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = "Buka Formula Tersimpan",
                            tint = PerfumePrimary
                        )
                    }
                    IconButton(onClick = { viewModel.clearFormula() }) {
                        Icon(
                            Icons.Default.AddCircleOutline,
                            contentDescription = "Buat Formula Baru",
                            tint = PerfumeOutline
                        )
                    }
                    uiState.activeFormulaId?.let { formulaId ->
                        IconButton(onClick = { viewModel.deleteFormula(formulaId) }) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Hapus Formula",
                                tint = PerfumeError
                            )
                        }
                    }
                    IconButton(onClick = { viewModel.showSaveDialog(true) }, enabled = !uiState.isSaving) {
                        Icon(
                            Icons.Default.Save,
                            contentDescription = "Simpan Formula",
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
                onClick = { viewModel.showIngredientPicker(true) },
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
                        text = "Tambah Bahan",
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
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val isExpanded = maxWidth >= 720.dp

            if (isExpanded) {
                // Adaptive 2-Pane Tablet Layout: Left = Ingredients Editor, Right = Live Formulation Analytics
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Left Column: Ingredients List & Formulation Identity
                    LazyColumn(
                        modifier = Modifier
                            .weight(1.1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 90.dp)
                    ) {
                        item {
                            FormulaIdentityCard(
                                name = uiState.formulaName,
                                notes = uiState.notes,
                                onEditClick = { viewModel.showSaveDialog(true) }
                            )
                        }

                        item {
                            Text(
                                text = "Komposisi Bahan (${uiState.items.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (uiState.items.isEmpty()) {
                            item {
                                EmptyFormulaPlaceholder(onAddClick = { viewModel.showIngredientPicker(true) })
                            }
                        } else {
                            itemsIndexed(uiState.items) { index, item ->
                                val calculatedDetail = calc?.calculatedItems?.getOrNull(index)
                                FormulaIngredientRow(
                                    item = item,
                                    detail = calculatedDetail,
                                    onAmountChanged = { viewModel.updateItemAmount(index, it) },
                                    onUnitChanged = { viewModel.updateItemUnit(index, it) },
                                    onRemove = { viewModel.removeItem(index) }
                                )
                            }
                        }
                    }

                    // Right Column: Summary Metrics, Radar Fragrance Wheel, and Note Breakdown
                    LazyColumn(
                        modifier = Modifier
                            .weight(0.9f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 90.dp)
                    ) {
                        item {
                            FormulaSummaryCard(calc = calc, error = uiState.calculationError)
                        }

                        if (calc != null) {
                            item {
                                FragranceWheelCard(
                                    wheelResult = calc.fragranceWheelResult,
                                    onClick = { viewModel.showWheelDetail(true) }
                                )
                            }
                        }
                    }
                }
            } else {
                // Phone Portrait: Clean, Focused Vertical Formulation Flow
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 90.dp)
                ) {
                    // 1. Formula Identity Header
                    item {
                        FormulaIdentityCard(
                            name = uiState.formulaName,
                            notes = uiState.notes,
                            onEditClick = { viewModel.showSaveDialog(true) }
                        )
                    }

                    // 2. Summary Card (Weight, Concentration %, Type, SPL)
                    item {
                        FormulaSummaryCard(calc = calc, error = uiState.calculationError)
                    }

                    // 3. Fragrance Wheel Radar Chart
                    if (calc != null) {
                        item {
                            FragranceWheelCard(
                                wheelResult = calc.fragranceWheelResult,
                                onClick = { viewModel.showWheelDetail(true) }
                            )
                        }
                    }

                    // 4. Ingredient Composition Header
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Komposisi Bahan (${uiState.items.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (uiState.items.isNotEmpty()) {
                                Text(
                                    text = calc?.let { String.format("Total: %.2f g", it.totalWeightG) } ?: "Total: —",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = PerfumePrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // 5. Ingredient Items
                    if (uiState.items.isEmpty()) {
                        item {
                            EmptyFormulaPlaceholder(onAddClick = { viewModel.showIngredientPicker(true) })
                        }
                    } else {
                        itemsIndexed(uiState.items) { index, item ->
                            val calculatedDetail = calc?.calculatedItems?.getOrNull(index)
                            FormulaIngredientRow(
                                item = item,
                                detail = calculatedDetail,
                                onAmountChanged = { viewModel.updateItemAmount(index, it) },
                                onUnitChanged = { viewModel.updateItemUnit(index, it) },
                                onRemove = { viewModel.removeItem(index) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Save / Edit Name Dialog
    if (uiState.isSaveDialogVisible) {
        SaveFormulaDialog(
            formulaName = uiState.formulaName,
            notes = uiState.notes,
            isSaving = uiState.isSaving,
            onNameChange = { viewModel.setFormulaName(it) },
            onNotesChange = { viewModel.setNotes(it) },
            onSave = { viewModel.saveFormula() },
            onDismiss = { viewModel.showSaveDialog(false) }
        )
    }

    // Ingredient Picker Bottom Sheet
    if (uiState.isIngredientPickerVisible) {
        IngredientPickerBottomSheet(
            ingredients = uiState.allIngredients,
            onDismiss = { viewModel.showIngredientPicker(false) },
            onSelect = { ing ->
                viewModel.addIngredient(ing)
                viewModel.showIngredientPicker(false)
            }
        )
    }

    // Saved Formulas Bottom Sheet
    if (uiState.isSavedFormulasSheetVisible) {
        SavedFormulasBottomSheet(
            formulas = uiState.savedFormulas,
            activeFormulaId = uiState.activeFormulaId,
            onDismiss = { viewModel.showSavedFormulasSheet(false) },
            onLoad = { viewModel.loadSavedFormula(it) },
            onDuplicate = { viewModel.duplicateFormula(it) },
            onDelete = { viewModel.deleteFormula(it.id) },
            onOpenTemplates = { viewModel.showTemplatesDialog(true) }
        )
    }

    // The library and ingredient mapping share the existing catalog picker.
    if (uiState.isTemplatesDialogVisible) {
        if (uiState.templatePickerIndex != null) {
            IngredientPickerBottomSheet(
                ingredients = uiState.templateCandidates,
                onDismiss = viewModel::dismissTemplateIngredientPicker,
                onSelect = viewModel::bindTemplateIngredient
            )
        } else {
            FormulaTemplatesBottomSheet(
                state = uiState,
                onDismiss = { viewModel.showTemplatesDialog(false) },
                onPreview = viewModel::previewTemplate,
                onClosePreview = viewModel::closeTemplatePreview,
                onBatchChange = viewModel::setTemplateBatch,
                onSelectIngredient = viewModel::chooseTemplateIngredient,
                onAcknowledge = viewModel::acknowledgeTemplateAdaptation,
                onApply = viewModel::applyTemplate
            )
        }
    }

    // Fragrance Wheel Full 12-Family Breakdown Sheet
    if (uiState.isWheelDetailVisible && calc != null && calc.fragranceWheelResult.hasFragranceProfile) {
        FragranceWheelDetailBottomSheet(
            wheelResult = calc.fragranceWheelResult,
            calculatedItems = calc.calculatedItems,
            onDismiss = { viewModel.showWheelDetail(false) }
        )
    }
}

@Composable
private fun FormulaIdentityCard(
    name: String,
    notes: String,
    onEditClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(cornerRadius = 14)
            .clickable { onEditClick() }
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name.ifBlank { "Untitled Formulation" },
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    color = PerfumeOnBackground
                )
                if (notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = notes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = PerfumeOutline,
                        maxLines = 2,
                        fontSize = 12.sp
                    )
                } else {
                    Text(
                        text = "Ketuk untuk mengubah nama formula & catatan",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PerfumeOutline,
                        fontSize = 11.sp
                    )
                }
            }
            Icon(
                Icons.Default.Edit,
                contentDescription = "Edit Nama Formula",
                tint = PerfumePrimary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun FormulaSummaryCard(calc: FormulaCalculationResult?, error: String?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard()
            .padding(16.dp)
    ) {
        Column {
            if (error != null) {
                Text("Perhitungan belum valid", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Text(error, style = MaterialTheme.typography.bodyMedium, color = PerfumeError)
                return@Column
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Total Berat",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PerfumeOutline
                    )
                    Text(
                        text = String.format("%.2f g", calc?.totalWeightG ?: 0.0),
                        style = MaterialTheme.typography.headlineMedium,
                        color = PerfumePrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Konsentrasi (w/w)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PerfumeOutline
                    )
                    Text(
                        text = String.format("%.1f%%", calc?.concentrationPct ?: 0.0),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = PerfumePrimaryContainer
                    ) {
                        Text(
                            text = when {
                                calc == null || calc.fragranceWeightG <= 0.0 -> "Belum ada bahan aroma"
                                calc.fragranceWeightG == calc.totalWeightG -> "Konsentrat tanpa pelarut"
                                else -> calc.concentrationType.displayName
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = PerfumeOnPrimaryContainer,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = PerfumeSurfaceVariant
            )

            Text(
                text = "Persentase massa bahan aroma; klasifikasi konsentrasi bersifat perkiraan.",
                style = MaterialTheme.typography.bodySmall,
                color = PerfumeOutline
            )
            if (calc != null && calc.fragranceWeightG > 0.0) {
                Spacer(modifier = Modifier.height(10.dp))
                SplGauge(splScore = calc.spl, splLabel = calc.splLabel)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Note Architecture
            NoteBreakdownBar(
                topRatio = calc?.topRatio ?: 0.0,
                middleRatio = calc?.middleRatio ?: 0.0,
                baseRatio = calc?.baseRatio ?: 0.0
            )
        }
    }
}

@Composable
private fun EmptyFormulaPlaceholder(onAddClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard()
            .clickable { onAddClick() }
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                Icons.Default.Science,
                contentDescription = null,
                modifier = Modifier.size(52.dp),
                tint = PerfumePrimary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Mulai Racik Formulasi Baru",
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Ketuk di sini atau tombol '+' di bawah untuk memilih bahan racikan pertama.",
                style = MaterialTheme.typography.bodyMedium,
                color = PerfumeOutline,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun FormulaIngredientRow(
    item: FormulaIngredientInput,
    detail: CalculatedItemResult?,
    onAmountChanged: (Double) -> Unit,
    onUnitChanged: (MeasurementUnit) -> Unit,
    onRemove: () -> Unit
) {
    var rawText by remember(item.ingredient.id, item.unit) {
        mutableStateOf(if (item.amount.isFinite() && item.amount > 0.0) item.amount.toString().removeSuffix(".0") else "")
    }
    LaunchedEffect(item.amount) {
        if (item.amount.isFinite() && rawText.toDoubleOrNull() != item.amount) {
            rawText = if (item.amount > 0.0) item.amount.toString().removeSuffix(".0") else ""
        }
    }
    val parsedAmount = rawText.toDoubleOrNull()
    val invalidAmount = rawText.isNotBlank() &&
        (parsedAmount == null || !parsedAmount.isFinite() || parsedAmount < 0.0)
    val invalidDensity = item.unit == MeasurementUnit.MILLILITER &&
        (!item.ingredient.densityGml.isFinite() || item.ingredient.densityGml <= 0.0)

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
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color(android.graphics.Color.parseColor(item.ingredient.fragranceFamily.hexColor)))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = item.ingredient.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        Text(
                            text = if (item.ingredient.isSolventOrDiluent) {
                                "Pelarut / carrier • ID ${item.ingredient.id}"
                            } else {
                                "${item.ingredient.noteType.displayName} • ${item.ingredient.fragranceFamily.displayName} • " +
                                    (item.ingredient.brand ?: if (item.ingredient.type == IngredientType.NATURAL) "Natural" else "Sintetis")
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 11.sp,
                            color = PerfumeOutline
                        )
                    }
                }
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Hapus ${item.ingredient.name}",
                        tint = PerfumeOutline,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Numeric Amount Field with Decimal Keyboard
                    OutlinedTextField(
                        value = rawText,
                        onValueChange = { input ->
                            val sanitized = input.replace(',', '.')
                            rawText = sanitized
                            onAmountChanged(if (sanitized.isBlank()) 0.0 else sanitized.toDoubleOrNull() ?: Double.NaN)
                        },
                        modifier = Modifier.width(96.dp),
                        label = { Text("Jumlah") },
                        isError = invalidAmount || invalidDensity,
                        supportingText = if (invalidAmount || invalidDensity) {
                            { Text(if (invalidDensity) "Densitas invalid" else "Angka ≥ 0") }
                        } else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PerfumePrimary,
                            unfocusedBorderColor = PerfumeOutline
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Clean Dropdown Unit Selector
                    UnitDropdown(
                        selectedUnit = item.unit,
                        onUnitSelected = onUnitChanged
                    )
                }

                // Calculated Results (Gram conversion and Percentage)
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = detail?.let { String.format("%.2f g", it.amountInGram) } ?: "—",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PerfumePrimary
                    )
                    Text(
                        text = detail?.let { String.format("%.1f%% dari formula", it.percentageOfTotal) } ?: "Periksa jumlah dan densitas",
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 11.sp,
                        color = PerfumeOutline
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IngredientPickerBottomSheet(
    ingredients: List<Ingredient>,
    onDismiss: () -> Unit,
    onSelect: (Ingredient) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }
    var selectedBrand by remember { mutableStateOf<String?>(null) }
    var selectedNote by remember { mutableStateOf<NoteType?>(null) }
    var selectedFamily by remember { mutableStateOf<FragranceFamily?>(null) }

    val filtered = remember(searchQuery, selectedBrand, selectedNote, selectedFamily, ingredients) {
        ingredients.filter { ing ->
            val matchQuery = searchQuery.isBlank() ||
                    ing.name.contains(searchQuery, ignoreCase = true) ||
                    ing.brand?.contains(searchQuery, ignoreCase = true) == true ||
                    ing.inspiredBy?.contains(searchQuery, ignoreCase = true) == true

            val matchSource = when (selectedBrand) {
                null -> true
                "NATURAL" -> ing.type == IngredientType.NATURAL
                "CUSTOM" -> ing.isCustom
                "MACRABAME" -> ing.brand.equals("MACRABAME", ignoreCase = true) || ing.brand.equals("MACBRAME", ignoreCase = true) || ing.name.contains("MACBRAME", ignoreCase = true)
                else -> ing.brand.equals(selectedBrand, ignoreCase = true)
            }
            val matchNote = when (selectedNote) {
                null -> true
                NoteType.TOP -> ing.noteType.isTop()
                NoteType.HEART, NoteType.MIDDLE -> ing.noteType.isMiddle()
                NoteType.BASE -> ing.noteType.isBase()
            }
            val matchFamily = when (selectedFamily) {
                null -> true
                FragranceFamily.FLORAL -> ing.fragranceFamily == FragranceFamily.FLORAL || ing.fragranceFamily == FragranceFamily.ALDEHYDIC
                FragranceFamily.WOODY -> ing.fragranceFamily == FragranceFamily.WOODY || ing.fragranceFamily == FragranceFamily.EARTHY || ing.fragranceFamily == FragranceFamily.CHYPRE || ing.fragranceFamily == FragranceFamily.LEATHER
                FragranceFamily.ORIENTAL -> ing.fragranceFamily == FragranceFamily.ORIENTAL || ing.fragranceFamily == FragranceFamily.GOURMAND || ing.fragranceFamily == FragranceFamily.SPICY || ing.fragranceFamily == FragranceFamily.MUSKY
                FragranceFamily.FRESH -> ing.fragranceFamily == FragranceFamily.FRESH || ing.fragranceFamily == FragranceFamily.CITRUS || ing.fragranceFamily == FragranceFamily.FOUGERE || ing.fragranceFamily == FragranceFamily.AQUATIC || ing.fragranceFamily == FragranceFamily.GREEN || ing.fragranceFamily == FragranceFamily.FRUITY
                else -> ing.fragranceFamily == selectedFamily
            }

            matchQuery && matchSource && matchNote && matchFamily
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PerfumeSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Pilih Bahan Formulasi",
                style = MaterialTheme.typography.titleLarge,
                fontFamily = FontFamily.Serif
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Cari nama, brand, atau inspirasi aroma...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = PerfumeOutline) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
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

            Spacer(modifier = Modifier.height(8.dp))

            // Filter Dropdowns Bar (Brand/Source, Note, Family)
            val hasActiveFilter = selectedBrand != null || selectedNote != null || selectedFamily != null
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Brand / Source Dropdown
                FilterDropdown(
                    selectedOption = when (selectedBrand) {
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
                    onOptionSelected = { selectedBrand = it },
                    modifier = Modifier.weight(1f)
                )

                // Note Dropdown
                FilterDropdown(
                    selectedOption = when (selectedNote) {
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
                        selectedNote = it?.let { n -> NoteType.valueOf(n) }
                    },
                    modifier = Modifier.weight(1f)
                )

                // Family Dropdown (Standard Michael Edwards & SFP)
                FilterDropdown(
                    selectedOption = when (selectedFamily) {
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
                        selectedFamily = it?.let { f -> FragranceFamily.valueOf(f) }
                    },
                    modifier = Modifier.weight(1f)
                )

                // Reset filter button when active
                if (hasActiveFilter) {
                    IconButton(
                        onClick = {
                            selectedBrand = null
                            selectedNote = null
                            selectedFamily = null
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

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Tersedia ${filtered.size} bahan",
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 11.sp,
                color = PerfumeOutline
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.id }) { ing ->
                    Surface(
                        onClick = { onSelect(ing) },
                        shape = RoundedCornerShape(12.dp),
                        color = PerfumeSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(android.graphics.Color.parseColor(ing.fragranceFamily.hexColor)))
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = ing.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${ing.noteType.displayName} • ${ing.fragranceFamily.displayName} • ${ing.brand ?: "Natural"} • ID ${ing.id}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontSize = 11.sp,
                                        color = PerfumeOutline
                                    )
                                }
                            }
                            Button(
                                onClick = { onSelect(ing) },
                                colors = ButtonDefaults.buttonColors(containerColor = PerfumePrimary),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Pilih", color = PerfumeOnPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavedFormulasBottomSheet(
    formulas: List<Formula>,
    activeFormulaId: Long?,
    onDismiss: () -> Unit,
    onLoad: (Formula) -> Unit,
    onDuplicate: (Formula) -> Unit,
    onDelete: (Formula) -> Unit,
    onOpenTemplates: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }

    val filteredFormulas = remember(formulas, searchQuery) {
        if (searchQuery.isBlank()) {
            formulas
        } else {
            val q = searchQuery.trim().lowercase()
            formulas.filter { f ->
                f.name.lowercase().contains(q) ||
                (f.notes?.lowercase()?.contains(q) == true) ||
                f.concentrationType.displayName.lowercase().contains(q) ||
                f.splLabel.displayName.lowercase().contains(q) ||
                f.ingredients.any { it.ingredient.name.lowercase().contains(q) }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PerfumeSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Formula Tersimpan (${formulas.size})",
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = FontFamily.Serif
                )
                if (searchQuery.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = PerfumePrimaryContainer
                    ) {
                        Text(
                            text = "${filteredFormulas.size} Hasil",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = PerfumeOnPrimaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Recipe library shortcut
            Surface(
                onClick = {
                    onDismiss()
                    onOpenTemplates()
                },
                shape = RoundedCornerShape(12.dp),
                color = PerfumePrimaryContainer.copy(alpha = 0.7f),
                border = BorderStroke(1.dp, PerfumePrimary.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(PerfumePrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = PerfumeOnPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Template Resep Natural & Bibit",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = PerfumeOnPrimaryContainer
                        )
                        Text(
                            text = "Natural botanical & bibit sintetis dari katalog",
                            style = MaterialTheme.typography.bodySmall,
                            color = PerfumeOutline
                        )
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = PerfumePrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search Bar for Saved Formulas
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = "Cari formula, aroma, atau bahan...",
                        color = PerfumeOutline.copy(alpha = 0.6f),
                        fontSize = 13.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Cari Formula",
                        tint = PerfumePrimary,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Hapus Pencarian",
                                tint = PerfumeOutline,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PerfumePrimary,
                    unfocusedBorderColor = PerfumeOutline.copy(alpha = 0.3f),
                    focusedContainerColor = PerfumeSurfaceVariant,
                    unfocusedContainerColor = PerfumeSurfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (formulas.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassCard()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Belum ada formula tersimpan.\nSimpan formula aktif melalui icon Save di atas.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PerfumeOutline,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else if (filteredFormulas.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassCard()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Tidak ditemukan formula yang cocok dengan \"$searchQuery\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = PerfumeOutline,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { searchQuery = "" }) {
                            Text("Reset Pencarian", color = PerfumePrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 450.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredFormulas, key = { it.id }) { f ->
                        val isActive = f.id == activeFormulaId
                        Surface(
                            onClick = { onLoad(f) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isActive) PerfumePrimaryContainer.copy(alpha = 0.35f) else PerfumeSurfaceVariant,
                            border = if (isActive) BorderStroke(1.dp, PerfumePrimary.copy(alpha = 0.6f)) else BorderStroke(1.dp, PerfumeOutline.copy(alpha = 0.15f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = f.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (isActive) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = PerfumePrimary.copy(alpha = 0.2f),
                                                border = BorderStroke(1.dp, PerfumePrimary.copy(alpha = 0.5f))
                                            ) {
                                                Text(
                                                    text = "Aktif",
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = PerfumePrimary,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${f.concentrationType.displayName} (${String.format("%.1f%%", f.concentrationPct)}) • SPL heuristik: ${f.splLabel.displayName} • ${f.ingredients.size} bahan",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontSize = 11.sp,
                                        color = PerfumeOutline,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { onDuplicate(f) }, modifier = Modifier.size(40.dp)) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Duplikasi Formula", tint = PerfumePrimary, modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(onClick = { onDelete(f) }, modifier = Modifier.size(40.dp)) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Hapus Formula", tint = PerfumeError, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SaveFormulaDialog(
    formulaName: String,
    notes: String,
    isSaving: Boolean,
    onNameChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    // If formulaName is default "Untitled Formulation", show empty text field so placeholder appears cleanly
    var localName by remember {
        mutableStateOf(if (formulaName == "Untitled Formulation") "" else formulaName)
    }
    var localNotes by remember { mutableStateOf(notes) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = PerfumeSurface,
            border = BorderStroke(1.dp, PerfumePrimary.copy(alpha = 0.4f)),
            shadowElevation = 16.dp,
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Dialog Header
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
                                    Icons.Default.BookmarkBorder,
                                    contentDescription = null,
                                    tint = PerfumePrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Text(
                            text = "Simpan Formula",
                            style = MaterialTheme.typography.titleLarge,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            color = PerfumeOnBackground
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Tutup",
                            tint = PerfumeOutline,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Text(
                    text = "Beri nama dan catatan untuk menyimpan racikan ke database lokal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = PerfumeOutline,
                    fontSize = 12.sp
                )

                // Input Field 1: Nama Formula
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Nama Formula",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = PerfumeSecondary
                    )
                    OutlinedTextField(
                        value = localName,
                        onValueChange = { localName = it },
                        placeholder = {
                            Text(
                                text = "Contoh: Velvet Rose & Oud",
                                color = PerfumeOutline.copy(alpha = 0.45f),
                                fontSize = 13.sp
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            if (localName.isNotEmpty()) {
                                IconButton(onClick = { localName = "" }) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = "Hapus",
                                        tint = PerfumeOutline,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PerfumePrimary,
                            unfocusedBorderColor = PerfumeOutline.copy(alpha = 0.3f),
                            focusedContainerColor = PerfumeSurfaceVariant,
                            unfocusedContainerColor = PerfumeSurfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Input Field 2: Catatan / Mood Formula
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Catatan Racikan (Opsional)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = PerfumeSecondary
                    )
                    OutlinedTextField(
                        value = localNotes,
                        onValueChange = { localNotes = it },
                        placeholder = {
                            Text(
                                text = "Deskripsi aroma, inspirasi, atau waktu maserasi...",
                                color = PerfumeOutline.copy(alpha = 0.45f),
                                fontSize = 12.sp
                            )
                        },
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PerfumePrimary,
                            unfocusedBorderColor = PerfumeOutline.copy(alpha = 0.3f),
                            focusedContainerColor = PerfumeSurfaceVariant,
                            unfocusedContainerColor = PerfumeSurfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Action Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, PerfumeOutline.copy(alpha = 0.4f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PerfumeOutline),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Text("Batal", fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            val finalName = localName.trim().ifBlank { "Untitled Formulation" }
                            onNameChange(finalName)
                            onNotesChange(localNotes.trim())
                            onSave()
                        },
                        enabled = !isSaving,
                        modifier = Modifier.weight(1.4f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PerfumePrimary),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = PerfumeOnPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isSaving) "Menyimpan..." else "Simpan",
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
