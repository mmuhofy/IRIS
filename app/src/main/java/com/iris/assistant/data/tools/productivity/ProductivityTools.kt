package com.iris.assistant.data.tools.productivity

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.CalendarContract
import com.iris.assistant.domain.tools.JarvisTool
import com.iris.assistant.domain.tools.ToolResult
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

// UNTESTED — verify productivity tool behavior on device before use

// ---------------------------------------------------------------------------
// set_alarm
// ---------------------------------------------------------------------------

@Singleton
class SetAlarmTool @Inject constructor(
    @ApplicationContext private val context: Context
) : JarvisTool {

    override val name        = "set_alarm"
    override val description = "Sets a one-time alarm at a specified time. Time is given as HH:MM in 24-hour format."
    override val parameters  = JSONObject("""
        {
            "type": "object",
            "properties": {
                "time": {
                    "type": "string",
                    "description": "Alarm time in HH:MM 24-hour format (e.g. 07:30)"
                },
                "label": {
                    "type": "string",
                    "description": "Optional label for the alarm"
                }
            },
            "required": ["time"]
        }
    """.trimIndent())
    override val requiredPermission: String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            Manifest.permission.SCHEDULE_EXACT_ALARM
        else null

    override suspend fun execute(args: JSONObject): ToolResult {
        val timeStr = args.optString("time").takeIf { it.isNotBlank() }
            ?: return ToolResult.Error("Alarm zamanı belirtilmedi (HH:MM).")
        val label = args.optString("label", "IRIS Alarmı")

        @Suppress("MagicNumber")
        val parts = timeStr.split(":").mapNotNull { it.toIntOrNull() }
        if (parts.size != 2 || parts[0] !in 0..23 || parts[1] !in 0..59) {
            return ToolResult.Error("Geçersiz zaman formatı. HH:MM kullanın (ör. 07:30).")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                return ToolResult.PermissionRequired(
                    permission = Manifest.permission.SCHEDULE_EXACT_ALARM,
                    rationale = "IRIS alarm kurabilmek için tam zamanlı alarm iznine ihtiyaç duyar."
                )
            }
        }

        return runCatching {
            val calendar = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, parts[0])
                set(java.util.Calendar.MINUTE, parts[1])
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
                if (before(java.util.Calendar.getInstance())) {
                    add(java.util.Calendar.DAY_OF_MONTH, 1)
                }
            }

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val intent = Intent("com.iris.assistant.ALARM_ACTION").apply {
                putExtra("label", label)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                timeStr.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(calendar.timeInMillis, pendingIntent),
                pendingIntent
            )

            val hour = "${parts[0]}".padStart(2, '0')
            val min = "${parts[1]}".padStart(2, '0')
            ToolResult.Success("Alarm kuruldu: $hour:$min — $label")
        }.getOrElse { e ->
            ToolResult.Error("Alarm kurulamadı: ${e.message}", e)
        }
    }
}

// ---------------------------------------------------------------------------
// add_reminder
// ---------------------------------------------------------------------------

@Singleton
class AddReminderTool @Inject constructor(
    @ApplicationContext private val context: Context
) : JarvisTool {

    override val name        = "add_reminder"
    override val description = "Sets a reminder that will notify the user at a specified time. Use for one-time reminders."
    override val parameters  = JSONObject("""
        {
            "type": "object",
            "properties": {
                "title": {
                    "type": "string",
                    "description": "Reminder title"
                },
                "time": {
                    "type": "string",
                    "description": "Reminder time in HH:MM 24-hour format (e.g. 14:00)"
                },
                "date": {
                    "type": "string",
                    "description": "Optional date in DD.MM.YYYY format (e.g. 17.06.2026). If omitted, today is used."
                }
            },
            "required": ["title", "time"]
        }
    """.trimIndent())
    override val requiredPermission: String? = null

    override suspend fun execute(args: JSONObject): ToolResult {
        val title = args.optString("title").takeIf { it.isNotBlank() }
            ?: return ToolResult.Error("Hatırlatıcı başlığı belirtilmedi.")
        val timeStr = args.optString("time").takeIf { it.isNotBlank() }
            ?: return ToolResult.Error("Hatırlatıcı zamanı belirtilmedi (HH:MM).")
        val dateStr = args.optString("date").takeIf { it.isNotBlank() }

        @Suppress("MagicNumber")
        val timeParts = timeStr.split(":").mapNotNull { it.toIntOrNull() }
        if (timeParts.size != 2 || timeParts[0] !in 0..23 || timeParts[1] !in 0..59) {
            return ToolResult.Error("Geçersiz zaman formatı. HH:MM kullanın (ör. 14:00).")
        }

        return runCatching {
            val calendar = java.util.Calendar.getInstance()

            if (dateStr != null) {
                val dateParts = dateStr.split(".").mapNotNull { it.toIntOrNull() }
                if (dateParts.size == 3) {
                    calendar.set(dateParts[2], dateParts[1] - 1, dateParts[0])
                } else {
                    return@runCatching ToolResult.Error("Geçersiz tarih formatı. GG.AA.YYYY kullanın (ör. 17.06.2026).")
                }
            }

            calendar.set(java.util.Calendar.HOUR_OF_DAY, timeParts[0])
            calendar.set(java.util.Calendar.MINUTE, timeParts[1])
            calendar.set(java.util.Calendar.SECOND, 0)
            calendar.set(java.util.Calendar.MILLISECOND, 0)

            if (calendar.before(java.util.Calendar.getInstance())) {
                return@runCatching ToolResult.Error("Belirtilen zaman geçmişte. İleri bir zaman girin.")
            }

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val intent = Intent("com.iris.assistant.REMINDER_ACTION").apply {
                putExtra("title", title)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                ("reminder_${calendar.timeInMillis}").hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)

            val hour = "${timeParts[0]}".padStart(2, '0')
            val min = "${timeParts[1]}".padStart(2, '0')
            val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)
            val month = calendar.get(java.util.Calendar.MONTH) + 1
            val year = calendar.get(java.util.Calendar.YEAR)
            ToolResult.Success("Hatırlatıcı kuruldu: $title — $day.$month.$year $hour:$min")
        }.getOrElse { e ->
            ToolResult.Error("Hatırlatıcı kurulamadı: ${e.message}", e)
        }
    }
}

