/*
 * Copyright (C) 2026 RisingOS (revived) Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.password

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.app.settings.SettingsEnums
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.UserHandle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import com.android.internal.widget.LockPatternUtils
import com.android.internal.widget.LockscreenCredential
import com.android.settings.R
import com.android.settings.SettingsActivity
import com.android.settings.SetupWizardUtils
import com.android.settings.Utils
import com.android.settings.core.InstrumentedFragment
import com.android.settings.notification.RedactionInterstitial
import com.android.settingslib.spa.framework.theme.SettingsTheme
import com.google.android.setupdesign.util.ThemeHelper

class ChooseLockKnockCode : SettingsActivity() {

    override fun getIntent(): Intent {
        val modIntent = Intent(super.getIntent())
        modIntent.putExtra(SettingsActivity.EXTRA_SHOW_FRAGMENT, getFragmentClass().name)
        modIntent.putExtra(
            ChooseLockSettingsHelper.EXTRA_KEY_USE_EXPRESSIVE_STYLE,
            ThemeHelper.shouldApplyGlifExpressiveStyle(applicationContext),
        )
        return modIntent
    }

    override fun isValidFragment(fragmentName: String?): Boolean =
        ChooseLockKnockCodeFragment::class.java.name == fragmentName

    override fun isToolbarEnabled(): Boolean = false

    internal fun getFragmentClass(): Class<out Fragment> = ChooseLockKnockCodeFragment::class.java

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(SetupWizardUtils.getTheme(this, intent))
        ThemeHelper.trySetDynamicColor(this)
        if (ThemeHelper.shouldApplyGlifExpressiveStyle(applicationContext)) {
            ThemeHelper.trySetSuwTheme(this)
        }
        super.onCreate(savedInstanceState)
        findViewById<View>(R.id.content_parent).setFitsSystemWindows(false)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    class IntentBuilder(context: Context) {

        private val intent: Intent =
            Intent(context, ChooseLockKnockCode::class.java).apply {
                putExtra(ChooseLockGeneric.CONFIRM_CREDENTIALS, false)
                putExtra(
                    ChooseLockSettingsHelper.EXTRA_KEY_USE_EXPRESSIVE_STYLE,
                    ThemeHelper.shouldApplyGlifExpressiveStyle(context),
                )
                putExtra(
                    LockPatternUtils.PASSWORD_TYPE_KEY,
                    DevicePolicyManager.PASSWORD_QUALITY_NUMERIC,
                )
            }

        fun setUserId(userId: Int): IntentBuilder = apply {
            intent.putExtra(Intent.EXTRA_USER_ID, userId)
        }

        fun setPassword(password: LockscreenCredential?): IntentBuilder = apply {
            intent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_PASSWORD, password)
        }

        fun setRequestGatekeeperPasswordHandle(
            requestGatekeeperPasswordHandle: Boolean
        ): IntentBuilder = apply {
            intent.putExtra(
                ChooseLockSettingsHelper.EXTRA_KEY_REQUEST_GK_PW_HANDLE,
                requestGatekeeperPasswordHandle,
            )
        }

        fun setProfileToUnify(
            profileId: Int,
            credential: LockscreenCredential?,
        ): IntentBuilder = apply {
            intent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_UNIFICATION_PROFILE_ID, profileId)
            intent.putExtra(
                ChooseLockSettingsHelper.EXTRA_KEY_UNIFICATION_PROFILE_CREDENTIAL,
                credential,
            )
        }

        fun build(): Intent = intent
    }

    class ChooseLockKnockCodeFragment : InstrumentedFragment(), SaveAndFinishWorker.Listener {

        private enum class Stage {
            Introduction,
            NeedToConfirm,
            ConfirmWrong,
        }

        private lateinit var lockPatternUtils: LockPatternUtils
        private var saveAndFinishWorker: SaveAndFinishWorker? = null
        private var currentCredential: LockscreenCredential? = null
        private var userId = UserHandle.USER_NULL
        private var requestGatekeeperPassword = false
        private var requestWriteRepairModePassword = false

        private val padState = KnockCodePadState()

        private var firstSequence: String? = null

        private var firstTapCount = 0

        private var stage = Stage.Introduction

        private var headerText: CharSequence by mutableStateOf<CharSequence>("")
        private var messageText: CharSequence by mutableStateOf<CharSequence>("")
        private var continueLabelRes by mutableIntStateOf(R.string.knock_code_continue)

        private var saving by mutableStateOf(false)

        private var gridSize: Int
            get() = padState.gridSize
            set(value) {
                padState.setGridSize(value)
            }

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            lockPatternUtils = LockPatternUtils(requireContext())

            val enclosing = requireActivity()
            if (enclosing !is ChooseLockKnockCode) {
                throw SecurityException("Fragment contained in wrong activity")
            }

            val intent = enclosing.intent
            userId = Utils.getUserIdFromBundle(enclosing, intent.extras)
            requestGatekeeperPassword =
                intent.getBooleanExtra(ChooseLockSettingsHelper.EXTRA_KEY_REQUEST_GK_PW_HANDLE, false)
            requestWriteRepairModePassword =
                intent.getBooleanExtra(
                    ChooseLockSettingsHelper.EXTRA_KEY_REQUEST_WRITE_REPAIR_MODE_PW,
                    false,
                )
            currentCredential =
                intent.getParcelableExtra<LockscreenCredential>(
                    ChooseLockSettingsHelper.EXTRA_KEY_PASSWORD
                )

            gridSize = lockPatternUtils.getKnockCodeGridSize(userId)

            if (savedInstanceState != null) {
                firstSequence = savedInstanceState.getString(KEY_FIRST_SEQUENCE)
                firstTapCount = savedInstanceState.getInt(KEY_FIRST_TAP_COUNT, 0)
                gridSize = savedInstanceState.getInt(KEY_GRID_SIZE, gridSize)
                stage =
                    Stage.valueOf(
                        savedInstanceState.getString(KEY_UI_STAGE, Stage.Introduction.name)
                    )
            }

            saveAndFinishWorker =
                parentFragmentManager.findFragmentByTag(FRAGMENT_TAG_SAVE_AND_FINISH) as
                    SaveAndFinishWorker?
            saveAndFinishWorker?.setListener(this)
        }

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?,
        ): View =
            ComposeView(requireContext()).apply {
                setViewCompositionStrategy(
                    ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
                )
                setOnApplyWindowInsetsListener { view, insets ->
                    val bars =
                        insets.getInsets(
                            WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
                        )
                    view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                    WindowInsets.CONSUMED
                }
                setContent {
                    SettingsTheme {
                        ChooseLockKnockCodeScreen(
                            header = headerText,
                            message = messageText,
                            continueLabelRes = continueLabelRes,
                            gridSize = gridSize,
                            padState = padState,
                            saving = saving,
                            onGridSelected = ::onGridSelected,
                            onContinue = ::handleContinue,
                        )
                    }
                }
            }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            view.requestApplyInsets()
            updateStage()
        }

        private fun onGridSelected(newGridSize: Int) {
            if (newGridSize == 0 || newGridSize == gridSize) {
                return
            }

            val restarted = firstSequence != null
            gridSize = newGridSize
            firstSequence = null
            firstTapCount = 0
            stage = Stage.Introduction

            updateStage()
            if (restarted) {
                messageText = getString(R.string.knock_code_grid_changed)
            }
        }

        override fun onSaveInstanceState(outState: Bundle) {
            super.onSaveInstanceState(outState)
            outState.putString(KEY_FIRST_SEQUENCE, firstSequence)
            outState.putInt(KEY_FIRST_TAP_COUNT, firstTapCount)
            outState.putInt(KEY_GRID_SIZE, gridSize)
            outState.putString(KEY_UI_STAGE, stage.name)
        }

        private fun updateStage() {
            when (stage) {
                Stage.Introduction -> {
                    headerText = getString(R.string.knock_code_create_header)
                    continueLabelRes = R.string.knock_code_continue
                    messageText = getString(R.string.knock_code_create_hint)
                }

                Stage.NeedToConfirm -> {
                    headerText = getString(R.string.knock_code_confirm_header)
                    continueLabelRes = R.string.knock_code_confirm_button
                    messageText = getString(R.string.knock_code_confirm_hint)
                }

                Stage.ConfirmWrong -> {
                    headerText = getString(R.string.knock_code_confirm_header)
                    continueLabelRes = R.string.knock_code_confirm_button
                    messageText = getString(R.string.knock_code_mismatch)
                }
            }
            padState.clearSequence()
        }

        private fun handleContinue() {
            val sequence = padState.sequence()
            val tapCount = padState.tapCount

            if (tapCount < LockPatternUtils.KNOCK_CODE_LENGTH_MIN) {
                messageText =
                    getString(
                        R.string.knock_code_too_short,
                        LockPatternUtils.KNOCK_CODE_LENGTH_MIN,
                    )
                return
            }

            when (stage) {
                Stage.Introduction -> {
                    firstSequence = sequence
                    firstTapCount = tapCount
                    stage = Stage.NeedToConfirm
                    updateStage()
                }

                Stage.NeedToConfirm,
                Stage.ConfirmWrong ->
                    if (TextUtils.equals(firstSequence, sequence)) {
                        saving = true
                        padState.setInputEnabled(false)
                        saveAndFinish(sequence)
                    } else {
                        stage = Stage.ConfirmWrong
                        updateStage()
                    }
            }
        }

        private fun saveAndFinish(sequence: String) {
            val chosen = LockscreenCredential.createPin(sequence)

            val worker =
                SaveAndFinishWorker().setListener(this)
                    .setRequestGatekeeperPasswordHandle(requestGatekeeperPassword)
                    .setRequestWriteRepairModePassword(requestWriteRepairModePassword)
            saveAndFinishWorker = worker

            parentFragmentManager
                .beginTransaction()
                .add(worker, FRAGMENT_TAG_SAVE_AND_FINISH)
                .commit()
            parentFragmentManager.executePendingTransactions()

            val intent = requireActivity().intent
            if (intent.hasExtra(ChooseLockSettingsHelper.EXTRA_KEY_UNIFICATION_PROFILE_ID)) {
                val profileCredential =
                    intent.getParcelableExtra<LockscreenCredential>(
                        ChooseLockSettingsHelper.EXTRA_KEY_UNIFICATION_PROFILE_CREDENTIAL
                    )
                if (profileCredential != null) {
                    try {
                        worker.setProfileToUnify(
                            intent.getIntExtra(
                                ChooseLockSettingsHelper.EXTRA_KEY_UNIFICATION_PROFILE_ID,
                                UserHandle.USER_NULL,
                            ),
                            profileCredential,
                        )
                    } finally {
                        profileCredential.close()
                    }
                }
            }

            worker.start(
                lockPatternUtils,
                chosen,
                currentCredential,
                userId,
                lockPatternUtils.getLockPatternSize(userId),
            )
        }

        override fun onChosenLockSaveFinished(wasSecureBefore: Boolean, resultData: Intent?) {
            val worker = saveAndFinishWorker
            if (worker != null && worker.wasSaveSuccessful() && firstSequence != null) {
                lockPatternUtils.setKnockCodeGridSize(gridSize, userId)
                lockPatternUtils.setKnockCodeLength(firstTapCount, userId)
                lockPatternUtils.setKnockCodeEnabled(true, userId)
            }

            requireActivity().setResult(RESULT_FINISHED, resultData)

            currentCredential?.zeroize()
            firstSequence = null
            firstTapCount = 0

            if (!wasSecureBefore) {
                getRedactionInterstitialIntent(requireContext())?.let { startActivity(it) }
            }

            requireActivity().finish()
        }

        protected fun getRedactionInterstitialIntent(context: Context): Intent? =
            RedactionInterstitial.createStartIntent(context, userId)

        override fun onDestroy() {
            super.onDestroy()
            currentCredential?.zeroize()
        }

        override fun getMetricsCategory(): Int =
            SettingsEnums.CHOOSE_LOCK_PASSWORD

        companion object {
            private const val KEY_FIRST_SEQUENCE = "first_sequence"
            private const val KEY_FIRST_TAP_COUNT = "first_tap_count"
            private const val KEY_GRID_SIZE = "grid_size"
            private const val KEY_UI_STAGE = "ui_stage"
            private const val FRAGMENT_TAG_SAVE_AND_FINISH = "save_and_finish_worker"

            @JvmField val RESULT_FINISHED: Int = Activity.RESULT_FIRST_USER
        }
    }
}

@Composable
private fun ChooseLockKnockCodeScreen(
    header: CharSequence,
    message: CharSequence,
    continueLabelRes: Int,
    gridSize: Int,
    padState: KnockCodePadState,
    saving: Boolean,
    onGridSelected: (Int) -> Unit,
    onContinue: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = header.toString(),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(12.dp))

            GridPicker(gridSize = gridSize, enabled = !saving, onGridSelected = onGridSelected)

            KnockCodePad(
                state = padState,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )

            Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = message.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier.fillMaxWidth().semantics {
                            liveRegion = LiveRegionMode.Polite
                        },
                )
            }

            Button(
                onClick = onContinue,
                enabled = !saving,
                modifier = Modifier.widthIn(min = 200.dp),
            ) {
                Text(stringResource(continueLabelRes))
            }
        }
    }
}

@Composable
private fun GridPicker(gridSize: Int, enabled: Boolean, onGridSelected: (Int) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (size in
            listOf(
                LockPatternUtils.KNOCK_CODE_GRID_SIZE_MIN,
                3,
                LockPatternUtils.KNOCK_CODE_GRID_SIZE_MAX,
            )) {
            FilterChip(
                selected = gridSize == size,
                onClick = { onGridSelected(size) },
                enabled = enabled,
                label = { Text(stringResource(gridLabelRes(size))) },
            )
        }
    }
}

private fun gridLabelRes(gridSize: Int): Int =
    when (gridSize) {
        3 -> R.string.knock_code_grid_size_label_3
        LockPatternUtils.KNOCK_CODE_GRID_SIZE_MAX -> R.string.knock_code_grid_size_label_4
        else -> R.string.knock_code_grid_size_label_2
    }
