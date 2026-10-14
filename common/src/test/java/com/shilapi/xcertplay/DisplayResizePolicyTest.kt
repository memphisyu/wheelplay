package com.shilapi.xcertplay
import org.junit.Assert.*
import org.junit.Test
class DisplayResizePolicyTest {
    @Test fun onlySmallViewportJitterKeepsSession() {
        assertTrue(DisplayResizePolicy.closeShape(1280,720,1280,720))
        assertTrue(DisplayResizePolicy.closeShape(1280,720,1282,722))
        assertTrue(DisplayResizePolicy.closeShape(1282,722,1280,720))
        assertTrue(DisplayResizePolicy.closeShape(1280,720,1300,732))
    }
    @Test fun browserBarsAndUniformScalingRenegotiate() {
        assertFalse(DisplayResizePolicy.closeShape(1280,846,1280,756))
        assertFalse(DisplayResizePolicy.closeShape(1280,756,1280,846))
        assertFalse(DisplayResizePolicy.closeShape(1280,720,1920,1080))
        assertFalse(DisplayResizePolicy.closeShape(1920,1080,1280,720))
        assertFalse(DisplayResizePolicy.closeShape(1280,720,1500,844))
        assertFalse(DisplayResizePolicy.closeShape(1280,720,1280,660))
    }
    @Test fun reportedG9ToolbarChangesKeepSessionInBothDirections() {
        assertTrue(DisplayResizePolicy.closeShape(3722,1836,3840,2024))
        assertTrue(DisplayResizePolicy.closeShape(3840,2024,3722,1836))
        assertFalse(DisplayResizePolicy.closeShape(3840,2160,3722,1836))
        assertFalse(DisplayResizePolicy.closeShape(3722,1836,3840,2160))
    }
    @Test fun splitScreenPortraitAndInvalidSizesDoNotKeepSession() {
        assertFalse(DisplayResizePolicy.closeShape(1280,846,1372,686))
        assertFalse(DisplayResizePolicy.closeShape(1280,846,1082,1280))
        assertFalse(DisplayResizePolicy.closeShape(0,0,1280,720))
    }
}
