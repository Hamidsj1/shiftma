package ir.shiftapp.ui

import android.content.Context

/** کلیدهای ظاهری سبک (فقط روی همین گوشی؛ جدا از Prefs و پشتیبان تا فایل‌های کاربر بی‌تغییر بمانند). */
object UiFlags {
    private fun sp(c: Context) = c.getSharedPreferences("ui_flags", Context.MODE_PRIVATE)
    fun callButton(c: Context) = sp(c).getBoolean("call_button", true)
    fun setCallButton(c: Context, on: Boolean) = sp(c).edit().putBoolean("call_button", on).apply()
    /** جای دکمهٔ تماس (کسری از محدودهٔ حرکت، FabPos)؛ null = جای پیش‌فرض. */
    fun callPos(c: Context): String? = sp(c).getString("call_pos", null)
    fun setCallPos(c: Context, raw: String?) = sp(c).edit().apply { if (raw == null) remove("call_pos") else putString("call_pos", raw) }.apply()
    /** تب آخرِ نوار ترکیبیِ «ضروری/تلویزیون»: "tv" یا "ess". */
    fun stripTab(c: Context): String = sp(c).getString("strip_tab", "ess") ?: "ess"
    fun setStripTab(c: Context, v: String) = sp(c).edit().putString("strip_tab", v).apply()
}
