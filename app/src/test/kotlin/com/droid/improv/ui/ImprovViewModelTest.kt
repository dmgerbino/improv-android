package com.droid.improv.ui

import android.os.Bundle
import com.droid.improv.DirectViewportReader
import com.droid.improv.ai.ImprovAppFunctionService
import com.droid.improv.ai.ImprovLocalAiEngine
import com.droid.improv.populateDirectByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.improv_android_ffi.AxisOrientation
import java.nio.ByteBuffer

class ImprovViewModelTest {

    @Test
    fun sampleModelInitializesCategoriesFormulasAndDirectBuffer() {
        val vm = ImprovViewModel()
        val state = vm.uiState.value

        assertEquals(1, state.rowCategories.size)
        assertEquals("Time", state.rowCategories.first().name)
        assertEquals(1, state.columnCategories.size)
        assertEquals("Product", state.columnCategories.first().name)
        assertEquals(1, state.pageCategories.size)
        assertEquals("Region", state.pageCategories.first().name)

        assertEquals(6, state.measures.size)
        assertEquals(3, state.formulas.size)
        assertTrue(state.selectedCellCoordinate.contains("Time:Q1"))
        assertTrue(state.selectedCellCoordinate.contains("Product:PixelBook"))
        assertTrue(state.selectedCellCoordinate.contains("Region:Americas"))
        assertTrue(state.selectedCellCoordinate.contains("Measure:Price"))
        assertTrue(state.directBufferBytesWritten > 16)
        assertFalse(state.directBufferSelectedValue.isNaN())
    }

    @Test
    fun pivotCategoryCycleAxisAndPageSelection() {
        val vm = ImprovViewModel()

        // Pivot Region to Row axis
        vm.pivotCategory("Region", AxisOrientation.ROW, 1)
        assertEquals(2, vm.uiState.value.rowCategories.size)
        assertEquals(0, vm.uiState.value.pageCategories.size)

        // Cycle Time axis from Row -> Column -> Page -> Row
        val timeTile = vm.uiState.value.rowCategories.first { it.name == "Time" }
        vm.cycleCategoryAxis(timeTile)
        assertTrue(vm.uiState.value.columnCategories.any { it.name == "Time" })

        val timeColTile = vm.uiState.value.columnCategories.first { it.name == "Time" }
        vm.cycleCategoryAxis(timeColTile)
        assertTrue(vm.uiState.value.pageCategories.any { it.name == "Time" })

        // Select page item on Time
        val timePageTile = vm.uiState.value.pageCategories.first { it.name == "Time" }
        val secondItem = timePageTile.items[1]
        vm.selectPageItem(timePageTile.id, secondItem.id)
        assertEquals(
            secondItem.id,
            vm.uiState.value.pageCategories.first { it.name == "Time" }.selectedPageItemId
        )

        // Add new category dimension
        vm.addCategory("Channel", "Online, Retail", AxisOrientation.PAGE)
        assertTrue(vm.uiState.value.pageCategories.any { it.name == "Channel" })
    }

