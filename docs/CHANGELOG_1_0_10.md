# نسخهٔ ۱.۰.۱۰ (versionCode 11)

موضوع: کاشی و بخش «برنامهٔ روزانه» (جزئیات در docs/PLANNER.md).

- بخش تازه `planner` با آیکن `ic_home_planner`؛ اولویت‌ها، برنامهٔ ساعتی با بافر، مرور شبانه، ارزیابی هفته.
- آلارم مستقل `PlannerAlarms` (کنش `PLANNER_FIRE` در `ReminderReceiver`)، متصل به `reschedule`.
- ذخیره: JSON در Setting (`pref_planner`) و در پشتیبان؛ بدون مهاجرت Room؛ بدون مجوز تازه.
- تست: `PlannerTest` (ارزیابی هفته، آلارم بعدی، کدک، موردهای فردا).
