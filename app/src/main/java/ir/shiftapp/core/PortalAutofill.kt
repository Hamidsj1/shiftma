package ir.shiftapp.core

import org.json.JSONObject
import java.net.URI

/**
 * پر کردن خودکار فرم ورود سامانه‌ها در مرورگر داخلی. منطق خالص و قابل تست:
 * فقط روی https و فقط وقتی میزبان صفحه با میزبان ذخیره‌شده هم‌دامنه باشد پر می‌شود (تا رمز به سایت دیگری نرسد).
 */
object PortalAutofill {
    fun hostOf(url: String): String? = try { URI(url).host?.lowercase() } catch (e: Exception) { null }

    /** دو برچسب آخر میزبان («my.nicico.com» → «nicico.com»). */
    fun siteOf(host: String): String = host.lowercase().split('.').filter { it.isNotEmpty() }.takeLast(2).joinToString(".")

    fun hostMatches(savedHost: String, pageUrl: String): Boolean {
        if (savedHost.isBlank()) return false
        val uri = try { URI(pageUrl) } catch (e: Exception) { return false }
        if (!"https".equals(uri.scheme, ignoreCase = true)) return false
        val h = uri.host?.lowercase() ?: return false
        return siteOf(h) == siteOf(savedHost)
    }

    /**
     * اسکریپت پر کردن: اولین فیلد رمز دیدنی را پیدا می‌کند، آخرین فیلد متنی دیدنی قبل از آن را نام کاربری می‌گیرد
     * و فقط فیلدهای خالی را با رویدادهای input/change پر می‌کند (برای سایت‌های React/Angular). دکمهٔ ورود زده نمی‌شود.
     */
    fun fillScript(user: String, pass: String): String =
        "(function(u,p){" +
            "function set(el,v){var d=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value');d.set.call(el,v);" +
            "el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));}" +
            "function vis(i){return !i.disabled&&!i.readOnly&&i.offsetParent!==null;}" +
            "var all=Array.prototype.slice.call(document.querySelectorAll('input'));" +
            "var pw=all.filter(function(i){return (i.type||'').toLowerCase()==='password'&&vis(i);})[0];" +
            "if(!pw)return 'nopw';" +
            "var ok=['text','email','tel','number',''];var us=null;" +
            "all.forEach(function(i){if(i!==pw&&vis(i)&&ok.indexOf((i.getAttribute('type')||'').toLowerCase())>=0&&(i.compareDocumentPosition(pw)&4))us=i;});" +
            "if(us&&!us.value)set(us,u);if(!pw.value)set(pw,p);return 'ok';" +
            "})(" + JSONObject.quote(user) + "," + JSONObject.quote(pass) + ")"
}
