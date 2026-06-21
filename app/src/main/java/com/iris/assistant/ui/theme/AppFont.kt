package com.iris.assistant.ui.theme

import android.content.Context
import android.net.Uri
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.iris.assistant.R
import java.io.File

sealed class AppFont(
    val key: String,
    val displayName: String,
    val fontFamily: FontFamily
) {
    data object SystemDefault : AppFont(
        key = "system_default",
        displayName = "Varsayılan",
        fontFamily = FontFamily.Default
    )
    data object Inter : AppFont(
        key = "inter",
        displayName = "Inter",
        fontFamily = variableFontFamily(R.font.inter_variable)
    )
    data object PlusJakartaSans : AppFont(
        key = "plus_jakarta_sans",
        displayName = "Plus Jakarta Sans",
        fontFamily = variableFontFamily(R.font.plus_jakarta_sans_variable)
    )
    data object Outfit : AppFont(
        key = "outfit",
        displayName = "Outfit",
        fontFamily = variableFontFamily(R.font.outfit_variable)
    )
    data object Geist : AppFont(
        key = "geist",
        displayName = "Geist",
        fontFamily = variableFontFamily(R.font.geist_variable)
    )

    class Custom(
        val filePath: String,
        displayName: String
    ) : AppFont(
        key = "custom_$filePath",
        displayName = displayName,
        fontFamily = FontFamily(Font(File(filePath)))
    )

    fun toTypography(): Typography = IrisTypography(fontFamily)

    companion object {
        val builtin: List<AppFont> get() = listOf(SystemDefault, Inter, PlusJakartaSans, Outfit, Geist)

        fun fromKey(key: String, customFonts: List<Custom> = emptyList()): AppFont =
            builtin.firstOrNull { it.key == key }
                ?: customFonts.firstOrNull { it.key == key }
                ?: SystemDefault

        fun customDir(context: Context): File {
            val dir = File(context.filesDir, "fonts")
            dir.mkdirs()
            return dir
        }

        private fun variableFontFamily(resId: Int): FontFamily = FontFamily(
            Font(resId, weight = FontWeight.Thin),
            Font(resId, weight = FontWeight.ExtraLight),
            Font(resId, weight = FontWeight.Light),
            Font(resId, weight = FontWeight.Normal),
            Font(resId, weight = FontWeight.Medium),
            Font(resId, weight = FontWeight.SemiBold),
            Font(resId, weight = FontWeight.Bold),
            Font(resId, weight = FontWeight.ExtraBold),
            Font(resId, weight = FontWeight.Black),
        )
    }
}

fun IrisTypography(fontFamily: FontFamily): Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize   = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize   = 45.sp,
        lineHeight = 52.sp
    ),
    displaySmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize   = 36.sp,
        lineHeight = 44.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize   = 32.sp,
        lineHeight = 40.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize   = 28.sp,
        lineHeight = 36.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize   = 24.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize   = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize   = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize   = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize   = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize   = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize   = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize   = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize   = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize   = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)
