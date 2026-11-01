package ir.shiftapp.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SportCalcTest {
    @Test fun bmi() {
        assertEquals(24.49, SportCalc.bmi(75.0, 175.0), 0.01)
        assertEquals("طبیعی", SportCalc.bmiLabel(24.49))
        assertEquals("اضافه‌وزن", SportCalc.bmiLabel(27.0))
        assertEquals("کم‌وزن", SportCalc.bmiLabel(17.0))
    }

    @Test fun bmrMaleFemale() {
        // مرد ۳۰ ساله، ۱۷۵ سانتی‌متر، ۷۵ کیلو: 750 + 1093.75 - 150 + 5 = 1698.75
        assertEquals(1698.75, SportCalc.bmr(0, 30, 175.0, 75.0), 0.001)
        // زن با همان مشخصات: 750 + 1093.75 - 150 - 161 = 1532.75
        assertEquals(1532.75, SportCalc.bmr(1, 30, 175.0, 75.0), 0.001)
    }

    @Test fun targetCaloriesHasSafeFloor() {
        assertEquals(1500, SportCalc.targetCalories(1800.0, 0, 0))
        assertEquals(1200, SportCalc.targetCalories(1500.0, 0, 1))
        assertEquals(2300, SportCalc.targetCalories(2000.0, 2, 0))
        assertEquals(2000, SportCalc.targetCalories(2000.0, 1, 0))
    }

    @Test fun macrosAreConsistent() {
        val m = SportCalc.macros(2500, 75.0, 2)
        assertEquals(135, m.protein)
        assertTrue(m.carb > 0)
        val kcal = m.protein * 4 + m.fat * 9 + m.carb * 4
        assertTrue(kotlin.math.abs(kcal - 2500) <= 12)
    }

    @Test fun oneRepMaxAndWater() {
        assertEquals(100.0, SportCalc.oneRepMax(100.0, 1), 0.001)
        assertEquals(133.33, SportCalc.oneRepMax(100.0, 10), 0.01)
        assertEquals(2475, SportCalc.waterMl(75.0))
    }
}
