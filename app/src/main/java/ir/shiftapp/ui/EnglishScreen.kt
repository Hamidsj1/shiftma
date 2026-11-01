package ir.shiftapp.ui

import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import ir.shiftapp.R
import ir.shiftapp.core.CardState
import ir.shiftapp.core.English
import ir.shiftapp.core.EnglishProgress
import ir.shiftapp.core.Fa
import ir.shiftapp.core.Word
import java.util.Locale

/*
 * بخش یادگیری انگلیسی: لغت روز، مرور فاصله‌دار (جعبهٔ لایتنر)، آزمون چهارگزینه‌ای و فهرست لغت‌ها.
 * داده‌ها در core/English.kt و پیشرفت کاربر در تنظیمات (pref_english) است، پس در بکاپ هم می‌آید.
 */

private const val NEW_PER_SESSION = 5
private const val QUIZ_SIZE = 10

private fun todayDay(): Int = java.time.LocalDate.now().toEpochDay().toInt()

@Composable
private fun EnText(text: String, style: TextStyle, modifier: Modifier = Modifier, color: Color = Color.Unspecified, align: TextAlign = TextAlign.Unspecified) {
    Text(text, modifier, color = color, style = style.copy(textDirection = TextDirection.Ltr), textAlign = align)
}

/** تابعی که متن انگلیسی را با موتور تلفظ گوشی می‌خواند. */
@Composable
private fun rememberSpeaker(): (String) -> Unit {
    val ctx = LocalContext.current
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var ready by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val t = TextToSpeech(ctx.applicationContext) { status -> if (status == TextToSpeech.SUCCESS) ready = true }
        tts = t
        onDispose { t.stop(); t.shutdown() }
    }
    return remember(tts, ready) {
        { text: String ->
            val t = tts
            if (t != null && ready) {
                t.setLanguage(Locale.US)
                t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "word")
            } else Toast.makeText(ctx, "موتور تلفظ گوشی آماده نیست", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun EnglishSection(vm: MainVm, onHome: () -> Unit) {
    val progress by vm.english.collectAsState()
    var mode by rememberSaveable { mutableStateOf("home") }
    var level by rememberSaveable { mutableIntStateOf(0) }
    val speak = rememberSpeaker()
    BackHandler { if (mode != "home") mode = "home" else onHome() }
    val inSub = mode != "home"
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title = when (mode) { "study" -> "مرور لغت‌ها"; "quiz" -> "آزمون"; "list" -> "فهرست لغت‌ها"; else -> "یادگیری انگلیسی" },
                leadingIcon = if (inSub) R.drawable.ic_arrow_back else R.drawable.ic_home,
                leadingDesc = if (inSub) "بازگشت" else "صفحهٔ اول",
                onLeading = { if (inSub) mode = "home" else onHome() },
                subtitle = if (level == 0) null else "سطح: ${English.levelName(level)}"
            )
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().padding(horizontal = 16.dp)) {
            val answer = { id: Int, known: Boolean -> vm.updateEnglish { it.answer(id, known, todayDay()) } }
            when (mode) {
                "study" -> StudyScreen(progress, level, answer, speak) { mode = "home" }
                "quiz" -> QuizScreen(level, answer, speak) { mode = "home" }
                "list" -> WordListScreen(progress, level, speak)
                else -> EnglishHome(
                    progress, level, speak,
                    onLevel = { level = it }, onGoal = { g -> vm.updateEnglish { it.copy(goal = g) } },
                    onMode = { mode = it }
                )
            }
        }
    }
}

// ───────────────────────── صفحهٔ اصلی بخش ─────────────────────────

