package com.iris.assistant.data.tools.communication

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.iris.assistant.domain.tools.JarvisTool
import com.iris.assistant.domain.tools.ToolResult
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

// UNTESTED — verify communication tool behavior on device before use

// ---------------------------------------------------------------------------
// make_call
// ---------------------------------------------------------------------------

@Singleton
class MakeCallTool @Inject constructor(
    @ApplicationContext private val context: Context
) : JarvisTool {

    override val name        = "make_call"
    override val description = "Opens the dialer with a given phone number pre-filled. Does NOT automatically dial — user must press the call button."
    override val parameters  = JSONObject("""
        {
            "type": "object",
            "properties": {
                "phone_number": {
                    "type": "string",
                    "description": "Phone number to call"
                }
            },
            "required": ["phone_number"]
        }
    """.trimIndent())
    override val requiredPermission: String? = null

    override suspend fun execute(args: JSONObject): ToolResult {
        val number = args.optString("phone_number").takeIf { it.isNotBlank() }
            ?: return ToolResult.Error("Telefon numarası belirtilmedi.")

        return runCatching {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$number")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                ToolResult.Success("$number için arama ekranı açıldı.")
            } else {
                ToolResult.Error("Telefon uygulaması bulunamadı.")
            }
        }.getOrElse { e ->
            ToolResult.Error("Arama başlatılamadı: ${e.message}", e)
        }
    }
}

// ---------------------------------------------------------------------------
// send_sms
// ---------------------------------------------------------------------------

@Singleton
class SendSmsTool @Inject constructor(
    @ApplicationContext private val context: Context
) : JarvisTool {

    override val name        = "send_sms"
    override val description = "Opens the SMS app with a recipient and message pre-filled. User must press send."
    override val parameters  = JSONObject("""
        {
            "type": "object",
            "properties": {
                "phone_number": {
                    "type": "string",
                    "description": "Recipient phone number"
                },
                "message": {
                    "type": "string",
                    "description": "Message text to pre-fill"
                }
            },
            "required": ["phone_number", "message"]
        }
    """.trimIndent())
    override val requiredPermission: String? = null

    override suspend fun execute(args: JSONObject): ToolResult {
        val number = args.optString("phone_number").takeIf { it.isNotBlank() }
            ?: return ToolResult.Error("Telefon numarası belirtilmedi.")
        val message = args.optString("message").takeIf { it.isNotBlank() }
            ?: return ToolResult.Error("Mesaj içeriği belirtilmedi.")

        return runCatching {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$number")
                putExtra("sms_body", message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                ToolResult.Success("$number'a SMS gönderme ekranı açıldı.")
            } else {
                ToolResult.Error("SMS uygulaması bulunamadı.")
            }
        }.getOrElse { e ->
            ToolResult.Error("SMS gönderilemedi: ${e.message}", e)
        }
    }
}

// ---------------------------------------------------------------------------
// post_notification
// ---------------------------------------------------------------------------

@Singleton
class PostNotificationTool @Inject constructor(
    @ApplicationContext private val context: Context
) : JarvisTool {

    override val name        = "post_notification"
    override val description = "Posts a notification to the Android notification tray. Use for reminders, alerts, or informational messages."
    override val parameters  = JSONObject("""
        {
            "type": "object",
            "properties": {
                "title": {
                    "type": "string",
                    "description": "Notification title"
                },
                "message": {
                    "type": "string",
                    "description": "Notification body text"
                }
            },
            "required": ["title", "message"]
        }
    """.trimIndent())
    override val requiredPermission: String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.POST_NOTIFICATIONS
        else null

    override suspend fun execute(args: JSONObject): ToolResult {
        val title = args.optString("title").takeIf { it.isNotBlank() }
            ?: return ToolResult.Error("Bildirim başlığı belirtilmedi.")
        val message = args.optString("message").takeIf { it.isNotBlank() }
            ?: return ToolResult.Error("Bildirim mesajı belirtilmedi.")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NotificationManagerCompat.from(context).areNotificationsEnabled()
        ) {
            return ToolResult.PermissionRequired(
                permission = Manifest.permission.POST_NOTIFICATIONS,
                rationale = "IRIS bildirim gönderebilmek için iznine ihtiyaç duyar."
            )
        }

        return runCatching {
            val notificationId = System.currentTimeMillis().toInt()
            val channelId = "iris_tools_notifications"

            // Ensure channel exists
            val channel = android.app.NotificationChannel(
                channelId,
                "IRIS Bildirimleri",
                android.app.NotificationManager.IMPORTANCE_DEFAULT
            )
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as android.app.NotificationManager
            notificationManager.createNotificationChannel(channel)

            val notification = android.app.Notification.Builder(context, channelId)
                .setContentTitle(title)
                .setContentText(message)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(notificationId, notification)
            ToolResult.Success("Bildirim gönderildi: $title")
        }.getOrElse { e ->
            ToolResult.Error("Bildirim gönderilemedi: ${e.message}", e)
        }
    }
}
