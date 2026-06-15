package com.iris.assistant.data.tools.system

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.iris.assistant.domain.tools.JarvisTool
import com.iris.assistant.domain.tools.ToolResult
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

// UNTESTED — verify system tool behavior on device before use

private const val TAG = "SystemTools"

// ---------------------------------------------------------------------------
// Known app name → package name mapping (common Turkish app names)
// ---------------------------------------------------------------------------
private val APP_NAME_MAP = mapOf(
    "whatsapp"         to "com.whatsapp",
    "youtube"          to "com.google.android.youtube",
    "telegram"         to "org.telegram.messenger",
    "instagram"        to "com.instagram.android",
    "twitter"          to "com.twitter.android",
    "x"                to "com.twitter.android",
    "facebook"         to "com.facebook.katana",
    "spotify"          to "com.spotify.music",
    "netflix"          to "com.netflix.mediaclient",
    "chrome"           to "com.android.chrome",
    "gmail"            to "com.google.android.gm",
    "haritalar"        to "com.google.android.apps.maps",
    "maps"             to "com.google.android.apps.maps",
    "takvim"           to "com.google.android.calendar",
    "galeri"           to "com.android.gallery3d",
    "ayarlar"          to "com.android.settings",
    "play store"       to "com.android.vending",
    "chatgpt"          to "com.openai.chatgpt",
    "gemini"           to "com.google.android.apps.gemini",
    "google"           to "com.google.android.googlequicksearchbox",
    "mesajlar"         to "com.google.android.apps.messaging",
    "telefon"          to "com.google.android.dialer",
    "rehber"           to "com.google.android.contacts",
    "dosyalar"         to "com.android.documentsui",
    "saat"             to "com.google.android.deskclock",
    "hava durumu"      to "com.google.android.apps.weather",
    "yemeksepeti"      to "com.yemeksepeti",
    "trendyol"         to "com.trendyol.app",
    "hepsiburada"      to "com.hepsiburada"
)

// ---------------------------------------------------------------------------
// open_app
// ---------------------------------------------------------------------------
@Singleton
class OpenAppTool @Inject constructor(
    @ApplicationContext private val context: Context
) : JarvisTool {

    override val name = "open_app"
    override val description = "Kullanıcının belirttiği uygulamayı açar. Parametre olarak uygulama adı (Türkçe) veya paket adı verilir."
    override val parameters = JSONObject("""
        {
          "type": "object",
          "properties": {
            "appName": {
              "type": "string",
              "description": "Açılacak uygulamanın adı (Türkçe, örn: 'WhatsApp', 'YouTube', 'Ayarlar') veya paket adı"
            }
          },
          "required": ["appName"]
        }
    """.trimIndent())
    override val requiredPermission: String? = null

    override suspend fun execute(args: JSONObject): ToolResult {
        val appName = args.optString("appName").trim().lowercase()
        if (appName.isBlank()) return ToolResult.Error("Uygulama adı belirtilmedi.")

        val packageName = resolvePackage(appName)
            ?: return ToolResult.Error("'$appName' uygulaması bulunamadı.")

        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return ToolResult.Error("'$appName' uygulaması başlatılamıyor (launch intent yok).")

        return runCatching {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            ToolResult.Success("$appName uygulaması açılıyor.", data = mapOf("package" to packageName))
        }.getOrElse { e ->
            Log.e(TAG, "open_app failed", e)
            ToolResult.Error("$appName açılamadı: ${e.message}")
        }
    }

    private fun resolvePackage(input: String): String? {
        val inputClean = input.removeSuffix("uygulaması").removeSuffix("uygulamasi").trim()

        APP_NAME_MAP[inputClean]?.let { return it }

        APP_NAME_MAP.entries.find { inputClean.contains(it.key) }?.value?.let { return it }

        val installed = context.packageManager.getInstalledApplications(0)
        var match: String? = null
        for (app in installed) {
            val label = context.packageManager.getApplicationLabel(app).toString().lowercase()
            if (label == inputClean || app.packageName.lowercase() == inputClean) {
                return app.packageName
            }
            if (label.contains(inputClean) || inputClean.contains(label)) {
                match = app.packageName
            }
        }
        return match
    }
}

