package ir.shiftapp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.shiftapp.R

/**
 * «تنظیمات» و «درباره و اشتراک‌گذاری» که از کشوی اصلی صفحهٔ اول باز می‌شوند (همان صفحه‌های کشوی «جدول شیفت»).
 * خودِ صفحه‌ها اسکرول دارند؛ این‌جا فقط نوار بالا، بازگشت به صفحهٔ اول و نمایش پیام‌های برنامه (مثل «فایل ذخیره شد») را می‌دهیم.
 */
@Composable
fun AppSettingsSection(vm: MainVm, onHome: () -> Unit) =
    HomeSubPage("تنظیمات", "ساعت شیفت، یادآور، تم، پشتیبان", vm, onHome) { SettingsScreen(vm) }

@Composable
fun AppAboutSection(vm: MainVm, onHome: () -> Unit) =
    HomeSubPage("درباره و اشتراک‌گذاری", "معرفی برنامه و ارسال برای دوستان", vm, onHome) { AboutScreen() }

@Composable
private fun HomeSubPage(title: String, subtitle: String, vm: MainVm, onHome: () -> Unit, content: @Composable () -> Unit) {
    val msg by vm.message.collectAsState()
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(msg) { msg?.let { snack.showSnackbar(it); vm.message.value = null } }
    BackHandler { onHome() }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snack) },
        topBar = { AppTopBar(title = title, leadingIcon = R.drawable.ic_home, leadingDesc = "صفحهٔ اول", onLeading = onHome, subtitle = subtitle) }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().padding(horizontal = 16.dp)) { content() }
    }
}
