package ir.shiftapp

import android.app.Application
import ir.shiftapp.system.DiagLog

/** فقط برای راه‌اندازی گزارش خطا (DiagLog)؛ کار دیگری نمی‌کند. */
class ShiftApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DiagLog.installCrashHandler(this)
        DiagLog.i("APP", "start")
        ir.shiftapp.system.AlarmKeyStop.restoreVolume(this)      // اگر پروسه وسط آلارمِ کم‌شده بسته شده بود
    }
}
