package ir.shiftapp.system

import androidx.activity.ComponentActivity
import ir.cafebazaar.poolakey.Connection
import ir.cafebazaar.poolakey.ConnectionState
import ir.cafebazaar.poolakey.Payment
import ir.cafebazaar.poolakey.config.PaymentConfiguration
import ir.cafebazaar.poolakey.config.SecurityCheck
import ir.cafebazaar.poolakey.entity.PurchaseState
import ir.cafebazaar.poolakey.request.PurchaseRequest
import ir.shiftapp.core.ProQuota
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * خرید درون‌برنامه‌ای کافه‌بازار (با کتابخانهٔ رسمی Poolakey) برای باز کردن همیشگی «کاهش حجم عکس».
 *
 * - محصول «ماندگار» (یک‌بار خرید، مصرف نمی‌شود) با شناسهٔ [PRODUCT_COMPRESS]؛ همین شناسه را در پنل بازار بسازید.
 * - اتصال به سرویس بازار تنبل است: فقط وقتی کاربر وارد بخش می‌شود (بازیابی) یا دکمهٔ خرید را می‌زند.
 * - امضای هر خرید با کلید عمومی RSA همین برنامه ([RSA_PUBLIC_KEY]) در خود Poolakey بررسی می‌شود.
 * - نتیجه در [ProQuota] کش می‌شود تا بدون اینترنت هم قفل باز بماند.
 */
class BazaarBilling(private val activity: ComponentActivity) {

    companion object {
        /** شناسهٔ محصول در پنل بازار (نوع: درون‌برنامه‌ای / ماندگار). */
        const val PRODUCT_COMPRESS = "compress_unlock"

        /** کلید عمومی RSA از پنل بازار ← بخش پرداخت درون‌برنامه‌ای. محرمانه نیست. */
        const val RSA_PUBLIC_KEY =
            "MIHNMA0GCSqGSIb3DQEBAQUAA4G7ADCBtwKBrwCypcWdA9iV18fuVGlcY1W4onj12frRIz370+M0ALmwi1wsnnDSpfxRhArl6YU8uRh10c7MFJ4CGc7VoOxhhMkETEwBQvJWM0k+b3baPvgBEswXGElHFjjsS6uuEBW6LQfyfkHKA7e/LX6Fm2xDiJGwG3OnA7REOTAM0BGM0nSQiUKV6NpgXxaWnvG5twtNGFMubIrWI2fUhkKXpoVeEwHwlDYVZARxboGHKVvh2qECAwEAAQ=="

        private const val PAYLOAD = "shiftma-compress"
        const val MSG_NO_BAZAAR = "اتصال به کافه‌بازار ممکن نشد. مطمئن شوید کافه‌بازار نصب است و وارد حساب خود شده‌اید."
    }

    private val _pro = MutableStateFlow(ProQuota.isPro(activity, ProQuota.FEATURE_COMPRESS))
    /** true = «کاهش حجم عکس» برای همیشه باز است (خرید انجام شده). */
    val pro: StateFlow<Boolean> get() = _pro

    private val _busy = MutableStateFlow(false)
    /** true = در حال اتصال به بازار یا باز بودن صفحهٔ پرداخت. */
    val busy: StateFlow<Boolean> get() = _busy

    private val _message = MutableStateFlow<String?>(null)
    /** آخرین پیام برای نمایش به کاربر (خطا، لغو، موفقیت). */
    val message: StateFlow<String?> get() = _message

    private val payment by lazy(LazyThreadSafetyMode.NONE) {
        Payment(
            context = activity,
            config = PaymentConfiguration(localSecurityCheck = SecurityCheck.Enable(rsaPublicKey = RSA_PUBLIC_KEY))
        )
    }
    private var connection: Connection? = null

    fun clearMessage() { _message.value = null }

    private fun setPro(v: Boolean) {
        ProQuota.setPro(activity, ProQuota.FEATURE_COMPRESS, v)
        _pro.value = v
    }

    /** اگر وصل نیست وصل می‌شود؛ بعد [block]. در شکست [onFail]. فقط یک‌بار از این دو صدا زده می‌شود. */
    private fun withConnection(onFail: () -> Unit, block: () -> Unit) {
        if (connection?.getState() == ConnectionState.Connected) { block(); return }
        runCatching { connection?.disconnect() }
        var fired = false
        connection = payment.connect {
            connectionSucceed { if (!fired) { fired = true; block() } }
            connectionFailed { if (!fired) { fired = true; onFail() } }
            disconnected { }
        }
    }

    /**
     * خریدهای این حساب بازار را می‌پرسد و وضعیت را به‌روز می‌کند.
     * [silent] = true: هنگام ورود به بخش؛ در شکست (بدون اینترنت/بازار) چیزی نشان نمی‌دهد و وضعیت کش‌شده می‌ماند.
     */
    fun refresh(silent: Boolean = true) {
        if (_busy.value) return
        _busy.value = true
        withConnection(onFail = {
            _busy.value = false
            if (!silent) _message.value = MSG_NO_BAZAAR
        }) {
            payment.getPurchasedProducts {
                querySucceed { list ->
                    val owned = list.any { it.productId == PRODUCT_COMPRESS && it.purchaseState == PurchaseState.PURCHASED }
                    setPro(owned)
                    _busy.value = false
                    if (!silent) _message.value = if (owned) "خرید قبلی شما بازیابی شد." else "خریدی برای این حساب بازار پیدا نشد."
                }
                queryFailed {
                    _busy.value = false
                    if (!silent) _message.value = "بازیابی خرید انجام نشد؛ کمی بعد دوباره امتحان کنید."
                }
            }
        }
    }

    /** صفحهٔ پرداخت بازار را باز می‌کند. */
    fun buy() {
        if (_busy.value) return
        _busy.value = true
        _message.value = null
        withConnection(onFail = {
            _busy.value = false
            _message.value = MSG_NO_BAZAAR
        }) {
            payment.purchaseProduct(
                registry = activity.activityResultRegistry,
                request = PurchaseRequest(productId = PRODUCT_COMPRESS, payload = PAYLOAD)
            ) {
                purchaseFlowBegan { }
                failedToBeginFlow {
                    _busy.value = false
                    _message.value = "باز کردن صفحهٔ پرداخت ممکن نشد. کافه‌بازار را به‌روز کنید و دوباره امتحان کنید."
                }
                purchaseSucceed { info ->
                    _busy.value = false
                    if (info.productId == PRODUCT_COMPRESS && info.purchaseState == PurchaseState.PURCHASED) {
                        setPro(true)
                        _message.value = "خرید انجام شد؛ ممنون از حمایت شما."
                    }
                }
                purchaseCanceled {
                    _busy.value = false
                    _message.value = "خرید لغو شد."
                }
                purchaseFailed {
                    _busy.value = false
                    _message.value = "خرید انجام نشد؛ دوباره امتحان کنید."
                }
            }
        }
    }

    /** با بسته شدن صفحه صدا زده شود تا اتصال به سرویس بازار رها شود. */
    fun close() {
        runCatching { connection?.disconnect() }
        connection = null
    }
}
