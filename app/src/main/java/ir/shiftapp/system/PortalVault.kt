package ir.shiftapp.system

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * نگهداری اختیاری نام کاربری و رمز سامانه‌ها (فقط وقتی کاربر خودش ذخیره کند).
 * رمزنگاری AES-256-GCM با کلیدی که داخل Android Keystore می‌ماند و از گوشی بیرون نمی‌آید؛ فقط متن رمزشده در فایل خصوصی برنامه است.
 * این فایل جدا از تنظیمات است، پس در پشتیبان، خروجی و اشتراک‌گذاری برنامه نمی‌آید. هیچ‌جا لاگ نمی‌شود.
 */
object PortalVault {
    class Cred(val user: String, val pass: String, val host: String)

    private const val ALIAS = "shiftma_portal_vault"
    private const val FILE = "portal_vault"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: ByteArray): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        return Base64.encodeToString(c.iv + c.doFinal(plain), Base64.NO_WRAP)
    }

    private fun decrypt(text: String): ByteArray {
        val raw = Base64.decode(text, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw, 0, 12))
        return c.doFinal(raw, 12, raw.size - 12)
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun has(ctx: Context, portal: String) = prefs(ctx).contains(portal)

    fun count(ctx: Context) = prefs(ctx).all.size

    /** null اگر ذخیره نشده یا خواندنش ممکن نبود (مثلاً کلید بعد از تغییر قفل گوشی از بین رفته؛ آن‌وقت ورودی پاک می‌شود). */
    fun get(ctx: Context, portal: String): Cred? {
        val enc = prefs(ctx).getString(portal, null) ?: return null
        return try {
            val j = JSONObject(String(decrypt(enc), Charsets.UTF_8))
            Cred(j.getString("u"), j.getString("p"), j.getString("h"))
        } catch (e: Exception) {
            remove(ctx, portal); null
        }
    }

    fun put(ctx: Context, portal: String, cred: Cred) {
        val json = JSONObject().put("u", cred.user).put("p", cred.pass).put("h", cred.host).toString()
        prefs(ctx).edit().putString(portal, encrypt(json.toByteArray(Charsets.UTF_8))).apply()
    }

    fun remove(ctx: Context, portal: String) { prefs(ctx).edit().remove(portal).apply() }

    fun clear(ctx: Context) { prefs(ctx).edit().clear().apply() }
}
