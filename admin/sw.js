/* سرویس‌ورکر پنل: فقط خود فایل‌های پنل را برای باز شدن آفلاین کش می‌کند؛ درخواست‌های گیت‌هاب و API هرگز کش نمی‌شوند. */
const CACHE = 'shiftma-admin-v1';
const FILES = ['./shiftma-admin-4.html', './manifest.webmanifest', './icon-192.png', './icon-512.png'];
self.addEventListener('install', e => { e.waitUntil(caches.open(CACHE).then(c => c.addAll(FILES)).then(() => self.skipWaiting())); });
self.addEventListener('activate', e => { e.waitUntil(caches.keys().then(ks => Promise.all(ks.filter(k => k !== CACHE).map(k => caches.delete(k)))).then(() => self.clients.claim())); });
self.addEventListener('fetch', e => {
  const u = new URL(e.request.url);
  if (e.request.method !== 'GET' || u.origin !== location.origin) return;   // فقط همین مبدأ
  e.respondWith(fetch(e.request).then(r => { const cp = r.clone(); caches.open(CACHE).then(c => c.put(e.request, cp)); return r; }).catch(() => caches.match(e.request)));   // اول شبکه (نسخهٔ تازه)، بعد کش
});
