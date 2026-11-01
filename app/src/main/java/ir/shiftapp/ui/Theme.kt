package ir.shiftapp.ui

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/** آیا تم تاریک فعال است (تنظیم کاربر یا تم سیستم). */
val LocalDark = compositionLocalOf { false }

/**
 * رنگ‌های معنایی برنامه (همه از یک‌جا). هیچ صفحه‌ای نباید رنگ ثابت خودش را بنویسد؛
 * رنگ پاستلیِ خودِ شیفت‌ها (ShiftType.color) تنها استثناست و همیشه با متن تیره خوانده می‌شود.
 */
object Pal {
    val blue: Color @Composable get() = MaterialTheme.colorScheme.primary
    val green: Color @Composable get() = if (LocalDark.current) Color(0xFF6FD9C8) else Color(0xFF0F8B7D)
    val coral: Color @Composable get() = if (LocalDark.current) Color(0xFFFF9B7B) else Color(0xFFD9481C)
    val amber: Color @Composable get() = if (LocalDark.current) Color(0xFFFFC46B) else Color(0xFFB45309)
    val holiday: Color @Composable get() = if (LocalDark.current) Color(0xFFFF9A9A) else Color(0xFFC62828)
    val muted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

    /** سطح کارت‌ها (سفید در روشن، کمی روشن‌تر از زمینه در تاریک). */
    val card: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainerLowest
    val rowBg: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainerLowest
    val today: Color @Composable get() = if (LocalDark.current) Color(0xFF262A56) else Color(0xFFEBEDFF)
    val tableHeader: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh
    val hairline: Color @Composable get() = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
    val dialog: Color @Composable get() = if (LocalDark.current) Color(0xFF232743) else Color.White

    /** جوهرهٔ نشانه‌های روز (تغییر دستی، مرخصی، اضافه‌کاری، یادداشت) روی رنگ پاستلی شیفت. */
    val markOverride = Color(0xFF111827)
    val markLeave = Color(0xFF0B7A55)
    val markOvertime = Color(0xFFC2410C)
    val markNote = Color(0xFF3730A3)

    val heroBrush: Brush @Composable get() =
        if (LocalDark.current) Brush.linearGradient(listOf(Color(0xFF3A34B8), Color(0xFF211E73)))
        else Brush.linearGradient(listOf(Color(0xFF5348E0), Color(0xFF3027A8)))
}

private val LightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF4338CA), onPrimary = Color.White,
    primaryContainer = Color(0xFFE2E1FF), onPrimaryContainer = Color(0xFF13116B),
    secondary = Color(0xFFB45309), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE8C2), onSecondaryContainer = Color(0xFF3A2200),
    tertiary = Color(0xFF0F8B7D), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCDF3EC), onTertiaryContainer = Color(0xFF00382F),
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF4F5FB), onBackground = Color(0xFF191B2B),
    surface = Color(0xFFF4F5FB), onSurface = Color(0xFF191B2B),
    surfaceVariant = Color(0xFFE4E5F2), onSurfaceVariant = Color(0xFF50546E),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF0F1F9),
    surfaceContainer = Color(0xFFEBECF6), surfaceContainerHigh = Color(0xFFE5E7F3),
    surfaceContainerHighest = Color(0xFFDFE1F0),
    outline = Color(0xFF7A7E98), outlineVariant = Color(0xFFD9DBEA)
)

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFBFC1FF), onPrimary = Color(0xFF1E1B8A),
    primaryContainer = Color(0xFF30309C), onPrimaryContainer = Color(0xFFE2E1FF),
    secondary = Color(0xFFFFB866), onSecondary = Color(0xFF4A2D00),
    secondaryContainer = Color(0xFF5A3A00), onSecondaryContainer = Color(0xFFFFE8C2),
    tertiary = Color(0xFF6FD9C8), onTertiary = Color(0xFF00382F),
    tertiaryContainer = Color(0xFF005046), onTertiaryContainer = Color(0xFFCDF3EC),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0E1020), onBackground = Color(0xFFE4E5F4),
    surface = Color(0xFF0E1020), onSurface = Color(0xFFE4E5F4),
    surfaceVariant = Color(0xFF2A2E47), onSurfaceVariant = Color(0xFFC2C5DE),
    surfaceContainerLowest = Color(0xFF151830), surfaceContainerLow = Color(0xFF1A1D36),
    surfaceContainer = Color(0xFF20233E), surfaceContainerHigh = Color(0xFF272A47),
    surfaceContainerHighest = Color(0xFF2E3150),
    outline = Color(0xFF8E92AD), outlineVariant = Color(0xFF33375A)
)

