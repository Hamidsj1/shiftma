package ir.shiftapp.core

/** ساعت شروع و پایان یک شیفت (دقیقه از نیمه‌شب). اگر پایان ≤ شروع باشد شیفت از نیمه‌شب می‌گذرد (مثل شبکار). */
data class ShiftTime(val startMin: Int, val endMin: Int) {
    val durationMin: Int get() { val d = endMin - startMin; return if (d <= 0) d + 1440 else d }
    val crossesMidnight: Boolean get() = endMin <= startMin

    companion object {
        fun fmt(min: Int): String = "%02d:%02d".format(min / 60, min % 60)
        /**
         * ساعت را آزادانه می‌خواند؛ ارقام فارسی هم پذیرفته می‌شود. null یعنی نامعتبر.
         * «17:30»، «5:30»، «5.30»، «5 30»، «1730»، «0530»، «530» و فقط ساعت («17» یا «5») همه درست‌اند.
         */
        fun parseClock(text: String): Int? {
            val s = DateText.normalizeDigits(text.trim())
            if (s.isEmpty()) return null
            val h: Int
            val m: Int
            if (s.all { it.isDigit() }) {
                when (s.length) {
                    1, 2 -> { h = s.toInt(); m = 0 }
                    3 -> { h = s.substring(0, 1).toInt(); m = s.substring(1).toInt() }
                    4 -> { h = s.substring(0, 2).toInt(); m = s.substring(2).toInt() }
                    else -> return null
                }
            } else {
                val parts = s.split(Regex("[:.\\s/\u066B\u060C,-]+"))
                if (parts.size != 2 || parts.any { it.isEmpty() || !it.all { c -> c.isDigit() } || it.length > 2 }) return null
                h = parts[0].toInt(); m = parts[1].toInt()
            }
            return if (h in 0..23 && m in 0..59) h * 60 + m else null
        }
    }
}

