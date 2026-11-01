package ir.shiftapp.core

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * انیمیشن حرکت‌های ورزشی به‌صورت آدمک خطی از نمای کنار (رو به راست).
 * کاملاً مستقل از اندروید است تا در تست واحد بررسی شود؛ رسم در `ui/ExerciseAnimView.kt` است.
 *
 * هر حرکت چند «ژست کلیدی» دارد که به‌صورت حلقه (آخری دوباره به اولی) با منحنی نرم پشت‌سرهم پخش می‌شود.
 * ژست = ۱۲ عدد: [hipX, hipY, torsoDeg, headDeg, handNx, handNy, handFx, handFy, footNx, footNy, footFx, footFy]
 * - hip و دست‌ها و پاها مختصات روی بوم ۱۰۰×۶۰ هستند و کف زمین y = 54 است.
 * - زاویه‌ها از «پایین» شروع می‌شوند و به سمت جلو (راست) مثبت‌اند: ۰ پایین، ۹۰ جلو، ۱۸۰ بالا، −۹۰ عقب.
 * - torsoDeg جهت لگن به گردن است و headDeg جهت گردن به مرکز سر.
 * - آرنج و زانو با IK دومفصلی از روی هدف دست و پا حساب می‌شوند (طول اندام‌ها همیشه ثابت می‌ماند؛
 *   اگر هدف دورتر از دسترس باشد، اندام صاف می‌شود).
 * N = اندام نزدیک به بیننده، F = اندام دور (کم‌رنگ‌تر رسم می‌شود).
 */
data class Pt(val x: Float, val y: Float)

class FigurePose(
    val hip: Pt, val shoulder: Pt, val neck: Pt, val head: Pt,
    val elbowN: Pt, val handN: Pt, val elbowF: Pt, val handF: Pt,
    val kneeN: Pt, val ankleN: Pt, val toeN: Pt, val kneeF: Pt, val ankleF: Pt, val toeF: Pt
)

class ExerciseAnim(val id: String, val periodMs: Int, val dumbbell: Boolean, val keys: List<FloatArray>)

object ExerciseAnims {
    const val W = 100f
    const val H = 60f
    const val FLOOR = 54f
    const val POSE_SIZE = 12
    const val HEAD_R = 3.4f

    private const val TORSO = 14f
    private const val SHOULDER = 12.5f
    private const val HEAD_D = 5.5f
    private const val THIGH = 11f
    private const val SHIN = 11f
    private const val UARM = 8f
    private const val FARM = 8f
    private const val FOOT = 3.2f
    private const val ARM_BEND = 1f   // آرنج به عقب/پایین خم می‌شود
    private const val LEG_BEND = -1f  // زانو به جلو خم می‌شود

    private fun a(id: String, periodMs: Int, dumbbell: Boolean, vararg keys: FloatArray) =
        ExerciseAnim(id, periodMs, dumbbell, keys.toList())

