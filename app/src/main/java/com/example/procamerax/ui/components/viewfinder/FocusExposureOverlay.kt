package com.example.procamerax.ui.components.viewfinder

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

@Stable
class FocusExposureState {
    var focusPoint by mutableStateOf<Offset?>(null)
        private set

    var isOverlayVisible by mutableStateOf(false)
        private set

    var isDraggingEv by mutableStateOf(false)
        private set

    var dragNormalizedEv by mutableFloatStateOf(0.5f)
        private set

    var focusTrigger by mutableIntStateOf(0)
        private set

    var dragSessionCounter by mutableIntStateOf(0)
        private set

    private var lastDispatchedIndex: Int? = null

    fun showFocusAt(
        point: Offset,
        currentExposureIndex: Int,
        minExposureIndex: Int,
        maxExposureIndex: Int
    ) {
        focusPoint = point
        isDraggingEv = false
        isOverlayVisible = true
        lastDispatchedIndex = currentExposureIndex
        dragNormalizedEv = computeNormalizedEv(
            exposureIndex = currentExposureIndex,
            minExposureIndex = minExposureIndex,
            maxExposureIndex = maxExposureIndex
        )
        focusTrigger++
    }

    fun dismiss() {
        isDraggingEv = false
        isOverlayVisible = false
        focusPoint = null
    }

    internal fun markAutoHidden() {
        if (!isDraggingEv) {
            isOverlayVisible = false
        }
    }

    fun syncExternalExposure(
        exposureIndex: Int,
        minExposureIndex: Int,
        maxExposureIndex: Int
    ) {
        if (isDraggingEv) return
        if (maxExposureIndex <= minExposureIndex) {
            dragNormalizedEv = 0.5f
            lastDispatchedIndex = exposureIndex
            return
        }
        val mappedFromCurrentFloat = (
            minExposureIndex + dragNormalizedEv * (maxExposureIndex - minExposureIndex)
            ).roundToInt().coerceIn(minExposureIndex, maxExposureIndex)

        if (mappedFromCurrentFloat != exposureIndex) {
            dragNormalizedEv = computeNormalizedEv(
                exposureIndex = exposureIndex,
                minExposureIndex = minExposureIndex,
                maxExposureIndex = maxExposureIndex
            )
        }
        lastDispatchedIndex = exposureIndex
    }

    fun beginEvDrag(
        currentExposureIndex: Int,
        minExposureIndex: Int,
        maxExposureIndex: Int
    ) {
        if (maxExposureIndex <= minExposureIndex) return
        if (!isDraggingEv) {
            syncExternalExposure(currentExposureIndex, minExposureIndex, maxExposureIndex)
            isDraggingEv = true
        }
        isOverlayVisible = true
    }

    fun updateEvDragByDelta(
        dragDeltaYPx: Float,
        dragRangePx: Float,
        minExposureIndex: Int,
        maxExposureIndex: Int,
        onExposureIndexChanged: (Int) -> Unit
    ) {
        if (maxExposureIndex <= minExposureIndex || dragRangePx <= 0f) return
        isOverlayVisible = true
        if (!isDraggingEv) {
            isDraggingEv = true
        }
        val nextNormalized = (dragNormalizedEv - (dragDeltaYPx / dragRangePx)).coerceIn(0f, 1f)
        dragNormalizedEv = nextNormalized

        val targetIndex = (
            minExposureIndex + nextNormalized * (maxExposureIndex - minExposureIndex)
            ).roundToInt().coerceIn(minExposureIndex, maxExposureIndex)

        if (targetIndex != lastDispatchedIndex) {
            lastDispatchedIndex = targetIndex
            onExposureIndexChanged(targetIndex)
        }
    }

    fun endEvDrag() {
        if (isDraggingEv) {
            isDraggingEv = false
            dragSessionCounter++
        }
    }

    companion object {
        fun computeNormalizedEv(
            exposureIndex: Int,
            minExposureIndex: Int,
            maxExposureIndex: Int
        ): Float {
            if (maxExposureIndex <= minExposureIndex) return 0.5f
            return (
                (exposureIndex - minExposureIndex).toFloat() /
                    (maxExposureIndex - minExposureIndex).toFloat()
                ).coerceIn(0f, 1f)
        }
    }
}

@Composable
fun rememberFocusExposureState(): FocusExposureState {
    return remember { FocusExposureState() }
}

