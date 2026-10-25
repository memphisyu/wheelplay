package com.shilapi.xcertplay.web

import com.shilapi.xcertplay.shared.R as UiR

/** A grace period for recoverable interruptions, not a video buffering budget. */
enum class RtcRecoveryPolicy(
    val graceMs: Long,
    @param:androidx.annotation.StringRes val labelRes: Int,
) {
    QUICK(4_000, UiR.string.ui_4_seconds_fast_fallback),
    BALANCED(10_000, UiR.string.ui_10_seconds_default),
    PATIENT(30_000, UiR.string.ui_30_seconds_keep_webrtc_longer);

    val firstFrameMs
        get() = maxOf(10_000L, graceMs)
}
