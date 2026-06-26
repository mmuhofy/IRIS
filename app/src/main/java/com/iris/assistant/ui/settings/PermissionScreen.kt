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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.iris.assistant.ui.theme.ColorTextPrimary
import com.iris.assistant.ui.theme.ColorTextSecondary
import com.iris.assistant.ui.theme.IrisTheme
import com.phosphor.icons.PhIcons
import com.phosphor.icons.regular.AddressBook
import com.phosphor.icons.regular.Alarm
import com.phosphor.icons.regular.ArrowLeft
import com.phosphor.icons.regular.ArrowSquareOut
import com.phosphor.icons.regular.BatteryHigh
import com.phosphor.icons.regular.Bell
import com.phosphor.icons.regular.Bluetooth
import com.phosphor.icons.regular.BookOpen
import com.phosphor.icons.regular.Calendar
import com.phosphor.icons.regular.Camera
import com.phosphor.icons.regular.ChatDots
import com.phosphor.icons.regular.DeviceMobile
import com.phosphor.icons.regular.Eye
import com.phosphor.icons.regular.GearSix
import com.phosphor.icons.regular.Microphone
import com.phosphor.icons.regular.PhoneCall
import com.phosphor.icons.regular.SpeakerHigh
import com.phosphor.icons.regular.Stack
import com.phosphor.icons.regular.WifiHigh

// ---------------------------------------------------------------------------
// Data
// ---------------------------------------------------------------------------

private data class PermissionGroup(
    val sectionTitle : String,
    val items        : List<PermissionItem>,
)

private data class PermissionItem(
    val label          : String,
    val icon           : ImageVector,
    val description    : String,
    val isGranted      : (Context) -> Boolean,
    val settingsIntent : (Context) -> Intent,
    val hasGuide       : Boolean = false,
)

