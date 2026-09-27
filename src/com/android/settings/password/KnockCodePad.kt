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

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.android.internal.widget.LockPatternUtils
import com.android.settings.R
import kotlin.math.min
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val RIPPLE_ALPHA = 0.2f

@Composable
fun KnockCodePad(state: KnockCodePadState, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val cellInset = dimensionResource(R.dimen.knock_code_quadrant_inset)
    val cellStroke = dimensionResource(R.dimen.knock_code_quadrant_stroke)
    val dotRadius = dimensionResource(R.dimen.knock_code_dot_radius)
    val dotSpacing = dimensionResource(R.dimen.knock_code_dot_spacing)
    val dotStroke = dimensionResource(R.dimen.knock_code_dot_stroke)
    val rippleRadius = dimensionResource(R.dimen.knock_code_ripple_radius)
    val padMaxSize = dimensionResource(R.dimen.knock_code_pad_max_size)

    val insetPx = with(density) { cellInset.toPx() }
    val strokePx = with(density) { cellStroke.toPx() }
    val dotRadiusPx = with(density) { dotRadius.toPx() }
    val dotSpacingPx = with(density) { dotSpacing.toPx() }
    val dotStrokePx = with(density) { dotStroke.toPx() }
    val rippleRadiusPx = with(density) { rippleRadius.toPx() }
    val padMaxSizePx = with(density) { padMaxSize.toPx() }
    val dotsBandPx = dotRadiusPx * 2f + dotSpacingPx
    val cornerPx = with(density) { (cellInset / 2f).toPx() }

    val padDescription = stringResource(R.string.knock_code_pad_description)
    val tapsDescription = stringResource(R.string.knock_code_taps, state.tapCount)

    val ripple = remember { Animatable(1f) }
    var rippleCenter by remember { mutableStateOf(Offset.Unspecified) }

    BoxWithConstraints(modifier) {
        val geometry =
            with(density) {
                computeGeometry(
                    widthPx = maxWidth.toPx(),
                    heightPx = maxHeight.toPx(),
                    insetPx = insetPx,
                    dotsBandPx = dotsBandPx,
                    maxSizePx = padMaxSizePx,
                )
            }

        LaunchedEffect(state.tapCount, state.expectedLength) {
            if (state.tapCount <= 0 || state.expectedLength <= 0) {
                return@LaunchedEffect
            }
            delay(KnockCodePadState.TAP_TIMEOUT_MS)
            state.onTapTimeout()
        }

        Canvas(
            modifier =
                Modifier.fillMaxSize()
                    .semantics {
                        contentDescription = padDescription
                        stateDescription = tapsDescription
                    }
                    .pointerInput(geometry) {
                        val parent = view.parent
                        awaitEachGesture {
                            val down =
                                awaitFirstDown(
                                    requireUnconsumed = false,
                                    pass = PointerEventPass.Initial,
                                )
                            down.consume()
                            parent?.requestDisallowInterceptTouchEvent(true)

                            val position = down.position
                            if (geometry != null && geometry.square.contains(position)) {
                                val accepted =
                                    state.recordTap(
                                        row = rowFor(geometry, state.gridSize, position.y),
                                        col = colFor(geometry, state.gridSize, position.x),
                                    )
                                if (accepted) {
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    rippleCenter = position
                                    scope.launch {
                                        ripple.snapTo(0f)
                                        ripple.animateTo(1f, tween(KnockCodePadState.RIPPLE_DURATION_MS))
                                    }
                                }
                            }

                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                event.changes.forEach { it.consume() }
                                if (event.changes.none { it.pressed }) {
                                    break
                                }
                            }
                            parent?.requestDisallowInterceptTouchEvent(false)
                        }
                    }
        ) {
            if (geometry == null) {
                return@Canvas
            }

            drawCellSeparators(
                geometry = geometry,
                gridSize = state.gridSize,
                color = scheme.outline,
                strokeWidth = strokePx,
            )

            drawRoundRect(
                color = scheme.outline,
                topLeft = geometry.square.topLeft,
                size = geometry.square.size,
                cornerRadius = CornerRadius(cornerPx, cornerPx),
                style = Stroke(width = strokePx),
            )

            if (rippleCenter != Offset.Unspecified && ripple.value < 1f) {
                drawCircle(
                    color = scheme.primary.copy(alpha = RIPPLE_ALPHA * (1f - ripple.value)),
                    radius = rippleRadiusPx,
                    center = rippleCenter,
                )
            }

            drawProgressDots(
                geometry = geometry,
                tapCount = state.tapCount,
                filledColor = scheme.primary,
                emptyColor = scheme.onSurfaceVariant,
                radius = dotRadiusPx,
                spacing = dotSpacingPx,
                strokeWidth = dotStrokePx,
            )
        }
    }
}

private data class PadGeometry(val square: Rect, val dotsY: Float)

private fun computeGeometry(
    widthPx: Float,
    heightPx: Float,
    insetPx: Float,
    dotsBandPx: Float,
    maxSizePx: Float,
): PadGeometry? {
    val side =
        min(
            min(widthPx - 2f * insetPx, heightPx - 2f * insetPx - dotsBandPx),
            maxSizePx,
        )
    if (side <= 0f) {
        return null
    }

    val contentHeight = side + dotsBandPx
    val left = (widthPx - side) / 2f
    val top = (heightPx - contentHeight) / 2f
    return PadGeometry(
        square = Rect(left, top, left + side, top + side),
        dotsY = top + side + dotsBandPx / 2f,
    )
}

private fun rowFor(geometry: PadGeometry, gridSize: Int, y: Float): Int {
    val row = ((y - geometry.square.top) * gridSize / geometry.square.height).toInt()
    return row.coerceIn(0, gridSize - 1)
}

private fun colFor(geometry: PadGeometry, gridSize: Int, x: Float): Int {
    val col = ((x - geometry.square.left) * gridSize / geometry.square.width).toInt()
    return col.coerceIn(0, gridSize - 1)
}

private fun DrawScope.drawCellSeparators(
    geometry: PadGeometry,
    gridSize: Int,
    color: Color,
    strokeWidth: Float,
) {
    val step = geometry.square.width / gridSize
    for (i in 1 until gridSize) {
        val x = geometry.square.left + i * step
        val y = geometry.square.top + i * step
        drawLine(
            color = color,
            start = Offset(x, geometry.square.top),
            end = Offset(x, geometry.square.bottom),
            strokeWidth = strokeWidth,
        )
        drawLine(
            color = color,
            start = Offset(geometry.square.left, y),
            end = Offset(geometry.square.right, y),
            strokeWidth = strokeWidth,
        )
    }
}

private fun DrawScope.drawProgressDots(
    geometry: PadGeometry,
    tapCount: Int,
    filledColor: Color,
    emptyColor: Color,
    radius: Float,
    spacing: Float,
    strokeWidth: Float,
) {
    val shown = maxOf(LockPatternUtils.KNOCK_CODE_LENGTH_MIN, tapCount)
    val rowWidth = (shown - 1) * spacing
    var x = geometry.square.center.x - rowWidth / 2f

    for (i in 0 until shown) {
        val center = Offset(x, geometry.dotsY)
        if (i < tapCount) {
            drawCircle(color = filledColor, radius = radius, center = center)
        } else {
            drawCircle(
                color = emptyColor,
                radius = radius,
                center = center,
                style = Stroke(width = strokeWidth),
            )
        }
        x += spacing
    }
}
