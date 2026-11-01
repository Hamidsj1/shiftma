# معماری
```
UI (Compose) → MainVm → ReportCalculator / DayResolver → ShiftCalculator → JalaliCalendar
                      ↘ AppDao (Room/SQLite) ; Backup(JSON) ; CsvExport
```
- `core/` بدون وابستگی به Android و کاملاً Unit-Testable.
- override ها هرگز در ShiftCalculator وارد نمی‌شوند؛ فقط DayResolver آن‌ها را روی نتیجه اعمال می‌کند.
- بازیابی Backup اتمی است: اول اعتبارسنجی کامل، بعد تراکنش جایگزینی.

## تب چهارشیفت (افزوده‌شده)
- `core/FourShiftTable.kt`: منطق خالص؛ برای هر روز ماه و هر یک از ۴ ستون با همان `DayResolver` شیفت مؤثر را می‌دهد (قابل تست بدون Android).
- `core/DateText.kt`: تبدیل متن تاریخ (ارقام لاتین/فارسی/عربی) به `JalaliDate`.
- `MainVm.slots`: چهار شناسهٔ الگو (۰=خالی) در جدول `settings` با کلیدهای `four_slot_0..3`؛ در Backup/Restore به‌طور خودکار هست و بعد از Restore دوباره بارگذاری می‌شود. حذف یک الگو، ستون‌های مربوط را خالی می‌کند.
- `MainVm.entriesAllPatterns`: ثبت‌های ماه برای همهٔ الگوها (برای نمایش تعویض/تغییر دستی هر ستون).
- `ui/Screens.kt`: `FourShiftScreen`، `SlotPickDialog`، و `PatternDialog` با ظاهر فرم نمونه.

## تب کاردکس (افزوده‌شده)
- `core/CardexTable.kt`: منطق خالص؛ برای الگوی انتخاب‌شده، ردیف‌های ماه (`EffectiveDay` + وقایع) و ترتیب ستون‌ها (`COLUMNS`) را می‌دهد.
- `ui/Screens.kt`: `CardexScreen` از همان `selected`/`entriesByDate` و `DayDialog` (با پارامتر `initialType`) استفاده می‌کند؛ ثبت وقایع منطق جدیدی ندارد.
- `SlotPickDialog(slot = null)` یعنی انتخاب شیفت برای کاردکس (`vm.select`)، و با `slot` یعنی انتساب به ستون چهارشیفت.
