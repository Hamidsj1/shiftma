# باز کردن و بیلد در Android Studio

همین پوشه یک پروژهٔ کامل Gradle است؛ لازم نیست چیزی تغییر کند.

## پیش‌نیاز
- Android Studio **Koala (2024.1.1) یا جدیدتر**. پلاگین Gradle این پروژه ۸٫۵٫۲ است و نسخهٔ قدیمی‌تر پیام «incompatible» می‌دهد.
- JDK ۱۷ (JBR داخل خود Android Studio کافی است: Settings ← Build Tools ← Gradle ← Gradle JDK = «jbr-17» یا بالاتر).
- Android SDK ۳۴ (اولین Sync پیشنهاد نصبش را می‌دهد).

## مراحل
1. فایل zip را باز کن، سپس Android Studio ← **Open** و خود پوشهٔ `ShiftMa` (که `settings.gradle.kts` داخلش است) را انتخاب کن.
2. صبر کن Gradle Sync تمام شود. بار اول Gradle ۸٫۷ و کتابخانه‌ها دانلود می‌شوند.
3. دکمهٔ **Run** (یا Build ← Build APK(s)) را بزن. خروجی: `app/build/outputs/apk/debug/app-debug.apk`.

## نکته‌ها
- فایل `gradle-wrapper.jar` و `gradlew` در پروژه نیست؛ Android Studio خودش Gradle را از روی `gradle/wrapper/gradle-wrapper.properties` می‌گیرد و نیازی به آن‌ها ندارد. برای بیلد از ترمینال، یک بار در Android Studio Sync کن یا `gradle wrapper` را اجرا کن.
- اگر دانلود Gradle از `services.gradle.org` کند یا مسدود است، VPN روشن کن یا در `gradle-wrapper.properties` مقدار `distributionUrl` را به یک آینهٔ Gradle عوض کن. مخزن‌های کتابخانه از قبل آینهٔ aliyun هم دارند (`settings.gradle.kts`).
- بیلد debug به کلید امضا نیاز ندارد. برای release فایل `keystore.properties.example` را به `keystore.properties` کپی و پر کن.
- بعد از هر آپدیت، نسخه (`versionCode`/`versionName`) در `app/build.gradle.kts` است.
