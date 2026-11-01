package ir.shiftapp.system

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import ir.shiftapp.R
import ir.shiftapp.core.English
import ir.shiftapp.ui.EXTRA_SECTION
import ir.shiftapp.ui.MainActivity

/**
 * ویجت «لغت روز انگلیسی»: هر روز یک لغت با معنی و جمله؛ دکمهٔ «لغت بعدی» لغت دیگری نشان می‌دهد
 * و لمس بقیهٔ ویجت بخش یادگیری انگلیسی را باز می‌کند. به دیتابیس نیازی ندارد (فقط یک عدد در SharedPreferences).
 */
class EnglishWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = render(context, manager, ids)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_NEXT) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val today = todayDay()
            val offset = if (prefs.getInt(KEY_DAY, -1) == today) prefs.getInt(KEY_OFFSET, 0) else 0
            prefs.edit().putInt(KEY_DAY, today).putInt(KEY_OFFSET, offset + 1).apply()
            val mgr = AppWidgetManager.getInstance(context)
            render(context, mgr, mgr.getAppWidgetIds(ComponentName(context, EnglishWidget::class.java)))
        } else super.onReceive(context, intent)
    }

    companion object {
        const val ACTION_NEXT = "ir.shiftapp.action.ENGLISH_NEXT"
        private const val PREFS = "english_widget"
        private const val KEY_DAY = "day"
        private const val KEY_OFFSET = "offset"

        private fun todayDay() = java.time.LocalDate.now().toEpochDay().toInt()

        private fun render(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
            if (ids.isEmpty()) return
            val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val today = todayDay()
            // «لغت بعدی» فقط برای همان روز می‌ماند؛ فردا دوباره لغت روزِ معمولی نمایش داده می‌شود
            val offset = if (prefs.getInt(KEY_DAY, -1) == today) prefs.getInt(KEY_OFFSET, 0) else 0
            val w = English.wordOfDay(today, offset)
            val open = PendingIntent.getActivity(
                ctx, 7020, Intent(ctx, MainActivity::class.java).putExtra(EXTRA_SECTION, "english").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val next = PendingIntent.getBroadcast(
                ctx, 7021, Intent(ctx, EnglishWidget::class.java).setAction(ACTION_NEXT),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            for (id in ids) {
                val rv = RemoteViews(ctx.packageName, R.layout.widget_english)
                rv.setTextViewText(R.id.e_word, w.en)
                rv.setTextViewText(R.id.e_meaning, w.fa)
                rv.setTextViewText(R.id.e_example, w.example)
                rv.setOnClickPendingIntent(R.id.e_root, open)
                rv.setOnClickPendingIntent(R.id.e_next, next)
                mgr.updateAppWidget(id, rv)
            }
        }
    }
}
