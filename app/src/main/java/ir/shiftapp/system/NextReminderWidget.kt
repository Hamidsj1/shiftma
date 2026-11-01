package ir.shiftapp.system

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
import java.time.LocalDate
import java.time.ZoneId

/**
 * ویجت «هشدار بعدی»: نزدیک‌ترین هشدار (یادآور شیفت، آلارم تکی، یادداشت یا اذان) با زمان و شمارش معکوس.
 * همان محاسبهٔ ReminderPlanner.next را که زمان‌بند آلارم‌ها می‌کند اجرا می‌کند، پس همیشه با هشدار واقعیِ بعدی یکی است.
 * لمس ویجت بخش مربوط (شیفت، یادداشت‌ها یا اوقات شرعی) را باز می‌کند.
 */
class NextReminderWidget : AppWidgetProvider() {
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
            val ids = mgr.getAppWidgetIds(ComponentName(app, NextReminderWidget::class.java))
            if (ids.isEmpty()) return
            CoroutineScope(Dispatchers.IO).launch { runCatching { update(app, mgr, ids) } }
        }

        private suspend fun update(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
            val dao = AppDb.get(ctx).dao()
            val prefs = dao.prefsOnce()
            val zone = ZoneId.systemDefault()
            val now = System.currentTimeMillis()
            val p = dao.selectedPattern()
            val today = JalaliCalendar.fromLocalDate(LocalDate.now(zone))
            val next = runCatching {
                if (p == null) ReminderPlanner.next(now, zone, null, null, emptyMap(), prefs)
                else {
                    val entries = dao.entriesByDateOnce(p.id, today, today.plusDays(50))
                    ReminderPlanner.next(now, zone, JalaliDate(p.baseYear, p.baseMonth, p.baseDay), ShiftPattern.parse(p.pattern), entries, prefs)
                }
            }.getOrNull()

            val section = when {
                next == null -> "shift"
                next.isPrayer -> "prayer"
                next.isNote -> "notes"
                else -> "shift"
            }
            val open = PendingIntent.getActivity(
                ctx, 7040, Intent(ctx, MainActivity::class.java).putExtra(EXTRA_SECTION, section).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            for (id in ids) {
                val rv = RemoteViews(ctx.packageName, R.layout.widget_next)
                if (next == null) {
                    rv.setTextViewText(R.id.n_title, "هشداری برنامه‌ریزی نشده")
                    rv.setTextViewText(R.id.n_when, "یادآور شیفت یا آلارم تکی بسازید")
                    rv.setViewVisibility(R.id.n_body, View.GONE)
                } else {
                    rv.setTextViewText(R.id.n_title, next.title)
                    rv.setTextViewText(R.id.n_when, WidgetText.nextLine(now, next.triggerAtMs, zone))
                    val body = next.body.trim()
                    rv.setTextViewText(R.id.n_body, body)
                    rv.setViewVisibility(R.id.n_body, if (body.isEmpty()) View.GONE else View.VISIBLE)
                }
                rv.setOnClickPendingIntent(R.id.n_root, open)
                mgr.updateAppWidget(id, rv)
            }
        }
    }
}