/** فونت استعداد (Estedad، مجوز OFL) از پوشهٔ res/font؛ هر وزنی که فایلش نباشد نادیده گرفته می‌شود. */
@Composable
private fun rememberAppFontFamily(): FontFamily {
    val ctx = LocalContext.current
    return remember {
        val weights = listOf(
            "thin" to FontWeight.Thin, "extralight" to FontWeight.ExtraLight, "light" to FontWeight.Light,
            "regular" to FontWeight.Normal, "medium" to FontWeight.Medium, "semibold" to FontWeight.SemiBold,
            "bold" to FontWeight.Bold, "extrabold" to FontWeight.ExtraBold, "black" to FontWeight.Black
        )
        val fonts = weights.mapNotNull { (name, w) ->
            val id = ctx.resources.getIdentifier("estedad_$name", "font", ctx.packageName)
            if (id != 0) Font(id, w) else null
        }
        if (fonts.isEmpty()) FontFamily.Default else FontFamily(fonts)
    }
}

/**
 * مقیاس تایپوگرافی مخصوص خط فارسی: فاصلهٔ حروف صفر (فاصلهٔ مثبت حروف متصل را از هم جدا می‌کند)
 * و ارتفاع خط بازتر از پیش‌فرض متریال (که برای لاتین است).
 */
private fun typographyOf(f: FontFamily): Typography {
    fun s(size: Int, line: Int, w: FontWeight) =
        TextStyle(fontFamily = f, fontWeight = w, fontSize = size.sp, lineHeight = line.sp, letterSpacing = 0.sp)
    return Typography(
        displayLarge = s(40, 54, FontWeight.Bold), displayMedium = s(34, 48, FontWeight.Bold),
        displaySmall = s(28, 40, FontWeight.Bold), headlineLarge = s(26, 38, FontWeight.Bold),
        headlineMedium = s(24, 34, FontWeight.Bold), headlineSmall = s(21, 31, FontWeight.Bold),
        titleLarge = s(19, 29, FontWeight.Bold), titleMedium = s(16, 25, FontWeight.SemiBold),
        titleSmall = s(14, 22, FontWeight.SemiBold), bodyLarge = s(16, 26, FontWeight.Normal),
        bodyMedium = s(14, 23, FontWeight.Normal), bodySmall = s(12, 19, FontWeight.Normal),
        labelLarge = s(14, 21, FontWeight.Medium), labelMedium = s(12, 18, FontWeight.Medium),
        labelSmall = s(11, 16, FontWeight.Medium)
    )
}

/** شعاع گوشه‌ها: همهٔ کارت‌ها/دکمه‌ها/فیلدها فقط از همین پنج اندازه استفاده می‌کنند. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(28.dp)
)

@Suppress("DEPRECATION")
private fun applySystemBars(window: android.view.Window, color: Int) {
    window.statusBarColor = color
    window.navigationBarColor = color
}

@Composable
fun AppTheme(dark: Boolean, content: @Composable () -> Unit) {
    val scheme = if (dark) DarkScheme else LightScheme
    // نوار وضعیت و نوار پایین گوشی هم‌رنگ زمینهٔ برنامه (در همهٔ بخش‌ها یکسان)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                applySystemBars(window, scheme.surface.toArgb())
                val ctrl = WindowCompat.getInsetsController(window, view)
                ctrl.isAppearanceLightStatusBars = !dark
                ctrl.isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = scheme, typography = typographyOf(rememberAppFontFamily()), shapes = AppShapes, content = content)
}
