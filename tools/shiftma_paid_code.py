#!/usr/bin/env python3
"""ساخت کد باز شدن بخش پولی برای یک گوشی (باید با core/PaidAccess.kt یکی باشد).

استفاده:
    python3 tools/shiftma_paid_code.py AB12CD34 reading
    (شناسهٔ گوشی که کاربر فرستاده، بعد شناسهٔ بخش مثل reading یا sport)

SECRET باید دقیقاً همان PaidAccess.SECRET باشد. قبل از انتشار عوضش کنید و این فایل را عمومی نکنید.
"""
import hashlib
import hmac
import sys

SECRET = "f9343a762e6897f1dc45e90e79a9bd5d"
DIGITS = 8


def code_for(install_id: str, section_id: str, secret: str = SECRET) -> str:
    msg = f"ShiftMa|paid|{install_id.strip().upper()}|{section_id}".encode("utf-8")
    h = hmac.new(secret.encode("utf-8"), msg, hashlib.sha256).digest()
    o = h[-1] & 0x0F
    bin_ = ((h[o] & 0x7F) << 24) | (h[o + 1] << 16) | (h[o + 2] << 8) | h[o + 3]
    return str(bin_ % (10 ** DIGITS)).zfill(DIGITS)


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    print(code_for(sys.argv[1], sys.argv[2]))
