package ir.shiftapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import ir.shiftapp.core.AppSearch
import ir.shiftapp.core.Fa
import ir.shiftapp.core.SearchItem

/*
 * جست‌وجوی سراسری: یک کادر نوشتن و فهرست نتایج. با انتخاب نتیجه، همان بخش/سامانه باز می‌شود.
 * منطق تطبیق و امتیاز در core/AppSearch.kt است (آفلاین و بدون ثبت جست‌وجوها).
 */
@Composable
internal fun AppSearchDialog(items: List<SearchItem>, onPick: (SearchItem) -> Unit, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query, items) { AppSearch.search(items, query) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    AppDialog("جست‌وجو", onDismiss, dismissText = "بستن", onDismissClick = onDismiss) {
        AppField(query, { query = it.take(60) }, "نام بخش، ابزار یا یادداشت", Modifier.fillMaxWidth().focusRequester(focus))
        Column(Modifier.fillMaxWidth().heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
            when {
                query.isBlank() -> HintText("مثلاً: شیفت، اذان، تسبیح، فیش، انگلیسی، مرخصی، رمضان")
                results.isEmpty() -> HintText("نتیجه‌ای پیدا نشد.")
                else -> {
                    HintText("${Fa.num(results.size)} نتیجه")
                    results.forEach { r ->
                        Column(
                            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                                .clickable(role = Role.Button) { onPick(r) }
                                .padding(horizontal = 8.dp, vertical = 10.dp)
                        ) {
                            Text(r.title, style = MaterialTheme.typography.titleSmall)
                            val sub = listOf(r.group, r.subtitle).filter { it.isNotBlank() }.joinToString(" · ")
                            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = Pal.muted, maxLines = 2)
                        }
                    }
                }
            }
        }
    }
}
