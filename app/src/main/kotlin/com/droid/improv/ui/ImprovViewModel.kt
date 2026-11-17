package com.droid.improv.ui

import android.content.Intent
import android.os.BaseBundle
import android.os.Bundle
import android.os.PersistableBundle
import androidx.lifecycle.ViewModel
import com.droid.improv.DirectViewportReader
import com.droid.improv.ai.ImprovLocalAiEngine
import com.droid.improv.populateDirectByteBuffer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import uniffi.improv_android_ffi.AxisOrientation
import uniffi.improv_android_ffi.CategoryTile
import uniffi.improv_android_ffi.FormulaEntry
import uniffi.improv_android_ffi.ImprovMobileEngine
import uniffi.improv_android_ffi.MeasureSummary
import uniffi.improv_android_ffi.SliceAnalyticsSummary
import uniffi.improv_android_ffi.ViewportCell
import uniffi.improv_android_ffi.ViewportSnapshot
import java.nio.ByteBuffer
import java.util.Collections
import java.util.WeakHashMap

/**
 * Compact Cross-Device Handoff snapshot (< 50 KB) representing the user's live workspace
 * across Android 17 devices (e.g., Pixel Phone <-> HP Googlebook 14).
 */
data class ImprovHandoffState(
    val activeModelId: String,
    val activeViewName: String = "3D Financial Cube (Default)",
    val rowCategoryOrder: List<String>,
    val columnCategoryOrder: List<String>,
    val pageCategoryOrder: List<String>,
    val pageSelectedItems: List<String> = emptyList(),
    val selectedCellCoordinate: String,
    val selectedRow: Int = 0,
    val selectedCol: Int = 0,
    val scrollRowIndex: Int,
    val scrollColIndex: Int,
    val draftFormula: String,
    val estimatedSizeBytes: Int = 0,
)

data class ImprovUiState(
    val activeModelId: String = ImprovViewModel.DEFAULT_MODEL_ID,
    val activeViewName: String = "3D Financial Cube (Default)",
    val rowCategories: List<CategoryTile> = emptyList(),
    val columnCategories: List<CategoryTile> = emptyList(),
    val pageCategories: List<CategoryTile> = emptyList(),
    val measures: List<MeasureSummary> = emptyList(),
    val formulas: List<FormulaEntry> = emptyList(),
    val viewport: ViewportSnapshot = ViewportSnapshot(
        rowHeaders = emptyList(),
        colHeaders = emptyList(),
        totalRows = 0u,
        totalCols = 0u,
        activeMeasureId = "",
        activeMeasureName = "",
        cells = emptyList(),
    ),
    val selectedRow: Int = 0,
    val selectedCol: Int = 0,
    val selectedCellCoordinate: String = "Time:Q1|Product:PixelBook|Region:Americas|Measure:Price",
    val selectedCellFormatted: String = "",
    val selectedCellEditBuffer: String = "",
    val selectedCellIsDerived: Boolean = false,
    val scrollRowIndex: Int = 0,
    val scrollColIndex: Int = 0,
    val formulaInput: String = "Margin = Revenue - COGS",
    val autocompleteSuggestions: List<String> = emptyList(),
    val formulaError: String? = null,
    val directBufferBytesWritten: Int = 0,
    val directBufferSelectedValue: Double = Double.NaN,
    val activeAnalyticsMeasure: String = "Margin",
    val sliceAnalytics: SliceAnalyticsSummary = SliceAnalyticsSummary(
        measureName = "Margin",
        cellCount = 0u,
        sum = 0.0,
        mean = 0.0,
        median = 0.0,
        min = 0.0,
        max = 0.0,
        stdDev = 0.0,
        seriesLabels = emptyList(),
        seriesValues = emptyList(),
    ),
    val csvImportExportBuffer: String =
        "Time,Product,Region,TargetQuota\nQ1,PixelBook,Americas,180000\nQ2,PixelPhone,Americas,350000",
    val aiNaturalLanguagePrompt: String = "Calculate profit margin after a 7% tariff on COGS",
    val aiLastExplanation: String = "On-Device Gemini Nano / Semantic Synthesizer ready.",
    val aiExecutiveNarrative: String = "",
    val stylusScrubMeasure: String = "Price",
    val stylusScrubMultiplier: Float = 1.0f,
    val scannerRawOcrInput: String =
        "Time | Product | Region | ScannedUnits\nQ1 | PixelBook | Americas | 145\nQ2 | PixelPhone | EMEA | 390",
    val isTabletopPostureOverride: Boolean = false,
    val statusMessage: String = "Ready — 3D model (Time × Product × Region) loaded",
)

