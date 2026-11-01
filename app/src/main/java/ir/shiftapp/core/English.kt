package ir.shiftapp.core

/** یک لغت انگلیسی با معنی فارسی و جملهٔ نمونه. level: ۱ = پایه، ۲ = ابتدایی، ۳ = متوسط، ۴ = محیط کار. */
data class Word(val id: Int, val en: String, val fa: String, val example: String, val level: Int)

/** وضعیت یک لغت در جعبهٔ لایتنر: box ۱..۵ و روزی (epochDay) که باید دوباره مرور شود. */
data class CardState(val box: Int, val due: Int)

/**
 * پیشرفت یادگیری. لغتی که در cards نیست «جدید» است.
 * مرور فاصله‌دار (لایتنر): بلد بودن => جعبهٔ بالاتر و فاصلهٔ بیشتر؛ بلد نبودن => برگشت به جعبهٔ ۱.
 */
data class EnglishProgress(
    val cards: Map<Int, CardState> = emptyMap(),
    val goal: Int = 10,          // هدف روزانه: تعداد لغتی که هر روز مرور می‌شود
    val streak: Int = 0,
    val lastDay: Int = -1,       // آخرین روزی که مرور شد
    val doneDay: Int = -1,       // روزی که doneToday مربوط به آن است
    val doneToday: Int = 0
) {
    fun doneOn(today: Int): Int = if (doneDay == today) doneToday else 0

    /** رشتهٔ پیاپی روزها؛ اگر دیروز یا امروز مرور نشده باشد صفر است. */
    fun streakOn(today: Int): Int = if (lastDay >= today - 1) streak else 0

    val learned: Int get() = cards.values.count { it.box >= LEARNED_BOX }

    fun dueIds(today: Int, level: Int = 0): List<Int> =
        cards.filter { (id, c) -> c.due <= today && inLevel(id, level) }.entries
            .sortedWith(compareBy({ it.value.due }, { it.key })).map { it.key }

    fun newIds(level: Int = 0): List<Int> = English.WORDS.filter { it.id !in cards && (level == 0 || it.level == level) }.map { it.id }

    private fun inLevel(id: Int, level: Int) = level == 0 || English.byId(id)?.level == level

    fun answer(id: Int, known: Boolean, today: Int): EnglishProgress {
        if (English.byId(id) == null) return this
        val old = cards[id]?.box ?: 0
        val box = if (known) minOf(old + 1, INTERVALS.size) else 1
        val due = today + if (known) INTERVALS[box - 1] else 1
        val newStreak = when {
            lastDay == today -> streak
            lastDay == today - 1 -> streak + 1
            else -> 1
        }
        return copy(
            cards = cards + (id to CardState(box, due)),
            streak = newStreak, lastDay = today,
            doneDay = today, doneToday = doneOn(today) + 1
        )
    }

    /** قالب: goal;streak;lastDay;doneDay;doneToday|id:box:due,id:box:due ... */
    fun encode(): String {
        val head = listOf(goal, streak, lastDay, doneDay, doneToday).joinToString(";")
        val body = cards.entries.sortedBy { it.key }.joinToString(",") { "${it.key}:${it.value.box}:${it.value.due}" }
        return "$head|$body"
    }

    companion object {
        const val LEARNED_BOX = 4
        /** فاصلهٔ مرور (روز) برای جعبهٔ ۱ تا ۵. */
        val INTERVALS = listOf(1, 2, 4, 8, 16)
        val GOALS = listOf(5, 10, 20, 30)

        /** مقدار خراب یا ناموجود => پیش‌فرض. */
        fun decode(s: String?): EnglishProgress {
            if (s.isNullOrBlank()) return EnglishProgress()
            return runCatching {
                val parts = s.split('|', limit = 2)
                val h = parts[0].split(';').map { it.trim().toInt() }
                require(h.size == 5)
                val cards = parts.getOrElse(1) { "" }.split(',').filter { it.isNotBlank() }.mapNotNull { e ->
                    val x = e.split(':')
                    if (x.size != 3) return@mapNotNull null
                    val id = x[0].toIntOrNull() ?: return@mapNotNull null
                    val box = x[1].toIntOrNull()?.takeIf { it in 1..INTERVALS.size } ?: return@mapNotNull null
                    val due = x[2].toIntOrNull() ?: return@mapNotNull null
                    if (English.byId(id) == null) null else id to CardState(box, due)
                }.toMap()
                EnglishProgress(cards, h[0].takeIf { it in 1..200 } ?: 10, maxOf(h[1], 0), h[2], h[3], maxOf(h[4], 0))
            }.getOrDefault(EnglishProgress())
        }
    }
}

