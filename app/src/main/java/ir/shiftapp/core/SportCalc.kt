package ir.shiftapp.core

import kotlin.math.pow
import kotlin.math.roundToInt

/** محاسبات ورزش و تغذیه (بدون وابستگی به اندروید؛ قابل تست). همهٔ فرمول‌ها برآورد هستند، نه تجویز پزشکی. */
object SportCalc {
    /** ضریب فعالیت: کم‌تحرک، سبک، متوسط، زیاد، خیلی زیاد */
    val ACTIVITY_NAMES = listOf("کم‌تحرک", "سبک (۱ تا ۳ روز)", "متوسط (۳ تا ۵ روز)", "زیاد (۶ تا ۷ روز)", "خیلی زیاد")
    val ACTIVITY_FACTORS = listOf(1.2, 1.375, 1.55, 1.725, 1.9)
    val GOAL_NAMES = listOf("کاهش وزن", "حفظ وزن", "افزایش حجم")

    data class Macros(val protein: Int, val fat: Int, val carb: Int)

    fun bmi(kg: Double, cm: Double): Double = if (cm <= 0.0) 0.0 else kg / (cm / 100.0).pow(2)

    fun bmiLabel(b: Double): String = when {
        b <= 0.0 -> "—"
        b < 18.5 -> "کم‌وزن"
        b < 25.0 -> "طبیعی"
        b < 30.0 -> "اضافه‌وزن"
        else -> "چاقی"
    }

    /** معادلهٔ میفلین-سن‌جئور. sex: ۰ مرد، ۱ زن. */
    fun bmr(sex: Int, age: Int, cm: Double, kg: Double): Double =
        10.0 * kg + 6.25 * cm - 5.0 * age + if (sex == 0) 5.0 else -161.0

    fun tdee(bmr: Double, activity: Int): Double = bmr * ACTIVITY_FACTORS[activity.coerceIn(0, ACTIVITY_FACTORS.lastIndex)]

    /** کالری هدف روزانه؛ برای کاهش وزن حداقل ایمن (زن ۱۲۰۰، مرد ۱۵۰۰) رعایت می‌شود. */
    fun targetCalories(tdee: Double, goal: Int, sex: Int): Int {
        val raw = when (goal) { 0 -> tdee - 500.0; 2 -> tdee + 300.0; else -> tdee }
        val floor = if (sex == 0) 1500.0 else 1200.0
        return (if (goal == 0) maxOf(raw, floor) else raw).roundToInt()
    }

    fun macros(kcal: Int, kg: Double, goal: Int): Macros {
        val perKg = when (goal) { 0 -> 2.0; 2 -> 1.8; else -> 1.6 }
        val protein = (kg * perKg).roundToInt()
        val fat = (kcal * 0.25 / 9.0).roundToInt()
        val carb = maxOf(0.0, (kcal - protein * 4.0 - fat * 9.0) / 4.0).roundToInt()
        return Macros(protein, fat, carb)
    }

    /** بازهٔ وزن سالم (BMI از ۱۸٫۵ تا ۲۴٫۹) به کیلوگرم. */
    fun healthyRange(cm: Double): Pair<Int, Int> {
        val m2 = (cm / 100.0).pow(2)
        return (18.5 * m2).roundToInt() to (24.9 * m2).roundToInt()
    }

    /** آب پیشنهادی روزانه به میلی‌لیتر (حدود ۳۳ میلی‌لیتر به‌ازای هر کیلوگرم). */
    fun waterMl(kg: Double): Int = (kg * 33.0).roundToInt()

    /** حداکثر یک‌تکرار تخمینی (فرمول اپلی). */
    fun oneRepMax(kg: Double, reps: Int): Double = if (reps <= 1) kg else kg * (1.0 + reps / 30.0)

    fun volume(kg: Double, reps: Int): Double = kg * reps
}