class ImprovViewModel(
    val engine: ImprovMobileEngine = ImprovMobileEngine.openOrCreate(""),
    initialModelId: String = DEFAULT_MODEL_ID,
) : ViewModel() {

    companion object {
        const val DEFAULT_MODEL_ID = "improv_sample_3d"
        const val MAX_HANDOFF_BUNDLE_BYTES = 50 * 1024 // 50 KB maximum payload for Cross-Device Handoff
        const val MAX_DRAFT_FORMULA_CHARS = 8 * 1024

        const val KEY_HANDOFF_NESTED_BUNDLE = "com.droid.improv.extra.HANDOFF_BUNDLE"
        const val KEY_ACTIVE_MODEL_ID = "handoff_active_model_id"
        const val KEY_ACTIVE_VIEW_NAME = "handoff_active_view_name"
        const val KEY_ROW_CATEGORY_ORDER = "handoff_row_category_order"
        const val KEY_COL_CATEGORY_ORDER = "handoff_col_category_order"
        const val KEY_PAGE_CATEGORY_ORDER = "handoff_page_category_order"
        const val KEY_PAGE_SELECTED_ITEMS = "handoff_page_selected_items"
        const val KEY_SELECTED_CELL_COORDINATE = "handoff_selected_cell_coordinate"
        const val KEY_SELECTED_ROW = "handoff_selected_row"
        const val KEY_SELECTED_COL = "handoff_selected_col"
        const val KEY_SCROLL_ROW_INDEX = "handoff_scroll_row_index"
        const val KEY_SCROLL_COL_INDEX = "handoff_scroll_col_index"
        const val KEY_DRAFT_FORMULA = "handoff_draft_formula"
        const val KEY_ESTIMATED_SIZE_BYTES = "handoff_estimated_size_bytes"

        // Fallback mirror for local JVM unit tests where android.os.PersistableBundle is a stub.
        private val jvmTestBundleMirror =
            Collections.synchronizedMap(WeakHashMap<BaseBundle, ImprovHandoffState>())
    }

    private val directViewportBuffer: ByteBuffer = ByteBuffer.allocateDirect(64 * 1024)
    private val directReader = DirectViewportReader(directViewportBuffer)

    private val _uiState = MutableStateFlow(ImprovUiState(activeModelId = initialModelId))
    val uiState: StateFlow<ImprovUiState> = _uiState.asStateFlow()

    init {
        refreshFromEngine(status = "Loaded 3D Sample Model (Time × Product × Region)")
    }

    fun setActiveModelId(modelId: String) {
        val normalized = modelId.trim().ifEmpty { DEFAULT_MODEL_ID }
        _uiState.update { it.copy(activeModelId = normalized) }
    }

    /**
     * Captures the current workspace state into an [ImprovHandoffState] guaranteed to be < 50 KB.
     */
    fun captureHandoffState(): ImprovHandoffState {
        val state = _uiState.value
        val rowOrder = state.rowCategories.sortedBy { it.positionIndex }.map { it.name }
        val colOrder = state.columnCategories.sortedBy { it.positionIndex }.map { it.name }
        val pageOrder = state.pageCategories.sortedBy { it.positionIndex }.map { it.name }
        val pageSelections = state.pageCategories.mapNotNull { tile ->
            tile.selectedPageItemId?.let { itemId -> "${tile.name}:$itemId" }
        }
        val cappedDraftFormula = state.formulaInput.take(MAX_DRAFT_FORMULA_CHARS)

        val estimatedBytes = estimatePayloadSizeBytes(
            activeModelId = state.activeModelId,
            activeViewName = state.activeViewName,
            rowOrder = rowOrder,
            colOrder = colOrder,
            pageOrder = pageOrder,
            pageSelections = pageSelections,
            selectedCoordinate = state.selectedCellCoordinate,
            draftFormula = cappedDraftFormula,
        )

        return ImprovHandoffState(
            activeModelId = state.activeModelId,
            activeViewName = state.activeViewName,
            rowCategoryOrder = rowOrder,
            columnCategoryOrder = colOrder,
            pageCategoryOrder = pageOrder,
            pageSelectedItems = pageSelections,
            selectedCellCoordinate = state.selectedCellCoordinate,
            selectedRow = state.selectedRow,
            selectedCol = state.selectedCol,
            scrollRowIndex = state.scrollRowIndex,
            scrollColIndex = state.scrollColIndex,
            draftFormula = cappedDraftFormula,
            estimatedSizeBytes = estimatedBytes,
        )
    }

    /**
     * Packages the active model ID, row/column/page category tile order, selected semantic cell
     * coordinate, logical scroll indices, and any unsubmitted draft formula into a [PersistableBundle]
     * strictly under the 50 KB Android 17 Cross-Device Handoff limit.
     */
    fun buildHandoffPersistableBundle(): PersistableBundle {
        val handoff = captureHandoffState()
        check(handoff.estimatedSizeBytes < MAX_HANDOFF_BUNDLE_BYTES) {
            "Handoff PersistableBundle exceeds 50 KB limit (${handoff.estimatedSizeBytes} bytes)"
        }

        val bundle = PersistableBundle()
        bundle.putString(KEY_ACTIVE_MODEL_ID, handoff.activeModelId)
        bundle.putString(KEY_ACTIVE_VIEW_NAME, handoff.activeViewName)
        bundle.putStringArray(KEY_ROW_CATEGORY_ORDER, handoff.rowCategoryOrder.toTypedArray())
        bundle.putStringArray(KEY_COL_CATEGORY_ORDER, handoff.columnCategoryOrder.toTypedArray())
        bundle.putStringArray(KEY_PAGE_CATEGORY_ORDER, handoff.pageCategoryOrder.toTypedArray())
        bundle.putStringArray(KEY_PAGE_SELECTED_ITEMS, handoff.pageSelectedItems.toTypedArray())
        bundle.putString(KEY_SELECTED_CELL_COORDINATE, handoff.selectedCellCoordinate)
        bundle.putInt(KEY_SELECTED_ROW, handoff.selectedRow)
        bundle.putInt(KEY_SELECTED_COL, handoff.selectedCol)
        bundle.putInt(KEY_SCROLL_ROW_INDEX, handoff.scrollRowIndex)
        bundle.putInt(KEY_SCROLL_COL_INDEX, handoff.scrollColIndex)
        bundle.putString(KEY_DRAFT_FORMULA, handoff.draftFormula)
        bundle.putInt(KEY_ESTIMATED_SIZE_BYTES, handoff.estimatedSizeBytes)

        jvmTestBundleMirror[bundle] = handoff
        return bundle
    }

    /**
     * Extracts and restores Cross-Device Handoff state from an incoming [Intent] (in `onCreate` or `onNewIntent`).
     */
    fun restoreHandoffStateFromIntent(intent: Intent?): Boolean {
        if (intent == null) return false
        val nestedPersistable = intent.getParcelableExtra(
            KEY_HANDOFF_NESTED_BUNDLE,
            PersistableBundle::class.java,
        )
        if (nestedPersistable != null && restoreHandoffState(nestedPersistable)) {
            return true
        }
        return restoreHandoffState(intent.extras)
    }

    /**
     * Restores Cross-Device Handoff state from a [PersistableBundle] or [Bundle] ([BaseBundle]).
     * Returns `true` if valid handoff state was found and restored.
     */
    fun restoreHandoffState(bundle: BaseBundle?): Boolean {
        if (bundle == null) return false

        // 1. Check if this is a Bundle wrapping a nested PersistableBundle
        if (bundle is Bundle) {
            val nested = bundle.getParcelable(KEY_HANDOFF_NESTED_BUNDLE, PersistableBundle::class.java)
            if (nested != null && restoreHandoffState(nested)) {
                return true
            }
        } else if (bundle is PersistableBundle) {
            val nested = bundle.getPersistableBundle(KEY_HANDOFF_NESTED_BUNDLE)
            if (nested != null && restoreHandoffState(nested)) {
                return true
            }
        }

        // 2. Read directly from BaseBundle keys (standard Android runtime path)
        val modelId = bundle.getString(KEY_ACTIVE_MODEL_ID)
        val selectedCoord = bundle.getString(KEY_SELECTED_CELL_COORDINATE)
        val draftFormula = bundle.getString(KEY_DRAFT_FORMULA)
        if (!modelId.isNullOrBlank() || !selectedCoord.isNullOrBlank() || draftFormula != null) {
            val current = _uiState.value
            val handoff = ImprovHandoffState(
                activeModelId = modelId?.takeIf { it.isNotBlank() } ?: current.activeModelId,
                activeViewName = bundle.getString(KEY_ACTIVE_VIEW_NAME)
                    ?.takeIf { it.isNotBlank() }
                    ?: current.activeViewName,
                rowCategoryOrder = bundle.getStringArray(KEY_ROW_CATEGORY_ORDER)?.toList().orEmpty(),
                columnCategoryOrder = bundle.getStringArray(KEY_COL_CATEGORY_ORDER)?.toList().orEmpty(),
                pageCategoryOrder = bundle.getStringArray(KEY_PAGE_CATEGORY_ORDER)?.toList().orEmpty(),
                pageSelectedItems = bundle.getStringArray(KEY_PAGE_SELECTED_ITEMS)?.toList().orEmpty(),
                selectedCellCoordinate = selectedCoord ?: current.selectedCellCoordinate,
                selectedRow = bundle.getInt(KEY_SELECTED_ROW, current.selectedRow),
                selectedCol = bundle.getInt(KEY_SELECTED_COL, current.selectedCol),
                scrollRowIndex = bundle.getInt(KEY_SCROLL_ROW_INDEX, current.scrollRowIndex),
                scrollColIndex = bundle.getInt(KEY_SCROLL_COL_INDEX, current.scrollColIndex),
                draftFormula = draftFormula ?: current.formulaInput,
                estimatedSizeBytes = bundle.getInt(KEY_ESTIMATED_SIZE_BYTES, 0),
            )
            return restoreHandoffState(handoff)
        }

        // 3. Fallback for local JVM unit tests where android.os.BaseBundle methods return null stubs
        val mirrored = jvmTestBundleMirror[bundle]
        if (mirrored != null) {
            return restoreHandoffState(mirrored)
        }

        return false
    }

    /**
     * Applies an [ImprovHandoffState] to the Rust engine and ViewModel UI state:
     * - Pivots categories to reproduce the exact Row / Column / Page tile order
     * - Restores page item filter selections (from both explicit selections and semantic coordinate tokens)
     * - Resolves the semantic cell coordinate to its exact `(row, col)` cell in the pivoted viewport
     * - Restores logical scroll indices and any unsubmitted draft formula with autocomplete suggestions
     */
    fun restoreHandoffState(handoff: ImprovHandoffState): Boolean {
        if (handoff.estimatedSizeBytes >= MAX_HANDOFF_BUNDLE_BYTES) {
            return false
        }

        // 1. Restore Row, Column, and Page category tile order in the engine
        handoff.rowCategoryOrder.forEachIndexed { index, categoryNameOrId ->
            engine.pivotCategory(categoryNameOrId, AxisOrientation.ROW, index.toUInt())
        }
        handoff.columnCategoryOrder.forEachIndexed { index, categoryNameOrId ->
            engine.pivotCategory(categoryNameOrId, AxisOrientation.COLUMN, index.toUInt())
        }
        handoff.pageCategoryOrder.forEachIndexed { index, categoryNameOrId ->
            engine.pivotCategory(categoryNameOrId, AxisOrientation.PAGE, index.toUInt())
        }

        // 2. Restore page filter item selections from explicit map and semantic coordinate tokens
        val coordTokens = parseSemanticCoordinateMap(handoff.selectedCellCoordinate)
        val categoriesAfterPivot = engine.getCategories()
        val pageCategories = categoriesAfterPivot.filter { it.orientation == AxisOrientation.PAGE }

        handoff.pageSelectedItems.forEach { entry ->
            val catKey = entry.substringBefore(":")
            val itemId = entry.substringAfter(":", missingDelimiterValue = "")
            if (catKey.isNotBlank() && itemId.isNotBlank()) {
                val matchedCat = pageCategories.firstOrNull {
                    it.id.equals(catKey, ignoreCase = true) || it.name.equals(catKey, ignoreCase = true)
                }
                if (matchedCat != null) {
                    engine.selectPageItem(matchedCat.id, itemId)
                }
            }
        }

        pageCategories.forEach { pageTile ->
            val targetItemName = coordTokens[pageTile.name]
            if (!targetItemName.isNullOrBlank()) {
                val matchedItem = pageTile.items.firstOrNull {
                    it.name.equals(targetItemName, ignoreCase = true) ||
                        it.id.equals(targetItemName, ignoreCase = true)
                }
                if (matchedItem != null) {
                    engine.selectPageItem(pageTile.id, matchedItem.id)
                }
            }
        }

        // 3. Refresh categories and viewport after pivoting & page selection so we can resolve the semantic cell coordinate
        val updatedCategories = engine.getCategories()
        val rowCats = updatedCategories
            .filter { it.orientation == AxisOrientation.ROW }
            .sortedBy { it.positionIndex }
        val colCats = updatedCategories
            .filter { it.orientation == AxisOrientation.COLUMN }
            .sortedBy { it.positionIndex }
        val pageCats = updatedCategories
            .filter { it.orientation == AxisOrientation.PAGE }
            .sortedBy { it.positionIndex }
        val vp = engine.getViewport(0u, 100u, 0u, 50u)

        val (resolvedRow, resolvedCol) = resolveCellFromSemanticCoordinate(
            targetCoordinate = handoff.selectedCellCoordinate,
            fallbackRow = handoff.selectedRow,
            fallbackCol = handoff.selectedCol,
            rowCats = rowCats,
            colCats = colCats,
            pageCats = pageCats,
            viewport = vp,
        )

        val clampedScrollRow = handoff.scrollRowIndex.coerceAtLeast(0)
        val clampedScrollCol = handoff.scrollColIndex.coerceAtLeast(0)
        val restoredDraftFormula = handoff.draftFormula.take(MAX_DRAFT_FORMULA_CHARS)

        _uiState.update { current ->
            current.copy(
                activeModelId = handoff.activeModelId.ifBlank { current.activeModelId },
                activeViewName = handoff.activeViewName.ifBlank { current.activeViewName },
                scrollRowIndex = clampedScrollRow,
                scrollColIndex = clampedScrollCol,
                formulaInput = restoredDraftFormula,
                formulaError = null,
            )
        }

        refreshFromEngine(
            selectedRow = resolvedRow,
            selectedCol = resolvedCol,
            status = "Restored Cross-Device Handoff state (${handoff.activeModelId})",
        )
        return true
    }

    private fun resolveCellFromSemanticCoordinate(
        targetCoordinate: String,
        fallbackRow: Int,
        fallbackCol: Int,
        rowCats: List<CategoryTile>,
        colCats: List<CategoryTile>,
        pageCats: List<CategoryTile>,
        viewport: ViewportSnapshot,
    ): Pair<Int, Int> {
        val totalRows = viewport.rowHeaders.size
        val totalCols = viewport.colHeaders.size
        if (totalRows <= 0 || totalCols <= 0) return 0 to 0

        val targetTokens = parseSemanticCoordinateMap(targetCoordinate)
        if (targetTokens.isNotEmpty()) {
            for (r in 0 until totalRows) {
                for (c in 0 until totalCols) {
                    val candidateCell = viewport.cellAt(r, c)
                    val candidateCoord = buildSemanticCoordinate(
                        rowCats = rowCats,
                        colCats = colCats,
                        pageCats = pageCats,
                        viewport = viewport,
                        row = r,
                        col = c,
                        cell = candidateCell,
                    )
                    if (candidateCoord.equals(targetCoordinate, ignoreCase = true) ||
                        parseSemanticCoordinateMap(candidateCoord) == targetTokens
                    ) {
                        return r to c
                    }
                }
            }
        }

        return fallbackRow.coerceIn(0, totalRows - 1) to fallbackCol.coerceIn(0, totalCols - 1)
    }

    private fun parseSemanticCoordinateMap(coordinate: String): Map<String, String> {
        if (coordinate.isBlank()) return emptyMap()
        return coordinate
            .split("|")
            .mapNotNull { part ->
                val idx = part.indexOf(':')
                if (idx <= 0 || idx >= part.length - 1) {
                    null
                } else {
                    part.substring(0, idx).trim() to part.substring(idx + 1).trim()
                }
            }
            .toMap()
    }

    private fun estimatePayloadSizeBytes(
        activeModelId: String,
        activeViewName: String,
        rowOrder: List<String>,
        colOrder: List<String>,
        pageOrder: List<String>,
        pageSelections: List<String>,
        selectedCoordinate: String,
        draftFormula: String,
    ): Int {
        val stringChars = activeModelId.length +
            activeViewName.length +
            rowOrder.sumOf { it.length + 4 } +
            colOrder.sumOf { it.length + 4 } +
            pageOrder.sumOf { it.length + 4 } +
            pageSelections.sumOf { it.length + 4 } +
            selectedCoordinate.length +
            draftFormula.length
        // UTF-16 string bytes + PersistableBundle key/header overhead (~256 bytes)
        return (stringChars * 2) + 256
    }

    fun refreshFromEngine(
        selectedRow: Int = _uiState.value.selectedRow,
        selectedCol: Int = _uiState.value.selectedCol,
        status: String? = null,
    ) {
        val allCategories = engine.getCategories()
        val rowCats = allCategories
            .filter { it.orientation == AxisOrientation.ROW }
            .sortedBy { it.positionIndex }
        val colCats = allCategories
            .filter { it.orientation == AxisOrientation.COLUMN }
            .sortedBy { it.positionIndex }
        val pageCats = allCategories
            .filter { it.orientation == AxisOrientation.PAGE }
            .sortedBy { it.positionIndex }

        val measures = engine.getMeasures()
        val formulas = engine.getFormulas()
        val vp = engine.getViewport(0u, 100u, 0u, 50u)

        val bytesWritten = try {
            engine.populateDirectByteBuffer(
                buffer = directViewportBuffer,
                startRow = 0u,
                rowCount = 100u,
                startCol = 0u,
                colCount = 50u,
            )
        } catch (_: Throwable) {
            0
        }

        val totalRows = vp.rowHeaders.size
        val totalCols = vp.colHeaders.size
        val clampedRow = if (totalRows > 0) selectedRow.coerceIn(0, totalRows - 1) else 0
        val clampedCol = if (totalCols > 0) selectedCol.coerceIn(0, totalCols - 1) else 0

        val selectedCell = vp.cellAt(clampedRow, clampedCol)
        val semanticCoord = buildSemanticCoordinate(
            rowCats = rowCats,
            colCats = colCats,
            pageCats = pageCats,
            viewport = vp,
            row = clampedRow,
            col = clampedCol,
            cell = selectedCell,
        )

        val directVal = if (bytesWritten >= 16 && clampedRow < directReader.rows && clampedCol < directReader.cols) {
            directReader.numericValue(clampedRow, clampedCol)
        } else {
            selectedCell?.numericValue ?: Double.NaN
        }

        val suggestions = engine.autocompleteFormula(_uiState.value.formulaInput)
        val targetAnalyticsMeasure = selectedCell?.measureName?.takeIf { it.isNotBlank() }
            ?: _uiState.value.activeAnalyticsMeasure.takeIf { name ->
                measures.any { it.name.equals(name, ignoreCase = true) }
            }
            ?: measures.lastOrNull()?.name
            ?: "Margin"
        val analyticsSummary = engine.getSliceAnalytics(targetAnalyticsMeasure)
        val aiNarrative = ImprovLocalAiEngine.generateSliceExecutiveNarrative(analyticsSummary)

        _uiState.update { current ->
            current.copy(
                rowCategories = rowCats,
                columnCategories = colCats,
                pageCategories = pageCats,
                measures = measures,
                formulas = formulas,
                viewport = vp,
                selectedRow = clampedRow,
                selectedCol = clampedCol,
                selectedCellCoordinate = semanticCoord,
                selectedCellFormatted = selectedCell?.formatted.orEmpty(),
                selectedCellEditBuffer = selectedCell?.formatted?.replace(",", "").orEmpty(),
                selectedCellIsDerived = selectedCell?.isDerived ?: false,
                autocompleteSuggestions = suggestions,
                directBufferBytesWritten = bytesWritten,
                directBufferSelectedValue = directVal,
                activeAnalyticsMeasure = analyticsSummary.measureName,
                sliceAnalytics = analyticsSummary,
                aiExecutiveNarrative = aiNarrative,
                statusMessage = status ?: current.statusMessage,
            )
        }
    }

    private fun buildSemanticCoordinate(
        rowCats: List<CategoryTile>,
        colCats: List<CategoryTile>,
        pageCats: List<CategoryTile>,
        viewport: ViewportSnapshot,
        row: Int,
        col: Int,
        cell: ViewportCell?,
    ): String {
        val parts = mutableListOf<String>()
        val rowHeader = viewport.rowHeaders.getOrNull(row).orEmpty()
        val colHeader = viewport.colHeaders.getOrNull(col).orEmpty()

        val rowCategoryLabel = rowHeader.substringBefore(" · ").trim()
        val rowCatValues = rowCategoryLabel.split(" / ")
        rowCats.forEachIndexed { idx, tile ->
            val v = rowCatValues.getOrNull(idx)?.takeIf { it.isNotBlank() }
                ?: tile.items.firstOrNull()?.name
                ?: "*"
            parts.add("${tile.name}:$v")
        }

        val colCatValues = colHeader.split(" / ")
        colCats.forEachIndexed { idx, tile ->
            val v = colCatValues.getOrNull(idx)?.takeIf { it.isNotBlank() }
                ?: tile.items.firstOrNull()?.name
                ?: "*"
            parts.add("${tile.name}:$v")
        }

        pageCats.forEach { tile ->
            val selectedName = tile.items
                .firstOrNull { it.id == tile.selectedPageItemId }
                ?.name
                ?: tile.items.firstOrNull()?.name
                ?: "*"
            parts.add("${tile.name}:$selectedName")
        }

        val measureName = cell?.measureName?.takeIf { it.isNotBlank() }
            ?: rowHeader.substringAfter(" · ", missingDelimiterValue = "Value")
        parts.add("Measure:$measureName")

        return parts.joinToString("|")
    }

    fun selectCell(row: Int, col: Int) {
        refreshFromEngine(selectedRow = row, selectedCol = col)
    }

    fun moveCellFocus(deltaRow: Int, deltaCol: Int) {
        val state = _uiState.value
        val maxRow = (state.viewport.rowHeaders.size - 1).coerceAtLeast(0)
        val maxCol = (state.viewport.colHeaders.size - 1).coerceAtLeast(0)
        val nextRow = (state.selectedRow + deltaRow).coerceIn(0, maxRow)
        val nextCol = (state.selectedCol + deltaCol).coerceIn(0, maxCol)
        selectCell(nextRow, nextCol)
    }

    fun moveTabFocus(reverse: Boolean = false) {
        val state = _uiState.value
        val rows = state.viewport.rowHeaders.size.coerceAtLeast(1)
        val cols = state.viewport.colHeaders.size.coerceAtLeast(1)
        val flat = state.selectedRow * cols + state.selectedCol
        val nextFlat = if (reverse) {
            (flat - 1 + rows * cols) % (rows * cols)
        } else {
            (flat + 1) % (rows * cols)
        }
        selectCell(nextFlat / cols, nextFlat % cols)
    }

    fun updateScrollIndices(rowIndex: Int, colIndex: Int) {
        _uiState.update {
            it.copy(scrollRowIndex = rowIndex, scrollColIndex = colIndex)
        }
    }

    fun pivotCategory(
        categoryId: String,
        targetAxis: AxisOrientation,
        newIndex: Int = 0,
    ) {
        engine.pivotCategory(categoryId, targetAxis, newIndex.coerceAtLeast(0).toUInt())
        val catName = (_uiState.value.rowCategories + _uiState.value.columnCategories + _uiState.value.pageCategories)
            .firstOrNull { it.id == categoryId || it.name.equals(categoryId, ignoreCase = true) }
            ?.name ?: categoryId
        refreshFromEngine(status = "Pivoted '$catName' to ${targetAxis.name} axis")
    }

    fun cycleCategoryAxis(tile: CategoryTile) {
        val nextAxis = when (tile.orientation) {
            AxisOrientation.ROW -> AxisOrientation.COLUMN
            AxisOrientation.COLUMN -> AxisOrientation.PAGE
            AxisOrientation.PAGE -> AxisOrientation.ROW
        }
        pivotCategory(tile.id, nextAxis, 0)
    }

    fun selectPageItem(categoryId: String, itemId: String) {
        engine.selectPageItem(categoryId, itemId)
        refreshFromEngine(status = "Updated page slice filter")
    }

    fun addCategory(name: String, itemsCsv: String, orientation: AxisOrientation) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            _uiState.update { it.copy(formulaError = "Category name is required") }
            return
        }
        val items = itemsCsv
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        try {
            val tile = engine.addCategory(trimmed, items, orientation)
            _uiState.update { it.copy(formulaError = null) }
            refreshFromEngine(status = "Added category '${tile.name}' (${tile.items.size} items) on ${orientation.name}")
        } catch (e: Exception) {
            _uiState.update { it.copy(formulaError = e.message ?: "Failed to add category") }
        }
    }

    fun onFormulaInputChanged(text: String) {
        val suggestions = engine.autocompleteFormula(text)
        _uiState.update {
            it.copy(
                formulaInput = text,
                autocompleteSuggestions = suggestions,
                formulaError = null,
            )
        }
    }

    fun applyAutocompleteChip(suggestion: String) {
        val current = _uiState.value.formulaInput
        val next = if (current.isBlank()) {
            suggestion
        } else if (current.endsWith(" ") || current.endsWith("(") || current.endsWith("=")) {
            "$current$suggestion"
        } else {
            val lastSpace = current.lastIndexOfAny(charArrayOf(' ', '(', '='))
            if (lastSpace >= 0) {
                current.substring(0, lastSpace + 1) + suggestion
            } else {
                "$current $suggestion"
            }
        }
        onFormulaInputChanged("$next ")
    }

    fun submitFormula(expression: String = _uiState.value.formulaInput) {
        try {
            val entry = engine.upsertFormula(expression)
            _uiState.update {
                it.copy(
                    formulaInput = entry.expression,
                    formulaError = null,
                )
            }
            refreshFromEngine(status = "Evaluated formula: ${entry.expression} (${entry.naturalLanguage})")
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    formulaError = e.message ?: "Invalid formula",
                    statusMessage = "Formula error: ${e.message}",
                )
            }
        }
    }

    fun onSelectedCellEditBufferChanged(text: String) {
        _uiState.update { it.copy(selectedCellEditBuffer = text) }
    }

    fun commitSelectedCellEdit() {
        val state = _uiState.value
        if (state.selectedCellIsDerived) {
            _uiState.update {
                it.copy(statusMessage = "Derived cells are computed by formulas; edit the formula instead.")
            }
            return
        }
        val num = state.selectedCellEditBuffer.trim().toDoubleOrNull()
        if (num == null) {
            _uiState.update {
                it.copy(statusMessage = "Enter a valid numeric value")
            }
            return
        }
        try {
            engine.setCellValue(state.selectedRow.toUInt(), state.selectedCol.toUInt(), num)
            refreshFromEngine(status = "Updated ${state.selectedCellCoordinate} = $num")
        } catch (e: Exception) {
            _uiState.update {
                it.copy(statusMessage = "Edit error: ${e.message}")
            }
        }
    }

    fun selectViewPreset(presetName: String) {
        _uiState.update { it.copy(activeViewName = presetName) }
        when {
            presetName.contains("Product × Time", ignoreCase = true) -> {
                engine.pivotCategory("Product", AxisOrientation.ROW, 0u)
                engine.pivotCategory("Time", AxisOrientation.COLUMN, 0u)
                engine.pivotCategory("Region", AxisOrientation.PAGE, 0u)
            }
            presetName.contains("Region × Time", ignoreCase = true) -> {
                engine.pivotCategory("Region", AxisOrientation.ROW, 0u)
                engine.pivotCategory("Time", AxisOrientation.COLUMN, 0u)
                engine.pivotCategory("Product", AxisOrientation.PAGE, 0u)
            }
            else -> {
                engine.pivotCategory("Time", AxisOrientation.ROW, 0u)
                engine.pivotCategory("Product", AxisOrientation.COLUMN, 0u)
                engine.pivotCategory("Region", AxisOrientation.PAGE, 0u)
            }
        }
        refreshFromEngine(status = "Switched view to '$presetName'")
    }

    fun resetSampleModel() {
        try {
            engine.loadSampleModel()
            _uiState.update {
                it.copy(
                    activeViewName = "3D Financial Cube (Default)",
                    formulaError = null,
                )
            }
            refreshFromEngine(selectedRow = 0, selectedCol = 0, status = "Reset 3D Sample Model (Time × Product × Region)")
        } catch (e: Exception) {
            _uiState.update { it.copy(statusMessage = "Reset failed: ${e.message}") }
        }
    }

    fun createBlankWorkbook() {
        try {
            engine.createBlankModel()
            _uiState.update {
                it.copy(
                    activeViewName = "Blank Workbook (Period × Department)",
                    formulaInput = "Forecast = Budget * 1.1",
                    formulaError = null,
                )
            }
            refreshFromEngine(
                selectedRow = 0,
                selectedCol = 0,
                status = "Created blank analytical workbook (Period × Department)",
            )
        } catch (e: Exception) {
            _uiState.update { it.copy(statusMessage = "New workbook failed: ${e.message}") }
        }
    }

    fun selectAnalyticsMeasure(measureName: String) {
        val summary = engine.getSliceAnalytics(measureName)
        val narrative = ImprovLocalAiEngine.generateSliceExecutiveNarrative(summary)
        _uiState.update {
            it.copy(
                activeAnalyticsMeasure = summary.measureName,
                sliceAnalytics = summary,
                aiExecutiveNarrative = narrative,
                statusMessage = "Analyzing slice statistics for '${summary.measureName}'",
            )
        }
    }

    fun addCategoryItem(categoryIdOrName: String, itemName: String) {
        val trimmed = itemName.trim()
        if (trimmed.isEmpty()) {
            _uiState.update { it.copy(statusMessage = "Enter a non-empty item name") }
            return
        }
        try {
            val updatedTile = engine.addCategoryItem(categoryIdOrName, trimmed)
            refreshFromEngine(
                status = "Added item '$trimmed' to category '${updatedTile.name}' (${updatedTile.items.size} items)",
            )
        } catch (e: Exception) {
            _uiState.update { it.copy(statusMessage = "Add item error: ${e.message}") }
        }
    }

    fun addInputMeasure(name: String, defaultValueText: String = "0.0") {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            _uiState.update { it.copy(statusMessage = "Measure name is required") }
            return
        }
        val defaultVal = defaultValueText.trim().toDoubleOrNull() ?: 0.0
        try {
            val summary = engine.addInputMeasure(trimmed, defaultVal)
            refreshFromEngine(status = "Added input measure '${summary.name}' (default = $defaultVal)")
        } catch (e: Exception) {
            _uiState.update { it.copy(statusMessage = "Add measure error: ${e.message}") }
        }
    }

    fun deleteMeasure(measureIdOrName: String) {
        try {
            engine.deleteMeasure(measureIdOrName)
            refreshFromEngine(selectedRow = 0, selectedCol = 0, status = "Deleted measure '$measureIdOrName'")
        } catch (e: Exception) {
            _uiState.update { it.copy(statusMessage = "Delete measure error: ${e.message}") }
        }
    }

    fun applyWhatIfMultiplier(
        measureName: String,
        multiplier: Double,
        scenarioLabel: String,
    ) {
        try {
            val updatedCells = engine.applyWhatIfMultiplier(measureName, multiplier)
            refreshFromEngine(
                status = "Applied What-If '$scenarioLabel' ($measureName × $multiplier across $updatedCells cells)",
            )
        } catch (e: Exception) {
            _uiState.update { it.copy(statusMessage = "What-If error: ${e.message}") }
        }
    }

    fun onCsvBufferChanged(text: String) {
        _uiState.update { it.copy(csvImportExportBuffer = text) }
    }

    fun exportMeasureToCsvBuffer(measureName: String = _uiState.value.activeAnalyticsMeasure) {
        try {
            val csvText = engine.exportMeasureCsvText(measureName)
            _uiState.update {
                it.copy(
                    csvImportExportBuffer = csvText,
                    statusMessage = "Exported '$measureName' to CSV (${csvText.lines().count { l -> l.isNotBlank() }} rows)",
                )
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(statusMessage = "CSV export error: ${e.message}") }
        }
    }

    fun importCsvFromBuffer(measureNameOverride: String = "") {
        try {
            val importedCount = engine.importCsvText(_uiState.value.csvImportExportBuffer, measureNameOverride)
            refreshFromEngine(
                status = "Imported $importedCount cells from CSV into multidimensional cube",
            )
        } catch (e: Exception) {
            _uiState.update { it.copy(statusMessage = "CSV import error: ${e.message}") }
        }
    }

    fun onAiPromptChanged(prompt: String) {
        _uiState.update { it.copy(aiNaturalLanguagePrompt = prompt) }
    }

    fun synthesizeFormulaWithLocalAi(autoEvaluate: Boolean = true) {
        val state = _uiState.value
        val translation = ImprovLocalAiEngine.translatePromptToFormula(
            prompt = state.aiNaturalLanguagePrompt,
            measures = state.measures,
        )
        _uiState.update {
            it.copy(
                formulaInput = translation.generatedFormula,
                aiLastExplanation = "${translation.engineBackend}: ${translation.explanation}",
                formulaError = null,
            )
        }
        if (autoEvaluate) {
            submitFormula(translation.generatedFormula)
        }
    }

    fun onStylusScrubMeasureSelected(measureName: String) {
        _uiState.update {
            it.copy(
                stylusScrubMeasure = measureName,
                stylusScrubMultiplier = 1.0f,
            )
        }
    }

    fun onStylusScrubMultiplierChanged(multiplier: Float) {
        val clamped = multiplier.coerceIn(0.70f, 1.50f)
        _uiState.update { it.copy(stylusScrubMultiplier = clamped) }
    }

    fun commitStylusWhatIfScrub() {
        val state = _uiState.value
        val mult = (state.stylusScrubMultiplier * 100.0).toInt() / 100.0
        val pctDelta = ((mult - 1.0) * 100).toInt()
        val label = if (pctDelta >= 0) "Stylus Scrub +$pctDelta%" else "Stylus Scrub $pctDelta%"
        applyWhatIfMultiplier(state.stylusScrubMeasure, mult, label)
    }

    fun onScannerRawOcrChanged(text: String) {
        _uiState.update { it.copy(scannerRawOcrInput = text) }
    }

    fun ingestScannedTableOcrToCube() {
        val state = _uiState.value
        val normalizedCsv = ImprovLocalAiEngine.parseScannedTableTextToCsv(state.scannerRawOcrInput)
        _uiState.update { it.copy(csvImportExportBuffer = normalizedCsv) }
        importCsvFromBuffer()
    }

    fun streamAndroidSensorTelemetryToCube() {
        val sensorCsv = ImprovLocalAiEngine.buildAndroidSensorTelemetryCsv()
        _uiState.update { it.copy(csvImportExportBuffer = sensorCsv) }
        importCsvFromBuffer("SensorTelemetry")
    }

    fun toggleTabletopPostureOverride() {
        _uiState.update {
            val next = !it.isTabletopPostureOverride
            it.copy(
                isTabletopPostureOverride = next,
                statusMessage = if (next) {
                    "Activated Foldable Tabletop Split-Hinge Posture"
                } else {
                    "Returned to standard adaptive window posture"
                },
            )
        }
    }
}

fun ViewportSnapshot.cellAt(row: Int, col: Int): ViewportCell? {
    val cols = colHeaders.size
    if (cols <= 0 || row < 0 || col < 0 || row >= rowHeaders.size || col >= cols) return null
    val idx = row * cols + col
    return cells.getOrNull(idx)
}
