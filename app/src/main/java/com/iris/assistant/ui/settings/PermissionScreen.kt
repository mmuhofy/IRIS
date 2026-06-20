package com.iris.assistant.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.iris.assistant.ui.theme.ColorTextPrimary
import com.iris.assistant.ui.theme.ColorTextSecondary
import com.iris.assistant.ui.theme.IrisTheme
import com.phosphor.icons.PhIcons
import com.phosphor.icons.regular.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var permRefreshTrigger by remember { mutableIntStateOf(0) }
    var showAccessibilityGuide by remember { mutableStateOf(false) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permRefreshTrigger++
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    if (showAccessibilityGuide) {
        AccessibilityGuideDialog(onDismiss = { showAccessibilityGuide = false })
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "İzin Yöneticisi",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            PhIcons.Regular.ArrowLeft,
                            contentDescription = "Geri",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            PermissionSection(
                context = context,
                refreshTrigger = permRefreshTrigger,
                onOpenAccessibilityGuide = { showAccessibilityGuide = true },
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

private class PermissionCheck(
    val label: String,
    val icon: ImageVector,
    val description: String,
    val isGranted: (Context) -> Boolean,
    val settingsIntent: (Context) -> Intent,
    val hasGuide: Boolean = false,
)

@Composable
private fun PermissionSection(
    context: Context,
    refreshTrigger: Int,
    onOpenAccessibilityGuide: () -> Unit,
) {
    val primary = IrisTheme.colors.primary

    val permissionGroups = remember(refreshTrigger) {
        listOf(
            listOf(
                PermissionCheck(
                    label = "Mikrofon",
                    icon = PhIcons.Regular.Microphone,
                    description = "Sesli komut ve konuşma",
                    isGranted = { ctx -> ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED },
                    settingsIntent = { ctx -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.parse("package:${ctx.packageName}") } },
                ),
                PermissionCheck(
                    label = "Bildirimler",
                    icon = PhIcons.Regular.Bell,
                    description = "Bildirim gönderme",
                    isGranted = { ctx -> androidx.core.app.NotificationManagerCompat.from(ctx).areNotificationsEnabled() },
                    settingsIntent = { ctx -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply { putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName) } },
                ),
                PermissionCheck(
                    label = "Pil optimizasyonu",
                    icon = PhIcons.Regular.BatteryHigh,
                    description = "Arka planda çalışma",
                    isGranted = { ctx ->
                        val pm = ctx.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                        pm.isIgnoringBatteryOptimizations(ctx.packageName)
                    },
                    settingsIntent = { ctx -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data = Uri.parse("package:${ctx.packageName}") } },
                ),
            ),
            listOf(
                PermissionCheck(
                    label = "Erişilebilirlik Servisi",
                    icon = PhIcons.Regular.Eye,
                    description = "Ekran okuma ve kontrol",
                    isGranted = { ctx ->
                        val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
                        enabled.split(':').any { it.trim().startsWith(ctx.packageName) }
                    },
                    settingsIntent = { Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS) },
                    hasGuide = true,
                ),
                PermissionCheck(
                    label = "Üstte görünme",
                    icon = PhIcons.Regular.Stack,
                    description = "Floating baloncuk ve önizleme",
                    isGranted = { ctx -> Settings.canDrawOverlays(ctx) },
                    settingsIntent = { ctx -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply { data = Uri.parse("package:${ctx.packageName}") } },
                ),
            ),
            listOf(
                PermissionCheck(
                    label = "Arama",
                    icon = PhIcons.Regular.PhoneCall,
                    description = "Telefon araması yapma",
                    isGranted = { ctx -> ContextCompat.checkSelfPermission(ctx, Manifest.permission.CALL_PHONE) == android.content.pm.PackageManager.PERMISSION_GRANTED },
                    settingsIntent = { ctx -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.parse("package:${ctx.packageName}") } },
                ),
                PermissionCheck(
                    label = "SMS",
                    icon = PhIcons.Regular.ChatDots,
                    description = "SMS gönderme",
                    isGranted = { ctx -> ContextCompat.checkSelfPermission(ctx, Manifest.permission.SEND_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED },
                    settingsIntent = { ctx -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.parse("package:${ctx.packageName}") } },
                ),
                PermissionCheck(
                    label = "Kişiler",
                    icon = PhIcons.Regular.AddressBook,
                    description = "Kişi listesini okuma",
                    isGranted = { ctx -> ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED },
                    settingsIntent = { ctx -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.parse("package:${ctx.packageName}") } },
                ),
                PermissionCheck(
                    label = "Telefon durumu",
                    icon = PhIcons.Regular.DeviceMobile,
                    description = "Cihaz bilgisi okuma",
                    isGranted = { ctx -> ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED },
                    settingsIntent = { ctx -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.parse("package:${ctx.packageName}") } },
                ),
            ),
            listOf(
                PermissionCheck(
                    label = "Ses ayarları",
                    icon = PhIcons.Regular.SpeakerHigh,
                    description = "Ses seviyesi değiştirme",
                    isGranted = { true },
                    settingsIntent = { ctx -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.parse("package:${ctx.packageName}") } },
                ),
                PermissionCheck(
                    label = "WiFi",
                    icon = PhIcons.Regular.WifiHigh,
                    description = "WiFi açma/kapama",
                    isGranted = { true },
                    settingsIntent = { ctx -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.parse("package:${ctx.packageName}") } },
                ),
                PermissionCheck(
                    label = "Bluetooth",
                    icon = PhIcons.Regular.Bluetooth,
                    description = "Bluetooth açma/kapama",
                    isGranted = { true },
                    settingsIntent = { ctx -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.parse("package:${ctx.packageName}") } },
                ),
                PermissionCheck(
                    label = "Kamera",
                    icon = PhIcons.Regular.Camera,
                    description = "Flaş kontrolü",
                    isGranted = { ctx -> ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED },
                    settingsIntent = { ctx -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.parse("package:${ctx.packageName}") } },
                ),
                PermissionCheck(
                    label = "Sistem ayarları",
                    icon = PhIcons.Regular.GearSix,
                    description = "Parlaklık değiştirme",
                    isGranted = { ctx -> Settings.System.canWrite(ctx) },
                    settingsIntent = { ctx -> Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply { data = Uri.parse("package:${ctx.packageName}") } },
                ),
            ),
            listOf(
                PermissionCheck(
                    label = "Kesin alarm",
                    icon = PhIcons.Regular.Alarm,
                    description = "Hatırlatıcı kurma",
                    isGranted = { ctx ->
                        val alarmManager = ctx.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
                        alarmManager.canScheduleExactAlarms()
                    },
                    settingsIntent = { ctx -> Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply { data = Uri.parse("package:${ctx.packageName}") } },
                ),
                PermissionCheck(
                    label = "Takvim",
                    icon = PhIcons.Regular.Calendar,
                    description = "Takvim okuma/yazma",
                    isGranted = { ctx ->
                        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    },
                    settingsIntent = { ctx -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.parse("package:${ctx.packageName}") } },
                ),
            ),
        )
    }

    Column {
        permissionGroups.forEachIndexed { groupIndex, group ->
            if (groupIndex > 0) PermissionGroupDivider()

            group.forEachIndexed { permIndex, perm ->
                if (permIndex > 0) PermissionItemDivider()

                val granted = perm.isGranted(context)
                PermissionItemRow(
                    icon = perm.icon,
                    label = perm.label,
                    description = perm.description,
                    granted = granted,
                    hasGuide = perm.hasGuide,
                    onOpenSettings = {
                        context.startActivity(perm.settingsIntent(context))
                    },
                    onOpenGuide = if (perm.hasGuide) onOpenAccessibilityGuide else null,
                )
            }
        }
    }
}

