package com.droid.improv.ai

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import com.droid.improv.ui.ImprovViewModel
import uniffi.improv_android_ffi.ImprovMobileEngine
import java.io.File

/**
 * Android 17 Agentic AppFunction Service (`android.app.appfunctions.AppFunctionService` /
 * `com.droid.improv.action.EXECUTE_APP_FUNCTION`) enabling system-wide AI assistants to query
 * multidimensional slices, run What-If simulations, and synthesize natural-language formulas
 * against the local SQLite/Mentat `.db` cube without opening the UI.
 */
class ImprovAppFunctionService : Service() {

    inner class LocalAgentBinder : Binder() {
        val service: ImprovAppFunctionService
            get() = this@ImprovAppFunctionService
    }

    private val binder = LocalAgentBinder()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val functionName = intent.getStringExtra(EXTRA_FUNCTION_NAME).orEmpty()
            val args = intent.extras ?: Bundle.EMPTY
            val modelId = args.getString(EXTRA_MODEL_ID) ?: ImprovViewModel.DEFAULT_MODEL_ID
            val dbPath = File(filesDir, "$modelId.db").absolutePath
            val engine = ImprovMobileEngine.openOrCreate(dbPath)
            executeAppFunction(engine, functionName, args)
        }
        return START_NOT_STICKY
    }

    /**
     * Executes an Android AppFunction against the given [ImprovMobileEngine] and returns
     * a structured [Bundle] result.
     */
    fun executeAppFunction(
        engine: ImprovMobileEngine,
        functionName: String,
        args: Bundle,
    ): Bundle {
        val out = Bundle()
        try {
            when (functionName) {
                FN_QUERY_SLICE_METRIC -> {
                    val measureName = args.getString(ARG_MEASURE_NAME) ?: "Margin"
                    val stats = engine.getSliceAnalytics(measureName)
                    val narrative = ImprovLocalAiEngine.generateSliceExecutiveNarrative(stats)
                    out.putBoolean(RESULT_SUCCESS, true)
                    out.putString(RESULT_MEASURE_NAME, stats.measureName)
                    out.putDouble(RESULT_SUM, stats.sum)
                    out.putDouble(RESULT_MEAN, stats.mean)
                    out.putDouble(RESULT_MEDIAN, stats.median)
                    out.putDouble(RESULT_MIN, stats.min)
                    out.putDouble(RESULT_MAX, stats.max)
                    out.putDouble(RESULT_STD_DEV, stats.stdDev)
                    out.putString(RESULT_AI_NARRATIVE, narrative)
                }

                FN_RUN_WHAT_IF_SIMULATION -> {
                    val measureName = args.getString(ARG_MEASURE_NAME) ?: "Price"
                    val multiplier = args.getDouble(ARG_MULTIPLIER, 1.10)
                    val updatedCells = engine.applyWhatIfMultiplier(measureName, multiplier)
                    val marginStats = engine.getSliceAnalytics("Margin")
                    out.putBoolean(RESULT_SUCCESS, true)
                    out.putInt(RESULT_UPDATED_CELLS, updatedCells.toInt())
                    out.putDouble(RESULT_SUM, marginStats.sum)
                    out.putString(
                        RESULT_AI_NARRATIVE,
                        ImprovLocalAiEngine.generateSliceExecutiveNarrative(marginStats),
                    )
                }

                FN_SYNTHESIZE_FORMULA -> {
                    val prompt = args.getString(ARG_PROMPT)
                        ?: "Calculate profit margin after a 7% tariff on COGS"
                    val translation = ImprovLocalAiEngine.translatePromptToFormula(
                        prompt = prompt,
                        measures = engine.getMeasures(),
                    )
                    val entry = engine.upsertFormula(translation.generatedFormula)
                    out.putBoolean(RESULT_SUCCESS, true)
                    out.putString(RESULT_FORMULA_EXPRESSION, entry.expression)
                    out.putString(RESULT_FORMULA_NL, entry.naturalLanguage)
                    out.putString(RESULT_AI_NARRATIVE, translation.explanation)
                }

                else -> {
                    out.putBoolean(RESULT_SUCCESS, false)
                    out.putString(RESULT_ERROR, "Unknown AppFunction '$functionName'")
                }
            }
        } catch (e: Exception) {
            out.putBoolean(RESULT_SUCCESS, false)
            out.putString(RESULT_ERROR, e.message ?: "AppFunction execution error")
        }
        return out
    }

    companion object {
        const val ACTION_EXECUTE_APP_FUNCTION = "com.droid.improv.action.EXECUTE_APP_FUNCTION"

        const val EXTRA_FUNCTION_NAME = "function_name"
        const val EXTRA_MODEL_ID = "model_id"

        const val FN_QUERY_SLICE_METRIC = "query_slice_metric"
        const val FN_RUN_WHAT_IF_SIMULATION = "run_what_if_simulation"
        const val FN_SYNTHESIZE_FORMULA = "synthesize_and_evaluate_formula"

        const val ARG_MEASURE_NAME = "measure_name"
        const val ARG_MULTIPLIER = "multiplier"
        const val ARG_PROMPT = "prompt"

        const val RESULT_SUCCESS = "success"
        const val RESULT_ERROR = "error"
        const val RESULT_MEASURE_NAME = "measure_name"
        const val RESULT_SUM = "sum"
        const val RESULT_MEAN = "mean"
        const val RESULT_MEDIAN = "median"
        const val RESULT_MIN = "min"
        const val RESULT_MAX = "max"
        const val RESULT_STD_DEV = "std_dev"
        const val RESULT_UPDATED_CELLS = "updated_cells"
        const val RESULT_FORMULA_EXPRESSION = "formula_expression"
        const val RESULT_FORMULA_NL = "formula_nl"
        const val RESULT_AI_NARRATIVE = "ai_narrative"
    }
}
