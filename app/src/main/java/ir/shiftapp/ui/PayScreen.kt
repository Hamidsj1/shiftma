package ir.shiftapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.shiftapp.R
import ir.shiftapp.core.*
import java.util.Locale

/** «۱٬۲۵۰٬۰۰۰» */
internal fun money(n: Long): String = Fa.digits(String.format(Locale.US, "%,d", n)).replace(',', '٬')

/** ورودی عددی کاربر (ارقام فارسی، ویرگول و ممیز فارسی هم قبول است) → عدد؛ خالی = ۰؛ نامعتبر = null. */
internal fun parseNumber(text: String): Double? {
    val t = DateText.normalizeDigits(text.trim()).replace(",", "").replace("٬", "").replace("،", "").replace('٫', '.')
    if (t.isEmpty()) return 0.0
    return t.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
}

private fun plain(v: Double): String = if (v == Math.floor(v)) v.toLong().toString() else v.toString()

@Composable
fun PaySection(vm: MainVm, onHome: () -> Unit) {
    val month by vm.month.collectAsState()
    val sel by vm.selected.collectAsState()
    val entries by vm.entriesByDate.collectAsState()
    val prefs by vm.prefs.collectAsState()
    val cal by vm.holidayCalendar.collectAsState()
    val hours = remember(month, sel, entries, prefs, cal) { vm.hours() }
    val pay = prefs.pay
    val result = remember(hours, pay, month) { hours?.let { PayCalculator.compute(it, pay, JalaliCalendar.daysInMonth(month.year, month.month)) } }

    ToolScaffold("حقوق و اضافه‌کار", "${Fa.MONTHS[month.month - 1]} ${Fa.num(month.year)}", onHome) {
        MonthSwitcher(vm, month)

        if (sel == null || hours == null || result == null) {
            EmptyState(R.drawable.ic_pay, "الگویی انتخاب نشده", text = "برای برآورد، اول در بخش «جدول شیفت» یک الگوی شیفت انتخاب کنید.")
        } else {
            ResultCard(result, hours, pay)
            if (hours.shiftsWithoutTime.isNotEmpty()) {
                HintText(
                    "ساعت این شیفت‌ها تنظیم نشده و صفر حساب شد: " +
                        hours.shiftsWithoutTime.joinToString("، ") { ShiftType.byCode(it)?.name ?: "کد ${Fa.num(it)}" } +
                        ". ساعت شیفت‌ها را در تنظیمات بخش شیفت بدهید.",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        SettingsGroup("تنظیمات حقوق", icon = R.drawable.ic_pay) {
            NumField("حقوق پایهٔ ماهانه (تومان)", pay.baseMonthly.toString(), money = true) { v -> vm.setPref(PrefKeys.PAY_BASE, v.toLong().toString()) }
            NumField("مزایای ثابت ماهانه (مسکن، خواربار، ...)", pay.benefits.toString(), money = true) { v -> vm.setPref(PrefKeys.PAY_BENEFITS, v.toLong().toString()) }
            Text("فوق‌العاده نوبت‌کاری", style = MaterialTheme.typography.titleSmall)
            ChipRow {
                listOf(0.0, 10.0, 15.0, 22.5).forEach { p ->
                    AppChip(if (p == 0.0) "ندارم" else "${Fa.digits(plain(p).replace('.', '٫'))}٪", pay.shiftPct == p, { vm.setPref(PrefKeys.PAY_SHIFT, p.toString()) })
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumField("اضافه‌کاری ٪", plain(pay.overtimePct), Modifier.weight(1f)) { vm.setPref(PrefKeys.PAY_OT, it.toString()) }
                NumField("شب‌کاری ٪", plain(pay.nightPct), Modifier.weight(1f)) { vm.setPref(PrefKeys.PAY_NIGHT, it.toString()) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumField("جمعه‌کاری ٪", plain(pay.fridayPct), Modifier.weight(1f)) { vm.setPref(PrefKeys.PAY_FRIDAY, it.toString()) }
                NumField("تعطیل‌کاری ٪", plain(pay.holidayPct), Modifier.weight(1f)) { vm.setPref(PrefKeys.PAY_HOLIDAY, it.toString()) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumField("تقسیم بر (ساعت)", plain(pay.divisor), Modifier.weight(1f)) { if (it >= 1) vm.setPref(PrefKeys.PAY_DIVISOR, it.toString()) }
                NumField("ساعت موظفی ماه (خالی/۰ = خودکار)", if (pay.dueHours > 0) plain(pay.dueHours) else "", Modifier.weight(1f)) { vm.setPref(PrefKeys.PAY_DUE, it.toString()) }
            }
            NumField("کسورات تقریبی ٪ (بیمه و مالیات)", plain(pay.deductionPct)) { vm.setPref(PrefKeys.PAY_DEDUCT, minOf(it, 100.0).toString()) }
            HintText("درصدها پیش‌فرضِ قانون کار است (اضافه‌کاری ۴۰٪، شب‌کاری ۳۵٪ برای ۲۲ تا ۶ بامداد، جمعه‌کاری ۴۰٪)، ولی قرارداد و آیین‌نامهٔ کارخانهٔ شما مقدم است؛ مثلاً شیفت‌کاران معمولاً فوق‌العادهٔ نوبت‌کاری می‌گیرند و شب‌کاری‌شان طبق شرایط ویژه حساب می‌شود. هر درصدی را ۰ بگذارید تا حساب نشود. «تقسیم بر ۲۲۰» یعنی مزد ساعت = حقوق ماهانه ÷ ۲۲۰.")
        }
        HintText("این صفحه فقط یک برآورد تقریبی از روی جدول شیفت است، نه فیش حقوقی و نه مشاورهٔ حقوقی. اعداد فقط روی همین گوشی می‌مانند.")
    }
}

@Composable
private fun NumField(label: String, initial: String, modifier: Modifier = Modifier.fillMaxWidth(), money: Boolean = false, onValue: (Double) -> Unit) {
    var text by remember { mutableStateOf(if (money && initial.toLongOrNull() != null) money(initial.toLong()) else initial) }
    val v = parseNumber(text)
    AppField(
        text, { t ->
            text = t
            parseNumber(t)?.let(onValue)
        }, label, modifier, keyboard = KeyboardType.Number, ltr = true, isError = v == null
    )
}

@Composable
private fun ResultCard(r: PayResult, h: HoursReport, pay: PaySettings) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(
            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).background(Pal.heroBrush).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (!pay.configured) {
                Text("حقوق پایه را وارد کنید", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text("ساعت‌های کار، شب‌کاری و جمعه‌کاری همین حالا از جدول شیفت حساب شده است؛ برای مبلغ، پایین حقوق پایه را بدهید.", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
            } else {
                Text("برآورد ناخالص ماه", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.75f))
                Text("${money(r.gross)} تومان", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                if (pay.deductionPct > 0) Text("پس از کسورات: ${money(r.net)} تومان", style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.9f))
                Text("مزد هر ساعت: ${money(r.hourly)} تومان", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
            }
        }
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                InfoLine("ساعت کار موظفی", Fa.hm(r.dueMinutes))
                InfoLine("کارکرد این ماه", Fa.hm(r.workedMinutes))
                if (r.autoOvertimeMinutes > 0) InfoLine("مازاد بر موظفی", Fa.hm(r.autoOvertimeMinutes))
                if (r.manualOvertimeMinutes > 0) InfoLine("اضافه‌کاریِ ثبت‌شده توسط شما", Fa.hm(r.manualOvertimeMinutes))
            }
        }
        if (pay.configured) AppCard(Modifier.fillMaxWidth()) {
            Column {
                r.lines.forEachIndexed { i, l ->
                    if (i > 0) RowDivider()
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(l.title, style = MaterialTheme.typography.titleSmall)
                            if (l.minutes != null) Text("${Fa.hm(l.minutes)} ساعت", style = MaterialTheme.typography.bodySmall, color = Pal.muted)
                        }
                        Text(if (l.amount == 0L) "—" else money(l.amount), style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}
