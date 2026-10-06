const state={user:null,page:'dashboard',reports:[],contacts:[],dashboard:null,detail:null,hospitals:[],chat:[],chatError:null,lang:'en',hospitalMap:null,hospitalMarkers:null,userMarker:null,sosLocation:null};
let hospitalSearchTimer=null,hospitalRequestController=null,hospitalRequestId=0,lastHospitalQueryKey='';
const overpassEndpoints=['https://overpass-api.de/api/interpreter','https://overpass.kumi.systems/api/interpreter','https://overpass.private.coffee/api/interpreter'];
const $=s=>document.querySelector(s); const app=$('#app');
function toast(msg){const t=$('#toast');t.textContent=msg;t.classList.add('show');setTimeout(()=>t.classList.remove('show'),2600)}
async function api(url,opt={}){const res=await fetch(url,{credentials:'include',...opt});let data={};try{data=await res.json()}catch{}if(!res.ok){const message=Array.isArray(data.detail)?'Invalid input. Check the form and try again.':data.detail||data.message||'Request failed';throw new Error(translateUiText(String(message)))}return data}
function escapeHtml(s=''){return String(s).replace(/[&<>'"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[c]))}
function languageOptions(selected){return Object.entries({en:'English',te:'తెలుగు',hi:'हिन्दी',ta:'தமிழ்',kn:'ಕನ್ನಡ',ml:'മലയാളം'}).map(([k,v])=>`<option value="${k}" ${k===selected?'selected':''}>${v}</option>`).join('')}
function authView(){app.innerHTML=`<div class="auth"><div class="auth-card"><div class="brand"><div class="brand-mark">✚</div>Jeevani AI</div><p class="muted">Your multilingual personal healthcare and emergency companion.</p><div class="tabs"><button id="tabLogin" class="active">Login</button><button id="tabRegister">Create account</button></div><div id="authForm"></div><p class="footer-note">For information and assistance only. AI output does not replace professional medical advice.</p></div></div>`;loginForm();$('#tabLogin').onclick=loginForm;$('#tabRegister').onclick=registerForm}
function loginForm(){setTab('tabLogin');$('#authForm').innerHTML=`<div class="field"><label>Email</label><input id="email" type="email" placeholder="you@example.com"></div><div class="field"><label>Password</label><input id="password" type="password" placeholder="••••••••"></div><button class="btn primary full" id="loginBtn">Login</button>`;$('#loginBtn').onclick=async()=>{try{await api('/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email:$('#email').value,password:$('#password').value})});await loadApp()}catch(e){toast(e.message)}}}
function registerForm(){setTab('tabRegister');$('#authForm').innerHTML=`<div class="field"><label>Full name</label><input id="name" placeholder="Your name"></div><div class="field"><label>Email</label><input id="email" type="email" placeholder="you@example.com"></div><div class="field"><label>Password</label><input id="password" type="password" placeholder="At least 6 characters"></div><div class="grid two"><div class="field"><label>Phone</label><input id="phone"></div><div class="field"><label>Blood group</label><input id="blood" placeholder="e.g. O+"></div></div><div class="field"><label>Preferred language</label><select id="lang" onchange="setAppLanguage(this.value)">${languageOptions('en')}</select></div><button class="btn primary full" id="registerBtn">Create account</button>`;$('#registerBtn').onclick=async()=>{try{await api('/api/auth/register',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({name:$('#name').value,email:$('#email').value,password:$('#password').value,phone:$('#phone').value,blood_group:$('#blood').value,preferred_language:$('#lang').value})});toast('Account created. Please log in.');loginForm()}catch(e){toast(e.message)}}}
function setTab(id){document.querySelectorAll('.tabs button').forEach(b=>b.classList.remove('active'));$('#'+id)?.classList.add('active')}
async function loadApp(){try{state.user=await api('/api/auth/me');state.lang=state.user.preferred_language||'en';setAppLanguage(state.lang);await refresh();renderShell()}catch{state.sosLocation=null;authView()}}
async function refresh(){[state.dashboard,state.reports,state.contacts]=await Promise.all([api('/api/dashboard'),api('/api/reports'),api('/api/emergency-contacts')])}
function nav(){return `<div class="nav">${[['dashboard','⌂','Dashboard'],['reports','▣','Medical Reports'],['insights','✦','AI Insights'],['chat','◌','Health Assistant'],['hospitals','⌖','Nearby Hospitals'],['contacts','♙','Emergency Contacts'],['profile','⚙','Profile']].map(x=>`<button class="${state.page===x[0]?'active':''}" onclick="go('${x[0]}')">${x[1]} &nbsp; ${x[2]}</button>`).join('')}</div>`}
function renderShell(){app.innerHTML=`<div class="shell"><aside class="side"><div class="brand"><div class="brand-mark">✚</div>Jeevani AI</div>${nav()}<div style="margin-top:auto"><div class="small" style="color:#a8cbc5">Signed in as</div><strong data-no-translate>${escapeHtml(state.user.name)}</strong><button class="btn ghost full" style="margin-top:12px" onclick="logout()">Logout</button></div></aside><main class="main"><div class="top"><div><div class="small muted">Jeevani AI</div><h1 style="margin:4px 0">${title()}</h1></div><div class="row"><select class="field" style="margin:0" id="topLang" onchange="changeLanguage(this.value)">${languageOptions(state.lang)}</select><button class="btn danger" onclick="go('sos')">SOS</button></div></div><div id="content"></div><div class="mobile-nav">${[['dashboard','⌂','Dashboard'],['reports','▣','Reports'],['insights','✦','AI Health Insights'],['chat','◌','Health Assistant'],['hospitals','⌖','Nearby Hospitals'],['profile','⚙','Profile']].map(x=>`<button class="${state.page===x[0]?'active':''}" onclick="go('${x[0]}')">${x[1]}<br>${x[2]}</button>`).join('')}</div></main></div>`;renderPage();setAppLanguage(state.lang)}
function title(){return ({dashboard:'Good to see you',reports:'Medical Reports',insights:'AI Health Insights',chat:'Health Assistant',hospitals:'Nearby Hospitals',contacts:'Emergency Contacts',profile:'Your Profile',sos:'Emergency SOS'})[state.page]||'Jeevani AI'}
async function go(page){state.page=page;renderShell()}
function renderPage(){const c=$('#content');if(!c)return;({dashboard:dashboardPage,reports:reportsPage,insights:insightsPage,chat:chatPage,hospitals:hospitalsPage,contacts:contactsPage,profile:profilePage,sos:sosPage}[state.page]||dashboardPage)(c)}
function dashboardPage(c){c.innerHTML=`<div class="grid two"><div class="card hero"><div class="small">Personal healthcare companion</div><h2>Keep your health information organized.</h2><p class="muted">Upload reports, understand important observations, and keep emergency support ready.</p><div class="row"><button class="btn" style="background:white;color:#0f766e" onclick="go('reports')">Manage reports</button><button class="btn" style="background:#ffffff22;color:white" onclick="go('hospitals')">Find hospitals</button></div></div><div class="card sos"><div class="small">Emergency assistance</div><h2>Need help?</h2><p>Share your current location with your registered emergency contacts.</p><button class="btn" onclick="go('sos')">Open SOS</button></div></div><div class="grid stats" style="margin-top:16px"><div class="card stat"><div class="icon">▣</div><div class="muted">Reports</div><div class="value">${state.dashboard.reports_count}</div></div><div class="card stat"><div class="icon">✦</div><div class="muted">AI analyses</div><div class="value">${state.reports.length}</div></div><div class="card stat"><div class="icon">♙</div><div class="muted">Emergency contacts</div><div class="value">${state.dashboard.contacts_count}</div></div><div class="card stat"><div class="icon">◎</div><div class="muted">Language</div><div class="value" style="font-size:19px">${escapeHtml({'en':'English','te':'తెలుగు','hi':'हिन्दी','ta':'தமிழ்','kn':'ಕನ್ನಡ','ml':'മലയാളം'}[state.lang]||'English')}</div></div></div><div class="section-title"><h3>Recent reports</h3><button class="btn ghost" onclick="go('reports')">View all</button></div><div class="card">${state.dashboard.latest_reports.length?state.dashboard.latest_reports.map(reportRow).join(''):`<div class="empty">No reports yet. Upload your first report to begin.</div>`}</div><div class="footer-note">AI-generated information is for educational purposes only and should not replace professional medical advice.</div>`}
function reportRow(r){return `<div class="report"><div><strong data-no-translate>${escapeHtml(r.report_name)}</strong><div class="small muted">${escapeHtml(r.report_type)} · ${escapeHtml(r.report_date||'Date not provided')}</div></div><div class="row"><span class="badge ${r.priority==='Needs Medical Review'?'danger':r.priority==='Moderate Attention'?'warn':''}">${escapeHtml(r.priority||'Pending')}</span><button class="btn ghost" onclick="viewReport(${r.id})">Open</button></div></div>`}
function reportsPage(c){c.innerHTML=`<div class="card"><div class="row" style="justify-content:space-between"><div><h3 style="margin:0">Your medical document library</h3><p class="muted">Store and classify reports in one place.</p></div><button class="btn primary" onclick="uploadModal()">+ Upload report</button></div></div><div class="card" style="margin-top:16px">${state.reports.length?state.reports.map(reportRow).join(''):`<div class="empty">Upload a PDF or image report to get started.</div>`}</div>`}
function insightsPage(c){const reviewed=state.reports.filter(r=>r.priority);c.innerHTML=`<div class="grid two">${reviewed.length?reviewed.map(r=>`<div class="card"><div class="row" style="justify-content:space-between"><strong data-no-translate>${escapeHtml(r.report_name)}</strong><span class="badge ${r.priority==='Needs Medical Review'?'danger':r.priority==='Moderate Attention'?'warn':''}">${escapeHtml(r.priority)}</span></div><p data-no-translate>${escapeHtml(r.summary||'Open the report for details.')}</p><button class="btn ghost" onclick="viewReport(${r.id})">View analysis</button></div>`).join(''):`<div class="card"><div class="empty">Upload a report to generate AI insights.</div></div>`}</div><div class="notice">AI summaries are informational and must not be treated as a diagnosis or a replacement for professional medical advice.</div>`}
function chatPage(c){c.innerHTML=`<div class="card chat-card"><div class="chat-heading"><div><h3>Your health assistant</h3><p class="muted">Ask about using Jeevani or general health information.</p></div><span class="badge">Gemini</span></div><div class="notice">Only Jeevani and general health topics. Do not use for emergencies.</div><div id="chatMessages" class="chat-messages" aria-live="polite"></div><form class="chat-form" onsubmit="sendChat(event)"><textarea id="chatInput" maxlength="1600" rows="2" placeholder="Type your message" required></textarea><button id="chatSend" class="btn primary" type="submit">Send</button></form><p class="small muted">Chat history is cleared when you log out.</p></div>`;renderChatMessages()}
function renderChatMessages(){const messages=$('#chatMessages');if(!messages)return;let html=state.chat.length?state.chat.map(m=>`<div class="chat-message ${m.role}" data-no-translate>${escapeHtml(m.content)}</div>`).join(''):`<div class="chat-empty"><strong>Start a conversation</strong><p>I can help with Jeevani features and general health education. I cannot diagnose or replace a clinician.</p></div>`;if(state.chatError)html+=`<div class="chat-message assistant chat-error" data-no-translate style="display:flex;align-items:center;gap:10px"><span>${escapeHtml(state.chatError)}</span><button class="btn ghost chat-retry" onclick="retryChat()">${escapeHtml(translateUiText('Try again'))}</button></div>`;messages.innerHTML=html;messages.scrollTop=messages.scrollHeight}
async function requestChatReply(){state.chatError=null;const button=$('#chatSend');if(button){button.disabled=true;button.textContent='Thinking…'}renderChatMessages();try{const response=await api('/api/chat',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({messages:state.chat.slice(-12)})});state.chat.push({role:'assistant',content:response.reply})}catch(error){state.chatError=translateUiText(error.message||'The assistant is temporarily unavailable')}finally{renderChatMessages();const sendButton=$('#chatSend');if(sendButton){sendButton.disabled=false;sendButton.textContent='Send'}if($('#chatInput'))$('#chatInput').focus()}}
async function sendChat(event){event.preventDefault();const input=$('#chatInput');const content=input.value.trim();if(!content){toast('Type a message first');return}if(state.chatError&&state.chat.at(-1)?.role==='user')state.chat.pop();state.chat.push({role:'user',content});input.value='';await requestChatReply()}
async function retryChat(){if(state.chat.at(-1)?.role==='user')await requestChatReply()}
function hospitalsPage(c){
  if(hospitalSearchTimer)clearTimeout(hospitalSearchTimer);
  hospitalSearchTimer=null;
  hospitalRequestId++;
  hospitalRequestController?.abort();
  hospitalRequestController=null;
  state.hospitalMap?.remove();
  state.hospitalMap=null;
  state.hospitalMarkers=null;
  state.userMarker=null;
  state.hospitals=[];
  lastHospitalQueryKey='';
  c.innerHTML=`<div class="card"><div class="row hospital-toolbar"><div><h3 style="margin:0">Find nearby hospitals and clinics</h3><p class="muted">Search within 5 km of your location or the center of the map.</p></div><div class="row"><button class="btn primary" onclick="findHospitals()">Use my location</button><button class="btn ghost" onclick="searchHospitals(true)">Search this area</button></div></div><div id="hospitalMap" class="hospital-map" role="region" aria-label="Map showing nearby hospitals and clinics"></div><p id="hospitalStatus" class="small muted" aria-live="polite">Requesting your location. Your search coordinates are sent to the public OpenStreetMap Overpass service.</p><div id="hospitalList" class="hospital-results"><div class="empty">Waiting for your location to search nearby hospitals and clinics…</div></div></div>`;
  if(typeof L==='undefined'){
    setHospitalStatus('The map library could not be loaded. Check your connection and reload the page.',true);
    return;
  }
  const sosLocation=state.sosLocation;
  const hasSOSLocation=Number.isFinite(sosLocation?.latitude)&&Number.isFinite(sosLocation?.longitude);
  const map=L.map('hospitalMap').setView(hasSOSLocation?[sosLocation.latitude,sosLocation.longitude]:[17.385,78.4867],hasSOSLocation?14:13);
  state.hospitalMap=map;
  state.hospitalMarkers=L.layerGroup().addTo(map);
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{
    maxZoom:19,
    attribution:'&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
  }).addTo(map);
  map.on('moveend',()=>scheduleHospitalSearch(false));
  map.on('locationfound',event=>{
    if(state.userMarker)map.removeLayer(state.userMarker);
    state.userMarker=L.circleMarker(event.latlng,{radius:7,color:'#075985',weight:2,fillColor:'#38bdf8',fillOpacity:1}).addTo(map).bindPopup('Your location');
    map.setView(event.latlng,Math.min(map.getZoom(),14));
    scheduleHospitalSearch(true);
  });
  map.on('locationerror',()=>{
    setHospitalStatus('Could not access your location. Showing hospitals near the default map area; allow location access or move the map to search elsewhere.',true);
    searchHospitals(true);
  });
  if(hasSOSLocation){
    state.userMarker=L.circleMarker([sosLocation.latitude,sosLocation.longitude],{radius:7,color:'#075985',weight:2,fillColor:'#38bdf8',fillOpacity:1}).addTo(map).bindPopup('SOS location');
    setHospitalStatus('Searching within 5 km of the SOS location. Your coordinates are sent to the public OpenStreetMap Overpass service.');
    searchHospitals(true);
  }else requestHospitalLocation();
}
function requestHospitalLocation(){
  if(!state.hospitalMap)return;
  setHospitalStatus('Requesting your location…');
  state.hospitalMap.locate({setView:false,maxZoom:14,timeout:10000,maximumAge:60000,enableHighAccuracy:false});
}
function setHospitalStatus(message,isError=false){
  const status=$('#hospitalStatus');
  if(!status)return;
  status.textContent=message;
  status.classList.toggle('danger-text',isError);
}
function scheduleHospitalSearch(force=false){
  if(hospitalSearchTimer)clearTimeout(hospitalSearchTimer);
  hospitalRequestId++;
  hospitalRequestController?.abort();
  hospitalRequestController=null;
  hospitalSearchTimer=setTimeout(()=>searchHospitals(force),650);
}
async function searchHospitals(force=false){
  if(!state.hospitalMap){if(force)toast('The map is not available. Reload the page and try again.');return}
  if(hospitalSearchTimer){clearTimeout(hospitalSearchTimer);hospitalSearchTimer=null}
  const center=state.hospitalMap.getCenter();
  const key=`${center.lat.toFixed(3)},${center.lng.toFixed(3)}`;
  if(!force&&key===lastHospitalQueryKey)return;
  const requestId=++hospitalRequestId;
  hospitalRequestController?.abort();
  const controller=new AbortController();
  hospitalRequestController=controller;
  const timeout=setTimeout(()=>controller.abort(),60000);
  setHospitalStatus('Searching OpenStreetMap for hospitals and clinics…');
  const list=$('#hospitalList');
  if(list)list.innerHTML='<div class="empty">Loading nearby healthcare locations…</div>';
  const query=`[out:json][timeout:20];(nwr["amenity"~"^(hospital|clinic)$"](around:5000,${center.lat},${center.lng}););out center tags;`;
  try{
    let data=null,lastError=null,fatalError=false;
    for(let index=0;index<overpassEndpoints.length;index++){
      try{
        const response=await fetch(overpassEndpoints[index],{
          method:'POST',
          signal:controller.signal,
          headers:{Accept:'application/json','Content-Type':'application/x-www-form-urlencoded;charset=UTF-8'},
          body:`data=${encodeURIComponent(query)}`
        });
        if(response.ok){data=await response.json();break}
        lastError=new Error(response.status===429?'The public map service is busy.':`OpenStreetMap search failed (${response.status}).`);
        if(response.status!==429&&response.status<500){fatalError=true;break}
      }catch(error){
        if(error.name==='AbortError')throw error;
        lastError=error;
      }
      if(fatalError)throw lastError;
      if(index<overpassEndpoints.length-1){
        setHospitalStatus(`Map service ${index+1} is unavailable; trying another OpenStreetMap service…`);
      }
    }
    if(!data){
      const reason=lastError?.name==='TypeError'?'All public OpenStreetMap search services are unavailable. Try again soon.':lastError?.message;
      throw new Error(reason||'OpenStreetMap search failed. Try again in a moment.');
    }
    if(!Array.isArray(data.elements))throw new Error('The map service returned an unexpected response.');
    if(requestId!==hospitalRequestId)return;
    lastHospitalQueryKey=key;
    const places=data.elements.map(element=>{
      const tags=element.tags||{};
      const lat=Number.isFinite(element.lat)?element.lat:element.center?.lat;
      const lng=Number.isFinite(element.lon)?element.lon:element.center?.lon;
      if(!Number.isFinite(lat)||!Number.isFinite(lng))return null;
      const address=[tags['addr:housenumber'],tags['addr:street'],tags['addr:city']].filter(Boolean).join(' ');
      return {lat,lng,name:tags.name||tags['name:en']||'Unnamed healthcare facility',type:tags.healthcare||tags.amenity||'Healthcare facility',emergency:tags.emergency||'Not specified',phone:tags.phone||tags['contact:phone']||'',address};
    }).filter(Boolean);
    state.hospitals=places;
    renderHospitalResults(places);
    setHospitalStatus(places.length?`Found ${places.length} ${places.length===1?'healthcare location':'healthcare locations'} within 5 km. Results come from OpenStreetMap and may be incomplete.`:'No hospitals or clinics were found within 5 km. OpenStreetMap data may be incomplete.');
  }catch(error){
    if(requestId!==hospitalRequestId)return;
    const message=error.name==='AbortError'?'The map search timed out. Please try again.':error.message||'Could not load healthcare locations. Please try again.';
    setHospitalStatus(message,true);
    if(list)list.innerHTML=`<div class="empty">${escapeHtml(message)}</div>`;
    toast(message);
  }finally{
    clearTimeout(timeout);
    if(requestId===hospitalRequestId)hospitalRequestController=null;
  }
}
function renderHospitalResults(places){
  if(!state.hospitalMap||!state.hospitalMarkers)return;
  state.hospitalMarkers.clearLayers();
  const list=$('#hospitalList');
  if(!list)return;
  if(!places.length){list.innerHTML='<div class="empty">No results in this area. Try moving the map or searching near your location.</div>';return}
  list.innerHTML=places.map(place=>{
    const marker=L.circleMarker([place.lat,place.lng],{radius:8,color:'#b42318',weight:2,fillColor:'#ef4444',fillOpacity:.85}).addTo(state.hospitalMarkers);
    const popup=`<div class="hospital-popup"><strong>${escapeHtml(place.name)}</strong><div>Type: ${escapeHtml(place.type)}</div><div>Emergency: ${escapeHtml(place.emergency)}</div></div>`;
    marker.bindPopup(popup);
    const address=place.address||'Address not listed in OpenStreetMap';
    const phone=String(place.phone).trim();
    return `<article class="hospital"><div><strong data-no-translate>${escapeHtml(place.name)}</strong><div class="small muted">${escapeHtml(address)} · ${escapeHtml(place.type)}</div><div class="small muted">Emergency services: ${escapeHtml(place.emergency)}</div></div><div class="row hospital-actions">${phone?`<a class="btn ghost" href="tel:${encodeURIComponent(phone)}">Call</a>`:''}<a class="btn primary" target="_blank" rel="noopener noreferrer" href="https://www.openstreetmap.org/?mlat=${place.lat}&mlon=${place.lng}#map=17/${place.lat}/${place.lng}">Directions</a><button class="btn ghost" onclick="focusHospital(${place.lat},${place.lng})">Show on map</button></div></article>`;
  }).join('');
}
function focusHospital(lat,lng){
  if(!state.hospitalMap)return;
  state.hospitalMap.setView([lat,lng],16);
  state.hospitalMarkers.eachLayer(marker=>{
    if(marker.getLatLng().lat===lat&&marker.getLatLng().lng===lng)marker.openPopup();
  });
}
function findHospitals(){
  if(!state.hospitalMap){toast('The map is not available. Reload the page and try again.');return}
  requestHospitalLocation();
}
function contactsPage(c){c.innerHTML=`<div class="card"><div class="row" style="justify-content:space-between"><div><h3 style="margin:0">Emergency contacts</h3><p class="muted">Contacts are used when you create an SOS alert.</p></div><button class="btn primary" onclick="contactModal()">+ Add contact</button></div><div style="margin-top:12px">${state.contacts.length?state.contacts.map(x=>`<div class="report"><div><strong data-no-translate>${escapeHtml(x.name)}</strong><div class="small muted">${escapeHtml(x.relationship||'Emergency contact')} · ${escapeHtml(x.phone)}${x.email?` · ${escapeHtml(x.email)}`:''}</div></div><div class="row"><button class="btn ghost" onclick="contactModal(${x.id})">Edit</button><button class="btn danger" onclick="deleteContact(${x.id})">Delete</button></div></div>`).join(''):`<div class="empty">No emergency contacts added.</div>`}</div></div>`}
function profilePage(c){const u=state.user;c.innerHTML=`<div class="card"><h3>Profile</h3><div class="grid two"><div class="field"><label>Full name</label><input id="pname" value="${escapeHtml(u.name)}"></div><div class="field"><label>Phone</label><input id="pphone" value="${escapeHtml(u.phone||'')}"></div><div class="field"><label>Date of birth</label><input id="pdob" type="date" value="${escapeHtml(u.dob||'')}"></div><div class="field"><label>Gender</label><input id="pgender" value="${escapeHtml(u.gender||'')}"></div><div class="field"><label>Blood group</label><input id="pblood" value="${escapeHtml(u.blood_group||'')}"></div><div class="field"><label>Preferred language</label><select id="plang">${languageOptions(state.lang)}</select></div></div><button class="btn primary" onclick="saveProfile()">Save changes</button></div>`}
function sosPage(c){c.innerHTML=`<div class="card sos"><h2>Emergency SOS</h2><p>Use this when you need immediate assistance. Jeevani AI will save an SOS event and prepare a location message for your registered contacts. You choose whether to send each SMS or place each call.</p><button class="btn" id="sendSOS" style="font-size:18px;padding:15px 24px" onclick="triggerSOS()">🚨 Send SOS Alert</button></div><div class="card" style="margin-top:16px"><h3>Before using SOS</h3><ul class="list"><li>Allow location access to include your current coordinates.</li><li>SMS opens in your phone's messaging app; review and tap Send there.</li><li>For a life-threatening emergency, contact local emergency services directly. This app does not call emergency services.</li></ul></div><div class="card" style="margin-top:16px"><h3>Recent SOS alerts</h3><div id="sosHistory"><div class="empty">Loading alert history…</div></div></div>`;loadSOSHistory()}
function uploadModal(){showModal(`<h3>Upload medical report</h3><div class="field"><label>PDF or image</label><input id="file" type="file" accept=".pdf,.png,.jpg,.jpeg,.webp"></div><div class="grid two"><div class="field"><label>Report date</label><input id="rdate" type="date"></div><div class="field"><label>Doctor/Hospital</label><input id="rdh" placeholder="Optional"></div></div><div class="notice">Gemini analysis sends extracted report text to Google for processing. Leave this unchecked to use local analysis.</div><label class="row" style="align-items:flex-start;margin:12px 0"><input id="geminiConsent" type="checkbox" style="width:16px;min-width:16px;margin-top:3px"><span>I agree to send this report's extracted text to Gemini.</span></label><button class="btn primary" onclick="uploadReport()">Upload & analyze</button> <button class="btn ghost" onclick="closeModal()">Cancel</button>`)}
async function uploadReport(){const f=$('#file').files[0];if(!f){toast('Choose a file');return}const fd=new FormData();fd.append('file',f);fd.append('report_date',$('#rdate').value);fd.append('doctor_hospital',$('#rdh').value);fd.append('gemini_consent',$('#geminiConsent').checked);try{await api('/api/reports/upload',{method:'POST',body:fd});closeModal();await refresh();go('reports');toast('Report uploaded and analyzed')}catch(e){toast(e.message)}}
async function viewReport(id){try{state.detail=await api(`/api/reports/${id}`);showModal(`<h3 data-no-translate>${escapeHtml(state.detail.report_name)}</h3><div class="row"><span class="badge">${escapeHtml(state.detail.report_type)}</span><span class="badge ${state.detail.priority==='Needs Medical Review'?'danger':state.detail.priority==='Moderate Attention'?'warn':''}">${escapeHtml(state.detail.priority)}</span></div><div class="notice"><strong>Summary</strong><p data-no-translate>${escapeHtml(state.detail.summary)}</p></div><h4>Findings</h4><ul class="list">${state.detail.findings.map(x=>`<li data-no-translate>${escapeHtml(x)}</li>`).join('')}</ul><h4>Precautions</h4><ul class="list">${state.detail.precautions.map(x=>`<li data-no-translate>${escapeHtml(x)}</li>`).join('')}</ul><div class="notice">AI-generated information is for educational purposes only and should not replace professional medical advice.</div><div class="row"><button class="btn primary" onclick="speakText('${encodeURIComponent(state.detail.summary)}')">🔊 Listen</button><button class="btn danger" onclick="deleteReport(${id})">Delete</button><button class="btn ghost" onclick="closeModal()">Close</button></div>`)}catch(e){toast(e.message)}}
async function deleteReport(id){if(!confirm('Delete this report?'))return;try{await api(`/api/reports/${id}`,{method:'DELETE'});closeModal();await refresh();go('reports');toast(translateUiText('Report deleted'))}catch(e){toast(e.message)}}
function contactModal(contactId=null){
  const contact=contactId===null?null:state.contacts.find(item=>item.id===contactId);
  if(contactId!==null&&!contact){toast('Contact not found');return}
  showModal(`<h3>${contact?'Edit emergency contact':'Add emergency contact'}</h3><div class="field"><label>Name</label><input id="cname" value="${escapeHtml(contact?.name||'')}"></div><div class="field"><label>Relationship</label><input id="crel" placeholder="Family / Friend" value="${escapeHtml(contact?.relationship||'')}"></div><div class="field"><label>Phone</label><input id="cphone" type="tel" required value="${escapeHtml(contact?.phone||'')}"></div><div class="field"><label>Email</label><input id="cemail" type="email" value="${escapeHtml(contact?.email||'')}"></div><button class="btn primary" onclick="${contact?'updateContact('+contact.id+')':'addContact()'}">${contact?'Save changes':'Save contact'}</button> <button class="btn ghost" onclick="closeModal()">Cancel</button>`);
}
function contactFormData(){
  return {name:$('#cname').value.trim(),relationship:$('#crel').value.trim(),phone:$('#cphone').value.trim(),email:$('#cemail').value.trim()};
}
async function addContact(){
  try{
    await api('/api/emergency-contacts',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(contactFormData())});
    closeModal();await refresh();go('contacts');toast('Contact added');
  }catch(e){toast(e.message)}
}
async function updateContact(contactId){
  try{
    await api(`/api/emergency-contacts/${contactId}`,{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify(contactFormData())});
    closeModal();await refresh();go('contacts');toast('Contact updated');
  }catch(e){toast(e.message)}
}
async function deleteContact(id){if(!confirm('Delete this contact?'))return;try{await api(`/api/emergency-contacts/${id}`,{method:'DELETE'});await refresh();go('contacts');toast('Contact deleted')}catch(e){toast(e.message)}}
async function saveProfile(){try{await api('/api/user/profile',{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({name:$('#pname').value,phone:$('#pphone').value,dob:$('#pdob').value,gender:$('#pgender').value,blood_group:$('#pblood').value,preferred_language:$('#plang').value})});state.user=await api('/api/auth/me');state.lang=state.user.preferred_language;setAppLanguage(state.lang);renderShell();toast('Profile updated')}catch(e){toast(e.message)}}
async function changeLanguage(lang){try{await api('/api/user/profile',{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({name:state.user.name,phone:state.user.phone||'',dob:state.user.dob||'',gender:state.user.gender||'',blood_group:state.user.blood_group||'',preferred_language:lang})});state.user=await api('/api/auth/me');state.lang=lang;setAppLanguage(lang);renderShell();toast('Language preference saved')}catch(e){toast(e.message)}}
async function loadSOSHistory(){
  const history=$('#sosHistory');
  if(!history)return;
  try{
    const alerts=await api('/api/sos');
    if(!history.isConnected)return;
    history.innerHTML=alerts.length?alerts.slice(0,5).map(alert=>`<div class="sos-history-item"><div><strong>${new Date(alert.created_at).toLocaleString()}</strong><div class="small muted">${Number.isFinite(alert.latitude)&&Number.isFinite(alert.longitude)?'Location saved':'Location unavailable'} · ${escapeHtml(alert.status)}</div></div>${Number.isFinite(alert.latitude)&&Number.isFinite(alert.longitude)?`<a class="btn ghost" target="_blank" rel="noopener noreferrer" href="https://www.openstreetmap.org/?mlat=${alert.latitude}&mlon=${alert.longitude}#map=16/${alert.latitude}/${alert.longitude}">View location</a>`:''}</div>`).join(''):'<div class="empty">No SOS alerts recorded yet.</div>';
  }catch(error){history.innerHTML=`<div class="empty">${escapeHtml(error.message)}</div>`}
}
function openHospitalsForSOS(){
  closeModal();
  if(!state.sosLocation){toast('No GPS location was saved for this SOS. Hospitals will be searched near your device location.')}
  go('hospitals');
}
async function triggerSOS(){
  if(!state.contacts.length){toast('Add at least one emergency contact first');go('contacts');return}
  const button=$('#sendSOS');
  if(button){button.disabled=true;button.textContent='Getting location and saving alert…'}
  let completed=false;
  const createAlert=async(latitude=null,longitude=null)=>{
    try{
      const result=await api('/api/sos',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({latitude,longitude})});
      completed=true;
      state.sosLocation=Number.isFinite(latitude)&&Number.isFinite(longitude)?{latitude,longitude}:null;
      const locationNotice=state.sosLocation?'Your location is included. Select a contact action below; SMS opens for you to review and send.':'GPS location was unavailable. The alert is saved, but its SMS will not include a map link.';
      showModal(`<h3>🚨 SOS saved</h3><div class="notice danger"><strong>${escapeHtml(locationNotice)}</strong><p data-no-translate>${escapeHtml(result.message)}</p></div><p>Choose how to contact your registered emergency contacts:</p><div class="sos-contact-actions">${result.contacts.map(contact=>`<div class="sos-contact-action"><strong>${escapeHtml(contact.name)}</strong><div class="row"><a class="btn primary" href="sms:${encodeURIComponent(contact.phone)}?body=${encodeURIComponent(result.message)}">Text ${escapeHtml(contact.name)}</a><a class="btn ghost" href="tel:${encodeURIComponent(contact.phone)}">Call ${escapeHtml(contact.name)}</a></div></div>`).join('')}</div><p class="small muted">Messages are not sent automatically. The SMS app opens with a draft; review it and tap Send. This does not contact emergency services.</p><div class="row"><button class="btn primary" onclick="openHospitalsForSOS()">Find nearby hospitals</button><button class="btn ghost" onclick="closeModal()">Close</button></div>`);
      loadSOSHistory();
    }catch(error){toast(error.message)}
    finally{if(button&&button.isConnected&&!completed){button.disabled=false;button.textContent='🚨 Send SOS Alert'}}
  };
  if(navigator.geolocation){
    navigator.geolocation.getCurrentPosition(position=>createAlert(position.coords.latitude,position.coords.longitude),()=>createAlert(),{enableHighAccuracy:false,timeout:12000,maximumAge:30000});
  }else createAlert();
}
function speakText(encoded){const text=decodeURIComponent(encoded);if(!('speechSynthesis'in window)){toast('Voice playback is not supported in this browser');return}const u=new SpeechSynthesisUtterance(text);u.lang={en:'en-IN',te:'te-IN',hi:'hi-IN',ta:'ta-IN',kn:'kn-IN',ml:'ml-IN'}[state.lang]||'en-IN';speechSynthesis.cancel();speechSynthesis.speak(u)}
function showModal(html){let m=$('#modal');if(!m){m=document.createElement('div');m.id='modal';m.className='modal';document.body.appendChild(m)}m.innerHTML=`<div class="modal-card">${html}</div>`;m.classList.add('open')}
function closeModal(){const m=$('#modal');if(m)m.classList.remove('open')}
async function logout(){await api('/api/auth/logout',{method:'POST'});state.user=null;state.chat=[];state.chatError=null;state.sosLocation=null;state.lang='en';setAppLanguage('en');authView()}
loadApp();
