package ir.shiftapp.phonebook

import org.junit.Assert.assertEquals
import org.junit.Test

class PhonebookTest {
    @Test fun fixDigitsConvertsPersianAndArabic() {
        assertEquals("0912 345", fixDigits("۰۹۱۲ ٣٤٥"))
    }

    @Test fun parseCsvSkipsCommentsAndBlankLinesAndBom() {
        val text = "\uFEFF# name,mobile\n\nعلی رضایی,۰۹۱۲۱۲۳۴۵۶۷,1234,مالی,حسابداری\n,0911,,,\nسارا,,5678\n"
        val r = parseCsv(text)
        assertEquals(2, r.size)
        assertEquals("09121234567", r[0].mobile)
        assertEquals("1234", r[0].landline)
        assertEquals("مالی", r[0].dept)
        assertEquals("حسابداری", r[0].unit)
        assertEquals("", r[1].mobile)
        assertEquals("5678", r[1].landline)
    }

    @Test fun parseCsvReadsPhotoColumnAndIgnoresPosition() {
        val b64 = "/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAYEBAUEBAYFBQUGBgYHCQ4JCQgICRINDQoOFRIWFhUSFBQXGiEcFxgfGRQUHScdHyIjJSUlFhwpLCgkKyEkJST/2wBDAQYG=="
        val text = "# name,mobile,landline,dept,unit,position,photo(base64 JPEG)  (UTF-8)\n" +
            "علی,09121234567,1234,مالی,حسابداری,,$b64\n" +
            "سارا,,5678,HSE,,مدیر,\n" +
            "رضا,0911,,,,,not-a-photo\n"
        val r = parseCsv(text)
        assertEquals(3, r.size)
        assertEquals(b64, r[0].photoData)
        assertEquals("", r[1].photoData)
        assertEquals("HSE", r[1].dept)
        assertEquals("", r[2].photoData)      // متنِ غیرتصویری عکس حساب نمی‌شود
    }

    @Test fun oldFilesWithoutPhotoColumnStillWork() {
        val r = parseCsv("علی,0912,1234,مالی,حسابداری\n")
        assertEquals(1, r.size); assertEquals("", r[0].photoData)
    }

    @Test fun onlyFourDigitNumbersGetPrefix() {
        assertEquals("+98211234,5678", PhoneActions.dialable("5678", "+98211234,"))
        assertEquals("09121234567", PhoneActions.dialable("09121234567", "+98211234"))
        assertEquals("123", PhoneActions.dialable("123", "+98211234"))
        assertEquals("5678", PhoneActions.dialable("5678", ""))
    }
}
