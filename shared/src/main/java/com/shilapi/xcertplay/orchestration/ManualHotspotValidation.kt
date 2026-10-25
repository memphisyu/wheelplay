package com.shilapi.xcertplay.orchestration

/**
 * Rules an existing (car) hotspot must meet before CarPlay can hand its credentials to the iPhone.
 */
object ManualHotspotValidation {
    /** Security implied by the password: the car hotspot UI only offers open or WPA2 networks. */
    fun securityFor(passphrase: String): ManualHotspotSecurity =
        if (passphrase.isEmpty()) ManualHotspotSecurity.OPEN else ManualHotspotSecurity.WPA2

    /** Returns a message for the user, or null when the name and password can be used. */
    fun validate(ssid: String, passphrase: String): com.shilapi.xcertplay.i18n.UiText? =
        when {
            ssid.isBlank() ->
                com.shilapi.xcertplay.i18n.UiText(
                    com.shilapi.xcertplay.shared.R.string.ui_enter_the_car_hotspot_name
                )
            ssid.encodeToByteArray().size > 32 ->
                com.shilapi.xcertplay.i18n.UiText(
                    com.shilapi.xcertplay.shared.R.string
                        .ui_the_hotspot_name_must_be_at_most_32_bytes
                )
            '\u0000' in ssid || '\u0000' in passphrase ->
                com.shilapi.xcertplay.i18n.UiText(
                    com.shilapi.xcertplay.shared.R.string
                        .ui_the_name_or_password_contains_an_invalid_character
                )
            passphrase.isNotEmpty() && passphrase.length !in 8..63 ->
                com.shilapi.xcertplay.i18n.UiText(
                    com.shilapi.xcertplay.shared.R.string
                        .ui_the_hotspot_password_must_be_8_63_characters
                )
            else -> null
        }
}
