const CACHE_NAME = 'buku-kas-v8-cache-v1';
const ASSETS_TO_CACHE = [
  'index.html',
  'manifest.json',
  'icon-192.png',
  'icon-512.png'
];

// 1. Tahap Instalasi: Simpan aset utama secara aman
self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => {
      // Menggunakan mode 'cors' atau 'no-cors' agar Vercel tidak memblokir request
      return Promise.all(
        ASSETS_TO_CACHE.map((url) => {
          return fetch(new Request(url, { cache: 'reload' }))
            .then((response) => {
              if (response.ok) return cache.put(url, response);
              throw new Error(`Gagal memuat file: ${url}`);
            })
            .catch((err) => console.warn('Aset dilewati:', url, err));
        })
      );
    })
  );
  self.skipWaiting();
});

// 2. Tahap Aktivasi: Bersihkan cache usang
self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((cacheNames) => {
      return Promise.all(
        cacheNames.map((cache) => {
          if (cache !== CACHE_NAME) {
            return caches.delete(cache);
          }
        })
      );
    })
  );
  self.clients.claim();
});

// 3. Tahap Fetch (Wajib untuk Syarat Instalasi PWA)
self.addEventListener('fetch', (event) => {
  // Abaikan request luar (seperti Supabase) agar tidak bentrok
  if (!event.request.url.startsWith(self.location.origin) || event.request.method !== 'GET') {
    return;
  }

  event.respondWith(
    fetch(event.request)
      .then((response) => {
        if (response && response.status === 200) {
          const responseToCache = response.clone();
          caches.open(CACHE_NAME).then((cache) => {
            cache.put(event.request, responseToCache);
          });
        }
        return response;
      })
      .catch(() => {
        return caches.match(event.request).then((cachedResponse) => {
          if (cachedResponse) return cachedResponse;
          // Jika offline total, arahkan navigasi ke index.html
          if (event.request.mode === 'navigate') {
            return caches.match('index.html');
          }
        });
      })
  );
});
