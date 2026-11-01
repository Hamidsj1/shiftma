# افزودن بخش تازه به صفحهٔ اول (مثل دفترچه تلفن یا یادداشت)

۱. در `ui/HomeScreen.kt` داخل لیست `sections` یک `HomeSection` بنویسید یا آن را فعال کنید
   (`ready = true`). برای آیکن یک وکتور ۲۴×۲۴ در `res/drawable` بسازید.
۲. صفحهٔ بخش را به‌شکل یک Composable بنویسید، مثلاً `fun NotesSection(vm: MainVm, onHome: () -> Unit)`
   و در آن `BackHandler { onHome() }` بگذارید. برای ظاهر یکدست از `AppTopBar` و اجزای `docs/DESIGN.md` استفاده کنید.
۳. در `ui/MainActivity.kt` داخل `App` در `when (section)` یک شاخه اضافه کنید:
   `"notes" -> NotesSection(vm) { section = null }`
۴. اگر دادهٔ ماندگار لازم دارید، جدول Room را در `data/` اضافه کنید و نسخهٔ پایگاه‌داده را بالا ببرید.

کاشی‌های سامانه‌ها (رسا سلامت، مس من و …) و بخش‌های مخفی (دفترچه تلفن، برنامه غذایی) پیش‌فرض داخل برنامه نیستند.
آن‌ها از «فایل کاشی‌ها» می‌آیند: فایل `.shiftma` با `"type": "tiles"` (`core/TilesPack.kt`).

```json
{ "app": "shiftma", "type": "tiles", "version": 1,
  "sites": [ { "key": "nicico", "title": "مس من", "subtitle": "پورتال مس من",
               "url": "https://my.nicico.com/...", "pdf": true, "logo": "<PNG کوچک به‌صورت base64، اختیاری>" } ],
  "sections": ["phonebook", "meals"],
  "mealsFeed": "https://raw.githubusercontent.com/USER/REPO/main/company-a/meals.json" }
```

- `url` باید https باشد. `key` شناسهٔ نام کاربری و رمز ذخیره‌شده است؛ ننویسید، از روی دامنه ساخته می‌شود.
- `logo` حداکثر ۹۰ کیلوبایت؛ بدون آن آیکن عمومی نشان داده می‌شود. حداکثر ۱۲ سامانه.
- `sections` فقط `phonebook` و `meals` را می‌پذیرد و بدون رمز روزانه نمایانشان می‌کند (در تنظیمات می‌شود دوباره پنهان کرد).
- نام کاربری و رمز هیچ‌وقت داخل فایل نیست.
- `mealsFeed` (اختیاری) آدرس خوراک برنامه غذایی شرکت است (فقط https روی گیت‌هاب)؛ جزئیات در `docs/MEAL_FEED.md`.