private fun buildPermissionGroups(): List<PermissionGroup> = listOf(
    PermissionGroup(
        sectionTitle = "Temel",
        items = listOf(
            PermissionItem(
                label          = "Mikrofon",
                icon           = PhIcons.Regular.Microphone,
                description    = "Sesli komut ve konuşma tanıma",
                isGranted      = { ctx -> ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED },
                settingsIntent = { ctx -> appDetailsIntent(ctx) },
            ),
            PermissionItem(
                label          = "Bildirimler",
                icon           = PhIcons.Regular.Bell,
                description    = "Hatırlatıcı ve bildirim gönderme",
                isGranted      = { ctx -> androidx.core.app.NotificationManagerCompat.from(ctx).areNotificationsEnabled() },
                settingsIntent = { ctx -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply { putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName) } },
            ),
            PermissionItem(
                label          = "Pil optimizasyonu",
                icon           = PhIcons.Regular.BatteryHigh,
                description    = "Arka planda kesintisiz çalışma",
                isGranted      = { ctx -> (ctx.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).isIgnoringBatteryOptimizations(ctx.packageName) },
                settingsIntent = { ctx -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data = Uri.parse("package:${ctx.packageName}") } },
            ),
        ),
    ),
    PermissionGroup(
        sectionTitle = "Ekran Kontrolü",
        items = listOf(
            PermissionItem(
                label          = "Erişilebilirlik Servisi",
                icon           = PhIcons.Regular.Eye,
                description    = "Ekran okuma ve otomatik kontrol",
                isGranted      = { ctx ->
                    val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
                    enabled.split(':').any { it.trim().startsWith(ctx.packageName) }
                },
                settingsIntent = { Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS) },
                hasGuide       = true,
            ),
            PermissionItem(
                label          = "Üstte görünme",
                icon           = PhIcons.Regular.Stack,
                description    = "Floating baloncuk ve önizleme katmanı",
                isGranted      = { ctx -> Settings.canDrawOverlays(ctx) },
                settingsIntent = { ctx -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply { data = Uri.parse("package:${ctx.packageName}") } },
            ),
        ),
    ),
    PermissionGroup(
        sectionTitle = "İletişim",
        items = listOf(
            PermissionItem(
                label          = "Arama",
                icon           = PhIcons.Regular.PhoneCall,
                description    = "Telefon araması yapma",
                isGranted      = { ctx -> ContextCompat.checkSelfPermission(ctx, Manifest.permission.CALL_PHONE) == android.content.pm.PackageManager.PERMISSION_GRANTED },
                settingsIntent = { ctx -> appDetailsIntent(ctx) },
            ),
            PermissionItem(
                label          = "SMS",
                icon           = PhIcons.Regular.ChatDots,
                description    = "SMS gönderme",
                isGranted      = { ctx -> ContextCompat.checkSelfPermission(ctx, Manifest.permission.SEND_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED },
                settingsIntent = { ctx -> appDetailsIntent(ctx) },
            ),
            PermissionItem(
                label          = "Kişiler",
                icon           = PhIcons.Regular.AddressBook,
                description    = "Kişi listesini okuma",
                isGranted      = { ctx -> ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED },
                settingsIntent = { ctx -> appDetailsIntent(ctx) },
            ),
            PermissionItem(
                label          = "Telefon durumu",
                icon           = PhIcons.Regular.DeviceMobile,
                description    = "Cihaz ve hat bilgisi okuma",
                isGranted      = { ctx -> ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED },
                settingsIntent = { ctx -> appDetailsIntent(ctx) },
            ),
        ),
    ),
    PermissionGroup(
        sectionTitle = "Sistem Kontrolü",
        items = listOf(
            PermissionItem(
                label          = "Ses ayarları",
                icon           = PhIcons.Regular.SpeakerHigh,
                description    = "Ses seviyesi değiştirme",
                isGranted      = { true },
                settingsIntent = { ctx -> appDetailsIntent(ctx) },
            ),
            PermissionItem(
                label          = "WiFi",
                icon           = PhIcons.Regular.WifiHigh,
                description    = "WiFi açma/kapama",
                isGranted      = { true },
                settingsIntent = { ctx -> appDetailsIntent(ctx) },
            ),
            PermissionItem(
                label          = "Bluetooth",
                icon           = PhIcons.Regular.Bluetooth,
                description    = "Bluetooth açma/kapama",
                isGranted      = { true },
                settingsIntent = { ctx -> appDetailsIntent(ctx) },
            ),
            PermissionItem(
                label          = "Kamera",
                icon           = PhIcons.Regular.Camera,
                description    = "Flaş kontrolü",
                isGranted      = { ctx -> ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED },
                settingsIntent = { ctx -> appDetailsIntent(ctx) },
            ),
            PermissionItem(
                label          = "Sistem ayarları",
                icon           = PhIcons.Regular.GearSix,
                description    = "Parlaklık seviyesi değiştirme",
                isGranted      = { ctx -> Settings.System.canWrite(ctx) },
                settingsIntent = { ctx -> Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply { data = Uri.parse("package:${ctx.packageName}") } },
            ),
        ),
    ),
    PermissionGroup(
        sectionTitle = "Verimlilik",
        items = listOf(
            PermissionItem(
                label          = "Kesin alarm",
                icon           = PhIcons.Regular.Alarm,
                description    = "Zamanlanmış hatırlatıcı kurma",
                isGranted      = { ctx -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) (ctx.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager).canScheduleExactAlarms() else false },
                settingsIntent = { ctx -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply { data = Uri.parse("package:${ctx.packageName}") } else appDetailsIntent(ctx) },
            ),
            PermissionItem(
                label          = "Takvim",
                icon           = PhIcons.Regular.Calendar,
                description    = "Takvim okuma ve etkinlik ekleme",
                isGranted      = { ctx -> ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) == android.content.pm.PackageManager.PERMISSION_GRANTED },
                settingsIntent = { ctx -> appDetailsIntent(ctx) },
            ),
        ),
    ),
)

