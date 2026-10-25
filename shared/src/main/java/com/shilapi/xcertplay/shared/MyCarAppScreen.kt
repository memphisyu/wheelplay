package com.shilapi.xcertplay.shared

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import com.shilapi.xcertplay.shared.R as UiR

class MyCarAppScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        return MessageTemplate.Builder(
                carContext.getString(
                    UiR.string.ui_hardware_transport_is_not_configured_board_i2c_needs_a
                )
            )
            .setHeaderAction(Action.APP_ICON)
            .setTitle(carContext.getString(UiR.string.ui_xcertplay_hardware_status))
            .build()
    }
}
