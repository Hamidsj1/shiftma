package ir.shiftapp.core

/** مکان محاسبهٔ اوقات شرعی. */
data class PrayerPlace(val name: String, val lat: Double, val lon: Double)

data class City(val id: String, val name: String, val lat: Double, val lon: Double)

/** شهرهای داخلی؛ محاسبه کاملاً آفلاین است و فقط به مختصات نیاز دارد. */
object PrayerCities {
    const val GPS = "gps"
    const val DEFAULT = "tehran"

    val ALL: List<City> = listOf(
        City("tehran", "تهران", 35.6892, 51.3890), City("karaj", "کرج", 35.8400, 50.9391),
        City("mashhad", "مشهد", 36.2605, 59.6168), City("isfahan", "اصفهان", 32.6546, 51.6680),
        City("shiraz", "شیراز", 29.5918, 52.5837), City("tabriz", "تبریز", 38.0962, 46.2738),
        City("qom", "قم", 34.6399, 50.8759), City("ahvaz", "اهواز", 31.3183, 48.6706),
        City("kermanshah", "کرمانشاه", 34.3277, 47.0778), City("urmia", "ارومیه", 37.5527, 45.0760),
        City("rasht", "رشت", 37.2808, 49.5832), City("zahedan", "زاهدان", 29.4963, 60.8629),
        City("kerman", "کرمان", 30.2839, 57.0834), City("hamadan", "همدان", 34.7983, 48.5148),
        City("yazd", "یزد", 31.8974, 54.3569), City("ardabil", "اردبیل", 38.2498, 48.2933),
        City("bandarabbas", "بندرعباس", 27.1865, 56.2808), City("arak", "اراک", 34.0954, 49.6892),
        City("zanjan", "زنجان", 36.6736, 48.4787), City("sanandaj", "سنندج", 35.3219, 46.9862),
        City("qazvin", "قزوین", 36.2688, 50.0041), City("khorramabad", "خرم‌آباد", 33.4878, 48.3558),
        City("gorgan", "گرگان", 36.8427, 54.4439), City("sari", "ساری", 36.5633, 53.0601),
        City("bojnurd", "بجنورد", 37.4747, 57.3290), City("birjand", "بیرجند", 32.8649, 59.2262),
        City("ilam", "ایلام", 33.6374, 46.4227), City("bushehr", "بوشهر", 28.9234, 50.8203),
        City("semnan", "سمنان", 35.5769, 53.3920), City("shahrekord", "شهرکرد", 32.3256, 50.8644),
        City("yasuj", "یاسوج", 30.6682, 51.5880), City("kashan", "کاشان", 33.9850, 51.4100),
        City("dezful", "دزفول", 32.3830, 48.4000), City("abadan", "آبادان", 30.3392, 48.3043),
        City("borujerd", "بروجرد", 33.8974, 48.7506), City("rafsanjan", "رفسنجان", 30.4067, 55.9939),
        City("sirjan", "سیرجان", 29.4500, 55.6810), City("jiroft", "جیرفت", 28.6768, 57.7370),
        City("bam", "بم", 29.1060, 58.3570), City("shahrebabak", "شهربابک", 30.1167, 55.1189),
        City("saveh", "ساوه", 35.0213, 50.3566), City("varamin", "ورامین", 35.3256, 51.6472),
        City("neyshabur", "نیشابور", 36.2133, 58.7958), City("sabzevar", "سبزوار", 36.2126, 57.6819),
        City("torbat", "تربت حیدریه", 35.2740, 59.2190), City("maragheh", "مراغه", 37.3889, 46.2387),
        City("marand", "مرند", 38.4329, 45.7744), City("ahar", "اهر", 38.4774, 47.0620),
        City("varzaqan", "ورزقان", 38.5100, 46.6560), City("jolfa", "جلفا", 38.9400, 45.6300),
        City("khoy", "خوی", 38.5503, 44.9521), City("mahabad", "مهاباد", 36.7631, 45.7222),
        City("bukan", "بوکان", 36.5210, 46.2090), City("saqqez", "سقز", 36.2499, 46.2669),
        City("amol", "آمل", 36.4696, 52.3507), City("babol", "بابل", 36.5512, 52.6788),
        City("qaemshahr", "قائم‌شهر", 36.4631, 52.8601), City("lahijan", "لاهیجان", 37.2072, 50.0034),
        City("anzali", "بندر انزلی", 37.4728, 49.4627), City("chalus", "چالوس", 36.6550, 51.4200),
        City("mahshahr", "ماهشهر", 30.5590, 49.1980), City("masjedsoleyman", "مسجدسلیمان", 31.9360, 49.3040),
        City("behbahan", "بهبهان", 30.5959, 50.2417), City("gachsaran", "گچساران", 30.3600, 50.8000),
        City("kazerun", "کازرون", 29.6195, 51.6540), City("marvdasht", "مرودشت", 29.8742, 52.8025),
        City("jahrom", "جهرم", 28.5000, 53.5600), City("lar", "لار", 27.6800, 54.3400),
        City("qeshm", "قشم", 26.9580, 56.2690), City("kish", "کیش", 26.5580, 53.9800),
        City("iranshahr", "ایرانشهر", 27.2025, 60.6848), City("chabahar", "چابهار", 25.2919, 60.6430),
        City("najafabad", "نجف‌آباد", 32.6344, 51.3668), City("golpayegan", "گلپایگان", 33.4540, 50.2880),
        City("malayer", "ملایر", 34.2969, 48.8237), City("nahavand", "نهاوند", 34.1886, 48.3720),
        City("gonbad", "گنبد کاووس", 37.2500, 55.1672), City("shahroud", "شاهرود", 36.4180, 54.9760),
        City("damghan", "دامغان", 36.1680, 54.3480)
    ).sortedBy { it.name }

    fun byId(id: String?): City? = ALL.firstOrNull { it.id == id }
    fun tehran(): City = byId(DEFAULT)!!
}