@Composable
private fun EnglishHome(
    progress: EnglishProgress, level: Int, speak: (String) -> Unit,
    onLevel: (Int) -> Unit, onGoal: (Int) -> Unit, onMode: (String) -> Unit
) {
    val today = remember { todayDay() }
    val done = progress.doneOn(today)
    val due = progress.dueIds(today, level).size
    val fresh = progress.newIds(level).size
    val word = remember { English.wordOfDay(today) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // کارت پیشرفت امروز
        Column(
            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).background(Pal.heroBrush).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("هدف امروز", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.8f))
            Text("${Fa.num(done)} از ${Fa.num(progress.goal)} لغت", style = MaterialTheme.typography.headlineSmall, color = Color.White)
            LinearProgressIndicator(
                progress = { (done.toFloat() / progress.goal).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                color = Color.White, trackColor = Color.White.copy(alpha = 0.25f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeroStat("پیاپی", "${Fa.num(progress.streakOn(today))} روز", Modifier.weight(1f))
                HeroStat("یادگرفته‌شده", Fa.num(progress.learned), Modifier.weight(1f))
                HeroStat("در جعبه", Fa.num(progress.cards.size), Modifier.weight(1f))
            }
        }

        // لغت روز
        AppCard {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("لغت روز", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    SmallIconButton(R.drawable.ic_volume, "تلفظ", { speak(word.en) })
                }
                EnText(word.en, MaterialTheme.typography.headlineMedium)
                Text(word.fa, style = MaterialTheme.typography.titleMedium)
                EnText(word.example, MaterialTheme.typography.bodyMedium, color = Pal.muted)
            }
        }

        // سطح و دکمه‌ها
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("سطح لغت‌ها")
            ChipRow {
                English.LEVEL_NAMES.indices.forEach { i -> AppChip(English.levelName(i), level == i, { onLevel(i) }) }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AppButton(
                if (due + fresh == 0) "همهٔ لغت‌ها مرور شد 🎉" else "شروع مرور (${Fa.num(minOf(due, 30))} مرور، ${Fa.num(minOf(fresh, NEW_PER_SESSION))} جدید)",
                { onMode("study") }, Modifier.fillMaxWidth(), icon = R.drawable.ic_check_circle, enabled = due + fresh > 0
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppButton("آزمون", { onMode("quiz") }, Modifier.weight(1f), icon = R.drawable.ic_timer, style = BtnStyle.Tonal)
                AppButton("فهرست لغت‌ها", { onMode("list") }, Modifier.weight(1f), icon = R.drawable.ic_note, style = BtnStyle.Tonal)
            }
            HintText("لغت‌هایی که می‌دانی با فاصلهٔ بیشتر (۱، ۲، ۴، ۸ و ۱۶ روز) و لغت‌هایی که نمی‌دانی فردا دوباره می‌آیند.")
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("هدف روزانه (تعداد لغت)")
            Segmented(EnglishProgress.GOALS.map { Fa.num(it) }, EnglishProgress.GOALS.indexOf(progress.goal)) { onGoal(EnglishProgress.GOALS[it]) }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier) {
    Column(
        modifier.clip(MaterialTheme.shapes.medium).background(Color.White.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f), maxLines = 1)
        Text(value, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1)
    }
}

// ───────────────────────── مرور (فلش‌کارت) ─────────────────────────

@Composable
private fun StudyScreen(progress: EnglishProgress, level: Int, onAnswer: (Int, Boolean) -> Unit, speak: (String) -> Unit, onFinish: () -> Unit) {
    val today = remember { todayDay() }
    val queue = remember {
        mutableStateListOf<Int>().apply {
            addAll(progress.dueIds(today, level).take(30))
            addAll(progress.newIds(level).take(NEW_PER_SESSION))
        }
    }
    val initial = remember { queue.size }
    val failed = remember { mutableStateListOf<Int>() }
    var flipped by remember { mutableStateOf(false) }
    var knownCount by remember { mutableIntStateOf(0) }
    var answered by remember { mutableIntStateOf(0) }

    if (initial == 0) {
        EmptyNote("برای این سطح چیزی برای مرور نیست.", onFinish)
        return
    }
    if (queue.isEmpty()) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("آفرین! 🎉", style = MaterialTheme.typography.headlineSmall)
                    Text("${Fa.num(knownCount)} لغت از ${Fa.num(initial)} لغت را درست دانستی.", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                    AppButton("بازگشت", onFinish, Modifier.fillMaxWidth())
                }
            }
        }
        return
    }
    val id = queue.first()
    val w = English.byId(id) ?: run { queue.removeAt(0); return }
    val isRetry = id in failed
    val box = progress.cards[id]?.box ?: 0

    fun next(known: Boolean) {
        if (!isRetry) {
            onAnswer(id, known)
            if (known) knownCount++ else failed.add(id)
        }
        queue.removeAt(0)
        if (!known && !isRetry) queue.add(id)
        answered++
        flipped = false
    }

    Column(Modifier.fillMaxSize().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        LinearProgressIndicator(
            progress = { (answered.toFloat() / (initial + failed.size)).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50))
        )
        Text("${Fa.num(queue.size)} کارت مانده", style = MaterialTheme.typography.bodySmall, color = Pal.muted)
        AppCard(Modifier.fillMaxWidth().weight(1f), onClick = { flipped = !flipped }) {
            Column(
                Modifier.fillMaxSize().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    if (isRetry) "تکرار" else if (box == 0) "جدید" else "جعبهٔ ${Fa.num(box)}",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(14.dp))
                EnText(w.en, MaterialTheme.typography.displaySmall, Modifier.fillMaxWidth(), align = TextAlign.Center)
                SmallIconButton(R.drawable.ic_volume, "تلفظ", { speak(w.en) })
                Spacer(Modifier.height(10.dp))
                if (flipped) {
                    Text(w.fa, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(10.dp))
                    EnText(w.example, MaterialTheme.typography.bodyLarge, Modifier.fillMaxWidth(), color = Pal.muted, align = TextAlign.Center)
                } else {
                    HintText("برای دیدن معنی لمس کن")
                }
            }
        }
        if (flipped) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppButton("نمی‌دانستم", { next(false) }, Modifier.weight(1f), icon = R.drawable.ic_close, style = BtnStyle.Danger)
                AppButton("می‌دانستم", { next(true) }, Modifier.weight(1f), icon = R.drawable.ic_check)
            }
        } else {
            AppButton("نمایش معنی", { flipped = true }, Modifier.fillMaxWidth(), style = BtnStyle.Tonal)
        }
    }
}

