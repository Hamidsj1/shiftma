# API داخلی
- `JalaliCalendar.isValid/daysInMonth/isLeap/toJdn/fromJdn/fromLocalDate`
- `JalaliDate(year,month,day)`: `isValid, dayOfWeek(شنبه=0), key(YYYYMMDD), plusDays`
- `ShiftPattern.of(codes) / parse("1;1;2")` ← خطا برای خالی/نامعتبر
- `ShiftCalculator.calculateShift(base: JalaliDate, target: JalaliDate, pattern: ShiftPattern): Int`
- `DayResolver.resolve(base, pattern, date, entries): EffectiveDay`
- `ReportCalculator.month(year, month, base, pattern, entriesByDate): MonthReport`
- `Backup.toJson(Snapshot) / fromJson(String)` ؛ `CsvExport.month(...)`
