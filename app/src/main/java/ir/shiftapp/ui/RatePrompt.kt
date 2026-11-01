package ir.shiftapp.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import ir.shiftapp.core.RatePolicy

/** ذخیرهٔ وضعیت درخواست امتیاز؛ جدا از Prefs و پشتیبان (چیزی از کاربر نیست و با بازیابی پشتیبان عوض نمی‌شود). */
object RatePrompt {
    private const val FILE = "rate_prompt"
    private var countedThisProcess = false
    private fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private fun today() = java.time.LocalDate.now().toEpochDay()

    /** هر بار اجرای برنامه (نه هر بار دیدن صفحهٔ اول) یک‌بار شمرده می‌شود. */
    fun countOpen(c: Context) {
        if (countedThisProcess) return
        countedThisProcess = true
        val s = sp(c)
        val e = s.edit().putInt("opens", s.getInt("opens", 0) + 1)
        if (s.getLong("first", 0L) <= 0L) e.putLong("first", today())
        e.apply()
    }

    fun shouldAsk(c: Context, hasData: Boolean): Boolean {
        val s = sp(c)
        return RatePolicy.shouldAsk(s.getInt("opens", 0), s.getLong("first", 0L), s.getLong("last", 0L), s.getBoolean("never", false), today(), hasData)
    }

    fun markAsked(c: Context) = sp(c).edit().putLong("last", today()).apply()
    fun markNever(c: Context) = sp(c).edit().putBoolean("never", true).putLong("last", today()).apply()

    /** صفحهٔ نظر دادن برنامه در کافه‌بازار؛ اگر بازار نصب نبود صفحهٔ وب. */
    fun openBazaar(c: Context) {
        val pkg = c.packageName
        val bazaar = Intent(Intent.ACTION_EDIT, Uri.parse("bazaar://details?id=$pkg")).setPackage("com.farsitel.bazaar")
        try { c.startActivity(bazaar.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (_: Exception) {
            runCatching { c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://cafebazaar.ir/app/$pkg?l=fa")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
}

/** پنجرهٔ مهربانِ درخواست امتیاز؛ فقط وقتی سیاست اجازه بدهد، روی صفحهٔ اول. */
@Composable
fun RatePromptHost(hasData: Boolean) {
    val ctx = LocalContext.current
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(hasData) {
        RatePrompt.countOpen(ctx)
        if (RatePrompt.shouldAsk(ctx, hasData)) { RatePrompt.markAsked(ctx); show = true }
    }
    if (show) AlertDialog(
        onDismissRequest = { show = false },
        title = { Text("از «شیفت ما» راضی هستید؟") },
        text = { Text("نظر یا ستارهٔ شما در کافه‌بازار به دیده‌شدن برنامه و رسیدنش به همکاران دیگر کمک می‌کند. اجباری نیست و هیچ بخشی به آن وابسته نیست.") },
        confirmButton = { TextButton({ show = false; RatePrompt.openBazaar(ctx) }) { Text("ثبت نظر در بازار") } },
        dismissButton = {
            Row2(
                { TextButton({ show = false }) { Text("بعداً") } },
                { TextButton({ show = false; RatePrompt.markNever(ctx) }) { Text("دیگر نپرس") } }
            )
        }
    )
}

@Composable
private fun Row2(a: @Composable () -> Unit, b: @Composable () -> Unit) =
    androidx.compose.foundation.layout.Row { a(); b() }
