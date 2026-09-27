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

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup.LayoutParams
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.android.settingslib.spa.framework.theme.SettingsTheme
import java.util.function.Consumer

class KnockCodePadView
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    FrameLayout(context, attrs, defStyleAttr) {

    private val padState = KnockCodePadState()

    private var onKnockCompleteListener: Consumer<String>? = null

    init {
        padState.onSequenceComplete = { sequence -> onKnockCompleteListener?.accept(sequence) }

        contentDescription = null

        addView(
            ComposeView(context).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent { SettingsTheme { KnockCodePad(padState, Modifier.fillMaxSize()) } }
            },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
    }

    fun getSequence(): String = padState.sequence()

    fun getTapCount(): Int = padState.tapCount

    fun setGridSize(gridSize: Int) {
        padState.setGridSize(gridSize)
    }

    fun clearSequence() {
        padState.clearSequence()
    }

    fun setExpectedLength(expectedLength: Int) {
        padState.setExpectedLength(expectedLength)
    }

    fun setOnKnockCompleteListener(listener: Consumer<String>?) {
        onKnockCompleteListener = listener
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        padState.setInputEnabled(enabled)
    }
}
