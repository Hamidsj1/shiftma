package ir.shiftapp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.shiftapp.R
import ir.shiftapp.core.JalaliDate

/*
 * کامپوننت‌های مشترک: همهٔ صفحه‌ها فقط از این‌ها (و رنگ/شکل/تایپوگرافی تم) استفاده می‌کنند
 * تا ظاهر بخش‌ها، دکمه‌ها، فیلدها و پنجره‌ها یکدست بماند.
 */

/** جهت چپ‌به‌راست برای شماره‌ها و متن‌های لاتین داخل صفحهٔ راست‌به‌چپ. */
internal val LtrStyle: TextStyle @Composable get() = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr)

@Composable
fun Ico(icon: Int, modifier: Modifier = Modifier, size: Dp = 24.dp, tint: Color = LocalContentColor.current, desc: String? = null) {
    Icon(painterResource(icon), contentDescription = desc, modifier = modifier.size(size), tint = tint)
}

@Composable
fun Dot(color: Color, size: Dp = 10.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(color))
}

/** نشان گرد/چهارگوشِ آیکن‌دار (در فهرست‌ها، کارت‌ها و حالت خالی). */
@Composable
fun IconBadge(
    icon: Int, modifier: Modifier = Modifier, size: Dp = 40.dp, iconSize: Dp = 22.dp,
    container: Color = MaterialTheme.colorScheme.primaryContainer, tint: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    circle: Boolean = false
) {
    Box(
        modifier.size(size).clip(if (circle) CircleShape else MaterialTheme.shapes.medium).background(container),
        contentAlignment = Alignment.Center
    ) { Ico(icon, size = iconSize, tint = tint) }
}

// ───────────────────────── کارت و عنوان‌ها ─────────────────────────

@Composable
fun AppCard(
    modifier: Modifier = Modifier, onClick: (() -> Unit)? = null,
    color: Color = Pal.card, content: @Composable ColumnScope.() -> Unit
) {
    val shape = MaterialTheme.shapes.large
    Column(
        modifier.clip(shape).background(color).border(1.dp, Pal.hairline, shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
        content = content
    )
}

/** عنوان کوچک بالای هر گروه از کارت‌ها. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text, modifier.padding(horizontal = 6.dp), style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
fun RowDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier.padding(horizontal = 16.dp), thickness = 1.dp, color = Pal.hairline)
}

/** یادداشت راهنمای ریز زیر یک بخش. */
@Composable
fun HintText(text: String, modifier: Modifier = Modifier, color: Color = Pal.muted) {
    Text(text, modifier, style = MaterialTheme.typography.bodySmall, color = color)
}

// ───────────────────────── نوار بالا ─────────────────────────

@Composable
fun BarIconButton(
    icon: Int, desc: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    tonal: Boolean = true, tint: Color = MaterialTheme.colorScheme.onSurface
) {
    // دکمهٔ «صفحهٔ اول»: به‌جای آیکن خانه، آرم خود برنامه
    if (icon == ir.shiftapp.R.drawable.ic_home) {
        Box(modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Image(
                painterResource(ir.shiftapp.R.drawable.app_logo), contentDescription = desc,
                modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp))
            )
        }
        return
    }
    Box(
        modifier.size(44.dp).clip(CircleShape)
            .background(if (tonal) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Ico(icon, size = 22.dp, tint = tint, desc = desc) }
}

/**
 * نوار بالای همهٔ بخش‌ها (جدول شیفت، دفترچه تلفن، زیرصفحه‌ها): دکمهٔ برگشت/خانه در ابتدا، عنوان و زیرعنوان، و عمل‌ها در انتها.
 * رنگ نوار = رنگ زمینه و نوار وضعیت گوشی، پس بدون لبه به صفحه می‌پیوندد.
 */
@Composable
fun AppTopBar(
    title: String, leadingIcon: Int, leadingDesc: String, onLeading: () -> Unit,
    modifier: Modifier = Modifier, subtitle: String? = null, subtitleDot: Color? = null, onTitleClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
            .heightIn(min = 68.dp).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        BarIconButton(leadingIcon, leadingDesc, onLeading)
        Column(
            Modifier.weight(1f).then(if (onTitleClick != null) Modifier.clip(MaterialTheme.shapes.small).clickable(onClick = onTitleClick) else Modifier),
            verticalArrangement = Arrangement.Center
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) Row(verticalAlignment = Alignment.CenterVertically) {
                if (subtitleDot != null) {
                    Dot(subtitleDot, 8.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Pal.muted)
            }
        }
        actions()
    }
}

// ───────────────────────── انتخاب ماه ─────────────────────────