@Composable
private fun PermissionItemRow(
    icon: ImageVector,
    label: String,
    description: String,
    granted: Boolean,
    hasGuide: Boolean,
    onOpenSettings: () -> Unit,
    onOpenGuide: (() -> Unit)?,
) {
    val primary = IrisTheme.colors.primary
    val green = Color(0xFF34C759)
    val gray = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PermissionIcon(icon, granted, primary)

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = ColorTextPrimary,
                )
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (granted) green else gray)
                )
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = ColorTextSecondary,
            )
        }

        if (hasGuide && onOpenGuide != null) {
            Surface(
                onClick = onOpenGuide,
                shape = RoundedCornerShape(10.dp),
                color = primary.copy(alpha = 0.1f),
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = PhIcons.Regular.BookOpen,
                        contentDescription = "Rehber",
                        tint = primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
        }

        Surface(
            onClick = onOpenSettings,
            shape = RoundedCornerShape(10.dp),
            color = primary.copy(alpha = 0.1f),
            modifier = Modifier.size(36.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = PhIcons.Regular.ArrowSquareOut,
                    contentDescription = "Ayarlara git",
                    tint = primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun PermissionIcon(icon: ImageVector, granted: Boolean, tint: Color) {
    val alpha = if (granted) 0.12f else 0.06f
    val iconAlpha = if (granted) 1f else 0.4f
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = alpha)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint.copy(alpha = iconAlpha),
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun PermissionGroupDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    )
}

@Composable
private fun PermissionItemDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 52.dp)
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    )
}

@Composable
private fun AccessibilityGuideDialog(onDismiss: () -> Unit) {
    val primary = IrisTheme.colors.primary

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = "Erişilebilirlik Servisi Aktivasyonu",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column {
                Text(
                    text = "IRIS'in ekranı okuması ve sizin adınıza tıklaması için erişilebilirlik servisini etkinleştirmeniz gerekiyor.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ColorTextSecondary,
                )
                Spacer(Modifier.height(16.dp))

                listOf(
                    "1️⃣" to "Ayarlar uygulamasını açın",
                    "2️⃣" to "Erişilebilirlik > Yüklü uygulamalar bölümüne girin",
                    "3️⃣" to "IRIS'i bulun ve üzerine dokunun",
                    "4️⃣" to "Erişilebilirlik servisi anahtarını açın",
                    "5️⃣" to "Açılan uyarıda İzin Ver'e dokunun",
                ).forEach { (emoji, step) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Text(text = emoji, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = step,
                            style = MaterialTheme.typography.bodyMedium,
                            color = ColorTextPrimary,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Tamam", color = primary)
            }
        },
    )
}
