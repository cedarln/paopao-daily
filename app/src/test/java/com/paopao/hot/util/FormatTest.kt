package com.paopao.hot.util

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class FormatTest {

    @Test
    fun `time handles timestamps older than yesterday`() {
        val millis = LocalDate.of(2024, 3, 5)
            .atTime(9, 30)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val text = Format.time(millis)

        assertTrue("unexpected: $text", text.matches(Regex("""03-05 09:30""")))
    }

    @Test
    fun `time handles missing timestamps`() {
        assertTrue(Format.time(null).isNotBlank())
        assertTrue(Format.time(0L).isNotBlank())
    }
}
