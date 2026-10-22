package com.parfumecreator.presentation.ui.components

import android.graphics.Paint
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parfumecreator.domain.model.*
import com.parfumecreator.presentation.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SplGauge(
    splScore: Double,
    splLabel: SplLabel,
    modifier: Modifier = Modifier
) {
    val safeScore = splScore.takeIf { it.isFinite() }?.coerceIn(0.0, 100.0) ?: 0.0
    val animatedProgress by animateFloatAsState(
        targetValue = (safeScore / 100.0).toFloat(),
        animationSpec = tween(600),
        label = "spl_gauge"
    )

    val gaugeColor = when (splLabel) {
        SplLabel.SUBTLE -> Color(0xFF7BC8A4)
        SplLabel.MODERATE -> PerfumePrimary
        SplLabel.STRONG -> Color(0xFFE59866)
        SplLabel.INTENSE -> Color(0xFFE74C3C)
    }

    Column(
        modifier = modifier.semantics {
            contentDescription = "SPL heuristik belum tervalidasi: ${safeScore.toInt()} dari 100, ${splLabel.displayName}. Bukan hasil uji sillage atau ketahanan."
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "SPL (heuristik)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Belum tervalidasi; bukan ukuran sillage atau ketahanan",
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 11.sp,
                    color = PerfumeOutline
                )
            }
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = gaugeColor.copy(alpha = 0.2f)
            ) {
                Text(
                    text = "${safeScore.toInt()}/100 • ${splLabel.displayName}",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = gaugeColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color(0xFF282315))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(5.dp))
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(PerfumePrimary, gaugeColor)
                        )
                    )
            )
        }
    }
}

@Composable
fun NoteBreakdownBar(
    topRatio: Double,
    middleRatio: Double,
    baseRatio: Double,
    modifier: Modifier = Modifier
) {
    val animatedTop by animateFloatAsState(targetValue = topRatio.toFloat().coerceIn(0f, 1f), label = "top_anim")
    val animatedMid by animateFloatAsState(targetValue = middleRatio.toFloat().coerceIn(0f, 1f), label = "mid_anim")
    val animatedBase by animateFloatAsState(targetValue = baseRatio.toFloat().coerceIn(0f, 1f), label = "base_anim")

    val topPct = (topRatio * 100).toInt()
    val midPct = (middleRatio * 100).toInt()
    val basePct = (baseRatio * 100).toInt()

    Column(
        modifier = modifier.semantics {
            contentDescription = "Note Breakdown: Top $topPct%, Heart $midPct%, Base $basePct%"
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Note Architecture",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Top $topPct% | Mid $midPct% | Base $basePct%",
                style = MaterialTheme.typography.bodyMedium,
                color = PerfumeOnBackground,
                fontSize = 12.sp
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
        ) {
            if (animatedTop > 0) {
                Box(
                    modifier = Modifier
                        .weight(animatedTop)
                        .fillMaxHeight()
                        .background(Color(0xFFF5C842))
                )
            }
            if (animatedMid > 0) {
                Box(
                    modifier = Modifier
                        .weight(animatedMid)
                        .fillMaxHeight()
                        .background(Color(0xFFE8B4D0))
                )
            }
            if (animatedBase > 0) {
                Box(
                    modifier = Modifier
                        .weight(animatedBase)
                        .fillMaxHeight()
                        .background(Color(0xFF8B6914))
                )
            }
            if (topRatio <= 0.0 && middleRatio <= 0.0 && baseRatio <= 0.0) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF282315))
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            LegendItem(name = "Top ($topPct%)", color = Color(0xFFF5C842))
            LegendItem(name = "Heart ($midPct%)", color = Color(0xFFE8B4D0))
            LegendItem(name = "Base ($basePct%)", color = Color(0xFF8B6914))
        }
    }
}

/**
 * Production Fragrance Wheel Radar / Spider Chart Visualization.
 * Renders 4 primary axes (Floral, Woody, Oriental, Fresh) with animated polygon fill,
 * axis grid rings, colored vertices, and contextual olfactory direction companion.
 */