/** نوار ماه یکسان برای تقویم، چهارشیفت، کاردکس، گرافیکی، گزارش و روز مشترک؛ با لمس نام ماه، انتخاب مستقیم ماه/سال باز می‌شود. */
@Composable
fun MonthSwitcher(vm: MainVm, month: JalaliDate, modifier: Modifier = Modifier) {
    var picker by remember { mutableStateOf(false) }
    Row(
        modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        BarIconButton(R.drawable.ic_chevron_left, "ماه قبل", { vm.shiftMonth(-1) })
        Row(
            Modifier.weight(1f).clip(MaterialTheme.shapes.medium).clickable { picker = true }.padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
        ) {
            Text("${MONTHS[month.month - 1]} ${month.year.fa()}", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(4.dp))
            Ico(R.drawable.ic_expand_more, size = 20.dp, tint = Pal.muted)
        }
        BarIconButton(R.drawable.ic_chevron_right, "ماه بعد", { vm.shiftMonth(1) })
        Box(
            Modifier.height(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(role = Role.Button) { vm.goToday() }.padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) { Text("امروز", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer) }
    }
    if (picker) MonthPickerDialog(month, onShift = { vm.shiftMonth(it); picker = false }, onClose = { picker = false })
}

@Composable
private fun MonthPickerDialog(current: JalaliDate, onShift: (Int) -> Unit, onClose: () -> Unit) {
    var year by remember { mutableStateOf(current.year) }
    AppDialog("انتخاب ماه", onClose, confirmText = "بستن", onConfirm = onClose) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BarIconButton(R.drawable.ic_chevron_left, "سال قبل", { year -= 1 })
            Text(year.fa(), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            BarIconButton(R.drawable.ic_chevron_right, "سال بعد", { year += 1 })
        }
        for (r in 0 until 4) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (c in 0 until 3) {
                    val m = r * 3 + c + 1
                    val sel = year == current.year && m == current.month
                    Box(
                        Modifier.weight(1f).height(46.dp).clip(MaterialTheme.shapes.medium)
                            .background(if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer)
                            .clickable { onShift((year - current.year) * 12 + (m - current.month)) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            MONTHS[m - 1], style = MaterialTheme.typography.labelLarge, maxLines = 1,
                            color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

// ───────────────────────── شیفت ─────────────────────────

/** برچسب رنگی شیفت (همان رنگ پاستلی تقویم)؛ marked = نقطهٔ «تغییر دستی/تعویض». */
@Composable
fun ShiftChip(
    code: Int, modifier: Modifier = Modifier, label: String = shiftName(code),
    marked: Boolean = false, compact: Boolean = false
) {
    Row(
        modifier.height(if (compact) 32.dp else 36.dp).clip(RoundedCornerShape(11.dp)).background(shiftColor(code))
            .padding(horizontal = if (compact) 4.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
    ) {
        Text(
            label, style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
            color = Color(0xFF1B1D2A), maxLines = 1, overflow = TextOverflow.Clip
        )
        if (marked) {
            Spacer(Modifier.width(4.dp))
            Dot(Pal.markOverride, 6.dp)
        }
    }
}

// ───────────────────────── حالت خالی ─────────────────────────

@Composable
fun EmptyState(icon: Int, title: String, modifier: Modifier = Modifier, text: String? = null, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        IconBadge(icon, size = 68.dp, iconSize = 32.dp, circle = true)
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (text != null) Text(text, style = MaterialTheme.typography.bodyMedium, color = Pal.muted, textAlign = TextAlign.Center)
        action?.invoke()
    }
}

// ───────────────────────── دکمه‌ها ─────────────────────────

enum class BtnStyle { Filled, Tonal, Outline, Danger }

@Composable
fun AppButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: Int? = null,
    style: BtnStyle = BtnStyle.Filled, enabled: Boolean = true
) {
    val shape = MaterialTheme.shapes.medium
    val pad = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
    val m = modifier.heightIn(min = 50.dp)
    val body: @Composable RowScope.() -> Unit = {
        if (icon != null) {
            Ico(icon, size = 20.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, textAlign = TextAlign.Center)
    }
    when (style) {
        BtnStyle.Filled -> Button(onClick, m, enabled = enabled, shape = shape, contentPadding = pad, content = body)
        BtnStyle.Tonal -> FilledTonalButton(onClick, m, enabled = enabled, shape = shape, contentPadding = pad, content = body)
        BtnStyle.Outline -> OutlinedButton(
            onClick, m, enabled = enabled, shape = shape, contentPadding = pad,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), content = body
        )
        BtnStyle.Danger -> Button(
            onClick, m, enabled = enabled, shape = shape, contentPadding = pad,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer
            ),
            content = body
        )
    }
}

/** دکمهٔ گردِ کوچک در ردیف‌ها (ویرایش، حذف، کپی، اشتراک ...). */
@Composable
fun SmallIconButton(icon: Int, desc: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Box(
        modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Ico(icon, size = 20.dp, tint = tint, desc = desc) }
}

// ───────────────────────── فیلد ورودی ─────────────────────────

@Composable
fun AppField(
    value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier.fillMaxWidth(),
    placeholder: String? = null, keyboard: KeyboardType = KeyboardType.Text, ltr: Boolean = false,
    singleLine: Boolean = true, isError: Boolean = false, supporting: String? = null,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None
) {
    val ph: (@Composable () -> Unit)? = if (placeholder != null) { { Text(placeholder) } } else null
    val sup: (@Composable () -> Unit)? = if (supporting != null) { { Text(supporting) } } else null
    OutlinedTextField(
        value, onChange, modifier, label = { Text(label) }, placeholder = ph, supportingText = sup,
        singleLine = singleLine, isError = isError, shape = MaterialTheme.shapes.medium,
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        textStyle = if (ltr) LocalTextStyle.current.copy(textDirection = TextDirection.Ltr) else LocalTextStyle.current
    )
}

// ───────────────────────── ردیف‌های تنظیمات ─────────────────────────

@Composable
fun SettingRow(
    title: String, modifier: Modifier = Modifier, subtitle: String? = null, icon: Int? = null,
    onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (icon != null) IconBadge(icon)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Pal.muted)
        }
        trailing?.invoke()
    }
}

