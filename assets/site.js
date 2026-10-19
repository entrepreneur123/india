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
var f=document.getElementById("checkForm");
if(f)f.addEventListener("submit",function(ev){ev.preventDefault();var out=document.getElementById("checkRes");out.textContent="";
var v=document.getElementById("ticket").value;if(!v.trim())return;var k=parse(v);if(!k){out.appendChild(el("div",null,t("ticket_invalid")));return}
var cs=D.map(function(d){return check(k,d)}).filter(Boolean),won=cs.filter(function(c){return c.w.length});
won.forEach(function(c){var b=el("div","won"),x=c.w[0];b.appendChild(el("strong",null,x.nf?t("maybe_first"):t("you_won")));
b.appendChild(el("div",null,tier(x.T.rank)+" · "+rup(x.T.prize)));b.appendChild(el("div","hint",c.d.name.replace(/ WEEKLY LOTTERY$/,"")));
if(x.nf)b.appendChild(el("div","hint",t("need_full_number")));out.appendChild(b)});
if(!won.length){var b=el("div");b.appendChild(el("strong",null,t("no_win_title",show(k))));b.appendChild(el("div","hint",t("no_win_body",cs.length)));out.appendChild(b)}
var dg=k.dg;document.querySelectorAll(".nums span").forEach(function(s){var x=s.textContent;s.classList.toggle("hit",dg.length>=x.length&&dg.slice(-x.length)===x)})});
// Today's pages refresh themselves while a result is due (static pages, so a reload is cheap).
var day=document.body.dataset.live;
if(day){var shown=D.length,url=document.querySelector('script[src$="/assets/site.js"]').getAttribute("src").replace(/assets\/site\.js$/,"v2/today.json");
setInterval(function(){if(document.hidden)return;fetch(url,{cache:"no-cache"}).then(function(r){return r.json()}).then(function(j){
var n=j.draws.filter(function(d){return d.date===day&&d.tiers&&d.tiers.length}).length;if(n>shown)location.reload()}).catch(function(){})},60000)}
})();