object PrefKeys {
    const val THEME = "pref_theme"                 // system | light | dark
    fun time(code: Int) = "pref_time_$code"        // "360-840"
    const val REM_ON = "pref_rem_on"
    // کلیدهای قدیمی pref_rem_mode و pref_rem_evening (یادآور «شب قبل») حذف شده‌اند؛ در بکاپ‌های قدیمی نادیده گرفته می‌شوند.
    const val REM_LEAD = "pref_rem_lead"           // دقیقه قبل از شروع شیفت
    const val REM_ALARM = "pref_rem_alarm"         // true = آلارم، false = اعلان
    const val REM_CODES = "pref_rem_codes"         // "1,2,3"
    const val ALARMS = "pref_alarms"               // آلارم‌های تکی (SingleAlarm.encode)
    const val NOTES = "pref_notes"                 // یادداشت‌ها (Note.encode)
    const val PORTAL_EXTERNAL = "pref_portal_external"   // کلید سامانه‌هایی که در مرورگر بیرونی باز شوند: "nicico,nowavaran"
    const val DHIKR = "pref_dhikr"                 // ذکرشمار و قضا (رشتهٔ فشردهٔ DhikrCodec)
    const val SPORT = "pref_sport"                 // حالت بخش ورزش (JSON)
    const val PLANNER = "pref_planner"             // برنامهٔ روزانه (JSON)
    const val READING = "pref_reading"             // حالت بخش مطالعه (JSON)
    const val INCOME = "pref_income"               // دریافتی‌های ماهانه (JSON)
    const val ENGLISH = "pref_english"             // پیشرفت یادگیری انگلیسی (EnglishProgress.encode)
    const val SUB_OUT = "pref_sub_out"             // جانشین شب: دقیقهٔ خروج (قبل از نیمه‌شب)
    const val SUB_IN = "pref_sub_in"               // جانشین شب: دقیقهٔ ورود (بعد از نیمه‌شب)
    const val LEAVE_CAP = "pref_leave_cap"
    const val LEAVE_REST = "pref_leave_rest"       // آیا مرخصیِ روز استراحت هم از سقف کم شود
    const val FRIDAY_OFF = "pref_friday_off"
    const val AUTO_BACKUP = "pref_auto_backup"
    const val AUTO_KEEP = "pref_auto_keep"
    const val ESS_SITES = "pref_ess_sites"         // قدیمی: سایت‌های ضروریِ افزودهٔ کاربر؛ فقط تا اولین ویرایش نوار خوانده می‌شود
    const val AD_FEED = "pref_ad_feed"             // آدرس خوراک تبلیغ محل کار کاربر (از فایل کاشی‌ها)؛ خالی = خوراک عمومی برنامه
    const val AD_PUBLIC = "pref_ad_public"         // کاربر محل کار علاوه بر تبلیغ محلش، تبلیغ عمومی را هم ببیند (از فایل کاشی‌ها)
    const val NOTICE_FEED = "pref_notice_feed"     // آدرس خوراک اعلان‌های محلی محل کار کاربر (از فایل کاشی‌ها)؛ خالی = فقط اعلان عمومی
    const val CONFIG_FEED = "pref_config_feed"     // آدرس config.json محل کاربر: نمایش/پنهان/پولی بودن بخش‌ها (از فایل کاشی‌ها)؛ خالی = همه نمایان و رایگان
    const val NOTICE_PUBLIC = "pref_notice_public" // کنار اعلان محلی، اعلان عمومی برنامه هم برسد (پیش‌فرض بله)
    const val MEAL_FEED = "pref_meal_feed"         // آدرس خوراک برنامه غذایی شرکت (از فایل کاشی‌ها)؛ خالی = دریافت خودکار خاموش
    const val ESS_LIST = "pref_ess_list"           // فهرست کاملِ ویرایش‌شدهٔ نوار «سایت‌های ضروری» (StripSites.encode)؛ خالی = پیش‌فرض
    const val TV_LIST = "pref_tv_list"             // فهرست کاملِ ویرایش‌شدهٔ نوار «تلویزیون»؛ خالی = پیش‌فرض
    const val SITES = "pref_sites"                 // کاشی‌های سامانه‌ها (فایل کاشی‌ها؛ TilesPackFile.encodeSites)
    const val HOME_LAYOUT = "pref_home_layout"     // چیدمان دلخواه صفحهٔ اول (HomeLayout.encode)
    const val LUNAR_SHIFT = "pref_lunar_shift"     // تطبیق تاریخ قمری با رؤیت ایران: -۲ تا +۲ روز
    const val DATE_BAR = "pref_date_bar"           // اعلان همیشگی تاریخ و اطلاعات در نوار اعلان‌های گوشی (پیش‌فرض روشن)

    // امکانات مذهبی (اختیاری؛ پیش‌فرض خاموش)
    const val PRAYER_ON = "pref_prayer_on"
    const val PRAYER_HINT_HIDDEN = "pref_prayer_hint_hidden"   // کارت پیشنهاد فعال‌سازی در صفحهٔ اول پنهان شده
    const val PRAYER_CITY = "pref_prayer_city"                 // شناسهٔ شهر یا "gps"
    const val PRAYER_LAT = "pref_prayer_lat"                   // فقط برای «موقعیت من»
    const val PRAYER_LON = "pref_prayer_lon"
    const val PRAYER_LOCNAME = "pref_prayer_locname"
    const val PRAYER_METHOD = "pref_prayer_method"             // shia | sunni
    const val PRAYER_ALERTS = "pref_prayer_alerts"             // شناسهٔ وقت‌هایی که هشدار دارند: "fajr,dhuhr"
    const val PRAYER_LEAD = "pref_prayer_lead"                 // دقیقه قبل از وقت
    const val PRAYER_ALARM = "pref_prayer_alarm"               // true = آلارم، false = اعلان
    const val PRAYER_SOUND = "pref_prayer_sound"               // نشانی صدای انتخابی؛ خالی = صدای پیش‌فرض گوشی (انتخاب دستی)
    const val PRAYER_QUIET_ON = "pref_prayer_quiet_on"
    const val PRAYER_QUIET = "pref_prayer_quiet"               // "شروع-پایان" (دقیقه از نیمه‌شب)

