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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.internal.widget.LockPatternUtils

class KnockCodePadState {

    var gridSize by mutableIntStateOf(LockPatternUtils.KNOCK_CODE_GRID_SIZE_DEFAULT)
        @JvmName("setGridSizeInternal") private set

    var expectedLength by mutableIntStateOf(0)
        @JvmName("setExpectedLengthInternal") private set

    var tapCount by mutableIntStateOf(0)
        private set

    var inputEnabled by mutableStateOf(true)
        @JvmName("setInputEnabledInternal") private set

    var onTap: (() -> Unit)? = null

    var onSequenceComplete: ((String) -> Unit)? = null

    var onTimedOut: (() -> Unit)? = null

    private val sequence = StringBuilder()

    fun sequence(): String = sequence.toString()

    fun setGridSize(gridSize: Int) {
        if (this.gridSize == gridSize) {
            return
        }
        this.gridSize = gridSize
        clearSequence()
    }

    fun setExpectedLength(expectedLength: Int) {
        if (this.expectedLength == expectedLength) {
            return
        }
        this.expectedLength = expectedLength
        clearSequence()
    }

    fun setInputEnabled(enabled: Boolean) {
        if (inputEnabled == enabled) {
            return
        }
        inputEnabled = enabled
        if (!enabled) {
            clearSequence()
        }
    }

    fun clearSequence() {
        sequence.setLength(0)
        tapCount = 0
    }

    fun recordTap(row: Int, col: Int): Boolean {
        if (!inputEnabled) {
            return false
        }
        if (tapCount >= LockPatternUtils.KNOCK_CODE_LENGTH_MAX) {
            return false
        }
        if (expectedLength > 0 && tapCount >= expectedLength) {
            return false
        }

        sequence.append(LockPatternUtils.encodeKnockCodeCell(gridSize, row, col))
        tapCount++
        onTap?.invoke()

        if (expectedLength > 0 && tapCount >= expectedLength) {
            onSequenceComplete?.invoke(sequence.toString())
            clearSequence()
        }
        return true
    }

    fun onTapTimeout() {
        if (tapCount == 0) {
            return
        }
        clearSequence()
        onTimedOut?.invoke()
    }

    companion object {
        const val TAP_TIMEOUT_MS = 2000L

        const val RIPPLE_DURATION_MS = 220
    }
}