    /** برای افزودن حرکت تازه یک ExerciseAnim با همان شناسهٔ SportData.EXERCISES اضافه کنید. */
    val ALL: List<ExerciseAnim> = listOf(
        // شنا سوئدی
        a("pushup", 2600, false,
            floatArrayOf(39.77f, 44.36f, 116f, 116f, 52f, 54f, 54f, 54f, 20f, 54f, 18.8f, 54f),
            floatArrayOf(41.79f, 50.94f, 98f, 98f, 52f, 54f, 54f, 54f, 20f, 54f, 18.8f, 54f)
        ),
        // اسکات وزن بدن
        a("squat_bw", 3000, false,
            floatArrayOf(46f, 32f, 176f, 176f, 66f, 20f, 66f, 20.6f, 48f, 54f, 52f, 54f),
            floatArrayOf(38f, 43f, 140f, 140f, 66f, 33f, 66f, 33.6f, 48f, 54f, 52f, 54f)
        ),
        // لانج
        a("lunge", 3200, false,
            floatArrayOf(49f, 34f, 178f, 178f, 52f, 35f, 53f, 35.5f, 58f, 54f, 40f, 54f),
            floatArrayOf(49f, 44f, 178f, 178f, 52f, 45f, 53f, 45.5f, 58f, 54f, 40f, 54f)
        ),
        // پل باسن
        a("glute_bridge", 3000, false,
            floatArrayOf(57f, 50.5f, -90f, -90f, 62f, 52.5f, 63f, 52.5f, 65f, 54f, 67f, 54f),
            floatArrayOf(55f, 45f, -56f, -90f, 62f, 52.5f, 63f, 52.5f, 65f, 54f, 67f, 54f)
        ),
        // کرانچ
        a("crunch", 2400, false,
            floatArrayOf(60f, 50.5f, -90f, -90f, 42f, 49f, 43f, 49.5f, 72f, 54f, 74f, 54f),
            floatArrayOf(60f, 50.5f, -128f, -140f, 45f, 43f, 46f, 43.5f, 72f, 54f, 74f, 54f)
        ),
        // پلانک
        a("plank", 3600, false,
            floatArrayOf(41.3f, 48.49f, 104.5f, 104.5f, 60f, 54f, 62f, 54f, 20f, 54f, 18.8f, 54f),
            floatArrayOf(41.42f, 48.98f, 103.2f, 103.2f, 60f, 54f, 62f, 54f, 20f, 54f, 18.8f, 54f)
        ),
        // کوهنورد
        a("mountain", 1000, false,
            floatArrayOf(39.77f, 44.36f, 116f, 116f, 52f, 54f, 54f, 54f, 44f, 52.5f, 18.8f, 54f),
            floatArrayOf(39.77f, 44.36f, 116f, 116f, 52f, 54f, 54f, 54f, 31f, 48.5f, 29f, 48.5f),
            floatArrayOf(39.77f, 44.36f, 116f, 116f, 52f, 54f, 54f, 54f, 18.8f, 54f, 44f, 52.5f),
            floatArrayOf(39.77f, 44.36f, 116f, 116f, 52f, 54f, 54f, 54f, 29f, 48.5f, 31f, 48.5f)
        ),
        // جلو بازو دمبل
        a("db_curl", 3200, true,
            floatArrayOf(50f, 32f, 180f, 180f, 57f, 18f, 50f, 36f, 48f, 54f, 53f, 54f),
            floatArrayOf(50f, 32f, 180f, 180f, 50f, 36f, 57f, 18f, 48f, 54f, 53f, 54f)
        ),
        // پرس سرشانه دمبل
        a("db_press", 3000, true,
            floatArrayOf(50f, 32f, 180f, 180f, 55f, 18f, 56f, 18.5f, 48f, 54f, 53f, 54f),
            floatArrayOf(50f, 32f, 180f, 180f, 51f, 4f, 52f, 4.5f, 48f, 54f, 53f, 54f)
        ),
        // ساق پا ایستاده
        a("calf_raise", 2200, false,
            floatArrayOf(50f, 32f, 180f, 180f, 50f, 36f, 51f, 34.5f, 48f, 54f, 53f, 54f),
            floatArrayOf(50f, 28.6f, 180f, 180f, 50f, 31f, 51f, 31f, 48f, 50.6f, 53f, 50.6f)
        ),
        // دویدن آرام
        a("jog", 900, false,
            floatArrayOf(50f, 31.5f, 172f, 172f, 44f, 30f, 58f, 25f, 57f, 47f, 42f, 52f),
            floatArrayOf(50f, 30.5f, 172f, 172f, 50f, 34f, 51f, 33f, 50f, 52f, 48f, 50f),
            floatArrayOf(50f, 31.5f, 172f, 172f, 58f, 25f, 44f, 30f, 42f, 52f, 57f, 47f),
            floatArrayOf(50f, 30.5f, 172f, 172f, 51f, 33f, 50f, 34f, 48f, 50f, 50f, 52f)
        ),
        // سوپرمن
        a("superman", 3000, false,
            floatArrayOf(38f, 51f, 90f, 90f, 66f, 52f, 67f, 52f, 16f, 52.5f, 17f, 52.5f),
            floatArrayOf(38f, 49.5f, 106f, 118f, 62f, 40f, 63f, 40.5f, 17f, 42f, 18f, 42.5f)
        ),
        // دوچرخه هوایی
        a("bicycle", 2000, false,
            floatArrayOf(60f, 50.5f, -125f, -135f, 44f, 44f, 45f, 44.5f, 66f, 43f, 80f, 48f),
            floatArrayOf(60f, 50.5f, -125f, -135f, 44f, 44f, 45f, 44.5f, 80f, 48f, 66f, 43f)
        )
    )