@Composable
private fun EmptyNote(text: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        AppButton("بازگشت", onBack, style = BtnStyle.Tonal)
    }
}

// ───────────────────────── آزمون ─────────────────────────

@Composable
private fun QuizScreen(level: Int, onAnswer: (Int, Boolean) -> Unit, speak: (String) -> Unit, onFinish: () -> Unit) {
    var round by remember { mutableIntStateOf(0) }
    key(round) { QuizRound(level, onAnswer, speak, onFinish) { round++ } }
}

@Composable
private fun QuizRound(level: Int, onAnswer: (Int, Boolean) -> Unit, speak: (String) -> Unit, onFinish: () -> Unit, onAgain: () -> Unit) {
    val seed = remember { System.nanoTime() }
    val pool = remember { English.WORDS.filter { level == 0 || it.level == level } }
    val words = remember { pool.shuffled(java.util.Random(seed)).take(QUIZ_SIZE) }
    var idx by remember { mutableIntStateOf(0) }
    var picked by remember { mutableStateOf<Int?>(null) }
    var score by remember { mutableIntStateOf(0) }

    if (words.size < 4) {
        EmptyNote("برای این سطح لغت کافی نیست.", onFinish)
        return
    }
    if (idx >= words.size) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("نتیجه", style = MaterialTheme.typography.titleMedium, color = Pal.muted)
                    Text("${Fa.num(score)} از ${Fa.num(words.size)}", style = MaterialTheme.typography.displaySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppButton("دوباره", onAgain, Modifier.weight(1f))
                        AppButton("بازگشت", onFinish, Modifier.weight(1f), style = BtnStyle.Tonal)
                    }
                }
            }
        }
        return
    }
    val w = words[idx]
    val options = remember(idx) { English.choices(w, seed, pool) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LinearProgressIndicator(
            progress = { idx.toFloat() / words.size },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50))
        )
        Text("سؤال ${Fa.num(idx + 1)} از ${Fa.num(words.size)}", style = MaterialTheme.typography.bodySmall, color = Pal.muted)
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("معنی این لغت چیست؟", style = MaterialTheme.typography.labelLarge, color = Pal.muted)
                Spacer(Modifier.height(8.dp))
                EnText(w.en, MaterialTheme.typography.displaySmall, Modifier.fillMaxWidth(), align = TextAlign.Center)
                SmallIconButton(R.drawable.ic_volume, "تلفظ", { speak(w.en) })
            }
        }
        options.forEach { o ->
            val chosen = picked
            val isRight = o.id == w.id
            val bg = when {
                chosen == null -> Pal.card
                isRight -> Pal.green.copy(alpha = 0.18f)
                o.id == chosen -> Pal.holiday.copy(alpha = 0.18f)
                else -> Pal.card
            }
            Box(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(bg)
                    .clickable(enabled = chosen == null, role = Role.Button) {
                        picked = o.id
                        if (isRight) score++
                        onAnswer(w.id, isRight)
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) { Text(o.fa, style = MaterialTheme.typography.titleSmall) }
        }
        if (picked != null) {
            EnText(w.example, MaterialTheme.typography.bodyMedium, color = Pal.muted)
            AppButton(if (idx == words.size - 1) "نمایش نتیجه" else "سؤال بعدی", { idx++; picked = null }, Modifier.fillMaxWidth())
        }
    }
}

// ───────────────────────── فهرست ─────────────────────────

@Composable
private fun WordListScreen(progress: EnglishProgress, level: Int, speak: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim()
    val list = remember(level, q) {
        English.WORDS.filter { (level == 0 || it.level == level) && (q.isEmpty() || it.en.contains(q, ignoreCase = true) || it.fa.contains(q)) }
    }
    Column(Modifier.fillMaxSize().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, shape = MaterialTheme.shapes.medium,
            placeholder = { Text("جست‌وجوی لغت") }
        )
        HintText("${Fa.num(list.size)} لغت")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(list, key = { it.id }) { w -> WordRow(w, progress.cards[w.id], speak) }
        }
    }
}

@Composable
private fun WordRow(w: Word, state: CardState?, speak: (String) -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                EnText(w.en, MaterialTheme.typography.titleMedium)
                Text(w.fa, style = MaterialTheme.typography.bodyMedium)
                EnText(w.example, MaterialTheme.typography.bodySmall, color = Pal.muted)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (state == null) "جدید" else if (state.box >= EnglishProgress.LEARNED_BOX) "یاد گرفته" else "جعبهٔ ${Fa.num(state.box)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (state != null && state.box >= EnglishProgress.LEARNED_BOX) Pal.green else Pal.muted
                )
                SmallIconButton(R.drawable.ic_volume, "تلفظ", { speak(w.en) })
            }
        }
    }
}
