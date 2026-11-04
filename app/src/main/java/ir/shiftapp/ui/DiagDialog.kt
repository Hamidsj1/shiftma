package ir.shiftapp.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.shiftapp.core.SpecialAccess
import ir.shiftapp.system.DiagLog
import ir.shiftapp.system.DiagReport
import kotlinx.coroutines.launch

private var diagFails = 0
private var diagLockedUntil = 0L

/**
 * گزارش عملکرد/خطا: با لمس طولانی روی «نسخه» در «درباره» باز می‌شود، رمز روزانهٔ دسترسی ویژه می‌خواهد
 * (همان رمز پنل ادمین) و فقط کپی/ارسال دستی دارد؛ هیچ‌چیز خودکار فرستاده نمی‌شود.
 */
@Composable
fun DiagDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var ok by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    var report by remember { mutableStateOf("") }
    if (!ok) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("گزارش عملکرد") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("رمز روزانه را وارد کنید.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(code, { code = it.take(12); err = "" }, singleLine = true, isError = err.isNotEmpty(),
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                    if (err.isNotEmpty()) Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton({
                    val now = System.currentTimeMillis()
                    if (now < diagLockedUntil) { err = "چند لحظه بعد دوباره امتحان کنید"; return@TextButton }
                    if (SpecialAccess.verify(code)) {
                        diagFails = 0; ok = true
                        scope.launch { report = runCatching { DiagReport.build(ctx) }.getOrElse { "خطا در ساخت گزارش: ${it.message}" } }
                    } else {
                        err = "رمز درست نیست"
                        if (++diagFails >= 5) { diagFails = 0; diagLockedUntil = now + 60_000L }
                    }
                }) { Text("ورود") }
            },
            dismissButton = { TextButton(onDismiss) { Text("بستن") } }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("گزارش عملکرد و خطا") },
            text = {
                Box(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    Text(report.ifEmpty { "در حال ساخت…" }, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp)
                }
            },
            confirmButton = {
                Row {
                    TextButton({
                        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("shiftma-report", report))
                        Toast.makeText(ctx, "گزارش کپی شد", Toast.LENGTH_SHORT).show()
                    }) { Text("کپی") }
                    TextButton({
                        runCatching {
                            ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report).putExtra(Intent.EXTRA_SUBJECT, "گزارش شیفت ما"), "ارسال گزارش"))
                        }
                    }) { Text("ارسال") }
                }
            },
            dismissButton = {
                Row {
                    TextButton({ DiagLog.clear(ctx); scope.launch { report = DiagReport.build(ctx) } }) { Text("پاک کردن رویدادها") }
                    TextButton(onDismiss) { Text("بستن") }
                }
            }
        )
    }
}
