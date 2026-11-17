package com.droid.improv

import android.app.HandoffActivityData
import android.app.HandoffActivityDataRequestInfo
import android.app.HandoffActivityParams
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.droid.improv.ui.ImprovAdaptiveApp
import com.droid.improv.ui.ImprovViewModel
import com.droid.improv.ui.theme.ImprovTheme
import uniffi.improv_android_ffi.ImprovMobileEngine
import java.io.File

class MainActivity : ComponentActivity() {

    private val improvViewModel: ImprovViewModel by viewModels {
        val modelId = resolveModelIdFromIntentOrBundle(intent, null)
        val dbPath = File(filesDir, "$modelId.db").absolutePath
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ImprovViewModel(
                    engine = ImprovMobileEngine.openOrCreate(dbPath),
                    initialModelId = modelId,
                ) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializeActivity(savedInstanceState = savedInstanceState, persistentState = null)
    }

    override fun onCreate(savedInstanceState: Bundle?, persistentState: PersistableBundle?) {
        super.onCreate(savedInstanceState, persistentState)
        initializeActivity(savedInstanceState = savedInstanceState, persistentState = persistentState)
    }

    private fun initializeActivity(
        savedInstanceState: Bundle?,
        persistentState: PersistableBundle?,
    ) {
        configureCrossDeviceHandoff()
        restoreIncomingHandoffState(
            intent = intent,
            savedInstanceState = savedInstanceState,
            persistentState = persistentState,
        )

        enableEdgeToEdge()
        setContent {
            ImprovTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ImprovAdaptiveApp(viewModel = improvViewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        restoreIncomingHandoffState(
            intent = intent,
            savedInstanceState = null,
            persistentState = null,
        )
    }

    /**
     * Enables Android 17 (API 37) Cross-Device Handoff so the system can transfer live
     * spreadsheet state between Pixel phones, tablets, and Googlebooks.
     */
    private fun configureCrossDeviceHandoff() {
        if (Build.VERSION.SDK_INT >= 37) {
            val params = HandoffActivityParams.Builder()
                .setAllowHandoffWithoutPackageInstalled(true)
                .build()
            setHandoffEnabled(true, params)
        }
    }

    /**
     * Packages the active model ID, row/column/page category tile order, selected semantic cell
     * coordinate, logical scroll indices, and any unsubmitted draft formula into a
     * [PersistableBundle] (< 50 KB) when Android 17 requests Cross-Device Handoff data.
     */
    override fun onHandoffActivityDataRequested(
        requestInfo: HandoffActivityDataRequestInfo,
    ): HandoffActivityData? {
        if (Build.VERSION.SDK_INT < 37) return null

        val handoffExtras: PersistableBundle = improvViewModel.buildHandoffPersistableBundle()
        val activeModelId = improvViewModel.uiState.value.activeModelId
        val fallbackUri = Uri.parse(
            "https://improv.droid.com/handoff?model=${Uri.encode(activeModelId)}"
        )

        return HandoffActivityData.Builder(componentName)
            .setExtras(handoffExtras)
            .setFallbackUri(fallbackUri)
            .build()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val handoffBundle = improvViewModel.buildHandoffPersistableBundle()
        outState.putAll(handoffBundle)
    }

    override fun onSaveInstanceState(outState: Bundle, outPersistentState: PersistableBundle) {
        super.onSaveInstanceState(outState, outPersistentState)
        val handoffBundle = improvViewModel.buildHandoffPersistableBundle()
        outState.putAll(handoffBundle)
        outPersistentState.putAll(handoffBundle)
    }

    private fun restoreIncomingHandoffState(
        intent: Intent?,
        savedInstanceState: Bundle?,
        persistentState: PersistableBundle?,
    ) {
        // 1. Prefer explicit cross-device HandoffActivityData or intent extras
        if (intent != null) {
            if (Build.VERSION.SDK_INT >= 37) {
                val handoffData = intent.getParcelableExtra(
                    EXTRA_HANDOFF_ACTIVITY_DATA,
                    HandoffActivityData::class.java,
                )
                if (handoffData != null && improvViewModel.restoreHandoffState(handoffData.extras)) {
                    return
                }
            }
            if (improvViewModel.restoreHandoffStateFromIntent(intent)) {
                return
            }
        }

        // 2. Fallback to PersistableBundle from persistent activity creation
        if (persistentState != null && improvViewModel.restoreHandoffState(persistentState)) {
            return
        }

        // 3. Fallback to savedInstanceState Bundle
        if (savedInstanceState != null) {
            improvViewModel.restoreHandoffState(savedInstanceState)
        }
    }

    private fun resolveModelIdFromIntentOrBundle(
        intent: Intent?,
        savedInstanceState: Bundle?,
    ): String {
        val fromIntent = intent?.extras?.getString(ImprovViewModel.KEY_ACTIVE_MODEL_ID)
        if (!fromIntent.isNullOrBlank()) return fromIntent

        val fromSaved = savedInstanceState?.getString(ImprovViewModel.KEY_ACTIVE_MODEL_ID)
        if (!fromSaved.isNullOrBlank()) return fromSaved

        return ImprovViewModel.DEFAULT_MODEL_ID
    }

    companion object {
        const val EXTRA_HANDOFF_ACTIVITY_DATA = "android.app.extra.HANDOFF_ACTIVITY_DATA"
    }
}
