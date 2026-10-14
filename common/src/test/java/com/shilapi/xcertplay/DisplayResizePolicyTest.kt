package com.shilapi.xcertplay
import org.junit.Assert.*
import org.junit.Test
class DisplayResizePolicyTest {
    @Test fun browserBarsAndUniformScalingKeepSession() {
        assertTrue(DisplayResizePolicy.closeShape(1280,846,1280,756))
        assertTrue(DisplayResizePolicy.closeShape(1280,756,1280,846))
        assertTrue(DisplayResizePolicy.closeShape(1280,720,1920,1080))
    }
    @Test fun splitScreenPortraitAndInvalidSizesDoNotKeepSession() {
        assertFalse(DisplayResizePolicy.closeShape(1280,846,1372,686))
        assertFalse(DisplayResizePolicy.closeShape(1280,846,1082,1280))
        assertFalse(DisplayResizePolicy.closeShape(0,0,1280,720))
    }
}
