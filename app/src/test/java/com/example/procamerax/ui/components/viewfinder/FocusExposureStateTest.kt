package com.example.procamerax.ui.components.viewfinder

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusExposureStateTest {

    @Test
    fun `continuous drag sweeps smoothly across full EV range without losing tracking`() {
        val state = FocusExposureState()
        val emittedIndices = mutableListOf<Int>()

        state.showFocusAt(
            point = Offset(500f, 800f),
            currentExposureIndex = 0,
            minExposureIndex = -6,
            maxExposureIndex = 6
        )

        assertTrue(state.isOverlayVisible)
        assertEquals(0.5f, state.dragNormalizedEv, 0.001f)

        state.beginEvDrag(
            currentExposureIndex = 0,
            minExposureIndex = -6,
            maxExposureIndex = 6
        )
        assertTrue(state.isDraggingEv)

        // Simulate 20 small upward finger move events (-3px each = -60px on a 120px track -> +0.5 normalized -> +6 EV)
        repeat(20) {
            state.updateEvDragByDelta(
                dragDeltaYPx = -3f,
                dragRangePx = 120f,
                minExposureIndex = -6,
                maxExposureIndex = 6,
                onExposureIndexChanged = { emittedIndices.add(it) }
            )
        }

        assertEquals(1.0f, state.dragNormalizedEv, 0.001f)
        assertEquals(listOf(1, 2, 3, 4, 5, 6), emittedIndices)

        // Now sweep all the way down to -6 EV (+120px on a 120px track) in fine increments
        emittedIndices.clear()
        repeat(40) {
            state.updateEvDragByDelta(
                dragDeltaYPx = 3f,
                dragRangePx = 120f,
                minExposureIndex = -6,
                maxExposureIndex = 6,
                onExposureIndexChanged = { emittedIndices.add(it) }
            )
        }

        assertEquals(0.0f, state.dragNormalizedEv, 0.001f)
        assertEquals(listOf(5, 4, 3, 2, 1, 0, -1, -2, -3, -4, -5, -6), emittedIndices)

        state.endEvDrag()
        assertFalse(state.isDraggingEv)
        assertEquals(1, state.dragSessionCounter)
    }

    @Test
    fun `overlay never auto-hides while user is actively dragging EV`() {
        val state = FocusExposureState()
        state.showFocusAt(
            point = Offset(300f, 400f),
            currentExposureIndex = 0,
            minExposureIndex = -12,
            maxExposureIndex = 12
        )

        state.beginEvDrag(0, -12, 12)
        state.markAutoHidden()
        assertTrue(state.isOverlayVisible)

        state.endEvDrag()
        state.markAutoHidden()
        assertFalse(state.isOverlayVisible)
    }

    @Test
    fun `tapping same point again after auto-hide re-shows overlay and increments trigger`() {
        val state = FocusExposureState()
        val samePoint = Offset(420f, 640f)

        state.showFocusAt(samePoint, 0, -6, 6)
        val firstTrigger = state.focusTrigger
        state.markAutoHidden()
        assertFalse(state.isOverlayVisible)

        state.showFocusAt(samePoint, 0, -6, 6)
        assertTrue(state.isOverlayVisible)
        assertEquals(firstTrigger + 1, state.focusTrigger)
    }

    @Test
    fun `release preserves sub-pixel thumb position while external EV reset syncs cleanly`() {
        val state = FocusExposureState()
        var currentIndex = 0

        state.showFocusAt(Offset(200f, 200f), 0, -6, 6)
        state.beginEvDrag(0, -6, 6)

        // Move upward by 23px out of 120px -> normalized = 0.5 + 23/120 = 0.691667 -> rounds to index +2
        state.updateEvDragByDelta(
            dragDeltaYPx = -23f,
            dragRangePx = 120f,
            minExposureIndex = -6,
            maxExposureIndex = 6,
            onExposureIndexChanged = { currentIndex = it }
        )
        assertEquals(2, currentIndex)
        val subPixelAtRelease = state.dragNormalizedEv
        state.endEvDrag()

        // Syncing with same rounded index (+2) must NOT snap or twitch the thumb
        state.syncExternalExposure(exposureIndex = 2, minExposureIndex = -6, maxExposureIndex = 6)
        assertEquals(subPixelAtRelease, state.dragNormalizedEv, 0.0001f)

        // External reset (e.g., Pro mode RESET to 0) syncs thumb back to 0.5f
        state.syncExternalExposure(exposureIndex = 0, minExposureIndex = -6, maxExposureIndex = 6)
        assertEquals(0.5f, state.dragNormalizedEv, 0.0001f)
    }
}