private fun appDetailsIntent(ctx: Context) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:${ctx.packageName}")
    }

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionScreen(onBack: () -> Unit) {
    val context                = LocalContext.current
    var permRefreshTrigger     by remember { mutableIntStateOf(0) }
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

    // NOTE: containerColor = Color.Transparent — do NOT change.
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text       = "İzin Yöneticisi",
                        style      = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            PhIcons.Regular.ArrowLeft,
                            contentDescription = "Geri",
                            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { innerPadding ->
        val groups = remember(permRefreshTrigger) { buildPermissionGroups() }

        LazyColumn(
            modifier       = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
        ) {
            groups.forEach { group ->
                // Section label
                item(key = "label_${group.sectionTitle}") {
                    Text(
                        text          = group.sectionTitle.uppercase(),
                        style         = MaterialTheme.typography.labelSmall,
                        color         = IrisTheme.colors.primary,
                        letterSpacing = 1.2.sp,
                        modifier      = Modifier.padding(start = 4.dp, bottom = 8.dp),
                    )
                }

                // Card for this group
                item(key = "card_${group.sectionTitle}") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surface),
                    ) {
                        group.items.forEachIndexed { idx, perm ->
                            val granted = perm.isGranted(context)
                            PermissionRow(
                                item           = perm,
                                granted        = granted,
                                onOpenSettings = { context.startActivity(perm.settingsIntent(context)) },
                                onOpenGuide    = if (perm.hasGuide) {
                                    { showAccessibilityGuide = true }
                                } else null,
                            )
                            if (idx < group.items.lastIndex) {
                                // Divider aligned to icon end
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 64.dp)
                                        .height(0.5.dp)
                                        .background(
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)
                                        ),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Permission row
// ---------------------------------------------------------------------------

@Composable
private fun PermissionRow(
    item           : PermissionItem,
    granted        : Boolean,
    onOpenSettings : () -> Unit,
    onOpenGuide    : (() -> Unit)?,
) {
    val statusGreen = Color(0xFF34C759)
    val statusGray  = MaterialTheme.colorScheme.outlineVariant

    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Icon circle with status badge overlay
        Box(modifier = Modifier.size(38.dp)) {
            Box(
                modifier         = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(
                        if (granted) MaterialTheme.colorScheme.primaryContainer else IrisTheme.colors.primary.copy(alpha = 0.05f)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector        = item.icon,
                    contentDescription = null,
                    tint               = IrisTheme.colors.primary.copy(alpha = if (granted) 1f else 0.35f),
                    modifier           = Modifier.size(18.dp),
                )
            }
            // Status dot — bottom-right corner
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface) // ring effect
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(if (granted) statusGreen else statusGray),
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = item.label,
                style      = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color      = ColorTextPrimary,
            )
            Text(
                text  = item.description,
                style = MaterialTheme.typography.bodySmall,
                color = ColorTextSecondary,
            )
        }

        Spacer(Modifier.width(8.dp))

        // Guide button (accessibility only)
        if (onOpenGuide != null) {
            Surface(
                onClick = onOpenGuide,
                shape   = RoundedCornerShape(10.dp),
                color   = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(34.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector        = PhIcons.Regular.BookOpen,
                        contentDescription = "Rehber",
                        tint               = IrisTheme.colors.primary,
                        modifier           = Modifier.size(16.dp),
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
        }

        // Settings button
        Surface(
            onClick  = onOpenSettings,
            shape    = RoundedCornerShape(10.dp),
            color    = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(34.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector        = PhIcons.Regular.ArrowSquareOut,
                    contentDescription = "Ayarlara git",
                    tint               = IrisTheme.colors.primary,
                    modifier           = Modifier.size(16.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Accessibility guide dialog
// ---------------------------------------------------------------------------

@Composable
private fun AccessibilityGuideDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = MaterialTheme.colorScheme.surface,
        shape            = RoundedCornerShape(20.dp),
        title = {
            Text(
                text       = "Erişilebilirlik Aktivasyonu",
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text  = "IRIS'in ekranı okuması için erişilebilirlik servisini etkinleştirin.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ColorTextSecondary,
                )
                Spacer(Modifier.height(8.dp))
                listOf(
                    "Ayarlar → Erişilebilirlik",
                    "Yüklü uygulamalar bölümüne girin",
                    "IRIS'i bulun ve açın",
                    "Erişilebilirlik servisini etkinleştirin",
                    "Açılan uyarıda \"İzin Ver\"e dokunun",
                ).forEachIndexed { i, step ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text       = "${i + 1}",
                                style      = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color      = IrisTheme.colors.primary,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text  = step,
                            style = MaterialTheme.typography.bodyMedium,
                            color = ColorTextPrimary,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Tamam", color = IrisTheme.colors.primary)
            }
        },
    )
}