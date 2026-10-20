(function(){"use strict";
var S=JSON.parse(document.getElementById("i18n").textContent),D=JSON.parse(document.getElementById("draws").textContent);
function t(k){var s=S[k]||k,a=[].slice.call(arguments,1);return s.replace(/%(\d)\$[sd]/g,function(_,n){return a[n-1]})}
function rup(n){var s=String(n);if(s.length<=3)return"₹"+s;return"₹"+s.slice(0,-3).replace(/\B(?=(\d{2})+(?!\d))/g,",")+","+s.slice(-3)}
function tier(r){var k={"1":"tier_1",CONS:"tier_cons","2":"tier_2","3":"tier_3","4":"tier_4","5":"tier_5"}[r];return k?t(k):t("tier_n",r)}
function parse(raw){var s=raw.replace(/[०-९]/g,function(d){return d.charCodeAt(0)-0x966}).replace(/[০-৯]/g,function(d){return d.charCodeAt(0)-0x9e6});
s=s.trim().toUpperCase().replace(/[-_./,]/g," ").replace(/\s+/g," ");
function p(x){var m=/^(\d{2})\s*([A-Z])\s*(\d{5})$/.exec(x);if(m)return(+m[1]>=34&&"ABCDEGHJKL".indexOf(m[2])>=0)?{pre:m[1]+m[2],dg:m[3],f:"D5"}:false;
if((m=/^(\d{2})\s+(\d{4})$/.exec(x)))return{pre:m[1],dg:m[2],f:"D4"};if((m=/^([A-Z]{1,2})\s*(\d{6})$/.exec(x)))return{pre:m[1],dg:m[2],f:"B6"};return null}
var r=p(s);if(r)return r;if(r===false)return null;var c=s.replace(/ /g,"");if(c!==s){r=p(c);if(r)return r;if(r===false)return null}
return/^\d{4,6}$/.test(c)?{pre:null,dg:c,f:"DIGITS"}:null}
function show(k){return k.pre?k.pre+" "+k.dg:k.dg}
function compat(k,d){if(d.format==="D5")return k.f==="D5"||(k.f==="DIGITS"&&k.dg.length<=5);if(d.format==="D4")return k.f==="D4"||(k.f==="DIGITS"&&k.dg.length===4);if(d.format==="B6")return k.f==="B6"||k.f==="DIGITS";return false}
function check(k,d){if(!d.tiers||!d.tiers.length||!compat(k,d))return null;var w=[],n=k.dg.length,full=k.f!=="DIGITS",sh=show(k);
d.tiers.forEach(function(T){var N=T.numbers||[];if(T.match==="FULL"){var f=N[0];if(!f)return;if(full){if(sh===f)w.push({T:T})}else if(n===5&&k.dg===f.slice(-5))w.push({T:T,nf:1})}
else if(T.match==="CONS"){if(n<5)return;var first=full&&(d.tiers[0].numbers||[])[0]===sh;if(!first&&N.indexOf(k.dg)>=0)w.push({T:T})}
else if(T.match==="EXACT"){if(full){if(N.indexOf(sh)>=0)w.push({T:T})}else if(n===6&&N.some(function(x){return x.slice(-6)===k.dg}))w.push({T:T,nf:1})}
else if(T.match==="LAST5"){if(n>=5&&N.indexOf(k.dg.slice(-5))>=0)w.push({T:T})}else if(T.match==="LAST4"){if(N.indexOf(k.dg.slice(-4))>=0)w.push({T:T})}});
w.sort(function(a,b){return b.T.prize-a.T.prize});return{d:d,w:w}}
function el(tag,cls,txt){var e=document.createElement(tag);if(cls)e.className=cls;if(txt!=null)e.textContent=txt;return e}
function hm(s){var p=s.split(":"),h=+p[0];return(h%12||12)+(p[1]!=="00"?":"+p[1]:"")+(h>=12?" PM":" AM")}
function box(head,cls){var b=el("div","res"+(cls?" "+cls:""));b.appendChild(el("div","rh",head));var c=el("div","rb");b.appendChild(c);return[b,c]}
var f=document.getElementById("checkForm");
if(f)f.addEventListener("submit",function(ev){ev.preventDefault();var out=document.getElementById("checkRes");out.textContent="";
var v=document.getElementById("ticket").value;if(!v.trim())return;var k=parse(v);if(!k){var e=box(t("ticket_invalid"),"no");out.appendChild(e[0]);return}
var cs=D.map(function(d){return check(k,d)}).filter(Boolean),won=cs.filter(function(c){return c.w.length});
won.forEach(function(c){var x=c.w[0],e=box(x.nf?t("maybe_first"):t("you_won"));e[1].appendChild(el("strong",null,tier(x.T.rank)+" · "+rup(x.T.prize)));
e[1].appendChild(el("div","hint",c.d.name.replace(/ WEEKLY LOTTERY$/,"")+" · "+c.d.date.split("-").reverse().join(".")+" "+hm(c.d.time)));
if(x.nf)e[1].appendChild(el("div","hint",t("need_full_number")));if(x.T.confirmed===false)e[1].appendChild(el("div","warn",t("tier_unconfirmed")));out.appendChild(e[0])});
if(!won.length){var e=box(t("no_win_title",show(k)),"no");e[1].appendChild(el("div","hint",t(cs.length===1&&S.no_win_body_one?"no_win_body_one":"no_win_body",cs.length)));out.appendChild(e[0])}
var dg=k.dg;document.querySelectorAll(".nums li").forEach(function(s){var x=s.textContent,hit=dg.length>=x.length&&dg.slice(-x.length)===x;
s.classList.toggle("hit",hit);if(hit){var dt=s.closest("details");if(dt)dt.open=true}});
out.scrollIntoView({block:"nearest",behavior:"smooth"})});
// Share: copy the link, or the phone's own share sheet where the browser has one.
document.querySelectorAll(".share .cp").forEach(function(b){b.addEventListener("click",function(){var u=b.dataset.url,l=b.textContent;
function done(){b.textContent=t("copied");setTimeout(function(){b.textContent=l},1800)}
if(navigator.clipboard)navigator.clipboard.writeText(u).then(done,function(){prompt("",u)});else prompt("",u)})});
if(navigator.share)document.querySelectorAll(".share .more").forEach(function(b){b.hidden=false;b.addEventListener("click",function(){
navigator.share({text:b.dataset.text}).catch(function(){})})});
// Draws still to come: countdown, then LIVE, then the "taking longer" note, as in the app.
var W=document.querySelectorAll(".when[data-at]");function tick(){var now=Date.now();W.forEach(function(e){var at=Date.parse(e.dataset.at),m=Math.floor((at-now)/60000);
if(m>=0){e.textContent=t("draw_in",Math.floor(m/60),m%60)}else if(m>-15){e.textContent="";e.appendChild(el("span","live",t("live")));e.appendChild(document.createTextNode(t("result_coming")))}
else e.textContent=t("result_delayed")})}if(W.length){tick();setInterval(tick,30000)}
// Number history: the 90-day feed is fetched only when someone searches.
var feed=function(name){return document.querySelector('script[src$="/assets/site.js"]').getAttribute("src").replace(/assets\/site\.js$/,"v2/"+name)};
var hf=document.getElementById("histForm"),H=null;
if(hf)hf.addEventListener("submit",function(ev){ev.preventDefault();var out=document.getElementById("histRes"),v=document.getElementById("histNum").value;
out.textContent="";if(!v.trim())return;var k=parse(v);if(!k){out.appendChild(box(t("ticket_invalid"),"no")[0]);return}
(H?Promise.resolve(H):fetch(feed("history.json")).then(function(r){if(!r.ok)throw 0;return r.json()}).then(function(j){H=j;return j})).then(function(j){
var days=j.days||90,hits=[];(j.draws||[]).forEach(function(d){var c=check(k,d);if(c&&c.w.length)hits.push(c)});
if(!hits.length){var e=box(t("history_none",show(k),days),"no");out.appendChild(e[0]);return}
var e=box(t(hits.length===1&&S.history_count_one?"history_count_one":"history_count",hits.length,days)),ul=el("ul","hl");
hits.forEach(function(c){var x=c.w[0];ul.appendChild(el("li",null,c.d.date.split("-").reverse().join(".")+" "+hm(c.d.time)+" · "+
c.d.name.replace(/ WEEKLY LOTTERY$/,"")+" · "+tier(x.T.rank)+" "+rup(x.T.prize)+(x.nf?" *":"")))});e[1].appendChild(ul);out.appendChild(e[0])
}).catch(function(){out.appendChild(box(t("history_unavailable"),"no")[0])})});
// Old results: jump to a date we have a page for.
var df=document.getElementById("dateForm");
if(df)df.addEventListener("submit",function(ev){ev.preventDefault();var v=document.getElementById("dateIn").value,p=v.split("-");if(p.length!==3)return;
var a=document.querySelector('a[href$="/result/'+p[2]+"-"+p[1]+"-"+p[0]+'/"]');if(a)location.href=a.getAttribute("href");
else document.getElementById("dateRes").textContent=df.dataset.none});
// Today's pages refresh themselves while a result is due (static pages, so a reload is cheap).
var day=document.body.dataset.live;
if(day){var shown=D.length,url=document.querySelector('script[src$="/assets/site.js"]').getAttribute("src").replace(/assets\/site\.js$/,"v2/today.json");
setInterval(function(){if(document.hidden)return;fetch(url,{cache:"no-cache"}).then(function(r){return r.json()}).then(function(j){
var n=j.draws.filter(function(d){return d.date===day&&d.tiers&&d.tiers.length}).length;if(n>shown)location.reload()}).catch(function(){})},30000)}
// A page waiting for one draw (Kerala before 4 PM, Morning/Day/Night before their result) reloads once it is in the feed.
var wid=document.body.dataset.wait;
if(wid)setInterval(function(){if(document.hidden)return;fetch(feed("today.json"),{cache:"no-cache"}).then(function(r){return r.json()}).then(function(j){
if(j.draws.some(function(d){return d.id===wid&&d.tiers&&d.tiers.length}))location.reload()}).catch(function(){})},30000);
})();
