const CACHE_NAME='jeevani-app-shell-v1';
const APP_SHELL=[
  '/',
  '/manifest.webmanifest',
  '/icons/jeevani-192.png',
  '/icons/jeevani-512.png',
  '/icons/jeevani.svg',
  '/styles.css',
  '/chat.css',
  '/hospitals.css',
  '/sos.css',
  '/i18n.js',
  '/app.js'
];

self.addEventListener('install',event=>{
  event.waitUntil(caches.open(CACHE_NAME).then(cache=>cache.addAll(APP_SHELL)));
  self.skipWaiting();
});

self.addEventListener('activate',event=>{
  event.waitUntil(
    caches.keys().then(keys=>Promise.all(
      keys.filter(key=>key.startsWith('jeevani-app-shell-')&&key!==CACHE_NAME)
        .map(key=>caches.delete(key))
    ))
  );
  self.clients.claim();
});

self.addEventListener('fetch',event=>{
  const request=event.request;
  const url=new URL(request.url);
  if(request.method!=='GET'||url.origin!==self.location.origin)return;
  if(url.pathname.startsWith('/api/')||url.pathname.startsWith('/uploads/'))return;

  if(request.mode==='navigate'){
    event.respondWith(
      fetch(request).then(response=>{
        if(response.ok){
          const copy=response.clone();
          caches.open(CACHE_NAME).then(cache=>cache.put('/',copy));
        }
        return response;
      }).catch(async()=>{
        const cached=await caches.match('/');
        return cached||Response.error();
      })
    );
    return;
  }

  event.respondWith(
    caches.match(request).then(cached=>cached||fetch(request).then(response=>{
      if(response.ok&&response.type==='basic'){
        const copy=response.clone();
        caches.open(CACHE_NAME).then(cache=>cache.put(request,copy));
      }
      return response;
    }))
  );
});
