package ir.shiftapp.system

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
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
import java.time.ZoneId

/**
 * ویجت کم‌ارتفاع صفحهٔ اصلی (جایگزین ویجت «امروز و فردا»):
 *  ۱) نوار شیفت در جریان با پیشرفت و باقی‌مانده (اگر شیفتی در جریان نباشد: شیفت امروز و فردا)
 *  ۲) تقویم فقط یک هفته (شنبه تا جمعه)، رنگ شیفت هر روز، عدد روزهای تعطیل (جمعه و رسمی) قرمز، امروز با قاب
 *  ۳) چیپ‌های نزدیک‌ترین آلارم/یادآور یادداشت و «+N» برای بقیه
 * با لمس، بخش جدول شیفت باز می‌شود.
 */
class ShiftWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { runCatching { updateAll(context) } } finally { pending.finish() }
        }
    }

    companion object {
        private const val TICK_REQUEST = 7011
        private const val TICK_MS = 5 * 60 * 1000L
        private const val HOLIDAY_LIGHT = 0xFFC62828.toInt()
        private const val TODAY_INDIGO = 0xFF3F36C9.toInt()

        fun refresh(context: Context) {
            val app = context.applicationContext
            CoroutineScope(Dispatchers.IO).launch { runCatching { updateAll(app) } }
        }

        /** هر دو ویجت (۴×۲ و نواری ۴×۱) با یک محاسبه؛ ویجتی که روی صفحه نیست رد می‌شود. */
        internal suspend fun updateAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val full = mgr.getAppWidgetIds(ComponentName(ctx, ShiftWidget::class.java))
            val strip = mgr.getAppWidgetIds(ComponentName(ctx, ShiftStripWidget::class.java))
            if (full.isEmpty() && strip.isEmpty()) return
            try { update(ctx, mgr, full, R.layout.widget_shift, strip) }
            catch (e: Throwable) {
                // هر خطای محاسبه/ساخت نما: نمای امن بگذار تا ویجت «بارگذاری نشد» نشان ندهد؛ بعداً خودش دوباره تلاش می‌کند
                android.util.Log.e("ShiftWidget", "update failed", e)
                for (id in full + strip) runCatching { mgr.updateAppWidget(id, fallbackViews(ctx)) }
            }
        }

        private fun fallbackViews(ctx: Context): RemoteViews {
            val rv = RemoteViews(ctx.packageName, R.layout.widget_fallback)
            val open = PendingIntent.getActivity(
                ctx, 7010, Intent(ctx, MainActivity::class.java).putExtra(EXTRA_SECTION, "shift").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            rv.setOnClickPendingIntent(R.id.w_fb_root, open)
            return rv
        }

        private val DAY_LABEL = intArrayOf(R.id.w_d0_lbl, R.id.w_d1_lbl, R.id.w_d2_lbl, R.id.w_d3_lbl, R.id.w_d4_lbl, R.id.w_d5_lbl, R.id.w_d6_lbl)
        private val DAY_BG = intArrayOf(R.id.w_d0_bg, R.id.w_d1_bg, R.id.w_d2_bg, R.id.w_d3_bg, R.id.w_d4_bg, R.id.w_d5_bg, R.id.w_d6_bg)
        private val DAY_RING = intArrayOf(R.id.w_d0_ring, R.id.w_d1_ring, R.id.w_d2_ring, R.id.w_d3_ring, R.id.w_d4_ring, R.id.w_d5_ring, R.id.w_d6_ring)
        private val DAY_NUM = intArrayOf(R.id.w_d0_num, R.id.w_d1_num, R.id.w_d2_num, R.id.w_d3_num, R.id.w_d4_num, R.id.w_d5_num, R.id.w_d6_num)
        private val WEEK_SHORT = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")

        private class Chip(val atMs: Long, val text: String, val isNote: Boolean)

        private suspend fun update(ctx: Context, mgr: AppWidgetManager, ids: IntArray, layout: Int, stripIds: IntArray) {
            val dao = AppDb.get(ctx).dao()
            val prefs = dao.prefsOnce()
            val cal = dao.holidayCalendarOnce(prefs.fridayOff)
            val p = dao.selectedPattern()
            val zone = ZoneId.systemDefault()
            val nowMs = System.currentTimeMillis()
            val today = JalaliDate.today()
            val weekStart = today.plusDays(-today.dayOfWeek)      // شنبه = ۰

            // ---- شیفت هر روز (هفتهٔ جاری + دیروز برای شبکارِ در جریان)
            val base = p?.let { JalaliDate(it.baseYear, it.baseMonth, it.baseDay) }
            val pattern = p?.let { runCatching { ShiftPattern.parse(it.pattern) }.getOrNull() }
            val entries = if (p != null) runCatching { dao.entriesByDateOnce(p.id, today.plusDays(-7), today.plusDays(7)) }.getOrDefault(emptyMap()) else emptyMap()
            fun resolve(d: JalaliDate) = if (base != null && pattern != null)
                runCatching { DayResolver.resolve(base, pattern, d, entries[d.key].orEmpty()) }.getOrNull() else null
            fun slot(d: JalaliDate): Pair<Int, ShiftTime>? {
                val r = resolve(d) ?: return null
                if (r.onLeave || ShiftType.byCode(r.effectiveCode)?.isRest == true) return null
                val t = prefs.shiftTimes[r.effectiveCode] ?: return null
                return r.effectiveCode to t
            }
            fun codeName(c: Int?) = c?.let { ShiftType.byCode(it)?.name ?: "کد ${Fa.num(it)}" } ?: "—"
            fun codeColor(c: Int?) = (c?.let { ShiftType.byCode(it)?.color } ?: 0xFFDDE5F0).toInt()

            val nowLocal = Instant.ofEpochMilli(nowMs).atZone(zone)
            val progress = ShiftProgress.compute(nowLocal.hour * 60 + nowLocal.minute, slot(today.plusDays(-1)), slot(today))

            // ---- چیپ‌ها: آلارم‌های فعال + یادآور یادداشت‌ها، نزدیک‌ترین اول
            val chips = (prefs.alarms.filter { it.enabled && it.triggerAtMs > nowMs }.map { Chip(it.triggerAtMs, it.title, false) } +
                prefs.notes.filter { (it.reminderAt ?: 0L) > nowMs }.map { Chip(it.reminderAt ?: 0L, it.displayTitle, true) })
                .sortedBy { it.atMs }

            val open = PendingIntent.getActivity(
                ctx, 7010, Intent(ctx, MainActivity::class.java).putExtra(EXTRA_SECTION, "shift").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            for (id in ids + stripIds) {
              try {
                val rv = RemoteViews(ctx.packageName, if (id in stripIds) R.layout.widget_shift_strip else layout)

                // نشان رنگی گروه (A..D) کنار نام شیفت
                val gl = p?.let { ir.shiftapp.ui.groupLetter(it.name) }
                if (gl == null) rv.setViewVisibility(R.id.w_grp, View.GONE) else {
                    rv.setViewVisibility(R.id.w_grp, View.VISIBLE)
                    rv.setTextViewText(R.id.w_grp, gl.toString())
                    rv.setInt(R.id.w_grp, "setBackgroundResource", when (gl) {
                        'A' -> R.drawable.widget_grp_a; 'B' -> R.drawable.widget_grp_b; 'C' -> R.drawable.widget_grp_c; 'D' -> R.drawable.widget_grp_d
                        else -> R.drawable.widget_grp_x
                    })
                }
                // ردیف ۱
                if (progress != null) {
                    rv.setInt(R.id.w_dot, "setColorFilter", codeColor(progress.code))
                    rv.setTextViewText(R.id.w_name, codeName(progress.code))
                    rv.setViewVisibility(R.id.w_bar, View.VISIBLE)
                    rv.setProgressBar(R.id.w_bar, 100, (progress.fraction * 100).toInt(), false)
                    rv.setTextViewText(R.id.w_remain, "${ShiftProgress.clock(progress.remainingMin)} مانده")
                } else {
                    val tc = resolve(today)?.effectiveCode
                    val tm = resolve(today.plusDays(1))?.effectiveCode
                    rv.setInt(R.id.w_dot, "setColorFilter", codeColor(tc))
                    rv.setTextViewText(R.id.w_name, if (p == null) "الگویی تعریف نشده" else "امروز ${codeName(tc)}")
                    rv.setViewVisibility(R.id.w_bar, View.INVISIBLE)      // جای نوار حفظ می‌شود تا چیدمان نپرد
                    rv.setTextViewText(R.id.w_remain, if (p == null) "" else "فردا ${codeName(tm)}")
                }

                // ردیف ۲: هفته
                for (i in 0 until 7) {
                    val d = weekStart.plusDays(i)
                    val code = resolve(d)?.effectiveCode
                    val holiday = cal.kind(d) != DayKind.NORMAL
                    rv.setTextViewText(DAY_LABEL[i], WEEK_SHORT[i])
                    rv.setTextViewText(DAY_NUM[i], Fa.num(d.day))
                    // روز جاری: پر با نیلی تیره و عدد سفید (حلقه هم می‌ماند)؛ بقیه رنگ شیفت. رنگ قرمز تعطیلی روی کاشی پاستلی خوانا است
                    val isToday = d == today
                    rv.setInt(DAY_BG[i], "setColorFilter", if (isToday) TODAY_INDIGO else codeColor(code))
                    rv.setTextColor(DAY_NUM[i], if (isToday) 0xFFFFFFFF.toInt() else if (holiday) HOLIDAY_LIGHT else 0xFF1B1D2A.toInt())
                    rv.setViewVisibility(DAY_RING[i], if (d == today) View.VISIBLE else View.GONE)
                }

                // ردیف ۳: چیپ‌ها
                fun showChip(n: Int, c: Chip?) {
                    val box = if (n == 1) R.id.w_chip1 else R.id.w_chip2
                    val ico = if (n == 1) R.id.w_chip1_ico else R.id.w_chip2_ico
                    val txt = if (n == 1) R.id.w_chip1_txt else R.id.w_chip2_txt
                    if (c == null) { rv.setViewVisibility(box, View.GONE); return }
                    rv.setViewVisibility(box, View.VISIBLE)
                    rv.setImageViewResource(ico, if (c.isNote) R.drawable.ic_note else R.drawable.ic_alarm)
                    val time = WidgetText.dayAndTime(nowMs, c.atMs, zone).removePrefix("امروز ")
                    rv.setTextViewText(txt, "${c.text} · $time")
                }
                if (chips.isEmpty()) {
                    showChip(1, Chip(0, "آلارم", false))
                    rv.setTextViewText(R.id.w_chip1_txt, "آلارمی تنظیم نشده")
                    showChip(2, null)
                    rv.setViewVisibility(R.id.w_more, View.GONE)
                } else {
                    showChip(1, chips[0]); showChip(2, chips.getOrNull(1))
                    if (chips.size > 2) { rv.setViewVisibility(R.id.w_more, View.VISIBLE); rv.setTextViewText(R.id.w_more, "+${Fa.num(chips.size - 2)}") }
                    else rv.setViewVisibility(R.id.w_more, View.GONE)
                }

                rv.setOnClickPendingIntent(R.id.w_root, open)
                mgr.updateAppWidget(id, rv)
              } catch (e: Throwable) {
                android.util.Log.e("ShiftWidget", "widget $id failed", e)
                runCatching { mgr.updateAppWidget(id, fallbackViews(ctx)) }
              }
            }

            scheduleTick(ctx, ids + stripIds, progress != null)
        }

        /** تا وقتی شیفتی در جریان است، هر ۵ دقیقه ویجت تازه می‌شود تا «باقی‌مانده» کهنه نماند (آلارم غیردقیق، بدون مجوز اضافه). */
        private fun scheduleTick(ctx: Context, ids: IntArray, active: Boolean) = runCatching {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(ctx, ShiftWidget::class.java)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            val pi = PendingIntent.getBroadcast(ctx, TICK_REQUEST, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            if (active) am.set(AlarmManager.RTC, System.currentTimeMillis() + TICK_MS, pi) else am.cancel(pi)
        }
    }
}
