# ساخت نسخهٔ انتشار «شیفت ما» با Android Studio

1. پوشهٔ پروژه را در Android Studio (Koala یا جدیدتر، JDK 17) باز کنید و منتظر Gradle Sync بمانید.
2. کلید امضای اصلی بازار را کنار `settings.gradle.kts` با نام `keystore.properties` بسازید (نمونه: `keystore.properties.example`؛ storeFile, storePassword, keyAlias, keyPassword). بدون آن، ساخت با کلید آزمایشی امضا می‌شود و بازار نمی‌پذیرد/نصب روی نسخهٔ قبلی ممکن نیست.
3. منوی Build ← Select Build Variant ← `release`.
4. Build ← Build Bundle(s)/APK(s) ← Build APK(s). خروجی: `app/build/outputs/apk/release/app-release.apk` (حدود ۶ مگابایت؛ R8 فعال است).
5. نسخه: `versionCode` و `versionName` در `app/build.gradle.kts` (الان 32 / 1.0.2) و تغییرنامه در `docs/CHANGELOG_1_0_31.md`.
6. قبل از آپلود، همین APK را روی گوشی واقعی نصب و آلارم، یادآور، ویجت و بازیابی را امتحان کنید.

نکته: ساخت debug (`assembleDebug`) کوچک‌سازی ندارد و حدود ۱۵ مگابایت است؛ برای انتشار از آن استفاده نکنید.