@Composable
fun FragranceWheelCard(
    wheelResult: FragranceWheelResult,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (!wheelResult.hasFragranceProfile) {
        Box(modifier = modifier.fillMaxWidth().glassCard().padding(16.dp)) {
            Text(
                text = "Belum ada profil aroma. Tambahkan bahan aroma selain pelarut.",
                style = MaterialTheme.typography.bodyMedium,
                color = PerfumeOutline
            )
        }
        return
    }
    val floralPct = wheelResult.primaryAxisPercentages[PrimaryFragranceWheel.FLORAL] ?: 0.0
    val woodyPct = wheelResult.primaryAxisPercentages[PrimaryFragranceWheel.WOODY] ?: 0.0
    val orientalPct = wheelResult.primaryAxisPercentages[PrimaryFragranceWheel.ORIENTAL] ?: 0.0
    val freshPct = wheelResult.primaryAxisPercentages[PrimaryFragranceWheel.FRESH] ?: 0.0

    val animFloral by animateFloatAsState(targetValue = (floralPct / 100.0).toFloat().coerceIn(0f, 1f), label = "floral_anim")
    val animWoody by animateFloatAsState(targetValue = (woodyPct / 100.0).toFloat().coerceIn(0f, 1f), label = "woody_anim")
    val animOriental by animateFloatAsState(targetValue = (orientalPct / 100.0).toFloat().coerceIn(0f, 1f), label = "oriental_anim")
    val animFresh by animateFloatAsState(targetValue = (freshPct / 100.0).toFloat().coerceIn(0f, 1f), label = "fresh_anim")

    val talkBackDesc = "Fragrance Wheel: Dominan ${wheelResult.primary.displayName} (${wheelResult.primaryAxisPercentages[wheelResult.primary]?.toInt() ?: 0}%), Arah ${wheelResult.olfactoryDirection}. Rincian 4 sumbu: Floral ${floralPct.toInt()}%, Woody ${woodyPct.toInt()}%, Oriental ${orientalPct.toInt()}%, Fresh ${freshPct.toInt()}%."

    Box(
        modifier = modifier
            .fillMaxWidth()
            .glassCard()
            .semantics { contentDescription = talkBackDesc }
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Fragrance Wheel Direction",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Komposisi massa: ${wheelResult.olfactoryDirection}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PerfumePrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (onClick != null) {
                    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "Lihat Detail Roda Aroma",
                            tint = PerfumePrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Canvas Radar Chart
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .size(190.dp)
                ) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val maxRadius = (size.minDimension / 2f) * 0.76f

                    val ringColor = Color(0x2E8A7C6A)
                    val axisColor = Color(0x44D4A853)

                    // 1. Concentric Reference Grid Circles (25%, 50%, 75%, 100%)
                    val levels = listOf(0.25f, 0.50f, 0.75f, 1.0f)
                    levels.forEach { level ->
                        drawCircle(
                            color = ringColor,
                            radius = maxRadius * level,
                            center = center,
                            style = Stroke(width = 1f)
                        )
                    }

                    // 4 Axis angles (radians):
                    // Top (Floral): -PI/2
                    // Right (Woody): 0
                    // Bottom (Oriental): PI/2
                    // Left (Fresh): PI
                    val angles = listOf(
                        -Math.PI / 2.0, // Top - Floral
                        0.0,            // Right - Woody
                        Math.PI / 2.0,  // Bottom - Oriental
                        Math.PI         // Left - Fresh
                    )

                    // Draw radial axis lines
                    angles.forEach { angle ->
                        val endX = center.x + (maxRadius * cos(angle)).toFloat()
                        val endY = center.y + (maxRadius * sin(angle)).toFloat()
                        drawLine(
                            color = axisColor,
                            start = center,
                            end = Offset(endX, endY),
                            strokeWidth = 1f
                        )
                    }

                    // 2. Compute radar polygon vertices based on animated values
                    val values = listOf(animFloral, animWoody, animOriental, animFresh)

                    val hasData = values.any { it > 0f }
                    if (hasData) {
                        val polygonPath = Path()
                        val vertexOffsets = mutableListOf<Offset>()

                        values.forEachIndexed { i, v ->
                            val angle = angles[i]
                            val r = maxRadius * v
                            val x = center.x + (r * cos(angle)).toFloat()
                            val y = center.y + (r * sin(angle)).toFloat()
                            val pt = Offset(x, y)
                            vertexOffsets.add(pt)
                            if (i == 0) polygonPath.moveTo(x, y) else polygonPath.lineTo(x, y)
                        }
                        polygonPath.close()

                        // Draw filled radar polygon
                        drawPath(
                            path = polygonPath,
                            brush = Brush.radialGradient(
                                colors = listOf(PerfumePrimary.copy(alpha = 0.55f), Color(0x33D4A853)),
                                center = center,
                                radius = maxRadius
                            )
                        )
                        // Draw polygon boundary stroke
                        drawPath(
                            path = polygonPath,
                            color = PerfumePrimary,
                            style = Stroke(width = 2.dp.toPx())
                        )

                        // Draw vertex dots with family colors
                        val vertexColors = listOf(
                            Color(0xFFE8B4D0), // Floral
                            Color(0xFF8B6914), // Woody
                            Color(0xFFC4612A), // Oriental
                            Color(0xFF7BC8A4)  // Fresh
                        )
                        vertexOffsets.forEachIndexed { i, pt ->
                            if (values[i] > 0f) {
                                drawCircle(
                                    color = vertexColors[i],
                                    radius = 4.dp.toPx(),
                                    center = pt
                                )
                                drawCircle(
                                    color = PerfumeOnBackground,
                                    radius = 2.dp.toPx(),
                                    center = pt
                                )
                            }
                        }
                    }
                }

                // 4 Overlay Text Labels positioned around the radar
                // Top: Floral
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 2.dp)
                ) {
                    AxisLabel(
                        name = "FLORAL",
                        pct = floralPct.toInt(),
                        color = Color(0xFFE8B4D0)
                    )
                }

                // Right: Woody
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 4.dp)
                ) {
                    AxisLabel(
                        name = "WOODY",
                        pct = woodyPct.toInt(),
                        color = Color(0xFF8B6914)
                    )
                }

                // Bottom: Oriental
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 2.dp)
                ) {
                    AxisLabel(
                        name = "ORIENTAL",
                        pct = orientalPct.toInt(),
                        color = Color(0xFFC4612A)
                    )
                }

                // Left: Fresh
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 4.dp)
                ) {
                    AxisLabel(
                        name = "FRESH",
                        pct = freshPct.toInt(),
                        color = Color(0xFF7BC8A4)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Textual Companion Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(android.graphics.Color.parseColor(wheelResult.dominantSubFamily.hexColor)))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Primer: ${wheelResult.primary.displayName} (${wheelResult.dominantSubFamily.displayName})",
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 11.sp,
                        color = PerfumeOnBackground
                    )
                }

                if (wheelResult.secondaryWheel != null && wheelResult.secondaryWheel != wheelResult.primary) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = PerfumeSecondaryContainer
                    ) {
                        Text(
                            text = "Pengaruh: ${wheelResult.secondaryWheel.displayName}",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = PerfumeOnSecondaryContainer,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AxisLabel(
    name: String,
    pct: Int,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            fontSize = 10.sp
        )
        Text(
            text = "$pct%",
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 9.sp,
            color = PerfumeOutline
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FragranceWheelDetailBottomSheet(
    wheelResult: FragranceWheelResult,
    calculatedItems: List<CalculatedItemResult>,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PerfumeSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    Text(
                        text = "Analisis Profil Roda Aroma",
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = FontFamily.Serif
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = PerfumePrimaryContainer
                    ) {
                        Text(
                            text = wheelResult.olfactoryDirection,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = PerfumeOnPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Tutup Detail", tint = PerfumeOutline)
                }
            }

            Text(
                text = "Distribusi aroma berdasarkan berat bahan dan klasifikasi keluarga olfaktori:",
                style = MaterialTheme.typography.bodyMedium,
                color = PerfumeOutline
            )

            // 4 Major Axes Summary Bars
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(cornerRadius = 12)
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "4 Sumbu Utama (berdasarkan massa katalog)",
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    PrimaryFragranceWheel.entries.forEach { axis ->
                        val pct = wheelResult.primaryAxisPercentages[axis] ?: 0.0
                        val scoreG = wheelResult.primaryAxisScores[axis] ?: 0.0
                        val axisColor = Color(android.graphics.Color.parseColor(axis.hexColor))

                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = axis.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontSize = 12.sp,
                                    color = axisColor,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = String.format("%.2f g (%.1f%%)", scoreG, pct),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontSize = 11.sp,
                                    color = PerfumeOutline
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            LinearProgressIndicator(
                                progress = { (pct / 100.0).toFloat().coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = axisColor,
                                trackColor = PerfumeSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Ingredient Contributions breakdown
            Text(
                text = "Kontribusi Tiap Bahan (${calculatedItems.filter { !it.ingredient.isSolventOrDiluent }.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val nonSolvents = calculatedItems.filter { !it.ingredient.isSolventOrDiluent }
                items(nonSolvents) { item ->
                    val familyColor = Color(android.graphics.Color.parseColor(item.ingredient.fragranceFamily.hexColor))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassCard(cornerRadius = 8)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(familyColor)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = item.ingredient.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${item.ingredient.fragranceFamily.displayName} • ${item.ingredient.noteType.displayName}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontSize = 10.sp,
                                    color = PerfumeOutline
                                )
                            }
                        }
                        Text(
                            text = String.format("%.2f g (%.1f%%)", item.amountInGram, item.percentageOfFragrance),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = PerfumePrimary,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendItem(name: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = name, style = MaterialTheme.typography.bodyMedium, fontSize = 11.sp)
    }
}

@Composable
fun FilterDropdown(
    selectedOption: String,
    options: List<Pair<String?, String>>,
    onOptionSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val isFiltered = !selectedOption.startsWith("Semua")

    Box(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (isFiltered) PerfumePrimary.copy(alpha = 0.15f) else PerfumeSurfaceVariant,
            border = BorderStroke(
                width = 1.dp,
                color = if (isFiltered) PerfumePrimary else PerfumeOutline.copy(alpha = 0.3f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = selectedOption,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (isFiltered) FontWeight.Bold else FontWeight.Medium,
                    color = if (isFiltered) PerfumePrimary else PerfumeOnBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = if (isFiltered) PerfumePrimary else PerfumeOutline,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(PerfumeSurface)
                .heightIn(max = 380.dp)
        ) {
            options.forEach { (key, display) ->
                val isSelected = (selectedOption == display)
                DropdownMenuItem(
                    text = {
                        Text(
                            text = display,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) PerfumePrimary else PerfumeOnBackground,
                            fontSize = 13.sp
                        )
                    },
                    leadingIcon = if (isSelected) {
                        { Icon(Icons.Default.Check, contentDescription = null, tint = PerfumePrimary, modifier = Modifier.size(16.dp)) }
                    } else null,
                    onClick = {
                        onOptionSelected(key)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun UnitDropdown(
    selectedUnit: MeasurementUnit,
    onUnitSelected: (MeasurementUnit) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = PerfumeSurfaceVariant,
            border = BorderStroke(1.dp, PerfumeOutline.copy(alpha = 0.3f)),
            modifier = Modifier
                .height(48.dp)
                .clickable { expanded = true }
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = selectedUnit.symbol,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = PerfumePrimary
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Pilih Satuan",
                    tint = PerfumeOutline,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(PerfumeSurface)
                .border(1.dp, PerfumeOutline.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
        ) {
            MeasurementUnit.entries.forEach { unit ->
                val isSelected = (selectedUnit == unit)
                val unitFullName = when (unit) {
                    MeasurementUnit.GRAM -> "Gram (g)"
                    MeasurementUnit.MILLIGRAM -> "Miligram (mg)"
                    MeasurementUnit.MILLILITER -> "Mililiter (ml)"
                }
                DropdownMenuItem(
                    text = {
                        Text(
                            text = unitFullName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) PerfumePrimary else PerfumeOnBackground,
                            fontSize = 13.sp
                        )
                    },
                    leadingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = PerfumePrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else null,
                    onClick = {
                        onUnitSelected(unit)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun MeasurementUnitDropdown(
    selectedUnit: MeasurementUnit,
    onUnitSelected: (MeasurementUnit) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val displayName = when (selectedUnit) {
        MeasurementUnit.GRAM -> "Gram (g)"
        MeasurementUnit.MILLIGRAM -> "Miligram (mg)"
        MeasurementUnit.MILLILITER -> "Mililiter (ml)"
    }

    Box(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = PerfumeSurfaceVariant,
            border = BorderStroke(1.dp, PerfumeOutline.copy(alpha = 0.3f)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = PerfumePrimary
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = PerfumeOutline,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(PerfumeSurface)
                .border(1.dp, PerfumeOutline.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
        ) {
            MeasurementUnit.entries.forEach { unit ->
                val isSelected = (selectedUnit == unit)
                val unitFullName = when (unit) {
                    MeasurementUnit.GRAM -> "Gram (g)"
                    MeasurementUnit.MILLIGRAM -> "Miligram (mg)"
                    MeasurementUnit.MILLILITER -> "Mililiter (ml)"
                }
                DropdownMenuItem(
                    text = {
                        Text(
                            text = unitFullName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) PerfumePrimary else PerfumeOnBackground,
                            fontSize = 13.sp
                        )
                    },
                    leadingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = PerfumePrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else null,
                    onClick = {
                        onUnitSelected(unit)
                        expanded = false
                    }
                )
            }
        }
    }
}
