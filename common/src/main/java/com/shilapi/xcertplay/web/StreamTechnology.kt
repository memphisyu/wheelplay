package com.shilapi.xcertplay.web

import com.shilapi.xcertplay.shared.R as UiR

enum class StreamTechnology(@param:androidx.annotation.StringRes val labelRes: Int) {
    WEBRTC(UiR.string.ui_webrtc_video_passthrough_automatic_fallback),
    JPEG(UiR.string.ui_jpeg_compatibility_mode),
}
