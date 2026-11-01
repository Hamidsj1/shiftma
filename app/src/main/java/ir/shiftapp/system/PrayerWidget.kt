package ir.shiftapp.system

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import ir.shiftapp.R
import ir.shiftapp.core.*
import ir.shiftapp.data.*
import ir.shiftapp.ui.EXTRA_SECTION
import ir.shiftapp.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * ویجت «اوقات شرعی»: پنج وقت (صبح، ظهر، عصر، مغرب، عشا) با برجسته‌کردن وقت بعدی و شمارش معکوس؛
 * در ماه رمضان به‌جای آن «تا افطار / تا پایان سحر» را می‌نویسد. بعد از اذان عشا وقت‌های فردا نشان داده می‌شود.
 * با خاموش بودن «امکانات مذهبی» فقط یک پیام نشان می‌دهد. محاسبه آفلاین است (همان PrayerCalc).
 */
class PrayerWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { update(context, manager, ids) } finally { pending.finish() }
        }
    }

    companion object {
        fun refresh(context: Context) {
            val app = context.applicationContext
            val mgr = AppWidgetManager.getInstance(app)
            val ids = mgr.getAppWidgetIds(ComponentName(app, PrayerWidget::class.java))
            if (ids.isEmpty()) return
            CoroutineScope(Dispatchers.IO).launch { runCatching { update(app, mgr, ids) } }
        }

        private val SHOWN = listOf(Prayer.FAJR, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA)
        private val LABELS = listOf("صبح", "ظهر", "عصر", "مغرب", "عشا")
        private val COLS = intArrayOf(R.id.p_c0, R.id.p_c1, R.id.p_c2, R.id.p_c3, R.id.p_c4)
        private val LABEL_IDS = intArrayOf(R.id.p_l0, R.id.p_l1, R.id.p_l2, R.id.p_l3, R.id.p_l4)
        private val TIME_IDS = intArrayOf(R.id.p_t0, R.id.p_t1, R.id.p_t2, R.id.p_t3, R.id.p_t4)
        private const val WHITE = -1       // 0xFFFFFFFF

        private suspend fun update(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
            val prefs = AppDb.get(ctx).dao().prefsOnce()
            val open = PendingIntent.getActivity(
                ctx, 7030, Intent(ctx, MainActivity::class.java).putExtra(EXTRA_SECTION, "prayer").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            for (id in ids) {
                val rv = RemoteViews(ctx.packageName, R.layout.widget_prayer)
                rv.setOnClickPendingIntent(R.id.p_root, open)
                for (i in SHOWN.indices) rv.setTextViewText(LABEL_IDS[i], LABELS[i])
                if (!prefs.prayerOn) {
                    rv.setTextViewText(R.id.p_title, "اوقات شرعی")
                    rv.setTextViewText(R.id.p_next, "امکانات مذهبی خاموش است؛ برای روشن‌کردن لمس کنید")
                    for (i in SHOWN.indices) rv.setTextViewText(TIME_IDS[i], "—")
                } else {
                    fill(rv, prefs)
                }
                mgr.updateAppWidget(id, rv)
            }
        }

        private fun fill(rv: RemoteViews, prefs: Prefs) {
            val zone = ZoneId.systemDefault()
            val now = System.currentTimeMillis()
            val pl = prefs.prayerPlace
            val today = LocalDate.now(zone)
            val next = PrayerCalc.next(now, zone, pl.lat, pl.lon, prefs.prayerMethod)
            // بعد از اذان عشا، وقت‌های فردا نشان داده می‌شود
            val showDate = next?.let { Instant.ofEpochMilli(it.atMs).atZone(zone).toLocalDate() } ?: today
            val times = PrayerCalc.times(showDate, pl.lat, pl.lon, zone, prefs.prayerMethod)
            rv.setTextViewText(R.id.p_title, "اوقات شرعی · ${pl.name}" + if (showDate != today) " · فردا" else "")

            val ram = Ramadan.status(now, zone, pl.lat, pl.lon, prefs.prayerMethod, prefs.lunarShift)
            val ramLine = ram?.let { r -> r.targetMs?.let { "${Ramadan.title(r)}: ${PrayerRules.remaining(it - now)}" } }
            val nextLine = next?.let { "${it.prayer.title} ${Fa.digits(ShiftTime.fmt(it.minuteOfDay % 1440))} · ${PrayerRules.remaining(it.atMs - now)} دیگر" }.orEmpty()
            rv.setTextViewText(R.id.p_next, ramLine ?: nextLine)

            SHOWN.forEachIndexed { i, p ->
                val m = times.minutes[p]
                rv.setTextViewText(TIME_IDS[i], if (m != null) Fa.digits(ShiftTime.fmt(m % 1440)) else "—")
                if (next?.prayer == p) {
                    // وقت بعدی: پس‌زمینهٔ دکمه‌ای و متن سفید
                    rv.setInt(COLS[i], "setBackgroundResource", R.drawable.widget_btn)
                    rv.setTextColor(LABEL_IDS[i], WHITE)
                    rv.setTextColor(TIME_IDS[i], WHITE)
                }
            }
        }
    }
}
