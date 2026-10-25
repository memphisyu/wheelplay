package com.shilapi.xcertplay.airplay

import com.shilapi.xcertplay.shared.R as UiR

/**
 * The single user-facing CarPlay size. iOS keeps controls at a fixed physical size, so the size is
 * expressed as the physical screen width reported to the iPhone: a wider screen gets smaller
 * controls.
 */
enum class CarPlaySize(
    @param:androidx.annotation.StringRes val labelRes: Int,
    val widthMillimeters: Int,
) {
    LARGE(UiR.string.host_large, 250),
    MEDIUM(UiR.string.host_medium, 300),
    SMALL(UiR.string.host_small, 350);

    companion object {
        val DEFAULT = MEDIUM

        /** Maps any stored width, including values from older builds, to the nearest preset. */
        fun fromWidthMillimeters(millimeters: Int): CarPlaySize =
            entries.minBy { kotlin.math.abs(it.widthMillimeters - millimeters) }
    }
}
