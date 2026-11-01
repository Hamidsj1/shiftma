package ir.shiftapp.system

import android.content.Context
import androidx.work.*
import ir.shiftapp.core.JalaliDate
import ir.shiftapp.data.*
import java.io.File
import java.util.concurrent.TimeUnit

/** پشتیبان خودکار روزانه در پوشهٔ اختصاصی برنامه (بدون نیاز به مجوز). با حذف برنامه این پوشه هم پاک می‌شود. */
object AutoBackup {
    private const val WORK = "auto_backup_daily"
    private const val PREFIX = "myshift-auto-"

    fun dir(ctx: Context): File = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "backups").apply { mkdirs() }

    fun files(ctx: Context): List<File> =
        dir(ctx).listFiles { f -> f.name.startsWith(PREFIX) && f.name.endsWith(".json") }?.sortedByDescending { it.name }.orEmpty()

    fun apply(ctx: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(ctx.applicationContext)
        if (enabled) wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS).build())
        else wm.cancelUniqueWork(WORK)
    }

    fun runNow(ctx: Context) {
        WorkManager.getInstance(ctx.applicationContext).enqueue(OneTimeWorkRequestBuilder<AutoBackupWorker>().build())
    }

    suspend fun write(ctx: Context): File {
        val dao = AppDb.get(ctx).dao()
        val keep = dao.prefsOnce().autoBackupKeep
        val json = Backup.toJson(Snapshot(dao.patternsOnce(), dao.allEntries(), dao.employeesOnce(), dao.settingsOnce(), dao.holidaysOnce()))
        val f = File(dir(ctx), "$PREFIX${JalaliDate.today().key}.json")
        f.writeText(json, Charsets.UTF_8)
        files(ctx).drop(keep).forEach { it.delete() }
        return f
    }
}

class AutoBackupWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = try { AutoBackup.write(applicationContext); Result.success() } catch (e: Exception) { Result.retry() }
}
