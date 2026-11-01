package ir.shiftapp.core

/** یک سایت در فهرست کامل «سایت‌های ضروری». */
data class SiteItem(val title: String, val subtitle: String, val url: String)

/** فهرست کامل سایت‌های ضروری (صفحهٔ «همه») و پیش‌فرضِ نوار «سایت‌های ضروری» صفحهٔ اول. */
object SiteCatalog {
    val GROUPS: List<Pair<String, List<SiteItem>>> = listOf(
        "خدمات دولتی و اداری" to listOf(
            SiteItem("سامانهٔ ثنا", "ابلاغ الکترونیک قضایی", "https://sana.adliran.ir"),
            SiteItem("تأمین اجتماعی", "خدمات بیمه و بازنشستگی", "https://www.tamin.ir"),
            SiteItem("بیمه سلامت ایران", "خدمات بیمهٔ سلامت", "https://ihio.gov.ir"),
            SiteItem("سازمان امور مالیاتی", "خدمات مالیاتی", "https://tax.gov.ir"),
            SiteItem("سازمان ثبت احوال", "خدمات ثبت احوال", "https://www.sabteahval.ir"),
            SiteItem("پلیس", "خدمات انتظامی", "https://www.police.ir")
        ),
        "مسیر، ترافیک و هوا" to listOf(
            SiteItem("نشان", "نقشه و مسیریابی", "https://neshan.org"),
            SiteItem("بلد", "نقشه و مسیریابی", "https://balad.ir"),
            SiteItem("هواشناسی", "پیش‌بینی وضع هوا", "https://www.irimo.ir"),
            SiteItem("راهداری", "وضعیت جاده‌ها", "https://rmto.ir")
        ),
        "اخبار" to listOf(
            SiteItem("ایرنا", "خبرگزاری جمهوری اسلامی", "https://www.irna.ir"),
            SiteItem("ایسنا", "خبرگزاری دانشجویان", "https://www.isna.ir"),
            SiteItem("مهر", "خبرگزاری مهر", "https://www.mehrnews.com"),
            SiteItem("همشهری آنلاین", "اخبار و گزارش", "https://www.hamshahrionline.ir"),
            SiteItem("ورزش ۳", "اخبار ورزشی", "https://www.varzesh3.com")
        ),
        "عمومی و ابزار" to listOf(
            SiteItem("نرخ ارز و طلا", "قیمت لحظه‌ای بازار", "https://www.tgju.org"),
            SiteItem("تایم", "تقویم و مناسبت‌ها", "https://www.time.ir"),
            SiteItem("ویکی‌پدیا فارسی", "دانشنامهٔ آزاد", "https://fa.wikipedia.org"),
            SiteItem("لغت‌نامهٔ دهخدا", "معنی واژه‌های فارسی", "https://dehkhoda.ut.ac.ir"),
            SiteItem("ترجمهٔ گوگل", "ترجمهٔ متن و صفحه", "https://translate.google.com"),
            SiteItem("دیکشنری کمبریج", "انگلیسی به انگلیسی", "https://dictionary.cambridge.org"),
            SiteItem("دیجی‌کالا", "خرید اینترنتی", "https://www.digikala.com"),
            SiteItem("دیوار", "نیازمندی‌ها", "https://divar.ir")
        )
    )

    /** همهٔ سایت‌ها به‌صورت تخت، به همان ترتیب گروه‌ها. */
    val ALL: List<SiteItem> get() = GROUPS.flatMap { it.second }
}