// ---------------------------------------------------------------------------
// add_calendar_event
// ---------------------------------------------------------------------------

@Singleton
class AddCalendarEventTool @Inject constructor(
    @ApplicationContext private val context: Context
) : JarvisTool {

    override val name        = "add_calendar_event"
    override val description = "Adds an event to the user's calendar. Requires date, time, and title. Duration defaults to 1 hour."
    override val parameters  = JSONObject("""
        {
            "type": "object",
            "properties": {
                "title": {
                    "type": "string",
                    "description": "Event title"
                },
                "date": {
                    "type": "string",
                    "description": "Event date in DD.MM.YYYY format (e.g. 17.06.2026)"
                },
                "start_time": {
                    "type": "string",
                    "description": "Start time in HH:MM 24-hour format (e.g. 10:00)"
                },
                "end_time": {
                    "type": "string",
                    "description": "Optional end time in HH:MM format. Defaults to 1 hour after start."
                },
                "description": {
                    "type": "string",
                    "description": "Optional event description"
                },
                "location": {
                    "type": "string",
                    "description": "Optional event location"
                }
            },
            "required": ["title", "date", "start_time"]
        }
    """.trimIndent())
    override val requiredPermission: String? = Manifest.permission.WRITE_CALENDAR

    override suspend fun execute(args: JSONObject): ToolResult {
        val title = args.optString("title").takeIf { it.isNotBlank() }
            ?: return ToolResult.Error("Etkinlik başlığı belirtilmedi.")
        val dateStr = args.optString("date").takeIf { it.isNotBlank() }
            ?: return ToolResult.Error("Etkinlik tarihi belirtilmedi (GG.AA.YYYY).")
        val startTime = args.optString("start_time").takeIf { it.isNotBlank() }
            ?: return ToolResult.Error("Başlangıç zamanı belirtilmedi (HH:MM).")
        val endTime = args.optString("end_time").takeIf { it.isNotBlank() }
        val description = args.optString("description", "")
        val location = args.optString("location", "")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            context.checkSelfPermission(Manifest.permission.WRITE_CALENDAR)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return ToolResult.PermissionRequired(
                permission = Manifest.permission.WRITE_CALENDAR,
                rationale = "IRIS takvime etkinlik ekleyebilmek için takvim iznine ihtiyaç duyar."
            )
        }

        @Suppress("MagicNumber")
        val dateParts = dateStr.split(".").mapNotNull { it.toIntOrNull() }
        if (dateParts.size != 3) {
            return ToolResult.Error("Geçersiz tarih formatı. GG.AA.YYYY kullanın (ör. 17.06.2026).")
        }

        @Suppress("MagicNumber")
        val startParts = startTime.split(":").mapNotNull { it.toIntOrNull() }
        if (startParts.size != 2 || startParts[0] !in 0..23 || startParts[1] !in 0..59) {
            return ToolResult.Error("Geçersiz başlangıç zamanı. HH:MM kullanın (ör. 10:00).")
        }

        return runCatching {
            val startCal = java.util.Calendar.getInstance().apply {
                set(dateParts[2], dateParts[1] - 1, dateParts[0], startParts[0], startParts[1], 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }

            val endCal = java.util.Calendar.getInstance().apply {
                if (endTime != null) {
                    val endParts = endTime.split(":").mapNotNull { it.toIntOrNull() }
                    if (endParts.size == 2) {
                        set(dateParts[2], dateParts[1] - 1, dateParts[0], endParts[0], endParts[1], 0)
                    } else {
                        timeInMillis = startCal.timeInMillis + 3_600_000L // 1 hour default
                    }
                } else {
                    timeInMillis = startCal.timeInMillis + 3_600_000L
                }
                set(java.util.Calendar.MILLISECOND, 0)
            }

            val values = ContentValues().apply {
                put(CalendarContract.Events.DTSTART, startCal.timeInMillis)
                put(CalendarContract.Events.DTEND, endCal.timeInMillis)
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DESCRIPTION, description)
                put(CalendarContract.Events.EVENT_LOCATION, location)
                put(CalendarContract.Events.CALENDAR_ID, 1) // primary calendar
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            }

            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            if (uri != null) {
                ToolResult.Success("Takvim etkinliği oluşturuldu: $title")
            } else {
                ToolResult.Error("Takvim etkinliği oluşturulamadı.")
            }
        }.getOrElse { e ->
            ToolResult.Error("Takvim etkinliği oluşturulamadı: ${e.message}", e)
        }
    }
}
