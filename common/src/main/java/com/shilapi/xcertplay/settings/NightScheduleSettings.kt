package com.shilapi.xcertplay.settings

import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.shilapi.xcertplay.AirPlayPersistence
import com.shilapi.xcertplay.NightSchedule
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.web.WebSession

@Composable
internal fun NightScheduleSettings() {
    val context = LocalContext.current
    var schedule by remember { mutableStateOf(AirPlayPersistence.loadNightSchedule(context)) }
    listOf(true, false).forEach { start ->
        val minutes = if (start) schedule.startMinute else schedule.endMinute
        val title = settingsString(if (start) R.string.xp_night_start else R.string.xp_night_end)
        TextButton(onClick = {
            TimePickerDialog(context, { _, hour, minute ->
                val value = hour * 60 + minute
                if (value == if (start) schedule.endMinute else schedule.startMinute)
                    Toast.makeText(context, context.getString(R.string.xp_schedule_distinct), Toast.LENGTH_SHORT).show()
                else {
                    val next = if (start) NightSchedule(value, schedule.endMinute) else NightSchedule(schedule.startMinute, value)
                    runCatching { AirPlayPersistence.saveNightSchedule(context, next) }
                        .onSuccess { schedule = next; WebSession.refreshAppearance(context) }
                        .onFailure { Toast.makeText(context, context.getString(R.string.xp_schedule_save), Toast.LENGTH_SHORT).show() }
                }
            }, minutes / 60, minutes % 60, true).show()
        }) { Text(title + " · " + java.lang.String.format(java.util.Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60)) }
    }
    SettingsDescription(R.string.xp_schedule_help)
}

@Composable
internal fun AppearanceSettings(state: SettingsState, actions: SettingsActions) {
    SettingsChoice(state, actions, ChoiceSetting.APPEARANCE, R.string.xp_appearance_title,
        com.shilapi.xcertplay.CarPlayAppearance.entries.map { settingsString(it.labelRes) })
    SettingsDescription(R.string.xp_appearance_help)
    val appearance = com.shilapi.xcertplay.CarPlayAppearance.entries[state.choices[ChoiceSetting.APPEARANCE] ?: 0]
    if (appearance == com.shilapi.xcertplay.CarPlayAppearance.SCHEDULE || appearance == com.shilapi.xcertplay.CarPlayAppearance.BROWSER)
        NightScheduleSettings()
}
