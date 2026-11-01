# Schema (Room، نسخه ۱)
- shift_patterns(id PK, name, company, baseYear, baseMonth, baseDay, pattern "1;1;2;...")
- day_entries(id PK, patternId, dateKey YYYYMMDD, type, shiftCode?, minutes?, text, employeeId?) index(patternId,dateKey)
  type ∈ MANUAL_SHIFT, EXCHANGE, LEAVE, OVERTIME, REST, NOTE
- employees(id PK, name, note)
- settings(key PK, value)
نگاشت به فهرست درخواستی: Leave/Overtime/Rest/ShiftExchange/Notes/DailyShifts(تغییر دستی) = day_entries با type مربوط
(یک جدول یکپارچه به‌جای ۶ جدول جدا)؛ DailyShifts محاسبه‌شده ذخیره نمی‌شود چون همیشه از الگوریتم قابل بازتولید است.

## نسخهٔ ۲ (برنامهٔ ۱٫۱)
جدول جدید `holidays(dateKey TEXT PK, title TEXT)` برای تعطیلات واردشدهٔ کاربر. تنظیمات جدید (ساعت شیفت‌ها، یادآور، سقف مرخصی، تم، پشتیبان خودکار) در همان جدول `settings` با کلیدهای `pref_*` ذخیره می‌شوند.