object English {
    val LEVEL_NAMES = listOf("همه", "پایه", "ابتدایی", "متوسط", "محیط کار")

    fun levelName(level: Int) = LEVEL_NAMES.getOrElse(level) { "" }

    private val index: Map<Int, Word> by lazy { WORDS.associateBy { it.id } }
    fun byId(id: Int): Word? = index[id]

    /** لغت روز: برای هر روز ثابت است؛ offset برای دکمهٔ «لغت بعدی» ویجت. */
    fun wordOfDay(epochDay: Int, offset: Int = 0): Word = WORDS[Math.floorMod(epochDay * 7 + offset, WORDS.size)]

    /** گزینه‌های آزمون: معنی درست + سه معنی غلط (بدون تکرار)، به ترتیبی که با seed ثابت می‌ماند. */
    fun choices(word: Word, seed: Long, pool: List<Word> = WORDS): List<Word> {
        val rnd = java.util.Random(seed xor (word.id * 7919L))
        val wrong = pool.filter { it.id != word.id && it.fa != word.fa }.shuffled(rnd).take(3)
        return (wrong + word).shuffled(rnd)
    }

    /** ردیف‌ها: انگلیسی|فارسی|جمله|سطح */
    private const val RAW = """
hello|سلام|Hello, how are you?|1
goodbye|خداحافظ|Goodbye, see you tomorrow.|1
please|لطفاً|Please sit down.|1
thanks|ممنون|Thanks for your help.|1
sorry|متأسفم، ببخشید|I am sorry I am late.|1
yes|بله|Yes, I understand.|1
no|نه، خیر|No, thank you.|1
water|آب|I drink water every day.|1
food|غذا|The food is hot.|1
bread|نان|I buy bread in the morning.|1
tea|چای|Would you like some tea?|1
milk|شیر|The baby drinks milk.|1
house|خانه|My house is near the park.|1
family|خانواده|I love my family.|1
mother|مادر|My mother cooks well.|1
father|پدر|My father works at night.|1
brother|برادر|My brother is a student.|1
sister|خواهر|My sister lives in Tehran.|1
friend|دوست|He is my best friend.|1
child|کودک|The child is playing outside.|1
man|مرد|That man is a doctor.|1
woman|زن|The woman is reading a book.|1
book|کتاب|This book is very good.|1
pen|خودکار|Can I borrow your pen?|1
school|مدرسه|The school opens at eight.|1
teacher|معلم|Our teacher is kind.|1
car|ماشین|We have a new car.|1
bus|اتوبوس|The bus is late today.|1
street|خیابان|The street is very busy.|1
city|شهر|Tehran is a big city.|1
day|روز|Today is a good day.|1
night|شب|I work at night.|1
morning|صبح|Good morning!|1
time|زمان، وقت|What time is it?|1
big|بزرگ|It is a big room.|1
small|کوچک|I have a small bag.|1
good|خوب|This is a good idea.|1
bad|بد|The weather is bad.|1
new|جدید|I need a new phone.|1
old|قدیمی، پیر|This is an old building.|1
hot|گرم|The tea is hot.|1
cold|سرد|It is cold outside.|1
happy|خوشحال|I am happy today.|1
tired|خسته|I am tired after work.|1
eat|خوردن|We eat dinner at seven.|1
drink|نوشیدن|Do you drink coffee?|1
sleep|خوابیدن|I sleep eight hours.|1
go|رفتن|I go to work by bus.|1
come|آمدن|Please come here.|1
see|دیدن|I can see the mountain.|1
work|کار کردن؛ کار|I work in a factory.|1
buy|خریدن|I want to buy a gift.|1
answer|پاسخ|Write your answer here.|2
ask|پرسیدن|Can I ask a question?|2
believe|باور کردن|I believe you.|2
borrow|قرض گرفتن|Can I borrow your charger?|2
bring|آوردن|Please bring your ID card.|2
change|تغییر دادن؛ تغییر|I need to change my shift.|2
choose|انتخاب کردن|You can choose any color.|2
clean|تمیز|The room is clean.|2
close|بستن|Please close the door.|2
cook|پختن|She cooks dinner every night.|2
cost|هزینه داشتن؛ هزینه|How much does it cost?|2
early|زود|I wake up early.|2
late|دیر|The train is late.|2
easy|آسان|This exercise is easy.|2
difficult|سخت|The test was difficult.|2
enjoy|لذت بردن|I enjoy reading.|2
every|هر|I walk every day.|2
expensive|گران|This coat is expensive.|2
cheap|ارزان|Vegetables are cheap here.|2
fast|سریع|He drives too fast.|2
slow|آهسته|The internet is slow.|2
forget|فراموش کردن|Don't forget your keys.|2
remember|به یاد داشتن|I remember his name.|2
free|آزاد؛ رایگان|I am free on Friday.|2
help|کمک کردن؛ کمک|Can you help me?|2
hungry|گرسنه|I am hungry now.|2
important|مهم|This meeting is important.|2
invite|دعوت کردن|I want to invite you to dinner.|2
learn|یاد گرفتن|I learn ten words a day.|2
leave|ترک کردن|We leave at six.|2
listen|گوش دادن|Listen to the teacher.|2
meet|ملاقات کردن|Nice to meet you.|2
need|نیاز داشتن|I need some rest.|2
open|باز کردن|Open the window, please.|2
pay|پرداخت کردن|I will pay by card.|2
plan|برنامه؛ برنامه‌ریزی کردن|What is your plan for tomorrow?|2
ready|آماده|Dinner is ready.|2
rest|استراحت|Take a rest after the shift.|2
start|شروع کردن|The class starts at nine.|2
stop|متوقف شدن|The bus stops here.|2
travel|سفر کردن|I like to travel by train.|2
wait|منتظر ماندن|Wait for me outside.|2
wake up|بیدار شدن|I wake up at six.|2
weather|آب‌وهوا|The weather is nice today.|2
week|هفته|I work six days a week.|2
month|ماه|The rent is due every month.|2
year|سال|I have worked here for a year.|2
achieve|به دست آوردن، موفق شدن|She worked hard to achieve her goal.|3
advice|توصیه|Thank you for your advice.|3
afford|توانایی مالی داشتن|I can't afford a new car.|3
agree|موافق بودن|I agree with you.|3
allow|اجازه دادن|They don't allow phones here.|3
amount|مقدار|A small amount of sugar is enough.|3
appointment|وقت قبلی، قرار ملاقات|I have a doctor's appointment.|3
arrange|ترتیب دادن|Let's arrange a meeting.|3
attend|حضور یافتن|He attended the training course.|3
available|در دسترس، آزاد|Is this seat available?|3
benefit|مزیت، سود|Exercise has many benefits.|3
compare|مقایسه کردن|Compare the two prices.|3
complain|شکایت کردن|Customers complain about the delay.|3
confirm|تأیید کردن|Please confirm your booking.|3
consider|در نظر گرفتن|Consider my offer.|3
decide|تصمیم گرفتن|I decided to learn English.|3
deliver|تحویل دادن|They deliver the goods on Monday.|3
develop|توسعه دادن|We develop new skills.|3
discuss|بحث کردن|Let's discuss the problem.|3
experience|تجربه|She has ten years of experience.|3
explain|توضیح دادن|Can you explain this word?|3
improve|بهبود دادن|I want to improve my English.|3
include|شامل شدن|The price includes tax.|3
manage|مدیریت کردن|He manages a small team.|3
necessary|ضروری|It is necessary to wear a helmet.|3
opportunity|فرصت|This job is a great opportunity.|3
prefer|ترجیح دادن|I prefer tea to coffee.|3
prepare|آماده کردن|Prepare your documents.|3
provide|فراهم کردن|The company provides lunch.|3
reduce|کاهش دادن|We must reduce costs.|3
require|نیاز داشتن، لازم داشتن|This job requires patience.|3
responsible|مسئول|He is responsible for safety.|3
result|نتیجه|The result was surprising.|3
succeed|موفق شدن|Hard work helps you succeed.|3
suggest|پیشنهاد کردن|I suggest taking a break.|3
support|حمایت کردن؛ حمایت|My family supports me.|3
view|نظر، دیدگاه|In my view, it is a good plan.|3
shift|شیفت کاری|I am on the night shift.|4
day shift|شیفت روز|The day shift starts at seven.|4
night shift|شیفت شب|He works the night shift this week.|4
overtime|اضافه‌کاری|I worked two hours of overtime.|4
leave|مرخصی|I took a day of leave.|4
salary|حقوق ماهانه|The salary is paid monthly.|4
payslip|فیش حقوقی|Please check your payslip.|4
colleague|همکار|My colleague is on leave today.|4
manager|مدیر|The manager is in a meeting.|4
supervisor|سرپرست|Report the problem to your supervisor.|4
team|تیم|Our team works well together.|4
meeting|جلسه|The meeting starts at ten.|4
deadline|مهلت تحویل|The deadline is on Thursday.|4
report|گزارش؛ گزارش دادن|Send me the daily report.|4
schedule|برنامهٔ زمانی|Check the shift schedule.|4
attendance|حضور و غیاب|Attendance is recorded every day.|4
clock in|ثبت ورود|Don't forget to clock in.|4
clock out|ثبت خروج|I clock out at seven.|4
safety|ایمنی|Safety comes first.|4
helmet|کلاه ایمنی|Always wear your helmet.|4
gloves|دستکش|Wear gloves when you work.|4
equipment|تجهیزات|The equipment needs repair.|4
machine|دستگاه، ماشین‌آلات|The machine is not working.|4
repair|تعمیر کردن|They repair the pump on Sunday.|4
maintenance|نگهداری و تعمیرات|Maintenance is planned for tonight.|4
production|تولید|Production increased this month.|4
quality|کیفیت|We check the quality of every product.|4
instruction|دستورالعمل|Follow the instructions carefully.|4
emergency|اضطراری|In an emergency, call the supervisor.|4
handover|تحویل شیفت|The handover takes ten minutes.|4
evening|عصر، شب (اوایل)|We eat dinner in the evening.|1
today|امروز|Today is a busy day.|1
tomorrow|فردا|See you tomorrow.|1
yesterday|دیروز|I was at work yesterday.|1
walk|راه رفتن|I walk to work.|1
drive|رانندگی کردن|He drives a bus.|1
read|خواندن|I read before bed.|1
write|نوشتن|Write your name here.|1
speak|صحبت کردن|Can you speak English?|1
carry|حمل کردن|He carries heavy boxes.|2
teach|آموزش دادن|She teaches math.|2
visit|ملاقات کردن، بازدید کردن|We visit my uncle on Fridays.|2
get up|از خواب بلند شدن|He gets up early.|2
arrive|رسیدن|The bus arrives at eight.|2
leave a place|ترک کردن|I leave home at seven.|2
return|برگشتن|I return home at night.|2
always|همیشه|He is always on time.|2
usually|معمولاً|I usually walk to work.|2
sometimes|گاهی اوقات|Sometimes I cook dinner.|2
never|هرگز|I never drink coffee at night.|2
rain|باران|It will rain tomorrow.|2
market|بازار|I buy fruit at the market.|2
hospital|بیمارستان|She works at a hospital.|2
doctor|پزشک|I have an appointment with the doctor.|2
medicine|دارو|Take this medicine twice a day.|2
price|قیمت|What is the price of this?|2
dangerous|خطرناک|This road is dangerous.|2
different|متفاوت|The two jobs are different.|2
same|مثل هم، یکسان|We have the same shift.|2
busy|پرمشغله|I am busy this week.|2
avoid|اجتناب کردن|Avoid working too late.|3
lend|قرض دادن|Can you lend me some money?|3
challenge|چالش|Night work is a real challenge.|3
continue|ادامه دادن|Let's continue tomorrow.|3
control|کنترل کردن؛ کنترل|You must control your anger.|3
create|ایجاد کردن|They create new jobs.|3
depend|وابسته بودن|It depends on the weather.|3
describe|توصیف کردن|Describe your daily routine.|3
earn|به دست آوردن (درآمد)|He earns a good salary.|3
encourage|تشویق کردن|My boss encourages us to learn.|3
enough|کافی|I don't have enough time.|3
environment|محیط|A clean environment is important.|3
expect|انتظار داشتن|I expect a reply soon.|3
experience level|سطح تجربه|Write your experience level here.|3
fail|شکست خوردن|Don't be afraid to fail.|3
focus|تمرکز کردن|I can't focus when I'm tired.|3
increase|افزایش دادن|Prices increase every year.|3
inform|اطلاع دادن|Please inform me of any change.|3
introduce|معرفی کردن|Let me introduce my colleague.|3
join|پیوستن|She joined our team last year.|3
manage time|مدیریت زمان|It is hard to manage time on night shifts.|3
notice|متوجه شدن|I noticed a mistake.|3
offer|پیشنهاد دادن|They offered me a new job.|3
order|سفارش دادن؛ دستور|I ordered some food.|3
organize|سازمان دادن، مرتب کردن|Organize your papers.|3
polite|مودب|He is always polite.|3
prevent|جلوگیری کردن|Wear a helmet to prevent injury.|3
promise|قول دادن؛ قول|I promise to be on time.|3
protect|محافظت کردن|Gloves protect your hands.|3
realize|پی بردن|I realized I was late.|3
recommend|توصیه کردن|I recommend this course.|3
refuse|رد کردن|He refused the offer.|3
relax|استراحت کردن، آرام گرفتن|I relax after a long shift.|3
replace|جایگزین کردن|Replace the old filter.|3
simple|ساده|The instructions are simple.|3
solve|حل کردن|We solved the problem together.|3
stress|استرس|Night shifts can cause stress.|3
useful|مفید|This app is very useful.|3
warn|هشدار دادن|The sign warns about danger.|3
worry|نگران بودن|Don't worry about it.|3
shift change|تعویض شیفت|The shift change is at seven.|4
swap shifts|عوض کردن شیفت با همکار|Can we swap shifts this Friday?|4
day off|روز استراحت|Tomorrow is my day off.|4
sick leave|مرخصی استعلاجی|He is on sick leave.|4
annual leave|مرخصی سالانه|I will take annual leave in July.|4
public holiday|تعطیلی رسمی|Monday is a public holiday.|4
weekend|آخر هفته|We work on the weekend.|4
rotating shift|شیفت گردشی|He works a rotating shift.|4
fixed shift|شیفت ثابت|I prefer a fixed shift.|4
on duty|در حال انجام وظیفه|Who is on duty tonight?|4
off duty|خارج از وظیفه|I am off duty now.|4
on call|آماده‌به‌کار|The engineer is on call this week.|4
late arrival|تأخیر در ورود|Your late arrival was recorded.|4
absence|غیبت|Please explain your absence.|4
approve|تأیید کردن|The manager approved my leave.|4
request|درخواست؛ درخواست کردن|I submitted a leave request.|4
contract|قرارداد|He signed a one-year contract.|4
employee|کارمند|Every employee must wear an ID card.|4
employer|کارفرما|My employer pays on the first of the month.|4
wage|دستمزد|The hourly wage is fixed.|4
bonus|پاداش|I received a bonus this month.|4
insurance|بیمه|The company pays for insurance.|4
promotion|ارتقا|She got a promotion.|4
training|آموزش (شغلی)|The training starts on Monday.|4
skill|مهارت|Communication is an important skill.|4
task|وظیفه، کار|Finish this task before noon.|4
procedure|روش اجرایی|Follow the safety procedure.|4
inspection|بازرسی|The inspection is at nine.|4
shutdown|توقف (دستگاه یا کارخانه)|The plant has a shutdown next week.|4
breakdown|خرابی|There was a breakdown on line two.|4
fire extinguisher|کپسول آتش‌نشانی|The fire extinguisher is by the door.|4
warning sign|علامت هشدار|Read the warning sign.|4
first aid|کمک‌های اولیه|He knows first aid.|4
incident|حادثه|Report every incident immediately.|4
shift report|گزارش شیفت|Write the shift report before you leave.|4
site|محل کار، سایت|The site is closed on Fridays.|4
workshop|کارگاه|He works in a workshop.|4
warehouse|انبار|The warehouse opens at six.|4
forklift|لیفتراک|He drives a forklift.|4
tool|ابزار|Put the tool back after use.|4
spare part|قطعهٔ یدکی|We need a spare part for the pump.|4
"""

    val WORDS: List<Word> by lazy {
        RAW.lines().map { it.trim() }.filter { it.isNotEmpty() }.mapIndexedNotNull { i, line ->
            val p = line.split('|')
            if (p.size != 4) null else Word(i + 1, p[0], p[1], p[2], p[3].toInt())
        }
    }
}
