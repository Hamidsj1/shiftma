package ir.shiftapp.system

import org.junit.Assert.*
import org.junit.Test

class DiagLogTest {
    @Test fun trimTailKeepsWholeLinesAndTheEnd() {
        val text = (1..100).joinToString("\n") { "line$it" } + "\n"
        val out = DiagLog.trimTail(text, 60)
        assertTrue(out.length <= 60)
        assertTrue(out.endsWith("line100\n"))
        assertTrue(out.startsWith("line"))          // با نیمهٔ یک خط شروع نمی‌شود
        assertEquals("short", DiagLog.trimTail("short", 60))
    }

    @Test fun traceIsBoundedAndNamesTheException() {
        val t = IllegalStateException("boom", RuntimeException("cause"))
        val s = DiagLog.trace(t)
        assertTrue(s.startsWith("java.lang.IllegalStateException: boom"))
        assertTrue(s.contains("Caused by: java.lang.RuntimeException: cause"))
        assertTrue(s.lines().size < 40)
    }
}
