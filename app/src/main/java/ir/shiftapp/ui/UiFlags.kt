package ir.shiftapp.ui

import android.content.Context

/** کلیدهای ظاهری سبک (فقط روی همین گوشی؛ جدا از Prefs و پشتیبان تا فایل‌های کاربر بی‌تغییر بمانند). */
object UiFlags {
    private fun sp(c: Context) = c.getSharedPreferences("ui_flags", Context.MODE_PRIVATE)
    fun callButton(c: Context) = sp(c).getBoolean("call_button", true)
    fun setCallButton(c: Context, on: Boolean) = sp(c).edit().putBoolean("call_button", on).apply()
}