    @Test
    fun formulasAutocompleteCellEditingViewPresetsAndReset() {
        val vm = ImprovViewModel()

        // Arrow-key and Tab cell focus navigation + scroll index tracking
        vm.moveCellFocus(0, 1)
        assertEquals(0, vm.uiState.value.selectedRow)
        assertEquals(1, vm.uiState.value.selectedCol)

        vm.moveTabFocus(reverse = false)
        assertEquals(0, vm.uiState.value.selectedRow)
        assertEquals(2, vm.uiState.value.selectedCol)

        vm.moveTabFocus(reverse = true)
        assertEquals(0, vm.uiState.value.selectedRow)
        assertEquals(1, vm.uiState.value.selectedCol)

        vm.updateScrollIndices(3, 1)
        assertEquals(3, vm.uiState.value.scrollRowIndex)
        assertEquals(1, vm.uiState.value.scrollColIndex)

        // Edit input cell (row 0 is Q1 · Price, an input measure)
        vm.selectCell(0, 0)
        assertFalse(vm.uiState.value.selectedCellIsDerived)
        vm.onSelectedCellEditBufferChanged("1499.0")
        vm.commitSelectedCellEdit()
        assertEquals(1499.0, vm.uiState.value.directBufferSelectedValue, 0.01)

        // Select a derived cell (row 3 is Q1 · Revenue) and verify derived protection
        vm.selectCell(3, 0)
        assertTrue(vm.uiState.value.selectedCellIsDerived)
        vm.onSelectedCellEditBufferChanged("999")
        vm.commitSelectedCellEdit()

        // Autocomplete and natural-language formula submission
        vm.onFormulaInputChanged("NetProfit equals ")
        vm.applyAutocompleteChip("Margin")
        vm.onFormulaInputChanged("NetProfit equals Margin minus UnitCost")
        assertTrue(vm.uiState.value.autocompleteSuggestions.isNotEmpty())
        vm.submitFormula()

        assertEquals(null, vm.uiState.value.formulaError)
        assertEquals(4, vm.uiState.value.formulas.size)
        assertTrue(vm.uiState.value.formulas.any { it.measureName == "NetProfit" })

        // Switch view presets
        vm.selectViewPreset("Product × Time")
        assertEquals("Product × Time", vm.uiState.value.activeViewName)
        vm.selectViewPreset("Region × Time")
        assertEquals("Region × Time", vm.uiState.value.activeViewName)
        vm.selectViewPreset("Time × Product")
        assertEquals("Time × Product", vm.uiState.value.activeViewName)

        // Reset sample model
        vm.resetSampleModel()
        assertEquals(3, vm.uiState.value.formulas.size)
    }

    @Test
    fun directViewportReaderReadsBinaryHeaderAndCells() {
        val vm = ImprovViewModel()
        val buf = ByteBuffer.allocateDirect(8192)
        val written = vm.engine.populateDirectByteBuffer(buf, 0u, 10u, 0u, 3u)
        assertTrue(written > 16)

        val reader = DirectViewportReader(buf)
        assertEquals(10, reader.rows)
        assertEquals(3, reader.cols)
        assertEquals(24, reader.totalRows)
        assertEquals(3, reader.totalCols)
        assertFalse(reader.numericValue(0, 0).isNaN())
        assertTrue(reader.measureId(0, 0) >= 100)
        assertTrue(reader.rowItemId(0, 0) > 0)
        assertTrue(reader.colItemId(0, 0) > 0)
        assertEquals(1.toByte(), reader.cellTag(0, 0))
        assertFalse(reader.isDerived(0, 0))
        assertTrue(reader.isDerived(3, 0))
        assertNotNull(vm.uiState.value.viewport.cellAt(0, 0))
    }