    // برآورد حقوق و اضافه‌کار (فقط روی همین گوشی)
    const val PAY_BASE = "pref_pay_base"
    const val PAY_DIVISOR = "pref_pay_divisor"
    const val PAY_DUE = "pref_pay_due"
    const val PAY_OT = "pref_pay_ot"
    const val PAY_NIGHT = "pref_pay_night"
    const val PAY_FRIDAY = "pref_pay_friday"
    const val PAY_HOLIDAY = "pref_pay_holiday"
    const val PAY_SHIFT = "pref_pay_shift"
    const val PAY_BENEFITS = "pref_pay_benefits"
    const val PAY_DEDUCT = "pref_pay_deduct"
}

/**
 * تنظیمات برنامه. در جدول settings به‌صورت key/value ذخیره می‌شود (پس در Backup هم هست).
 * مقادیر پیش‌فرض ساعت شیفت‌ها فقط نقطهٔ شروع‌اند؛ قوانین هر شرکت فرق دارد و در تنظیمات قابل‌تغییرند.
 */
data class Prefs(
    val theme: String = "system",
    val shiftTimes: Map<Int, ShiftTime> = DEFAULT_TIMES,
    val reminderEnabled: Boolean = false,
    val reminderLeadMin: Int = 30,
    val reminderAlarm: Boolean = false,
    val reminderCodes: Set<Int> = setOf(1, 2, 3),
    val alarms: List<SingleAlarm> = emptyList(),
    val notes: List<Note> = emptyList(),
    val english: EnglishProgress = EnglishProgress(),
    val externalPortals: Set<String> = emptySet(),
    val sportRaw: String = "",
    val dhikrRaw: String = "",
    val readingRaw: String = "",
    val plannerRaw: String = "",
    val incomeRaw: String = "",
    val subOutMin: Int = SubNight.DEFAULT_OUT,
    val subInMin: Int = SubNight.DEFAULT_IN,
    val leaveCapDays: Int = 26,
    val leaveCountsRestDays: Boolean = false,
    val fridayOff: Boolean = true,
    val autoBackup: Boolean = false,
    val autoBackupKeep: Int = 7,
    val lunarShift: Int = 0,
    val dateBar: Boolean = true,
    val homeLayoutRaw: String = "",
    val sitesRaw: String = "",
    val essSitesRaw: String = "",
    val essListRaw: String = "",
    val mealFeed: String = "",
    val adFeed: String = "",
    val adPublic: Boolean = false,
    val noticeFeed: String = "",
    val configFeed: String = "",
    val noticePublic: Boolean = true,
    val tvListRaw: String = "",
    val prayerOn: Boolean = false,
    val prayerHintHidden: Boolean = false,
    val prayerCity: String = PrayerCities.DEFAULT,
    val prayerLat: Double = 35.6892,
    val prayerLon: Double = 51.3890,
    val prayerLocName: String = "",
    val prayerMethodId: String = PrayerMethod.SHIA.id,
    val prayerAlerts: Set<String> = emptySet(),
    val prayerLeadMin: Int = 0,
    val prayerAlarm: Boolean = true,
    val prayerSound: String = AzanSounds.BUILTIN,      // پیش‌فرض: اذان همراه برنامه (نه صدای آلارم گوشی)
    val prayerQuietOn: Boolean = false,
    val prayerQuietStart: Int = 22 * 60,
    val prayerQuietEnd: Int = 6 * 60,
    val pay: PaySettings = PaySettings()
) {
    val prayerMethod: PrayerMethod get() = PrayerMethod.byId(prayerMethodId)

    /** مکان محاسبهٔ اوقات شرعی: شهر انتخابی یا «موقعیت من»؛ اگر شناسه ناشناخته بود تهران. */
    val prayerPlace: PrayerPlace get() =
        if (prayerCity == PrayerCities.GPS) PrayerPlace(prayerLocName.ifBlank { "موقعیت من" }, prayerLat, prayerLon)
        else (PrayerCities.byId(prayerCity) ?: PrayerCities.tehran()).let { PrayerPlace(it.name, it.lat, it.lon) }

    fun toMap(): Map<String, String> {
        val m = linkedMapOf(
            PrefKeys.THEME to theme,
            PrefKeys.REM_ON to reminderEnabled.toString(), PrefKeys.REM_LEAD to reminderLeadMin.toString(),
            PrefKeys.ALARMS to SingleAlarm.encode(alarms), PrefKeys.NOTES to Note.encode(notes), PrefKeys.ENGLISH to english.encode(), PrefKeys.PORTAL_EXTERNAL to externalPortals.sorted().joinToString(","), PrefKeys.SPORT to sportRaw, PrefKeys.READING to readingRaw, PrefKeys.PLANNER to plannerRaw, PrefKeys.INCOME to incomeRaw, PrefKeys.SUB_OUT to subOutMin.toString(), PrefKeys.SUB_IN to subInMin.toString(),
            PrefKeys.REM_ALARM to reminderAlarm.toString(), PrefKeys.REM_CODES to reminderCodes.sorted().joinToString(","),
            PrefKeys.LEAVE_CAP to leaveCapDays.toString(), PrefKeys.LEAVE_REST to leaveCountsRestDays.toString(),
            PrefKeys.FRIDAY_OFF to fridayOff.toString(), PrefKeys.AUTO_BACKUP to autoBackup.toString(),
            PrefKeys.AUTO_KEEP to autoBackupKeep.toString(), PrefKeys.LUNAR_SHIFT to lunarShift.toString(), PrefKeys.DATE_BAR to dateBar.toString(), PrefKeys.HOME_LAYOUT to homeLayoutRaw, PrefKeys.SITES to sitesRaw, PrefKeys.ESS_SITES to essSitesRaw, PrefKeys.ESS_LIST to essListRaw, PrefKeys.MEAL_FEED to mealFeed, PrefKeys.AD_FEED to adFeed, PrefKeys.NOTICE_FEED to noticeFeed, PrefKeys.CONFIG_FEED to configFeed, PrefKeys.NOTICE_PUBLIC to noticePublic.toString(), PrefKeys.TV_LIST to tvListRaw,
            PrefKeys.PRAYER_ON to prayerOn.toString(), PrefKeys.PRAYER_HINT_HIDDEN to prayerHintHidden.toString(),
            PrefKeys.PRAYER_CITY to prayerCity, PrefKeys.PRAYER_LAT to prayerLat.toString(), PrefKeys.PRAYER_LON to prayerLon.toString(),
            PrefKeys.PRAYER_LOCNAME to prayerLocName, PrefKeys.PRAYER_METHOD to prayerMethodId,
            PrefKeys.PRAYER_ALERTS to prayerAlerts.sorted().joinToString(","), PrefKeys.PRAYER_LEAD to prayerLeadMin.toString(),
            PrefKeys.PRAYER_ALARM to prayerAlarm.toString(), PrefKeys.PRAYER_SOUND to prayerSound,
            PrefKeys.PRAYER_QUIET_ON to prayerQuietOn.toString(), PrefKeys.PRAYER_QUIET to "$prayerQuietStart-$prayerQuietEnd",
            PrefKeys.DHIKR to dhikrRaw,
            PrefKeys.PAY_BASE to pay.baseMonthly.toString(), PrefKeys.PAY_DIVISOR to pay.divisor.toString(), PrefKeys.PAY_DUE to pay.dueHours.toString(),
            PrefKeys.PAY_OT to pay.overtimePct.toString(), PrefKeys.PAY_NIGHT to pay.nightPct.toString(), PrefKeys.PAY_FRIDAY to pay.fridayPct.toString(),
            PrefKeys.PAY_HOLIDAY to pay.holidayPct.toString(), PrefKeys.PAY_SHIFT to pay.shiftPct.toString(),
            PrefKeys.PAY_BENEFITS to pay.benefits.toString(), PrefKeys.PAY_DEDUCT to pay.deductionPct.toString()
        )
        shiftTimes.forEach { (c, t) -> m[PrefKeys.time(c)] = "${t.startMin}-${t.endMin}" }
        return m
    }

    companion object {
        val DEFAULT_TIMES: Map<Int, ShiftTime> = mapOf(
            1 to ShiftTime(7 * 60, 19 * 60), 2 to ShiftTime(14 * 60, 22 * 60), 3 to ShiftTime(19 * 60, 7 * 60)
        )

        private fun parseTime(s: String, allowEqual: Boolean = false): ShiftTime? {
            val p = s.split('-')
            if (p.size != 2) return null
            val a = p[0].trim().toIntOrNull() ?: return null
            val b = p[1].trim().toIntOrNull() ?: return null
            return if (a in 0..1439 && b in 0..1439 && (a != b || allowEqual)) ShiftTime(a, b) else null
        }

        /** مقدار خراب یا ناموجود => پیش‌فرض (برنامه هیچ‌وقت به‌خاطر یک تنظیم خراب از کار نمی‌افتد). */
        fun from(m: Map<String, String>): Prefs {
            val d = Prefs()
            val times = d.shiftTimes.toMutableMap()
            for (c in 1..6) m[PrefKeys.time(c)]?.let { parseTime(it, c == 5) }?.let { times[c] = it }
            fun bool(k: String, def: Boolean) = m[k]?.toBooleanStrictOrNull() ?: def
            fun int(k: String, def: Int, r: IntRange) = m[k]?.toIntOrNull()?.takeIf { it in r } ?: def
            fun lng(k: String, def: Long, r: LongRange) = m[k]?.toLongOrNull()?.takeIf { it in r } ?: def
            fun dbl(k: String, def: Double, r: ClosedFloatingPointRange<Double>) = m[k]?.toDoubleOrNull()?.takeIf { it.isFinite() && it in r } ?: def
            val quiet = m[PrefKeys.PRAYER_QUIET]?.split('-')?.takeIf { it.size == 2 }?.let { p ->
                val a = p[0].trim().toIntOrNull(); val b = p[1].trim().toIntOrNull()
                if (a != null && b != null && a in 0..1439 && b in 0..1439) a to b else null
            }
            return Prefs(
                theme = m[PrefKeys.THEME]?.takeIf { it in listOf("system", "light", "dark") } ?: d.theme,
                shiftTimes = times,
                reminderEnabled = bool(PrefKeys.REM_ON, d.reminderEnabled),
                reminderLeadMin = int(PrefKeys.REM_LEAD, d.reminderLeadMin, 0..72 * 60),
                reminderAlarm = bool(PrefKeys.REM_ALARM, d.reminderAlarm),
                alarms = SingleAlarm.decode(m[PrefKeys.ALARMS]),
                notes = Note.decode(m[PrefKeys.NOTES]),
                english = EnglishProgress.decode(m[PrefKeys.ENGLISH]),
                externalPortals = m[PrefKeys.PORTAL_EXTERNAL]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet(),
                sportRaw = m[PrefKeys.SPORT].orEmpty(),
                dhikrRaw = m[PrefKeys.DHIKR]?.take(60_000).orEmpty(),
                readingRaw = m[PrefKeys.READING].orEmpty(),
                plannerRaw = m[PrefKeys.PLANNER]?.take(400_000).orEmpty(),
                incomeRaw = m[PrefKeys.INCOME].orEmpty(),
                subOutMin = int(PrefKeys.SUB_OUT, d.subOutMin, 0..1439),
                subInMin = int(PrefKeys.SUB_IN, d.subInMin, 0..1439),
                reminderCodes = m[PrefKeys.REM_CODES]?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.filter { it >= 1 }?.toSet() ?: d.reminderCodes,
                leaveCapDays = int(PrefKeys.LEAVE_CAP, d.leaveCapDays, 0..366),
                leaveCountsRestDays = bool(PrefKeys.LEAVE_REST, d.leaveCountsRestDays),
                fridayOff = bool(PrefKeys.FRIDAY_OFF, d.fridayOff),
                autoBackup = bool(PrefKeys.AUTO_BACKUP, d.autoBackup),
                autoBackupKeep = int(PrefKeys.AUTO_KEEP, d.autoBackupKeep, 1..60),
                lunarShift = int(PrefKeys.LUNAR_SHIFT, d.lunarShift, -2..2),
                dateBar = bool(PrefKeys.DATE_BAR, d.dateBar),
                homeLayoutRaw = m[PrefKeys.HOME_LAYOUT]?.take(2000).orEmpty(),
                sitesRaw = m[PrefKeys.SITES]?.takeIf { it.length <= 1_800_000 }.orEmpty(),
                essSitesRaw = m[PrefKeys.ESS_SITES]?.takeIf { it.length <= 200_000 }.orEmpty(),
                adFeed = m[PrefKeys.AD_FEED]?.takeIf { MealFeed.isSafeUrl(it) }.orEmpty(),
                adPublic = bool(PrefKeys.AD_PUBLIC, d.adPublic),
                noticeFeed = m[PrefKeys.NOTICE_FEED]?.takeIf { MealFeed.isSafeUrl(it) }.orEmpty(),
                configFeed = m[PrefKeys.CONFIG_FEED]?.takeIf { MealFeed.isSafeUrl(it) }.orEmpty(),
                noticePublic = bool(PrefKeys.NOTICE_PUBLIC, d.noticePublic),
                mealFeed = m[PrefKeys.MEAL_FEED]?.takeIf { MealFeed.isSafeUrl(it) }.orEmpty(),
                essListRaw = m[PrefKeys.ESS_LIST]?.takeIf { it.length <= 200_000 }.orEmpty(),
                tvListRaw = m[PrefKeys.TV_LIST]?.takeIf { it.length <= 200_000 }.orEmpty(),
                prayerOn = bool(PrefKeys.PRAYER_ON, d.prayerOn),
                prayerHintHidden = bool(PrefKeys.PRAYER_HINT_HIDDEN, d.prayerHintHidden),
                prayerCity = m[PrefKeys.PRAYER_CITY]?.takeIf { it == PrayerCities.GPS || PrayerCities.byId(it) != null } ?: d.prayerCity,
                prayerLat = m[PrefKeys.PRAYER_LAT]?.toDoubleOrNull()?.takeIf { it in -90.0..90.0 } ?: d.prayerLat,
                prayerLon = m[PrefKeys.PRAYER_LON]?.toDoubleOrNull()?.takeIf { it in -180.0..180.0 } ?: d.prayerLon,
                prayerLocName = m[PrefKeys.PRAYER_LOCNAME]?.take(60) ?: d.prayerLocName,
                prayerMethodId = m[PrefKeys.PRAYER_METHOD]?.takeIf { id -> PrayerMethod.ALL.any { it.id == id } } ?: d.prayerMethodId,
                prayerAlerts = m[PrefKeys.PRAYER_ALERTS]?.split(',')?.map { it.trim() }?.filter { Prayer.byId(it) != null }?.toSet() ?: d.prayerAlerts,
                prayerLeadMin = int(PrefKeys.PRAYER_LEAD, d.prayerLeadMin, 0..120),
                prayerAlarm = bool(PrefKeys.PRAYER_ALARM, d.prayerAlarm),
                prayerSound = m[PrefKeys.PRAYER_SOUND]?.take(500) ?: d.prayerSound,
                prayerQuietOn = bool(PrefKeys.PRAYER_QUIET_ON, d.prayerQuietOn),
                prayerQuietStart = quiet?.first ?: d.prayerQuietStart,
                prayerQuietEnd = quiet?.second ?: d.prayerQuietEnd,
                pay = PaySettings(
                    baseMonthly = lng(PrefKeys.PAY_BASE, d.pay.baseMonthly, 0L..100_000_000_000L),
                    divisor = dbl(PrefKeys.PAY_DIVISOR, d.pay.divisor, 1.0..1000.0),
                    dueHours = dbl(PrefKeys.PAY_DUE, d.pay.dueHours, 0.0..744.0),
                    overtimePct = dbl(PrefKeys.PAY_OT, d.pay.overtimePct, 0.0..500.0),
                    nightPct = dbl(PrefKeys.PAY_NIGHT, d.pay.nightPct, 0.0..500.0),
                    fridayPct = dbl(PrefKeys.PAY_FRIDAY, d.pay.fridayPct, 0.0..500.0),
                    holidayPct = dbl(PrefKeys.PAY_HOLIDAY, d.pay.holidayPct, 0.0..500.0),
                    shiftPct = dbl(PrefKeys.PAY_SHIFT, d.pay.shiftPct, 0.0..500.0),
                    benefits = lng(PrefKeys.PAY_BENEFITS, d.pay.benefits, 0L..100_000_000_000L),
                    deductionPct = dbl(PrefKeys.PAY_DEDUCT, d.pay.deductionPct, 0.0..100.0)
                )
            )
        }
    }
}
