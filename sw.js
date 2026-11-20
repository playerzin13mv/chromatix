self.addEventListener('install',e=>e.waitUntil(caches.open('c1').then(c=>c.addAll(['./','index.html','manifest.json','icon-192.png','icon-512.png']))));
self.addEventListener('fetch',e=>e.respondWith(fetch(e.request).catch(()=>caches.match(e.request))));
