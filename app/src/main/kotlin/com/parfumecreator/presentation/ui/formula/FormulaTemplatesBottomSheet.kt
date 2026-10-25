package com.parfumecreator.presentation.ui.formula

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.parfumecreator.domain.model.FormulaTemplate
import com.parfumecreator.domain.model.Ingredient
import com.parfumecreator.presentation.ui.components.FilterDropdown
import java.net.URI
import java.util.Locale

/** Searchable recipe library and unsaved preview, using the existing Formula editor state. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormulaTemplatesBottomSheet(
    state: FormulaUiState,
    onDismiss: () -> Unit,
    onPreview: (FormulaTemplate) -> Unit,
    onClosePreview: () -> Unit,
    onBatchChange: (String) -> Unit,
    onSelectIngredient: (Int) -> Unit,
    onAcknowledge: (Boolean) -> Unit,
    onApply: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = sheetState,
        modifier = Modifier.fillMaxHeight(0.95f), containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).imePadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.templateDraft != null) {
                    IconButton(onClick = onClosePreview) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali ke daftar resep")
                    }
                }
                Text("Template Resep", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Tutup template") }
            }
            val draft = state.templateDraft
            if (draft == null) {
                RecipeLibrary(state, onPreview, Modifier.weight(1f))
            } else {
                RecipePreview(draft, state.allIngredients, onBatchChange, onSelectIngredient,
                    onAcknowledge, onApply, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RecipeLibrary(state: FormulaUiState, onPreview: (FormulaTemplate) -> Unit, modifier: Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var family by rememberSaveable { mutableStateOf<String?>(null) }
    val templates = state.templates
    val filtered = remember(templates, query, category, family) {
        templates.filter { template ->
            (category == null || template.category == category) &&
                (family == null || template.familyLabel == family) &&
                (query.isBlank() || listOf(template.name, template.sourceId.orEmpty(), template.familyLabel,
                    template.ingredients.joinToString(" ") { "${it.name} ${it.brand.orEmpty()} ${it.catalogCode.orEmpty()}" })
                    .any { it.contains(query.trim(), ignoreCase = true) })
        }.sortedBy { if (it.isExperimental) 0 else 1 }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${templates.count { it.sourceId?.startsWith("NAT-") == true }} natural baru · " +
            "${templates.count { it.sourceId?.startsWith("BIB-") == true }} bibit baru · ${templates.size} total",
            style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
            label = { Text("Cari resep, ID, bahan atau brand") }, singleLine = true
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val categories = listOf(null to "Semua (${templates.size})",
                "NATURAL" to "Natural (${templates.count { it.category == "NATURAL" }})",
                "SYNTHETIC" to "Bibit sintetis (${templates.count { it.category == "SYNTHETIC" }})")
            FilterDropdown(
                selectedOption = categories.first { it.first == category }.second, options = categories,
                onOptionSelected = { category = it }, modifier = Modifier.weight(1f)
            )
            val families = listOf(null to "Semua aroma") + templates.map { it.familyLabel }.distinct().sorted().map { it to it }
            FilterDropdown(
                selectedOption = family ?: "Semua aroma", options = families,
                onOptionSelected = { family = it }, modifier = Modifier.weight(1f)
            )
        }
        if (state.isLoadingTemplates) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.templateLoadErrors.forEach { Text("Sumber gagal dimuat: $it", color = MaterialTheme.colorScheme.error) }
        Text("${filtered.size} resep. Studi belum diuji; konsentrat bukan parfum siap pakai di kulit.",
            style = MaterialTheme.typography.bodySmall)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (filtered.isEmpty() && !state.isLoadingTemplates) item {
                Text("Tidak ada template yang cocok. Ubah pencarian atau filter.", modifier = Modifier.padding(16.dp))
            }
            items(filtered, key = { it.id }) { template ->
                Surface(
                    shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${template.sourceId ?: "#${template.id}"} · " +
                            "${if (template.category == "NATURAL") "Natural" else "Bibit sintetis"} · ${template.familyLabel}",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(template.name, style = MaterialTheme.typography.titleMedium)
                        Text("${template.ingredients.size} bahan · ${grams(template.ingredients.sumOf { it.amount })} g referensi · " +
                            if (template.concentrationPct == 100.0) "Konsentrat 100%" else "${template.concentrationPct}% w/w",
                            style = MaterialTheme.typography.bodySmall)
                        Text(template.ingredients.joinToString(" · ") { it.name }, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { onPreview(template) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text("Detail & gunakan")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecipePreview(
    draft: TemplateDraft, catalog: List<Ingredient>, onBatchChange: (String) -> Unit,
    onSelectIngredient: (Int) -> Unit, onAcknowledge: (Boolean) -> Unit, onApply: () -> Unit, modifier: Modifier
) {
    val template = draft.template
    val uriHandler = LocalUriHandler.current
    var linkError by remember(template.id) { mutableStateOf<String?>(null) }
    val sourceTotal = template.ingredients.sumOf { it.amount }
    val batch = draft.batchText.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 && it <= 100_000 }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text(template.name, style = MaterialTheme.typography.titleLarge)
                Text("${template.sourceId ?: "#${template.id}"} · ${template.familyLabel}", style = MaterialTheme.typography.bodySmall)
            }
            item {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (template.concentrationPct == 100.0) "Konsentrat 100% w/w — tanpa pelarut" else "${template.concentrationPct}% massa (w/w)",
                            style = MaterialTheme.typography.titleSmall)
                        Text(template.description, style = MaterialTheme.typography.bodySmall)
                        Text("Belum ada clearance keselamatan/IFRA. Jangan langsung digunakan di kulit. " +
                            "Bahan pilihan berbeda dari sumber menjadikan resep adaptasi.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = draft.batchText, onValueChange = onBatchChange, label = { Text("Target batch (gram)") },
                    supportingText = { Text("Referensi ${grams(sourceTotal)} g. Persentase massa dipertahankan; tidak ada dilusi otomatis.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            itemsIndexed(template.ingredients) { index, item ->
                val selected = catalog.singleOrNull { it.id == draft.bindings[index] }
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(item.name, style = MaterialTheme.typography.titleSmall)
                        Text("${grams(item.amount / sourceTotal * 100)}% · ${batch?.let { grams(item.amount / sourceTotal * it) } ?: "—"} g",
                            style = MaterialTheme.typography.bodyMedium)
                        item.brand?.let { Text("Brand sumber: $it", style = MaterialTheme.typography.bodySmall) }
                        item.catalogCode?.let { Text("Kode menurut file: $it (belum diverifikasi ulang)", style = MaterialTheme.typography.bodySmall) }
                        if (item.requiresSelection) Text("Kebutuhan accord, bukan SKU produk terverifikasi. Pilih produk nyata.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        Text(selected?.let { "Pilihan: ${it.name} · ID ${it.id}" } ?: "Belum terhubung ke bahan katalog.",
                            style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { onSelectIngredient(index) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text("${if (selected == null) "Pilih" else "Ubah"} bahan: ${item.name}")
                        }
                    }
                }
            }
            item {
                Text("Sumber: ${template.sourceFile}", style = MaterialTheme.typography.bodySmall)
                Text("Tautan dicantumkan oleh dataset; bukan bukti endorsement atau sertifikasi.", style = MaterialTheme.typography.bodySmall)
                template.sourceUrls.forEachIndexed { index, url ->
                    TextButton(onClick = {
                        try {
                            uriHandler.openUri(url)
                        } catch (error: IllegalArgumentException) {
                            linkError = "Tautan tidak dapat dibuka. Periksa aplikasi browser."
                        } catch (error: android.content.ActivityNotFoundException) {
                            linkError = "Browser tidak tersedia untuk membuka sumber."
                        } catch (error: SecurityException) {
                            linkError = "Akses tautan sumber ditolak perangkat."
                        }
                    }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Referensi ${index + 1} · ${URI(url).host}")
                    }
                }
                linkError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
        draft.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (draft.application?.isAdaptation == true || template.ingredients.any { it.requiresSelection }) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                    value = draft.acknowledgeAdaptation, role = Role.Checkbox, onValueChange = onAcknowledge
                ), verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = draft.acknowledgeAdaptation, onCheckedChange = null)
                Text("Saya menyetujui adaptasi bahan pilihan; hasil bukan formula sumber persis.", style = MaterialTheme.typography.bodySmall)
            }
        }
        Text("Memuat mengganti draft aktif; tidak otomatis disimpan.", style = MaterialTheme.typography.bodySmall)
        Button(
            onClick = onApply, enabled = draft.application != null && (!draft.application.isAdaptation || draft.acknowledgeAdaptation),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) { Text("Gunakan di Formula Creator") }
        Spacer(Modifier.height(12.dp))
    }
}

private fun grams(value: Double): String = if (value != 0.0 && kotlin.math.abs(value) < 0.0001) {
    String.format(Locale.ROOT, "%.4g", value)
} else String.format(Locale.ROOT, "%.4f", value).trimEnd('0').trimEnd('.')
