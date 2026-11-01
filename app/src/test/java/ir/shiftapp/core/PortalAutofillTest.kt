package ir.shiftapp.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PortalAutofillTest {
    @Test fun hostParsing() {
        assertEquals("my.nicico.com", PortalAutofill.hostOf("https://my.nicico.com/Authentication/Login?ReturnUrl=%2Fx"))
        assertNull(PortalAutofill.hostOf("نامعتبر"))
        assertEquals("nicico.com", PortalAutofill.siteOf("my.nicico.com"))
        assertEquals("nowavaran-mes.ir", PortalAutofill.siteOf("nowavaran-mes.ir"))
    }

    @Test fun fillsOnlyOnSameSiteOverHttps() {
        assertTrue(PortalAutofill.hostMatches("my.nicico.com", "https://my.nicico.com/Authentication/Login"))
        assertTrue(PortalAutofill.hostMatches("my.nicico.com", "https://sso.nicico.com/login"))
        assertFalse(PortalAutofill.hostMatches("my.nicico.com", "http://my.nicico.com/login"))
        assertFalse(PortalAutofill.hostMatches("my.nicico.com", "https://nicico.com.evil.example/login"))
        assertFalse(PortalAutofill.hostMatches("my.nicico.com", "https://evil.com/my.nicico.com"))
        assertFalse(PortalAutofill.hostMatches("", "https://my.nicico.com/"))
    }

    @Test fun scriptEscapesSecrets() {
        val js = PortalAutofill.fillScript("ali", "p\"w';alert(1);//\\")
        assertTrue(js.contains("\"ali\""))
        assertFalse(js.contains("p\"w'"))      // نقل‌قول خام نباید به اسکریپت راه پیدا کند
        assertTrue(js.contains("p\\\"w"))
    }
}
