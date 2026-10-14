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
        assertFalse(DisplayResizePolicy.closeShape(1280,720,1320,744))
        assertFalse(DisplayResizePolicy.closeShape(1280,720,1300,708))
    }
    @Test fun splitScreenPortraitAndInvalidSizesDoNotKeepSession() {
        assertFalse(DisplayResizePolicy.closeShape(1280,846,1372,686))
        assertFalse(DisplayResizePolicy.closeShape(1280,846,1082,1280))
        assertFalse(DisplayResizePolicy.closeShape(0,0,1280,720))
    }
}