    @Test
    fun crossDeviceHandoffPackagesPersistableBundleUnder50KbAndRestoresExactState() {
        val sourceVm = ImprovViewModel(initialModelId = "q4_exec_forecast_3d")

        // 1. Pivot categories: Product -> Row(0), Region -> Row(1), Time -> Column(0)
        // Wait: let's test a custom arrangement with Row, Column, and Page tiles:
        // Product on ROW, Time on COLUMN, Region on PAGE (with EMEA selected)
        sourceVm.selectViewPreset("Product × Time")
        val regionTile = sourceVm.uiState.value.pageCategories.first { it.name == "Region" }
        val emeaItem = regionTile.items.first { it.name == "EMEA" }
        sourceVm.selectPageItem(regionTile.id, emeaItem.id)

        // 2. Select cell (row = 8 -> PixelPhone · Revenue, col = 2 -> Q3)
        sourceVm.selectCell(row = 8, col = 2)
        val expectedSemanticCoord = sourceVm.uiState.value.selectedCellCoordinate
        assertTrue(expectedSemanticCoord.contains("Product:PixelPhone"))
        assertTrue(expectedSemanticCoord.contains("Time:Q3"))
        assertTrue(expectedSemanticCoord.contains("Region:EMEA"))

        // 3. Update logical scroll indices and type an unsubmitted draft formula
        sourceVm.updateScrollIndices(rowIndex = 6, colIndex = 2)
        val draftFormula = "GrossMarginPct equals Margin divided by Revenue"
        sourceVm.onFormulaInputChanged(draftFormula)

        // 4. Package state into PersistableBundle (< 50 KB)
        val handoffSnapshot = sourceVm.captureHandoffState()
        assertTrue(handoffSnapshot.estimatedSizeBytes in 1 until ImprovViewModel.MAX_HANDOFF_BUNDLE_BYTES)
        assertEquals("q4_exec_forecast_3d", handoffSnapshot.activeModelId)
        assertEquals(listOf("Product"), handoffSnapshot.rowCategoryOrder)
        assertEquals(listOf("Time"), handoffSnapshot.columnCategoryOrder)
        assertEquals(listOf("Region"), handoffSnapshot.pageCategoryOrder)
        assertEquals(expectedSemanticCoord, handoffSnapshot.selectedCellCoordinate)
        assertEquals(6, handoffSnapshot.scrollRowIndex)
        assertEquals(2, handoffSnapshot.scrollColIndex)
        assertEquals(draftFormula, handoffSnapshot.draftFormula)

        val persistableBundle = sourceVm.buildHandoffPersistableBundle()
        assertNotNull(persistableBundle)

        // 5. Restore into a brand-new ViewModel (simulating target device in onCreate / onNewIntent)
        val targetVm = ImprovViewModel(initialModelId = "default_blank")
        assertTrue(targetVm.restoreHandoffState(persistableBundle))

        val restored = targetVm.uiState.value
        assertEquals("q4_exec_forecast_3d", restored.activeModelId)
        assertEquals("Product × Time", restored.activeViewName)
        assertEquals(listOf("Product"), restored.rowCategories.map { it.name })
        assertEquals(listOf("Time"), restored.columnCategories.map { it.name })
        assertEquals(listOf("Region"), restored.pageCategories.map { it.name })
        assertEquals(emeaItem.id, restored.pageCategories.first().selectedPageItemId)
        assertEquals(expectedSemanticCoord, restored.selectedCellCoordinate)
        assertEquals(8, restored.selectedRow)
        assertEquals(2, restored.selectedCol)
        assertEquals(6, restored.scrollRowIndex)
        assertEquals(2, restored.scrollColIndex)
        assertEquals(draftFormula, restored.formulaInput)
        // Verify the draft formula was NOT prematurely submitted into the model's committed formula list
        assertEquals(3, restored.formulas.size)
    }

    @Test
    fun analyticalStudioSliceStatsWhatIfShocksSchemaAuthoringAndCsvRoundTrip() {
        val vm = ImprovViewModel()

        // 1. Slice Analytics on Margin
        vm.selectAnalyticsMeasure("Margin")
        val marginStats = vm.uiState.value.sliceAnalytics
        assertEquals("Margin", marginStats.measureName)
        assertEquals(12u, marginStats.cellCount)
        assertTrue(marginStats.sum > 0.0)
        assertTrue(marginStats.max >= marginStats.min)
        assertEquals(4, marginStats.seriesValues.size)

        // 2. What-If Scenario Shock (+10% Price Lift)
        val initialRevenueSum = vm.engine.getSliceAnalytics("Revenue").sum
        vm.applyWhatIfMultiplier("Price", 1.10, "+10% Price Lift")
        val shockedRevenueSum = vm.engine.getSliceAnalytics("Revenue").sum
        assertTrue(shockedRevenueSum > initialRevenueSum)

        // 3. Dynamic Schema Authoring: add item to Time and add/delete an input measure
        vm.addCategoryItem("Time", "Q5")
        assertEquals(5, vm.uiState.value.rowCategories.first { it.name == "Time" }.items.size)

        vm.addInputMeasure("PromoSpend", "500.0")
        assertTrue(vm.uiState.value.measures.any { it.name == "PromoSpend" })
        vm.deleteMeasure("PromoSpend")
        assertFalse(vm.uiState.value.measures.any { it.name == "PromoSpend" })

        // 4. CSV Export and Import into the Multidimensional Cube
        vm.exportMeasureToCsvBuffer("Revenue")
        assertTrue(vm.uiState.value.csvImportExportBuffer.contains("Time,Product,Region,Revenue"))

        vm.onCsvBufferChanged(
            "Time,Product,Region,TargetQuota\nQ1,PixelBook,Americas,220000\nQ2,PixelPhone,EMEA,410000"
        )
        vm.importCsvFromBuffer()
        assertTrue(vm.uiState.value.measures.any { it.name == "TargetQuota" })

        // 5. Create Blank Workbook
        vm.createBlankWorkbook()
        assertEquals("Blank Workbook (Period × Department)", vm.uiState.value.activeViewName)
        assertTrue(vm.uiState.value.measures.any { it.name == "Budget" })
    }

