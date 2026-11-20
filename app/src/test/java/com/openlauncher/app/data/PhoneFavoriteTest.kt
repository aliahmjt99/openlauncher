package com.openlauncher.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneFavoriteTest {
    @Test
    fun normalizesFormattedAndInternationalNumbers() {
        assertEquals("+9647701234567", normalizeDialNumber("+964 (770) 123-4567"))
        assertEquals("0123", normalizeDialNumber("01.23"))
    }

    @Test
    fun rejectsInvalidAndUnsupportedDialStrings() {
        for (number in listOf("", "++123", "123+4", "tel:123", "1,2", "+", "9".repeat(41))) {
            assertNull(normalizeDialNumber(number))
        }
    }
}