@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, subtitle: String? = null, icon: Int? = null) {
    SettingRow(title, modifier, subtitle, icon, onClick = { onChange(!checked) }) {
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** یک بخش تنظیمات: عنوان (با آیکن) + کارتِ محتوا. */
@Composable
fun SettingsGroup(title: String, modifier: Modifier = Modifier, icon: Int? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (icon != null) Ico(icon, size = 18.dp, tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

// ───────────────────────── انتخاب‌ها ─────────────────────────

@Composable
fun AppChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, dotColor: Color? = null) {
    val lead: (@Composable () -> Unit)? =
        if (selected) { { Ico(R.drawable.ic_check, size = 18.dp) } }
        else if (dotColor != null) { { Dot(dotColor, 10.dp) } }
        else null
    FilterChip(
        selected, onClick, label = { Text(label, style = MaterialTheme.typography.labelLarge) }, modifier = modifier,
        shape = RoundedCornerShape(50), leadingIcon = lead,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

/** چند چیپِ پشت‌سرهم با اسکرول افقی. */
@Composable
fun ChipRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically, content = content
    )
}

/** انتخاب یک‌گزینه‌ای تمام‌عرض (تم، نوع هشدار، زمان یادآوری). */
@Composable
fun Segmented(options: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    Row(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { i, t ->
            val sel = i == selected
            Box(
                Modifier.weight(1f).heightIn(min = 42.dp).clip(RoundedCornerShape(11.dp))
                    .background(if (sel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(i) }.padding(horizontal = 6.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    t, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 2,
                    color = if (sel) MaterialTheme.colorScheme.onPrimaryContainer else Pal.muted,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

// ───────────────────────── پنجره‌ها ─────────────────────────

/** پنجرهٔ یکدست برنامه: گوشهٔ ۲۸، زمینهٔ روشن/تیره از Pal، عنوان، محتوای اسکرول‌شونده و دکمه‌های متنی. */
@Composable
fun AppDialog(
    title: String?, onDismiss: () -> Unit, confirmText: String? = null, onConfirm: (() -> Unit)? = null,
    dismissText: String? = null, onDismissClick: (() -> Unit)? = null, confirmEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val titleSlot: (@Composable () -> Unit)? =
        if (title != null) { { Text(title, style = MaterialTheme.typography.titleLarge) } } else null
    val dismissSlot: (@Composable () -> Unit)? =
        if (dismissText != null) { { TextButton(onDismissClick ?: onDismiss) { Text(dismissText) } } } else null
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Pal.dialog, shape = MaterialTheme.shapes.extraLarge, tonalElevation = 0.dp,
        title = titleSlot,
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp), content = content) },
        confirmButton = {
            if (confirmText != null && onConfirm != null) TextButton(onConfirm, enabled = confirmEnabled) {
                Text(confirmText, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = dismissSlot
    )
}

@Composable
fun ConfirmDialog(text: String, okLabel: String, onOk: () -> Unit, onCancel: () -> Unit, title: String? = null, danger: Boolean = false) {
    val titleSlot: (@Composable () -> Unit)? =
        if (title != null) { { Text(title, style = MaterialTheme.typography.titleLarge) } } else null
    AlertDialog(
        onDismissRequest = onCancel, containerColor = Pal.dialog, shape = MaterialTheme.shapes.extraLarge, tonalElevation = 0.dp,
        title = titleSlot,
        text = { Text(text, style = MaterialTheme.typography.bodyLarge) },
        confirmButton = {
            TextButton(onOk) {
                Text(okLabel, fontWeight = FontWeight.Bold, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onCancel) { Text("انصراف") } }
    )
}

// ───────────────────────── آمار ─────────────────────────

/** کاشی آمار (عدد بزرگ + برچسب) برای گزارش و مرخصی. */
@Composable
fun StatTile(label: String, value: String, icon: Int, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary) {
    AppCard(modifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            IconBadge(icon, size = 34.dp, iconSize = 18.dp, container = tint.copy(alpha = 0.14f), tint = tint, circle = true)
            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(label, style = MaterialTheme.typography.bodySmall, color = Pal.muted)
        }
    }
}
