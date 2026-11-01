package ir.shiftapp.core

/** یک حرکت در تمرین راهنما: تعداد ست و مقدار هر ست (تکرار یا ثانیه). seconds > 0 یعنی حرکت زمانی. */
data class PlanStep(
    val exerciseId: String, val name: String, val sets: Int,
    val reps: Int, val seconds: Int, val perSide: Boolean, val restSec: Int
) {
    val timed: Boolean get() = seconds > 0
}

enum class PhaseKind { READY, WORK, REST }

/** یک مرحله از تمرین؛ duration صفر یعنی دستی (کاربر «انجام شد» را می‌زند). */
data class Phase(val kind: PhaseKind, val step: Int, val set: Int, val duration: Int)

object WorkoutPlan {
    const val UNIT_REPS = 0
    const val UNIT_SEC = 1
    const val UNIT_MIN = 2

    const val READY_SECONDS = 10
    private const val MIN_TRANSITION_REST = 20

    /** واحد ثبت یک حرکت از روی متن پیشنهادی آن: تکرار، ثانیه یا دقیقه. */
    fun unitOf(e: Exercise?): Int = when {
        e == null -> UNIT_REPS
        e.sets.contains("دقیقه") -> UNIT_MIN
        e.sets.contains("ثانیه") -> UNIT_SEC
        else -> UNIT_REPS
    }

    data class Spec(val sets: Int, val reps: Int, val seconds: Int, val perSide: Boolean)

    private val ROUNDS = Regex("""(\d+)(?:\s*تا\s*\d+)?\s*دور""")
    private val SETS = Regex("""(\d+)\s*[×xX]\s*(\d+)(?:\s*تا\s*\d+)?\s*(ثانیه|دقیقه)?""")
    private val MINUTES = Regex("""(\d+)(?:\s*تا\s*\d+)?\s*دقیقه""")

    /** متن‌هایی مثل «۳×۱۲» «۳×۸ تا ۱۲» «۳×۳۰ ثانیه» «۳۰ تا ۴۵ دقیقه» «۸ دور» را به ست و مقدار تبدیل می‌کند (کران پایین). */
    fun parse(spec: String): Spec {
        val s = Fa.latin(spec)
        val side = s.contains("هر پا") || s.contains("هر دست")
        val rounds = ROUNDS.find(s)
        if (rounds != null) return Spec(rounds.groupValues[1].toInt().coerceIn(1, 30), 0, 30, false)
        val sets = SETS.find(s)
        if (sets != null) {
            val n = sets.groupValues[1].toInt().coerceIn(1, 10)
            val v = sets.groupValues[2].toInt().coerceAtLeast(1)
            return when (sets.groupValues[3]) {
                "ثانیه" -> Spec(n, 0, v.coerceIn(5, 600), side)
                "دقیقه" -> Spec(n, 0, (v * 60).coerceIn(60, 7200), side)
                else -> Spec(n, v.coerceAtMost(100), 0, side)
            }
        }
        val min = MINUTES.find(s)
        if (min != null) return Spec(1, 0, min.groupValues[1].toInt().coerceIn(1, 120) * 60, false)
        return Spec(3, 10, 0, side)
    }

    /** استراحت بین ستها بر اساس نوع حرکت. */
    fun restFor(e: Exercise?, spec: Spec): Int = when {
        spec.sets <= 1 -> 0
        e == null -> 60
        spec.seconds > 0 && e.muscle == 6 -> 30
        e.equipment == 2 -> 90
        e.equipment == 1 -> 60
        else -> 45
    }

    fun step(id: String, spec: String): PlanStep? {
        val e = SportData.exercise(id) ?: return null
        val p = parse(spec)
        return PlanStep(id, e.name, p.sets, p.reps, p.seconds, p.perSide, restFor(e, p))
    }

    fun fromDay(day: SportData.ProgramDay): List<PlanStep> =
        day.items.mapNotNull { (id, spec) -> step(id, spec) }

    fun fromExercises(ids: List<String>): List<PlanStep> =
        ids.distinct().mapNotNull { id -> SportData.exercise(id)?.let { step(id, it.sets) } }

    /** ترتیب مرحله‌ها: آماده‌باش، سپس برای هر ست «کار» و «استراحت» (به‌جز آخرین ست). */
    fun phases(steps: List<PlanStep>): List<Phase> {
        val out = ArrayList<Phase>()
        if (steps.isEmpty()) return out
        out.add(Phase(PhaseKind.READY, 0, 0, READY_SECONDS))
        steps.forEachIndexed { i, st ->
            for (s in 0 until st.sets) {
                out.add(Phase(PhaseKind.WORK, i, s, if (st.timed) st.seconds else 0))
                val last = i == steps.lastIndex && s == st.sets - 1
                if (!last) {
                    val rest = if (s == st.sets - 1) maxOf(st.restSec, MIN_TRANSITION_REST) else st.restSec
                    out.add(Phase(PhaseKind.REST, i, s, rest))
                }
            }
        }
        return out
    }

    /** برآورد کل زمان تمرین به دقیقه (هر تکرار حدود ۴ ثانیه). */
    fun estimateMinutes(steps: List<PlanStep>): Int {
        var sec = READY_SECONDS
        for (p in phases(steps)) {
            sec += when (p.kind) {
                PhaseKind.READY -> 0
                PhaseKind.REST -> p.duration
                PhaseKind.WORK -> if (p.duration > 0) p.duration else steps[p.step].reps * 4 + 10
            }
        }
        return ((sec + 59) / 60).coerceAtLeast(1)
    }

    /** مقدار قابل ثبت برای یک ست: تکرار، یا ثانیه/دقیقه برای حرکت زمانی. */
    fun logValue(step: PlanStep, e: Exercise?, elapsedSec: Int): Int =
        if (!step.timed) step.reps
        else if (unitOf(e) == UNIT_MIN) (elapsedSec / 60).coerceAtLeast(1)
        else elapsedSec.coerceAtLeast(1)
}
