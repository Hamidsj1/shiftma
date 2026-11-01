package ir.shiftapp.sport

import org.json.JSONArray
import org.json.JSONObject

/** مشخصات بدنی برای محاسبهٔ BMI و کالری. sex: ۰ مرد، ۱ زن. */
data class SportProfile(
    val sex: Int = 0, val age: Int = 30, val heightCm: Int = 175, val weightKg: Double = 75.0,
    val activity: Int = 1, val goal: Int = 1
)

data class SetLog(val reps: Int, val kg: Double)
data class ExerciseLog(val exerciseId: String, val sets: List<SetLog> = emptyList())

/** تمرین یک روز؛ dayEpoch همان LocalDate.toEpochDay() است. */
data class WorkoutLog(val dayEpoch: Int, val items: List<ExerciseLog> = emptyList())
data class WeightLog(val dayEpoch: Int, val kg: Double)
data class MealLog(val dayEpoch: Int, val foodId: String, val grams: Int)

data class SportState(
    val profile: SportProfile = SportProfile(),
    val profileSet: Boolean = false,
    val workouts: List<WorkoutLog> = emptyList(),
    val weights: List<WeightLog> = emptyList(),
    val meals: List<MealLog> = emptyList(),
    val water: Map<Int, Int> = emptyMap(),          // روز → تعداد لیوان (۲۵۰ میلی‌لیتر)
    val activeProgram: String? = null,
    val programDay: Int = 0                         // اندیس روز بعدی برنامهٔ فعال
) {
    fun workoutOn(day: Int): WorkoutLog = workouts.firstOrNull { it.dayEpoch == day } ?: WorkoutLog(day)
    fun withWorkout(w: WorkoutLog): SportState {
        val rest = workouts.filter { it.dayEpoch != w.dayEpoch }
        return copy(workouts = if (w.items.isEmpty()) rest else (rest + w).sortedBy { it.dayEpoch })
    }
    fun mealsOn(day: Int): List<MealLog> = meals.filter { it.dayEpoch == day }
    fun waterOn(day: Int): Int = water[day] ?: 0
}

/** تبدیل حالت ورزش به رشتهٔ JSON برای ذخیره در تنظیمات برنامه (همراه فایل پشتیبان می‌آید). */
object SportCodec {
    fun encodeStr(d: SportState): String = encode(d).toString()
    fun decodeStr(s: String): SportState = try { decode(JSONObject(s)) } catch (e: Exception) { SportState() }

    private const val KEEP_DAYS = 400

    fun encode(d: SportState): JSONObject = JSONObject().apply {
        put("profile", JSONObject().apply {
            put("sex", d.profile.sex); put("age", d.profile.age); put("h", d.profile.heightCm)
            put("w", d.profile.weightKg); put("act", d.profile.activity); put("goal", d.profile.goal)
        })
        put("profileSet", d.profileSet)
        put("program", d.activeProgram ?: JSONObject.NULL)
        put("pd", d.programDay)
        put("workouts", JSONArray().apply {
            d.workouts.forEach { w ->
                put(JSONObject().apply {
                    put("d", w.dayEpoch)
                    put("items", JSONArray().apply {
                        w.items.forEach { it0 ->
                            put(JSONObject().apply {
                                put("e", it0.exerciseId)
                                put("s", JSONArray().apply { it0.sets.forEach { s -> put(JSONArray().put(s.reps).put(s.kg)) } })
                            })
                        }
                    })
                })
            }
        })
        put("weights", JSONArray().apply { d.weights.forEach { put(JSONArray().put(it.dayEpoch).put(it.kg)) } })
        put("meals", JSONArray().apply { d.meals.forEach { put(JSONArray().put(it.dayEpoch).put(it.foodId).put(it.grams)) } })
        put("water", JSONArray().apply { d.water.forEach { (k, v) -> put(JSONArray().put(k).put(v)) } })
    }

    fun decode(o: JSONObject): SportState {
        val p = o.optJSONObject("profile")
        val profile = if (p == null) SportProfile() else SportProfile(
            sex = p.optInt("sex", 0), age = p.optInt("age", 30), heightCm = p.optInt("h", 175),
            weightKg = p.optDouble("w", 75.0), activity = p.optInt("act", 1), goal = p.optInt("goal", 1)
        )
        val workouts = ArrayList<WorkoutLog>()
        o.optJSONArray("workouts")?.let { a ->
            for (i in 0 until a.length()) {
                val w = a.optJSONObject(i) ?: continue
                val items = ArrayList<ExerciseLog>()
                w.optJSONArray("items")?.let { ia ->
                    for (j in 0 until ia.length()) {
                        val e = ia.optJSONObject(j) ?: continue
                        val sets = ArrayList<SetLog>()
                        e.optJSONArray("s")?.let { sa ->
                            for (k in 0 until sa.length()) {
                                val s = sa.optJSONArray(k) ?: continue
                                sets.add(SetLog(s.optInt(0), s.optDouble(1)))
                            }
                        }
                        items.add(ExerciseLog(e.optString("e"), sets))
                    }
                }
                workouts.add(WorkoutLog(w.optInt("d"), items))
            }
        }
        val weights = ArrayList<WeightLog>()
        o.optJSONArray("weights")?.let { a -> for (i in 0 until a.length()) a.optJSONArray(i)?.let { weights.add(WeightLog(it.optInt(0), it.optDouble(1))) } }
        val meals = ArrayList<MealLog>()
        o.optJSONArray("meals")?.let { a -> for (i in 0 until a.length()) a.optJSONArray(i)?.let { meals.add(MealLog(it.optInt(0), it.optString(1), it.optInt(2))) } }
        val water = HashMap<Int, Int>()
        o.optJSONArray("water")?.let { a -> for (i in 0 until a.length()) a.optJSONArray(i)?.let { water[it.optInt(0)] = it.optInt(1) } }
        return SportState(
            profile, o.optBoolean("profileSet", false), workouts.sortedBy { it.dayEpoch }, weights.sortedBy { it.dayEpoch },
            meals, water, if (o.isNull("program")) null else o.optString("program"),
            programDay = o.optInt("pd", 0).coerceAtLeast(0)
        )
    }

    /** داده‌های خیلی قدیمی را حذف می‌کند تا فایل بی‌انتها بزرگ نشود. */
    fun trimmed(d: SportState, today: Int): SportState {
        val min = today - KEEP_DAYS
        return d.copy(
            meals = d.meals.filter { it.dayEpoch >= min },
            water = d.water.filterKeys { it >= min }
        )
    }
}
