package ir.shiftapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.shiftapp.core.Fa
import ir.shiftapp.core.ProQuota
import ir.shiftapp.system.BazaarBilling

/** خرید درون‌برنامه‌ای فعال؛ MainActivity مقدارش را می‌دهد. */
val LocalBilling = staticCompositionLocalOf<BazaarBilling> { error("BazaarBilling در MainActivity فراهم نشده است") }

/**
 * کارت وضعیت قابلیت پولی: تعداد استفادهٔ رایگان باقی‌مانده، یا (وقتی سهمیه تمام شد) توضیح و دکمهٔ خرید از کافه‌بازار.
 * @param used تعداد دفعات استفادهٔ رایگانِ مصرف‌شده
 */
@Composable
fun ProStatusCard(featureTitle: String, used: Int, modifier: Modifier = Modifier) {
    val billing = LocalBilling.current
    val pro by billing.pro.collectAsState()
    val busy by billing.busy.collectAsState()
    val message by billing.message.collectAsState()
    val left = ProQuota.remaining(used)
    val exhausted = !pro && left == 0

    AppCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when {
                pro -> {
                    Text("«$featureTitle» برای همیشه باز است", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    HintText("از حمایت شما ممنونیم.")
                }
                exhausted -> {
                    Text("سهمیهٔ رایگان تمام شد", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${Fa.num(ProQuota.FREE_USES)} بار استفادهٔ رایگان از «$featureTitle» را مصرف کرده‌اید. " +
                            "با یک‌بار خرید از کافه‌بازار، این قابلیت برای همیشه و بدون محدودیت باز می‌شود.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                else -> {
                    Text(
                        "${Fa.num(left)} بار استفادهٔ رایگان باقی مانده",
                        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary
                    )
                    HintText("بعد از آن، «$featureTitle» با یک‌بار خرید از کافه‌بازار برای همیشه باز می‌شود.")
                }
            }

            if (!pro) {
                AppButton(
                    "خرید از کافه‌بازار", { billing.buy() }, Modifier.fillMaxWidth(),
                    style = if (exhausted) BtnStyle.Filled else BtnStyle.Tonal, enabled = !busy
                )
                AppButton(
                    "بازیابی خرید قبلی", { billing.refresh(silent = false) }, Modifier.fillMaxWidth(),
                    style = BtnStyle.Outline, enabled = !busy
                )
            }
            message?.let { HintText(it) }
        }
    }
}
