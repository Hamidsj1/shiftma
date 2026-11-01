#!/usr/bin/env python3
"""رمز روزانهٔ «دسترسی ویژه» شیفت ما (آفلاین). نمونه:
    python3 tools/shiftma_code.py            # رمز امروز (به وقت تهران)
    python3 tools/shiftma_code.py 2026-10-15 # رمز یک روز مشخص
    python3 tools/shiftma_code.py --week     # رمز ۷ روز آینده
کلید باید دقیقاً با SpecialAccess.SECRET در برنامه یکی باشد. این فایل را عمومی نکنید."""
import hmac, hashlib, sys, datetime
from zoneinfo import ZoneInfo

SECRET = "29bee50893c8bc2df06c5269cb1135b4"

def code_for(d: datetime.date) -> str:
    h = hmac.new(SECRET.encode(), f"ShiftMa|{d.isoformat()}".encode(), hashlib.sha256).digest()
    o = h[-1] & 0x0F
    n = int.from_bytes(h[o:o + 4], "big") & 0x7FFFFFFF
    return str(n % 10**6).zfill(6)

if __name__ == "__main__":
    today = datetime.datetime.now(ZoneInfo("Asia/Tehran")).date()
    arg = sys.argv[1] if len(sys.argv) > 1 else ""
    if arg == "--week":
        for i in range(7):
            d = today + datetime.timedelta(days=i)
            print(d, code_for(d))
    else:
        d = datetime.date.fromisoformat(arg) if arg else today
        print(d, code_for(d))