    @Test
    fun uniqueAndroidAiStylusOcrSensorTabletopAndAppFunctionCapabilities() {
        val vm = ImprovViewModel()

        // 1. On-Device AI Executive Narrative
        vm.selectAnalyticsMeasure("Margin")
        assertTrue(vm.uiState.value.aiExecutiveNarrative.contains("On-Device AI Insight (Margin)"))

        // 2. On-Device AI Fuzzy NL -> Formal Improv Formula Synthesis & Evaluation
        vm.onAiPromptChanged("Calculate profit margin after a 7% tariff on COGS")
        vm.synthesizeFormulaWithLocalAi(autoEvaluate = true)
        assertTrue(vm.uiState.value.measures.any { it.name == "TariffAdjustedMargin" })

        vm.onAiPromptChanged("Operating profit after 15% SG&A overhead on Revenue")
        vm.synthesizeFormulaWithLocalAi(autoEvaluate = true)
        assertTrue(vm.uiState.value.measures.any { it.name == "OperatingProfit" })

        // 3. Stylus / Gesture What-If Scrubber
        val preScrubRev = vm.engine.getSliceAnalytics("Revenue").sum
        vm.onStylusScrubMeasureSelected("Price")
        vm.onStylusScrubMultiplierChanged(1.20f)
        vm.commitStylusWhatIfScrub()
        val postScrubRev = vm.engine.getSliceAnalytics("Revenue").sum
        assertTrue(postScrubRev > preScrubRev)

        // 4. Camera / ML Kit OCR Table Ingestor -> improv_storage_csv
        vm.onScannerRawOcrChanged(
            "Time | Product | Region | OcrUnits\nQ1 | PixelBook | Americas | $1,450\nQ2 | PixelPhone | EMEA | $2,890"
        )
        vm.ingestScannedTableOcrToCube()
        assertTrue(vm.uiState.value.measures.any { it.name == "OcrUnits" })

        // 5. Live Android Sensor Telemetry Cube Stream
        vm.streamAndroidSensorTelemetryToCube()
        assertTrue(vm.uiState.value.measures.any { it.name == "SensorTelemetry" })

        // 6. Foldable Tabletop Posture Toggle
        assertFalse(vm.uiState.value.isTabletopPostureOverride)
        vm.toggleTabletopPostureOverride()
        assertTrue(vm.uiState.value.isTabletopPostureOverride)

        // 7. Android 17 AppFunctionService Execution
        val appFnService = ImprovAppFunctionService()
        val resBundle = appFnService.executeAppFunction(
            engine = vm.engine,
            functionName = ImprovAppFunctionService.FN_QUERY_SLICE_METRIC,
            args = Bundle(),
        )
        assertNotNull(resBundle)
        assertTrue(ImprovLocalAiEngine.parseScannedTableTextToCsv("A\tB\nX\t$100").contains("X,100"))
    }
}
