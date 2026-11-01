# شیفت ما: قوانین R8 برای نسخهٔ انتشار.
# کد خود برنامه (Room، WorkManager، Receiver و Service که با نام کلاس ساخته می‌شوند) کامل نگه داشته می‌شود؛ ایمن‌ترین حالت.
-keep class ir.shiftapp.** { *; }
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, SourceFile, LineNumberTable
-dontwarn org.json.**
# پرداخت کافه‌بازار (Poolakey): BillingReceiver در مانیفست کتابخانه با نام کلاس ساخته می‌شود
-keep class ir.cafebazaar.poolakey.** { *; }