@Composable
fun FocusExposureOverlay(
    focusPoint: Offset?,
    exposureIndex: Int,
    minExposureIndex: Int,
    maxExposureIndex: Int,
    exposureStep: Float,
    onExposureIndexChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    state: FocusExposureState = rememberFocusExposureState(),
    onTapFocus: ((Offset) -> Unit)? = null
) {
    LaunchedEffect(focusPoint) {
        if (focusPoint == null) {
            if (state.focusPoint != null) {
                state.dismiss()
            }
        } else if (state.focusPoint != focusPoint) {
            state.showFocusAt(
                point = focusPoint,
                currentExposureIndex = exposureIndex,
                minExposureIndex = minExposureIndex,
                maxExposureIndex = maxExposureIndex
            )
        }
    }

    LaunchedEffect(exposureIndex, minExposureIndex, maxExposureIndex) {
        state.syncExternalExposure(
            exposureIndex = exposureIndex,
            minExposureIndex = minExposureIndex,
            maxExposureIndex = maxExposureIndex
        )
    }

    LaunchedEffect(
        state.focusTrigger,
        state.isDraggingEv,
        state.dragSessionCounter,
        state.isOverlayVisible
    ) {
        if (state.isOverlayVisible && !state.isDraggingEv) {
            delay(2500L)
            state.markAutoHidden()
        }
    }

    val ringScaleAnimatable = remember { Animatable(1f) }
    LaunchedEffect(state.focusTrigger) {
        if (state.focusTrigger > 0) {
            ringScaleAnimatable.snapTo(1.25f)
            ringScaleAnimatable.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
            )
        }
    }

    val ringAlpha by animateFloatAsState(
        targetValue = if (state.isOverlayVisible) 1f else 0f,
        animationSpec = tween(durationMillis = if (state.isOverlayVisible) 120 else 350),
        label = "focusRingAlpha"
    )

    val thumbScale by animateFloatAsState(
        targetValue = if (state.isDraggingEv) 1.18f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "evThumbScale"
    )

    val point = focusPoint ?: state.focusPoint ?: return
    if (ringAlpha <= 0f) return

    val hasExposureRange = maxExposureIndex > minExposureIndex
    val evValue = exposureIndex * exposureStep
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = ringAlpha }
    ) {
        val containerWidthPx = constraints.maxWidth.toFloat()
        val containerHeightPx = constraints.maxHeight.toFloat()

        val trackHalfHeightPx = with(density) { 56.dp.toPx() }
        val edgePaddingPx = with(density) { 28.dp.toPx() }
        val isRightSide = point.x + with(density) { 74.dp.toPx() } < containerWidthPx
        val sliderCenterX = if (isRightSide) {
            point.x + with(density) { 54.dp.toPx() }
        } else {
            point.x - with(density) { 54.dp.toPx() }
        }

        val minCenterY = (edgePaddingPx + trackHalfHeightPx).coerceAtMost(containerHeightPx / 2f)
        val maxCenterY = (containerHeightPx - edgePaddingPx - trackHalfHeightPx).coerceAtLeast(minCenterY)
        val sliderCenterY = point.y.coerceIn(minCenterY, maxCenterY)
        val topY = sliderCenterY - trackHalfHeightPx
        val bottomY = sliderCenterY + trackHalfHeightPx
        val trackHeightPx = (bottomY - topY).coerceAtLeast(1f)

        Canvas(modifier = Modifier.fillMaxSize()) {
            val radiusPx = 34.dp.toPx() * ringScaleAnimatable.value
            drawCircle(
                color = Color.Yellow,
                radius = radiusPx,
                center = point,
                style = Stroke(width = 2.dp.toPx())
            )

            // Inner focus ticks
            val tickLen = 6.dp.toPx()
            drawLine(
                color = Color.Yellow,
                start = Offset(point.x, point.y - radiusPx),
                end = Offset(point.x, point.y - radiusPx + tickLen),
                strokeWidth = 2.dp.toPx()
            )
            drawLine(
                color = Color.Yellow,
                start = Offset(point.x, point.y + radiusPx - tickLen),
                end = Offset(point.x, point.y + radiusPx),
                strokeWidth = 2.dp.toPx()
            )
            drawLine(
                color = Color.Yellow,
                start = Offset(point.x - radiusPx, point.y),
                end = Offset(point.x - radiusPx + tickLen, point.y),
                strokeWidth = 2.dp.toPx()
            )
            drawLine(
                color = Color.Yellow,
                start = Offset(point.x + radiusPx - tickLen, point.y),
                end = Offset(point.x + radiusPx, point.y),
                strokeWidth = 2.dp.toPx()
            )

            // Draw Exposure Slider Track beside the focus ring
            if (hasExposureRange) {
                val thumbY = bottomY - state.dragNormalizedEv * trackHeightPx

                // Inactive track
                drawLine(
                    color = Color.White.copy(alpha = 0.45f),
                    start = Offset(sliderCenterX, topY),
                    end = Offset(sliderCenterX, bottomY),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Active fill from bottom to thumb
                drawLine(
                    color = Color.Yellow,
                    start = Offset(sliderCenterX, thumbY),
                    end = Offset(sliderCenterX, bottomY),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        if (hasExposureRange) {
            val hitWidthDp = 64.dp
            val hitHeightDp = 152.dp
            val hitWidthPx = with(density) { hitWidthDp.toPx() }
            val hitHeightPx = with(density) { hitHeightDp.toPx() }
            val hitBoxOffsetX = (sliderCenterX - hitWidthPx / 2f).roundToInt()
            val hitBoxOffsetY = (sliderCenterY - hitHeightPx / 2f).roundToInt()
            val dragStartThresholdPx = with(density) { 2.dp.toPx() }

            val currentExposureIndex by rememberUpdatedState(exposureIndex)
            val currentMinExposureIndex by rememberUpdatedState(minExposureIndex)
            val currentMaxExposureIndex by rememberUpdatedState(maxExposureIndex)
            val currentTrackHeightPx by rememberUpdatedState(trackHeightPx)
            val currentHitBoxOffsetX by rememberUpdatedState(hitBoxOffsetX)
            val currentHitBoxOffsetY by rememberUpdatedState(hitBoxOffsetY)
            val currentOnExposureIndexChanged by rememberUpdatedState(onExposureIndexChanged)
            val currentOnTapFocus by rememberUpdatedState(onTapFocus)

            Box(
                modifier = Modifier
                    .offset { IntOffset(hitBoxOffsetX, hitBoxOffsetY) }
                    .size(width = hitWidthDp, height = hitHeightDp)
                    .pointerInput(state) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val pointerId = down.id
                            var totalDx = 0f
                            var totalDy = 0f
                            var isDraggingThisGesture = false

                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pressedCount = event.changes.count { it.pressed }
                                    if (pressedCount > 1 && !isDraggingThisGesture) {
                                        break
                                    }

                                    val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                                    if (!change.pressed) {
                                        change.consume()
                                        if (isDraggingThisGesture) {
                                            state.endEvDrag()
                                        } else {
                                            val tapInParent = Offset(
                                                x = currentHitBoxOffsetX + down.position.x,
                                                y = currentHitBoxOffsetY + down.position.y
                                            )
                                            currentOnTapFocus?.invoke(tapInParent)
                                        }
                                        break
                                    }

                                    val delta = change.positionChange()
                                    if (delta != Offset.Zero) {
                                        totalDx += delta.x
                                        totalDy += delta.y

                                        if (!isDraggingThisGesture) {
                                            if (
                                                abs(totalDy) >= dragStartThresholdPx &&
                                                abs(totalDy) >= abs(totalDx) * 0.7f
                                            ) {
                                                isDraggingThisGesture = true
                                                state.beginEvDrag(
                                                    currentExposureIndex = currentExposureIndex,
                                                    minExposureIndex = currentMinExposureIndex,
                                                    maxExposureIndex = currentMaxExposureIndex
                                                )
                                                change.consume()
                                                val initialExcessDy =
                                                    totalDy - dragStartThresholdPx * sign(totalDy)
                                                state.updateEvDragByDelta(
                                                    dragDeltaYPx = initialExcessDy,
                                                    dragRangePx = currentTrackHeightPx,
                                                    minExposureIndex = currentMinExposureIndex,
                                                    maxExposureIndex = currentMaxExposureIndex,
                                                    onExposureIndexChanged = currentOnExposureIndexChanged
                                                )
                                            } else if (
                                                abs(totalDx) > dragStartThresholdPx * 4f &&
                                                abs(totalDx) > abs(totalDy) * 1.5f
                                            ) {
                                                break
                                            }
                                        } else {
                                            change.consume()
                                            state.updateEvDragByDelta(
                                                dragDeltaYPx = delta.y,
                                                dragRangePx = currentTrackHeightPx,
                                                minExposureIndex = currentMinExposureIndex,
                                                maxExposureIndex = currentMaxExposureIndex,
                                                onExposureIndexChanged = currentOnExposureIndexChanged
                                            )
                                        }
                                    }
                                }
                            } finally {
                                if (isDraggingThisGesture && state.isDraggingEv) {
                                    state.endEvDrag()
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.WbSunny,
                    contentDescription = "Exposure Compensation",
                    tint = Color.Yellow,
                    modifier = Modifier
                        .offset {
                            val thumbOffsetYPx =
                                ((0.5f - state.dragNormalizedEv) * trackHeightPx).roundToInt()
                            IntOffset(0, thumbOffsetYPx)
                        }
                        .graphicsLayer {
                            scaleX = thumbScale
                            scaleY = thumbScale
                        }
                        .size(24.dp)
                        .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                        .padding(3.dp)
                )
            }

            if (exposureIndex != 0 || state.isDraggingEv) {
                val topSafeMarginPx = with(density) { 16.dp.toPx() }
                val badgeRawY = point.y - with(density) { 64.dp.toPx() }
                val badgeY = if (badgeRawY >= topSafeMarginPx) {
                    badgeRawY.roundToInt()
                } else {
                    (point.y + with(density) { 42.dp.toPx() })
                        .coerceAtMost(containerHeightPx - with(density) { 36.dp.toPx() })
                        .roundToInt()
                }
                val minBadgeX = with(density) { 12.dp.toPx() }
                val maxBadgeX =
                    (containerWidthPx - with(density) { 76.dp.toPx() }).coerceAtLeast(minBadgeX)
                val badgeX = (point.x - with(density) { 28.dp.toPx() })
                    .coerceIn(minBadgeX, maxBadgeX)
                    .roundToInt()

                Box(
                    modifier = Modifier
                        .offset { IntOffset(badgeX, badgeY) }
                        .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = String.format(Locale.US, "%+.1f EV", evValue),
                        color = Color.Yellow,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}
