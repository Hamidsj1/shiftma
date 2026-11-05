package ir.shiftapp.system

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * ویجت نواری ۴×۱ (کنار ویجت ۴×۲ که برای کاربران فعلی می‌ماند). محاسبه و رنگ‌ها مشترک است: [ShiftWidget.updateAll].
 */
class ShiftStripWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { runCatching { ShiftWidget.updateAll(context) } } finally { pending.finish() }
        }
    }
}