// ---------------------------------------------------------------------------
// set_volume
// ---------------------------------------------------------------------------
@Singleton
class SetVolumeTool @Inject constructor(
    @ApplicationContext private val context: Context
) : JarvisTool {

    override val name = "set_volume"
    override val description = "Ses seviyesini ayarlar. level 0-100 arası, type opsiyonel (music, ring, notification, alarm, call)."
    override val parameters = JSONObject("""
        {
          "type": "object",
          "properties": {
            "level": {
              "type": "integer",
              "description": "Ses seviyesi (0-100 arası)"
            },
            "type": {
              "type": "string",
              "description": "Ses tipi: 'music', 'ring' (zil), 'notification' (bildirim), 'alarm' veya 'call' (görüşme). Varsayılan: music",
              "enum": ["music", "ring", "notification", "alarm", "call"]
            }
          },
          "required": ["level"]
        }
    """.trimIndent())
    override val requiredPermission: String? = "android.permission.MODIFY_AUDIO_SETTINGS"

    override suspend fun execute(args: JSONObject): ToolResult {
        val level = args.optInt("level", -1)
        if (level < 0 || level > 100) return ToolResult.Error("Ses seviyesi 0-100 arası olmalı.")

        val type = args.optString("type", "music")
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val streamType = when (type) {
            "ring"         -> AudioManager.STREAM_RING
            "notification" -> AudioManager.STREAM_NOTIFICATION
            "alarm"        -> AudioManager.STREAM_ALARM
            "call"         -> AudioManager.STREAM_VOICE_CALL
            else           -> AudioManager.STREAM_MUSIC
        }

        return runCatching {
            val max = audioManager.getStreamMaxVolume(streamType)
            val scaled = (level * max / 100).coerceIn(0, max)
            audioManager.setStreamVolume(streamType, scaled, AudioManager.FLAG_SHOW_UI)
            ToolResult.Success("$type ses seviyesi $level olarak ayarlandı.",
                data = mapOf("type" to type, "level" to level, "scaled" to scaled))
        }.getOrElse { e ->
            ToolResult.Error("Ses ayarlanamadı: ${e.message}")
        }
    }
}

// ---------------------------------------------------------------------------
// set_brightness
// ---------------------------------------------------------------------------
@Singleton
class SetBrightnessTool @Inject constructor(
    @ApplicationContext private val context: Context
) : JarvisTool {

    override val name = "set_brightness"
    override val description = "Ekran parlaklığını ayarlar. level 0-100 arası. WRITE_SETTINGS özel izni gerektirir."
    override val parameters = JSONObject("""
        {
          "type": "object",
          "properties": {
            "level": {
              "type": "integer",
              "description": "Parlaklık seviyesi (0-100 arası)"
            }
          },
          "required": ["level"]
        }
    """.trimIndent())
    override val requiredPermission: String? = "android.permission.WRITE_SETTINGS"

    override suspend fun execute(args: JSONObject): ToolResult {
        val level = args.optInt("level", -1)
        if (level < 0 || level > 100) return ToolResult.Error("Parlaklık 0-100 arası olmalı.")

        if (!Settings.System.canWrite(context)) {
            return ToolResult.PermissionRequired(
                permission = "android.permission.WRITE_SETTINGS",
                rationale = "Parlaklık ayarı için sistem ayarlarını değiştirme izni gerekiyor. İzin ver butonuna bastığınızda ayarlara yönlendirileceksiniz."
            )
        }

        return runCatching {
            val scaled = (level * 255 / 100).coerceIn(0, 255)
            val cr = context.contentResolver
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, scaled)
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            ToolResult.Success("Ekran parlaklığı %$level olarak ayarlandı.",
                data = mapOf("level" to level, "scaled" to scaled))
        }.getOrElse { e ->
            ToolResult.Error("Parlaklık ayarlanamadı: ${e.message}")
        }
    }
}

// ---------------------------------------------------------------------------
// toggle_wifi
// ---------------------------------------------------------------------------
@Singleton
class ToggleWifiTool @Inject constructor(
    @ApplicationContext private val context: Context
) : JarvisTool {

    override val name = "toggle_wifi"
    override val description = "Wi-Fi'yi açar veya kapatır. enabled=true aç, false kapat."
    override val parameters = JSONObject("""
        {
          "type": "object",
          "properties": {
            "enabled": {
              "type": "boolean",
              "description": "true = Wi-Fi aç, false = Wi-Fi kapat"
            }
          },
          "required": ["enabled"]
        }
    """.trimIndent())
    override val requiredPermission: String? = "android.permission.CHANGE_WIFI_STATE"

    override suspend fun execute(args: JSONObject): ToolResult {
        val enabled = args.optBoolean("enabled")

        return runCatching {
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            val success = wifiManager.setWifiEnabled(enabled)
            if (success) {
                val msg = "Wi-Fi ${if (enabled) "açıldı" else "kapatıldı"}."
                ToolResult.Success(msg, data = mapOf("enabled" to enabled))
            } else {
                ToolResult.Error("Wi-Fi ${if (enabled) "açılamadı" else "kapatılamadı"}.")
            }
        }.getOrElse { e ->
            ToolResult.Error("Wi-Fi değiştirilemedi: ${e.message}")
        }
    }
}

