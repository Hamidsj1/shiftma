package ir.shiftapp.ui

import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.shiftapp.core.Fa
import ir.shiftapp.core.JalaliDate
import ir.shiftapp.system.AlarmKeyStop
import ir.shiftapp.system.AlarmRingService
import ir.shiftapp.system.AlarmStrip
import ir.shiftapp.system.ReminderScheduler
import ir.shiftapp.system.StripDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.util.Locale
import kotlin.math.abs

/**
 * صفحهٔ تمام‌صفحهٔ آلارم؛ از روی اعلان (full-screen intent) روی قفل‌صفحه باز می‌شود.
 * «قطع» و «چرت» همان کارهای دکمه‌های اعلان را انجام می‌دهند. با تمام‌شدن آلارم خودش بسته می‌شود.
 * قطع با کشیدن صفحه (به چپ/راست یا بالا) یا با دکمهٔ صدا (بالا/پایین) هم ممکن است.
 * دکمهٔ برگشت فقط صفحه را می‌بندد و آلارم ادامه دارد (اعلان و دکمه‌هایش می‌مانند).
 */
class AlarmActivity : ComponentActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private val watch = object : Runnable {
        override fun run() {
            if (!AlarmKeyStop.isRinging()) finish() else handler.postDelayed(this, 700)
        }
    }

    private var stopAlarm: () -> Unit = {}
    private val gesture = AlarmKeyStop.HoldGesture(
        onTap = { stopAlarm() },
        onHold = { code -> AlarmKeyStop.adjustAlarmVolume(applicationContext, raise = code == KeyEvent.KEYCODE_VOLUME_UP) }
    )

    /** دکمه‌های صدا آلارم را قطع می‌کنند (به‌جای تغییر صدا)؛ از dispatchKeyEvent تا هیچ ویوی دیگری قبل از آن دکمه را نگیرد. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (AlarmKeyStop.isVolumeKey(event.keyCode)) {
            // لمس کوتاه = قطع، نگه‌داشتن = کم/زیاد کردن صدای آلارم
            if (event.action == KeyEvent.ACTION_DOWN) gesture.down(event.keyCode, event.repeatCount) else gesture.up(event.keyCode)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE) ?: "آلارم شیفت"
        val body = intent.getStringExtra(ReminderScheduler.EXTRA_BODY) ?: ""
        val kind = intent.getStringExtra(ReminderScheduler.EXTRA_KIND)

        var handled = false
        fun act(action: String, code: Int) {
            if (handled) return
            handled = true
            runCatching { ReminderScheduler.actionIntent(this, action, code, title, body, kind).send() }
            finish()
        }
        stopAlarm = { act(ReminderScheduler.ACTION_DONE, 7005) }

        setContent {
            AppTheme(dark = true) {
                var strip by remember { mutableStateOf(emptyList<StripDay>()) }
                LaunchedEffect(Unit) { strip = withContext(Dispatchers.IO) { AlarmStrip.load(applicationContext) } }
                AlarmScreen(
                    title = title, body = body, strip = strip, canSnooze = kind != ir.shiftapp.system.AzanService.KIND_AZAN,
                    onStop = { act(ReminderScheduler.ACTION_DONE, 7005) },
                    onSnooze = { act(ReminderScheduler.ACTION_SNOOZE, 7004) }
                )
            }
        }
        handler.post(watch)
    }

    override fun onDestroy() {
        handler.removeCallbacks(watch)
        super.onDestroy()
    }
}

private val AlarmBg = Color(0xFF0F1224)
private val AlarmInk = Color(0xFFF2F4FF)
private val AlarmMuted = Color(0xFFA9B0D6)

@Composable
private fun AlarmScreen(title: String, body: String, strip: List<StripDay>, canSnooze: Boolean, onStop: () -> Unit, onSnooze: () -> Unit) {
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1000); tick++ } }
    val time = remember(tick / 20) {
        val t = LocalTime.now()
        Fa.digits(String.format(Locale.US, "%02d:%02d", t.hour, t.minute))
    }
    val date = remember {
        val d = JalaliDate.today()
        "${Fa.WEEK_FULL[d.dayOfWeek]} ${Fa.num(d.day)} ${Fa.MONTHS[d.month - 1]}"
    }

    // کشیدن صفحه: به چپ یا راست (بیش از ۲۲٪ عرض) یا به بالا (بیش از ۱۵٪ ارتفاع) و رها کردن = قطع آلارم؛ وگرنه برمی‌گردد
    var dx by remember { mutableStateOf(0f) }
    var dy by remember { mutableStateOf(0f) }
    var w by remember { mutableStateOf(1f) }
    var h by remember { mutableStateOf(1f) }
    val progress = maxOf(abs(dx) / (w * 0.22f), -dy / (h * 0.15f)).coerceIn(0f, 1f)
    Box(
        Modifier.fillMaxSize().background(AlarmBg)
            .onSizeChanged { w = it.width.toFloat().coerceAtLeast(1f); h = it.height.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = { if (abs(dx) >= w * 0.22f || -dy >= h * 0.15f) onStop() else { dx = 0f; dy = 0f } },      // state خوانده می‌شود، نه نسخهٔ قدیمی progress
                    onDragCancel = { dx = 0f; dy = 0f }
                ) { change, drag -> change.consume(); dx += drag.x; dy = minOf(dy + drag.y, 0f) }
            }
    ) {
    Column(
        Modifier.fillMaxSize().offset { IntOffset(dx.toInt(), dy.toInt()) }.alpha(1f - 0.6f * progress)
            .systemBarsPadding().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Spacer(Modifier.height(24.dp))
            Text(date, fontSize = 16.sp, color = AlarmMuted)
            Text(time, fontSize = 76.sp, fontWeight = FontWeight.Bold, color = AlarmInk)
            Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = AlarmInk, textAlign = TextAlign.Center)
            if (body.isNotBlank()) Text(body, fontSize = 16.sp, color = AlarmMuted, textAlign = TextAlign.Center)
        }
        if (strip.isNotEmpty()) StripRow(strip)
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // برای آدم نیمه‌خواب: متن درشت و کوتاه، و یک هدف غول‌پیکر برای قطع (لمس هرجای آن کافی است)
            Text(
                "بکش یا دکمهٔ صدا را بزن", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AlarmInk,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
            Box(
                Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(36.dp))
                    .background(Color(0xFFE5484D)).clickable { onStop() },
                contentAlignment = Alignment.Center
            ) {
                Text("قطع", fontSize = 64.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            // چرت کوچک‌تر و دورتر از قطع است تا کسی که نیمه‌خواب است اشتباهی آن را نزند
            Spacer(Modifier.height(28.dp))
            if (canSnooze) AppButton(
                "چرت — ${Fa.num(ReminderScheduler.SNOOZE_MIN)} دقیقه بعد", onSnooze,
                Modifier.fillMaxWidth().height(48.dp), style = BtnStyle.Outline
            )
        }
    }
    }
}

@Composable
private fun StripRow(strip: List<StripDay>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        strip.forEach { d ->
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                    .background(if (d.today) Color(0x33808CFF) else Color.Transparent)
                    .padding(vertical = 8.dp, horizontal = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(d.weekday, fontSize = 12.sp, color = AlarmMuted)
                Text(d.num, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = AlarmInk)
                Text(
                    d.shift, fontSize = 9.sp, color = Color(0xFF111827), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Color(d.color)).padding(vertical = 3.dp)
                )
            }
        }
    }
}
