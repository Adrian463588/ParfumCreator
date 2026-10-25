package com.parfumecreator

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.parfumecreator.core.formula.FormulaTemplateLoader
import com.parfumecreator.domain.model.FormulaTemplate
import com.parfumecreator.presentation.ui.formula.FormulaTemplatesBottomSheet
import com.parfumecreator.presentation.ui.formula.FormulaUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real packaged assets and library UI; execution requires an Android device/emulator. */
@RunWith(AndroidJUnit4::class)
class RecipeTemplateLibraryAndroidTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun packagedLibraryIncludesAllSourcesAndIsCached() {
        val loader = FormulaTemplateLoader(InstrumentationRegistry.getInstrumentation().targetContext)
        val templates = loader.getTemplates()
        assertEquals(232, templates.size)
        assertEquals(100, templates.count { it.sourceId?.startsWith("NAT-") == true })
        assertEquals(100, templates.count { it.sourceId?.startsWith("BIB-") == true })
        assertTrue(loader.errors.isEmpty())
        assertTrue(templates === loader.getTemplates())
    }

    @Test
    fun librarySearchesTheLastSuppliedRecipeAndOpensItsPreview() {
        val templates = FormulaTemplateLoader(InstrumentationRegistry.getInstrumentation().targetContext).getTemplates()
        var selected: FormulaTemplate? = null
        compose.setContent {
            MaterialTheme {
                FormulaTemplatesBottomSheet(
                    state = FormulaUiState(templates = templates, isLoadingTemplates = false),
                    onDismiss = {}, onPreview = { selected = it }, onClosePreview = {},
                    onBatchChange = {}, onSelectIngredient = {}, onAcknowledge = {}, onApply = {}
                )
            }
        }
        compose.onNodeWithText("Cari resep, ID, bahan atau brand").performTextInput("BIB-100")
        val family = templates.single { it.sourceId == "BIB-100" }.familyLabel
        compose.onNodeWithText("BIB-100 · Bibit sintetis · $family").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Detail & gunakan").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("BIB-100", selected?.sourceId) }
    }
}
