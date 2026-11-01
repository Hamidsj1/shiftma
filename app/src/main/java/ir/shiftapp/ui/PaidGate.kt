package ir.shiftapp.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.shiftapp.core.PaidAccess

/**
 * صفحهٔ قفل یک بخش پولی: شناسهٔ گوشی، دکمهٔ لینک پرداخت و جای وارد کردن کد.
 * [onUnlocked] بعد از کد درست صدا زده می‌شود تا صفحهٔ بالا دوباره وضعیت قفل را بخواند.
 * اگر دوست دارید ظاهرش مثل بقیهٔ برنامه باشد، Button/OutlinedButton را با AppButton عوض کنید.
 */
@Composable
fun PaidGate(sectionId: String, title: String, payLink: String, onUnlocked: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val installId = remember { PaidAccess.installId(ctx) }
    var code by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(if (title.isBlank()) "این بخش ویژه است" else "«$title» بخش ویژه است", style = MaterialTheme.typography.titleLarge)
        Text("بعد از پرداخت، شناسهٔ زیر را برای ادمین بفرستید تا کد باز شدن همین بخش را برایتان بسازد.")

        SelectionContainer {
            Text(installId, style = MaterialTheme.typography.headlineSmall)
        }

        if (payLink.isNotEmpty()) {
            Button(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(payLink))) }, Modifier.fillMaxWidth()) {
                Text("رفتن به صفحهٔ پرداخت")
            }
        }

        OutlinedTextField(
            value = code, onValueChange = { code = it; error = "" },
            label = { Text("کد باز شدن") }, singleLine = true, isError = error.isNotEmpty(),
            supportingText = { if (error.isNotEmpty()) Text(error) },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                val left = PaidAccess.lockRemainingMs()
                when {
                    left > 0 -> error = "چند بار اشتباه زدید؛ ${(left / 1000) + 1} ثانیه صبر کنید"
                    PaidAccess.tryUnlock(ctx, sectionId, code) -> onUnlocked()
                    else -> error = "کد درست نیست"
                }
            },
            enabled = code.isNotBlank(), modifier = Modifier.fillMaxWidth()
        ) { Text("باز کردن") }

        OutlinedButton(onClick = onBack, Modifier.fillMaxWidth()) { Text("بازگشت") }
    }
}