// ---------------------------------------------------------------------------
// toggle_bluetooth
// ---------------------------------------------------------------------------
@Singleton
class ToggleBluetoothTool @Inject constructor(
    @ApplicationContext private val context: Context
) : JarvisTool {

    override val name = "toggle_bluetooth"
    override val description = "Bluetooth'u açar veya kapatır. enabled=true aç, false kapat."
    override val parameters = JSONObject("""
        {
          "type": "object",
          "properties": {
            "enabled": {
              "type": "boolean",
              "description": "true = Bluetooth aç, false = Bluetooth kapat"
            }
          },
          "required": ["enabled"]
        }
    """.trimIndent())
    override val requiredPermission: String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "android.permission.BLUETOOTH_CONNECT"
        else "android.permission.BLUETOOTH_ADMIN"

    override suspend fun execute(args: JSONObject): ToolResult {
        val enabled = args.optBoolean("enabled")

        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) return ToolResult.Error("Bu cihazda Bluetooth bulunmuyor.")

        return runCatching {
            val success = if (enabled) adapter.enable() else adapter.disable()
            if (success) {
                val msg = "Bluetooth ${if (enabled) "açıldı" else "kapatıldı"}."
                ToolResult.Success(msg, data = mapOf("enabled" to enabled))
            } else {
                ToolResult.Error("Bluetooth ${if (enabled) "açılamadı" else "kapatılamadı"}.")
            }
        }.getOrElse { e ->
            if (e is SecurityException) {
                val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    "android.permission.BLUETOOTH_CONNECT" else "android.permission.BLUETOOTH_ADMIN"
                ToolResult.PermissionRequired(
                    permission = perm,
                    rationale = "Bluetooth kontrolü için izin gerekiyor."
                )
            } else {
                ToolResult.Error("Bluetooth değiştirilemedi: ${e.message}")
            }
        }
    }
}

// ---------------------------------------------------------------------------
// toggle_flashlight
// ---------------------------------------------------------------------------
@Singleton
class ToggleFlashlightTool @Inject constructor(
    @ApplicationContext private val context: Context
) : JarvisTool {

    override val name = "toggle_flashlight"
    override val description = "Telefonun flash ışığını (fener) açar veya kapatır. enabled=true aç, false kapat. Kamera izni gerektirir."
    override val parameters = JSONObject("""
        {
          "type": "object",
          "properties": {
            "enabled": {
              "type": "boolean",
              "description": "true = flash aç, false = flash kapat"
            }
          },
          "required": ["enabled"]
        }
    """.trimIndent())
    override val requiredPermission: String? = "android.permission.CAMERA"

    override suspend fun execute(args: JSONObject): ToolResult {
        val enabled = args.optBoolean("enabled")

        return runCatching {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                    .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE)
                chars == true
            } ?: return@runCatching ToolResult.Error("Flash ışığı bulunamadı.")

            cameraManager.setTorchMode(cameraId, enabled)
            val msg = "Flash ${if (enabled) "açıldı" else "kapatıldı"}."
            ToolResult.Success(msg, data = mapOf("enabled" to enabled))
        }.getOrElse { e ->
            if (e is SecurityException) {
                ToolResult.PermissionRequired(
                    permission = "android.permission.CAMERA",
                    rationale = "Flash kullanımı için kamera izni gerekiyor."
                )
            } else {
                ToolResult.Error("Flash değiştirilemedi: ${e.message}")
            }
        }
    }
}

// ---------------------------------------------------------------------------
// get_battery_status
// ---------------------------------------------------------------------------
@Singleton
class GetBatteryStatusTool @Inject constructor(
    @ApplicationContext private val context: Context
) : JarvisTool {

    override val name = "get_battery_status"
    override val description = "Pil durumunu ve şarj seviyesini döndürür. Hiçbir parametre gerektirmez."
    override val parameters = JSONObject("""{"type": "object", "properties": {}}""")
    override val requiredPermission: String? = null

    override suspend fun execute(args: JSONObject): ToolResult {
        return runCatching {
            val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (batteryIntent == null) return@runCatching ToolResult.Error("Pil bilgisi alınamadı.")

            val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val percentage = if (level >= 0 && scale > 0) (level * 100 / scale) else -1

            val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val statusText = when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "şarj oluyor"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "şarj olmuyor"
                BatteryManager.BATTERY_STATUS_FULL -> "tam dolu"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "şarj olmuyor"
                else -> "bilinmiyor"
            }

            val plugged = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
            val plugText = when {
                plugged == BatteryManager.BATTERY_PLUGGED_AC -> " (AC priz)"
                plugged == BatteryManager.BATTERY_PLUGGED_USB -> " (USB)"
                plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS -> " (kablosuz)"
                else -> ""
            }

            val temp = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f

            val displayText = if (percentage >= 0) {
                "Pil %$percentage, $statusText$plugText. Sıcaklık: ${temp}°C."
            } else {
                "Pil bilgisi alınamadı."
            }

            ToolResult.Success(displayText, data = mapOf(
                "percentage" to percentage,
                "status" to statusText,
                "temperature" to temp
            ))
        }.getOrElse { e ->
            ToolResult.Error("Pil bilgisi alınamadı: ${e.message}")
        }
    }
}
