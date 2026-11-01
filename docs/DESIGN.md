# سیستم طراحی (Design System)

همهٔ صفحه‌ها فقط از این اجزا استفاده می‌کنند تا ظاهر برنامه یکدست بماند. رنگ، شکل یا فاصلهٔ ثابتِ جدا در صفحه‌ها ننویسید.

## تم (`ui/Theme.kt`)
- **رنگ:** پالت ایندیگو (هم‌خانوادهٔ آیکن برنامه) با زمینه و سطح‌های روشن/تاریک. رنگ‌های معنایی در `Pal`: `card`, `hairline`, `today`, `holiday`, `green`, `coral`, `amber`, `muted`, `dialog`, `heroBrush`.
- **شکل:** پنج اندازه در `AppShapes` (۶ / ۱۰ / ۱۴ / ۲۰ / ۲۸). کارت = `large`، دکمه و فیلد = `medium`، پنجره = `extraLarge`.
- **تایپوگرافی:** مقیاس مخصوص خط فارسی با ارتفاع خط بازتر و `letterSpacing = 0` (فاصلهٔ مثبت حروف فارسی را از هم جدا می‌کند). همیشه از `MaterialTheme.typography.*` استفاده کنید، نه `fontSize` دستی.
- **نوار وضعیت/ناوبری گوشی:** در `AppTheme` هم‌رنگ زمینه تنظیم می‌شود؛ صفحه‌ها خودشان رنگ آن را عوض نکنند.

## اجزای مشترک (`ui/Components.kt`)
| جزء | کاربرد |
|---|---|
| `AppTopBar`, `BarIconButton` | نوار بالای همهٔ بخش‌ها (برگشت/خانه، عنوان، زیرعنوان، عمل‌ها) |
| `MonthSwitcher` | انتخاب ماه (قبل/بعد/امروز + لمس نام ماه برای انتخاب مستقیم ماه و سال) |
| `AppCard`, `SettingsGroup`, `SettingRow`, `SwitchRow`, `RowDivider` | کارت‌ها و ردیف‌های فهرست/تنظیمات |
| `AppButton` (`BtnStyle.Filled/Tonal/Outline/Danger`), `SmallIconButton` | دکمه‌ها |
| `AppField` | همهٔ فیلدهای ورودی (`ltr = true` برای عدد/ساعت/تاریخ) |
| `AppChip`, `ChipRow`, `Segmented` | انتخاب‌ها |
| `AppDialog`, `ConfirmDialog` | همهٔ پنجره‌ها |
| `ShiftChip`, `ShiftLegend` | نمایش رنگی شیفت و راهنمای رنگ‌ها |
| `EmptyState`, `StatTile`, `IconBadge`, `Dot` | حالت خالی، آمار، نشان آیکن |

## آیکن‌ها
آیکن‌ها وکتور هستند (`res/drawable/ic_*.xml`)؛ از ایموجی یا نویسه‌هایی مثل «‹ › ✱» استفاده نشود (در دستگاه‌های مختلف متفاوت دیده می‌شوند). آیکن‌های جهت‌دار (`ic_chevron_*`, `ic_arrow_back`) خودکار در راست‌به‌چپ آینه می‌شوند.

## جدول‌ها
چهارشیفت و کاردکس از `TableShell` + `TableRow` + `DateCell` ساخته می‌شوند (ارتفاع ردیف ۵۶dp، ستون روز = عدد + نام روز، شیفت‌ها با `ShiftChip`).

## افزودن صفحهٔ جدید
۱. نوار بالا: `AppTopBar`؛ ۲. محتوا: `AppCard` / `SettingsGroup`؛ ۳. فرم: `AppField` + `AppButton`؛ ۴. پنجره: `AppDialog`.