    private val byId = ALL.associateBy { it.id }
    fun get(id: String): ExerciseAnim? = byId[id]

    private fun smooth(u: Float) = u * u * (3f - 2f * u)

    /** ژست درون‌یابی‌شده در لحظهٔ u از ۰ تا ۱ (حلقه). */
    fun sample(keys: List<FloatArray>, u: Float): FloatArray {
        val n = keys.size
        val x = (((u % 1f) + 1f) % 1f) * n
        val i = x.toInt().coerceIn(0, n - 1)
        val f = smooth(x - i)
        val p = keys[i]
        val q = keys[(i + 1) % n]
        return FloatArray(POSE_SIZE) { p[it] + (q[it] - p[it]) * f }
    }

    fun pose(anim: ExerciseAnim, u: Float): FigurePose = solve(sample(anim.keys, u))

    private fun ik(rx: Float, ry: Float, tx: Float, ty: Float, l1: Float, l2: Float, bend: Float): Array<Pt> {
        val dx = tx - rx
        val dy = ty - ry
        val d0 = max(hypot(dx, dy), 1e-4f)
        val ux = dx / d0
        val uy = dy / d0
        val d = min(max(d0, abs(l1 - l2) + 0.01f), l1 + l2 - 0.01f)
        val a = (l1 * l1 - l2 * l2 + d * d) / (2f * d)
        val h = sqrt(max(0f, l1 * l1 - a * a))
        val joint = Pt(rx + ux * a - uy * h * bend, ry + uy * a + ux * h * bend)
        return arrayOf(joint, Pt(rx + ux * d, ry + uy * d))
    }

    private fun rad(deg: Float) = Math.toRadians(deg.toDouble())

    internal fun solve(p: FloatArray): FigurePose {
        val hip = Pt(p[0], p[1])
        val t = rad(p[2])
        val shoulder = Pt(hip.x + SHOULDER * sin(t).toFloat(), hip.y + SHOULDER * cos(t).toFloat())
        val neck = Pt(hip.x + TORSO * sin(t).toFloat(), hip.y + TORSO * cos(t).toFloat())
        val hd = rad(p[3])
        val head = Pt(neck.x + HEAD_D * sin(hd).toFloat(), neck.y + HEAD_D * cos(hd).toFloat())
        val (eN, wN) = ik(shoulder.x, shoulder.y, p[4], p[5], UARM, FARM, ARM_BEND).let { it[0] to it[1] }
        val (eF, wF) = ik(shoulder.x, shoulder.y, p[6], p[7], UARM, FARM, ARM_BEND).let { it[0] to it[1] }
        val (kN, aN) = ik(hip.x, hip.y, p[8], p[9], THIGH, SHIN, LEG_BEND).let { it[0] to it[1] }
        val (kF, aF) = ik(hip.x, hip.y, p[10], p[11], THIGH, SHIN, LEG_BEND).let { it[0] to it[1] }
        fun toe(a: Pt) = Pt(a.x + FOOT, min(FLOOR, a.y + 3f))
        return FigurePose(hip, shoulder, neck, head, eN, wN, eF, wF, kN, aN, toe(aN), kF, aF, toe(aF))
    }
}
