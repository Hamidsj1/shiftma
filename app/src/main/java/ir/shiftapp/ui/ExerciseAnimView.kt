package ir.shiftapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ir.shiftapp.core.ExerciseAnim
import ir.shiftapp.core.ExerciseAnims
import ir.shiftapp.core.Pt

/**
 * نمایش انیمیشن آدمک خطی یک حرکت ورزشی. با لمس، پخش متوقف یا ادامه می‌یابد.
 * فقط وقتی روی صفحه است انیمیشن می‌گیرد (کارت حرکت باز باشد)، پس روی باتری و کارایی فشاری ندارد.
 */
@Composable
fun ExerciseAnimView(anim: ExerciseAnim, name: String, modifier: Modifier = Modifier) {
    var playing by rememberSaveable { mutableStateOf(true) }
    var u by remember(anim.id) { mutableFloatStateOf(0f) }

    LaunchedEffect(playing, anim.id) {
        if (!playing) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            u = (u + (now - last) / 1_000_000f / anim.periodMs) % 1f
            last = now
        }
    }

    val body = MaterialTheme.colorScheme.primary
    val far = body.copy(alpha = 0.42f)
    val floor = Pal.hairline
    val bg = Pal.card
    val weight = Pal.coral

    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(ExerciseAnims.W / ExerciseAnims.H)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .semantics { contentDescription = "انیمیشن حرکت $name" }
                .clickable { playing = !playing }
        ) {
            val sc = size.width / ExerciseAnims.W
            val q = ExerciseAnims.pose(anim, u)
            fun o(p: Pt) = Offset(p.x * sc, p.y * sc)
            val w = 2.4f * sc

            drawLine(floor, o(Pt(3f, ExerciseAnims.FLOOR + 1.2f)), o(Pt(97f, ExerciseAnims.FLOOR + 1.2f)), strokeWidth = 0.9f * sc, cap = StrokeCap.Round)

            fun limb(color: Color, width: Float, vararg pts: Pt) {
                val path = Path().apply {
                    moveTo(o(pts[0]).x, o(pts[0]).y)
                    for (i in 1 until pts.size) lineTo(o(pts[i]).x, o(pts[i]).y)
                }
                drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }

            // اندام دور (پشت)، سپس تنه و اندام نزدیک
            limb(far, w * 0.85f, q.shoulder, q.elbowF, q.handF)
            limb(far, w * 0.85f, q.hip, q.kneeF, q.ankleF, q.toeF)
            limb(body, w, q.hip, q.shoulder, q.neck)
            limb(body, w, q.hip, q.kneeN, q.ankleN, q.toeN)
            limb(body, w, q.shoulder, q.elbowN, q.handN)

            val hc = o(q.head)
            drawCircle(bg, ExerciseAnims.HEAD_R * sc, hc)
            drawCircle(body, ExerciseAnims.HEAD_R * sc, hc, style = Stroke(w * 0.9f))

            if (anim.dumbbell) {
                drawCircle(weight.copy(alpha = 0.55f), 2.3f * sc, o(q.handF))
                drawCircle(weight, 2.3f * sc, o(q.handN))
            }
        }
        HintText(if (playing) "برای توقف روی تصویر بزنید" else "برای ادامه روی تصویر بزنید", Modifier.padding(top = 4.dp))
    }
}

/** اگر برای این حرکت انیمیشن ساخته شده باشد نمایش می‌دهد. */
@Composable
fun ExerciseAnimOrNothing(id: String, name: String) {
    ExerciseAnims.get(id)?.let { ExerciseAnimView(it, name, Modifier.fillMaxWidth()) }
}
