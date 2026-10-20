// SSC Job Alerts website. Reads the static JSON that jobs/pipeline publishes next to this page:
//   v1/feed.json, exams.json, calendar.json, resources.json, rules/index.json, rules/<id>.json
// Every string from SSC goes into the page through textContent, never innerHTML.
import { evaluate, todayIST } from './eligibility.js';
import { getLang, setLang, t } from './i18n.js';
import { practiceText } from './passages.js';
import { ageOn, buildIcs, examEvents, makeSignature, typingScore } from './tools.js';
import { timeline } from './tracker.js';

const DATA = new URL('v1/', document.baseURI);
const KINDS = [
  ['all', 'kindAll'], ['recruitment', 'kindRecruitment'], ['result', 'kindResult'], ['admit_card', 'kindAdmit'],
  ['answer_key', 'kindAnswer'], ['amendment', 'kindAmendment'], ['vacancy', 'kindVacancy'],
];
const QUALS = ['10', '12', 'diploma', 'graduate', 'postgraduate'];
const REASON = {
  age_below_min: (n, r) => (getLang() === 'hi' ? `आयु कम: ${day(r.age.asOn)} को कम से कम ${n} वर्ष होनी चाहिए।` : `Too young: you must be at least ${n} on ${day(r.age.asOn)}.`),
  age_above_max: (n, r) => (getLang() === 'hi' ? `आयु सीमा से अधिक: ${day(r.age.asOn)} को ${n} वर्ष से कम होनी चाहिए (छूट सहित)।` : `Over the age limit: you must be under ${n} on ${day(r.age.asOn)} (relaxation included).`),
  age_other_relaxation: (_, r) => (getLang() === 'hi' ? `सामान्य आयु सीमा से अधिक। भूतपूर्व सैनिक / सरकारी कर्मचारी / विधवा की छूट लागू हो सकती है: नोटिस ${r.age.ref} देखें।` : `Over the general age limit. Your ex-serviceman / Govt employee / widow relaxation may cover it: see Notice ${r.age.ref}.`),
  education_needs: (lvl) => (lvl === 'graduate' ? (getLang() === 'hi' ? 'स्नातक डिग्री चाहिए।' : "Needs a bachelor's degree.") : lvl === '12' ? (getLang() === 'hi' ? '12वीं पास चाहिए।' : 'Needs Class 12 pass.') : (getLang() === 'hi' ? '10वीं पास चाहिए।' : 'Needs Class 10 pass.')),
  result_needed_by: (d) => (getLang() === 'hi' ? `आपका परिणाम ${day(d)} तक घोषित होना चाहिए।` : `Your result must be declared by ${day(d)}.`),
  appearing_not_stated: (d) => (getLang() === 'hi' ? `नोटिस में परिणाम-प्रतीक्षित उम्मीदवारों का उल्लेख नहीं है; योग्यता ${day(d)} तक होनी चाहिए।` : `The Notice does not say candidates awaiting results may apply; you must hold the qualification by ${day(d)}.`),
  pwbd_not_in_notice: () => (getLang() === 'hi' ? 'इस नोटिस में दिव्यांग (PwBD) आयु छूट नहीं दी गई है: नोटिस देखें।' : 'This Notice lists no PwBD age relaxation: check the Notice.'),
  gender_only: (g) => (getLang() === 'hi' ? `केवल ${g === 'male' ? 'पुरुष' : g === 'female' ? 'महिला' : 'पुरुष/महिला'} उम्मीदवारों के लिए।` : `Only for ${g.replace('/', ' / ')} candidates.`),
  'requires:state_language': () => (getLang() === 'hi' ? 'राज्य की राजभाषा/क्षेत्रीय भाषा में दक्षता चाहिए (माध्यमिक स्तर का प्रमाण पत्र)।' : "Needs proficiency in the State's official/regional language (Secondary School level certificate)."),
  'requires:jso_maths_or_statistics': () => (getLang() === 'hi' ? '12वीं में गणित में 60% अंक, या डिग्री में सांख्यिकी विषय चाहिए।' : 'Needs 60% in Mathematics at Class 12, or Statistics as a subject in your degree.'),
  'requires:sig2_degree_subject': () => (getLang() === 'hi' ? 'डिग्री सांख्यिकी, गणित, अर्थशास्त्र, जनसांख्यिकी, कंप्यूटर आदि विषयों में होनी चाहिए (नोटिस पैरा 8.3)।' : 'Degree must be in Statistics, Mathematics, Economics, Demography, Population Studies, Operations Research, IT, Computer Science/Engineering/Applications, Data Science or AI.'),
  'requires:driving_licence_lmv': () => (getLang() === 'hi' ? 'PE&MT की तिथि पर LMV (मोटरसाइकिल और कार) का वैध ड्राइविंग लाइसेंस चाहिए।' : 'Needs a valid LMV (motorcycle and car) driving licence on the date of the physical test.'),
  'requires:si_fire_qualification': () => (getLang() === 'hi' ? 'भौतिकी, रसायन और गणित के साथ B.Sc., या 10वीं के साथ मैकेनिकल/सिविल/इलेक्ट्रिकल आदि में 3 वर्ष का डिप्लोमा चाहिए।' : 'Needs a B.Sc. with Physics, Chemistry and Mathematics, or Class 10 plus a 3-year diploma in Mechanical, Civil, Electrical or a listed engineering branch.'),
};
const NOTE = {
  physical_standards: { en: 'Physical and medical standards apply (see the Notice).', hi: 'शारीरिक और चिकित्सा मानक लागू (नोटिस देखें)।' },
  bro_male_only: { en: 'Border Roads Organisation vacancies: male candidates only, physical standards apply.', hi: 'सीमा सड़क संगठन की रिक्तियाँ: केवल पुरुष, शारीरिक मानक लागू।' },
  deo_science_maths_some: { en: 'In some offices (Consumer Affairs & Food, SSC, Culture) this post needs Class 12 Science with Mathematics.', hi: 'कुछ कार्यालयों में इस पद के लिए 12वीं विज्ञान (गणित सहित) चाहिए।' },
  stenography_skill_test: { en: 'A stenography skill test is part of the selection.', hi: 'चयन में आशुलिपि कौशल परीक्षा शामिल है।' },
};

const $ = (id) => document.getElementById(id);
const store = {
  get(key, fallback) { try { return JSON.parse(localStorage.getItem(key)) ?? fallback; } catch { return fallback; } },
  set(key, value) { try { localStorage.setItem(key, JSON.stringify(value)); } catch { /* private mode */ } },
};

function el(tag, attrs = {}, ...children) {
  const node = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs)) {
    if (k === 'class') node.className = v;
    else if (k.startsWith('on') && typeof v === 'function') node.addEventListener(k.slice(2), v);
    else if (v !== undefined && v !== null && v !== false) node.setAttribute(k, v === true ? '' : v);
  }
  for (const c of children.flat()) if (c !== null && c !== undefined && c !== false) node.append(c);
  return node;
}
// replaceChildren(null) would print "null" (seen twice in testing): every view goes through this.
const fill = (node, ...children) => node.replaceChildren(...children.flat().filter((c) => c !== null && c !== undefined && c !== false));

const locale = () => (getLang() === 'hi' ? 'hi-IN' : 'en-IN');
function day(iso) {
  return new Intl.DateTimeFormat(locale(), { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' })
    .format(new Date(`${iso}T00:00:00Z`));
}
function when(isoTime) {
  return new Intl.DateTimeFormat(locale(), {
    day: 'numeric', month: 'short', year: 'numeric', hour: 'numeric', minute: '2-digit', timeZone: 'Asia/Kolkata',
  }).format(new Date(isoTime));
}
function size(bytes) {
  if (!Number.isFinite(bytes)) return '';
  return bytes >= 1e6 ? ` · ${(bytes / 1e6).toFixed(1)} MB` : ` · ${Math.max(1, Math.round(bytes / 1e3))} KB`;
}
function safeUrl(u) {
  try { const x = new URL(u); return x.protocol === 'https:' && /(^|\.)(gov|nic)\.in$/.test(x.hostname) ? x.href : null; } catch { return null; }
}
function payText(rules, level) {
  if (!level || !rules.payScales) return null;
  const inr = (n) => n.toLocaleString('en-IN');
  return String(level).split('/').filter((l) => rules.payScales[l])
    .map((l) => t('payRange', { from: inr(rules.payScales[l][0]), to: inr(rules.payScales[l][1]), l })).join(' · ') || null;
}
const daysBetween = (a, b) => Math.round((Date.parse(`${b}T00:00:00Z`) - Date.parse(`${a}T00:00:00Z`)) / 864e5);

const cache = {};
async function getJSON(path) {
  if (cache[path]) return cache[path];
  const res = await fetch(new URL(path, DATA), { cache: 'no-cache' });
  if (!res.ok) throw new Error(`${path}: HTTP ${res.status}`);
  cache[path] = await res.json();
  return cache[path];
}
const errorBox = (e) => { console.error(e); return el('p', { class: 'error' }, t('loadError')); };

let toastTimer;
function toast(msg) {
  document.querySelector('.toast')?.remove();
  clearTimeout(toastTimer);
  const n = el('div', { class: 'toast', role: 'status' }, msg);
  document.body.append(n);
  toastTimer = setTimeout(() => n.remove(), 2200);
}

// ---------- saved items and followed exams (this device only) ----------
const savedItems = () => store.get('saved', {});
const followed = () => store.get('follow', []);
function toggleSave(item, btn) {
  const s = savedItems();
  if (s[item.id]) delete s[item.id]; else s[item.id] = item;
  store.set('saved', s);
  toast(t(s[item.id] ? 'toastSaved' : 'toastRemoved'));
  btn.textContent = s[item.id] ? `★ ${t('saved')}` : `☆ ${t('save')}`;
  btn.setAttribute('aria-pressed', String(Boolean(s[item.id])));
}
function toggleFollow(code, btn) {
  const f = new Set(followed());
  if (f.has(code)) f.delete(code); else f.add(code);
  store.set('follow', [...f]);
  toast(t(f.has(code) ? 'toastFollow' : 'toastUnfollow'));
  btn.textContent = f.has(code) ? `✓ ${t('following')}` : `+ ${t('follow')}`;
  btn.setAttribute('aria-pressed', String(f.has(code)));
}
async function share(title, url) {
  const text = t('shareText', { title, url });
  if (navigator.share) {
    try { await navigator.share({ title, text, url }); return; } catch { /* cancelled: fall through to nothing */ return; }
  }
  window.open(`https://wa.me/?text=${encodeURIComponent(text)}`, '_blank', 'noopener');
}

// ---------- shared pieces ----------
function itemCard(i, { isNew = false, families = {} } = {}) {
  const pdf = i.files.map((f) => safeUrl(f.url)).find(Boolean);
  const saveBtn = el('button', { class: 'chip small', type: 'button', 'aria-pressed': String(Boolean(savedItems()[i.id])) },
    savedItems()[i.id] ? `★ ${t('saved')}` : `☆ ${t('save')}`);
  saveBtn.addEventListener('click', () => toggleSave(i, saveBtn));
  const fam = i.exam ? families[i.exam] : null;
  return el('li', { class: 'item' },
    el('div', { class: 'meta' },
      isNew ? el('span', { class: 'badge new' }, t('newBadge')) : null,
      el('span', { class: `badge ${i.kind}` }, t(`kind_${i.kind}`)),
      // SSC's codes (CGL, CHSL) are what candidates say; other boards' codes are ours, so their name is shown.
      i.exam ? el('a', { class: 'badge', href: `#exam/${i.exam}`, title: fam?.name ?? '' }, `${fam?.source ? fam.short : i.exam}${i.examYear ? ` ${i.examYear}` : ''}`) : null,
      // Some boards give only the day; showing midnight as a time would be invented.
      el('time', { datetime: i.dateOnly ? i.publishedAt.slice(0, 10) : i.publishedAt }, i.dateOnly ? day(i.publishedAt.slice(0, 10)) : `${when(i.publishedAt)} ${t('ist')}`)),
    el('h3', { lang: i.lang }, i.title),
    el('div', { class: 'files' },
      ...i.files.map((f) => {
        const href = safeUrl(f.url);
        return href ? el('a', { href, rel: 'noopener', target: '_blank' }, `${t('pdf')}: ${f.name}${size(f.size)}`) : null;
      }),
      i.link && safeUrl(i.link) ? el('a', { href: safeUrl(i.link), rel: 'noopener', target: '_blank' }, t('officialPage')) : null),
    el('div', { class: 'actions' }, saveBtn,
      pdf ? el('button', { class: 'chip small', type: 'button', onclick: () => share(i.title, pdf) }, `↗ ${t('share')}`) : null));
}

// ---------- Latest ----------
const filters = store.get('filters', { kind: 'all', exam: 'all', qual: 'all', lang: 'en', dept: false, mine: false, q: '' });

async function viewLatest(root, preloaded = null) {
  let feed;
  let families;
  try {
    [feed, { families }] = await Promise.all([preloaded ?? getJSON('feed.json'), getJSON('exams.json')]);
  } catch (e) { fill(root, errorBox(e)); return; }
  const lastSeen = store.get('lastSeen', null);
  const newest = feed.items.reduce((m, i) => (i.publishedAt > m ? i.publishedAt : m), '');
  const isNew = (i) => lastSeen !== null && i.publishedAt > lastSeen;
  const newCount = feed.items.filter(isNew).length;

  const list = el('ul', { class: 'list' });
  const render = () => {
    store.set('filters', filters);
    const q = filters.q.trim().toLowerCase();
    const mine = new Set(followed());
    const items = feed.items.filter((i) =>
      (filters.kind === 'all' || i.kind === filters.kind) &&
      (filters.exam === 'all' || i.exam === filters.exam) &&
      (filters.qual === 'all' || (families[i.exam]?.qualifications ?? []).includes(filters.qual)) &&
      // SSC posts each notice in English and Hindi; other boards post one copy, always shown.
      (filters.lang === 'all' || i.lang === filters.lang || (i.source ?? 'ssc') !== 'ssc') &&
      (filters.dept || i.exam !== 'LDCE') &&
      (!filters.mine || mine.has(i.exam)) &&
      // Headlines from some boards are Hindi only: the exam's search name is searched too ("up police", "cgl").
      (!q || `${i.title} ${i.exam ?? ''} ${families[i.exam]?.short ?? ''}`.toLowerCase().includes(q)));
    fill(list, ...(items.length ? items.slice(0, 150).map((i) => itemCard(i, { isNew: isNew(i), families }))
      : [el('li', { class: 'empty' }, t('nothing'))]));
  };
  const chips = el('div', { class: 'filters', role: 'group' });
  const drawChips = () => fill(chips, ...KINDS.map(([k, key]) => el('button', {
    class: 'chip', type: 'button', 'aria-pressed': String(filters.kind === k),
    onclick: () => { filters.kind = k; drawChips(); render(); },
  }, t(key))));
  drawChips();
  const exams = [...new Set(feed.items.map((i) => i.exam).filter((e) => e && e !== 'LDCE'))].sort();
  const examSel = el('select', { 'aria-label': t('allExams'), onchange: (e) => { filters.exam = e.target.value; render(); } },
    el('option', { value: 'all' }, t('allExams')), ...exams.map((e) => el('option', { value: e }, families[e]?.name ?? e)));
  examSel.value = exams.includes(filters.exam) ? filters.exam : 'all';
  const qualSel = el('select', { 'aria-label': t('anyQualification'), onchange: (e) => { filters.qual = e.target.value; render(); } },
    el('option', { value: 'all' }, t('anyQualification')), ...QUALS.map((q) => el('option', { value: q }, t(`qual_${q}`))));
  qualSel.value = filters.qual;
  const langSel = el('select', { 'aria-label': 'Language', onchange: (e) => { filters.lang = e.target.value; render(); } },
    el('option', { value: 'en' }, t('langEn')), el('option', { value: 'hi' }, t('langHi')), el('option', { value: 'all' }, t('langBoth')));
  langSel.value = filters.lang;
  const search = el('input', { type: 'search', placeholder: t('search'), 'aria-label': t('search'), value: filters.q });
  search.addEventListener('input', () => { filters.q = search.value; render(); });
  const check = (key, label) => {
    const box = el('input', { type: 'checkbox' });
    box.checked = Boolean(filters[key]);
    box.addEventListener('change', () => { filters[key] = box.checked; render(); });
    return el('label', { class: 'check' }, box, ` ${label}`);
  };
  // Content first: on a 360 px phone the filters used to fill the whole first screen.
  chips.classList.add('scroll-row');
  const active = ['exam', 'qual', 'mine', 'dept'].filter((k) => (k === 'exam' || k === 'qual' ? filters[k] !== 'all' : filters[k])).length
    + (filters.lang !== 'en' ? 1 : 0);
  fill(root,
    search, chips,
    el('details', { class: 'filter-box' }, el('summary', {}, `${t('filters')}${active ? ` (${active})` : ''}`),
      el('div', { class: 'filters' }, examSel, qualSel, langSel, check('mine', t('myExams')), check('dept', t('departmental')))),
    el('p', { class: 'small', 'aria-live': 'polite' }, t('updated', { time: when(feed.generatedAt) }),
      newCount ? el('strong', {}, ` · ${t('newSince', { n: newCount })}`) : null),
    list);
  render();
  if (newest) store.set('lastSeen', newest);
}

// ---------- Exams ----------
async function viewExams(root) {
  let families; let index; let cal; let resources;
  try {
    [{ families }, { exams: index }, cal, resources] = await Promise.all([
      getJSON('exams.json'), getJSON('rules/index.json'), getJSON('calendar.json'), getJSON('resources.json')]);
  } catch (e) { fill(root, errorBox(e)); return; }
  const today = todayIST();
  const mine = new Set(followed());
  const cards = Object.entries(families).filter(([c]) => c !== 'LDCE').map(([code, f]) => {
    const latest = index.filter((r) => r.exam === code).sort((a, b) => b.examYear - a.examYear)[0];
    const btn = el('button', { class: 'chip small', type: 'button', 'aria-pressed': String(mine.has(code)) },
      mine.has(code) ? `✓ ${t('following')}` : `+ ${t('follow')}`);
    btn.addEventListener('click', (ev) => { ev.preventDefault(); toggleFollow(code, btn); });
    return el('li', { class: 'item' },
      el('h3', {}, el('a', { href: `#exam/${code}` }, f.name)),
      el('div', { class: 'small' }, f.short ?? '', ' · ', el('span', { lang: 'hi' }, f.hi)),
      el('div', { class: 'small' }, f.qualifications ? t('minQual', { q: f.qualifications.map((q) => t(`qual_${q}`)).join(' / ') }) : t('qualUnknown')),
      latest ? el('div', { class: `banner small-banner ${statusOf(latest, today)}` }, statusText(latest, today)) : null,
      el('div', { class: 'actions' }, btn));
  });
  fill(root, 
    el('p', { class: 'small' }, t('examsIntro')),
    el('ul', { class: 'list' }, ...cards),
    el('h2', {}, t('calendarTitle')), el('p', { class: 'small' }, t('calendarNote')),
    el('div', { class: 'card scroll' }, el('table', {},
      el('tbody', {}, ...cal.entries.map((e) => el('tr', {},
        el('td', {}, e.title, e.stage ? el('div', { class: 'small' }, e.stage) : null),
        el('td', {}, `${day(e.applyFrom)} – ${day(e.applyTo)}`),
        el('td', {}, e.examWindow ?? '')))))),
    el('h2', {}, t('resourcesTitle')),
    ...Object.entries(resources.sections).map(([k, items]) => resourceBlock(k, items, k !== 'syllabus')));
}

function resourceBlock(section, items, collapsed) {
  if (!items.length) return null;
  return el('details', { class: 'group', open: !collapsed },
    el('summary', {}, `${t(`res_${section}`)} (${items.length})`),
    el('ul', { class: 'rows' }, ...items.map((i) => {
      const href = i.files.map((f) => safeUrl(f.url)).find(Boolean);
      return el('li', {}, href ? el('a', { href, rel: 'noopener', target: '_blank', lang: i.lang }, i.title) : el('span', { lang: i.lang }, i.title),
        el('span', { class: 'small' }, ` · ${day(i.publishedAt.slice(0, 10))}`));
    })));
}

// Worked out from today's date, not the index's build-time status, which can be hours old.
const statusOf = (r, today) => (today < r.applyFrom ? 'upcoming' : today <= r.applyTo ? 'open' : 'closed');
function statusText(r, today) {
  const status = statusOf(r, today);
  const base = t(`status_${status}`, { date: day(status === 'upcoming' ? r.applyFrom : r.applyTo) });
  if (status !== 'open') return base;
  const left = daysBetween(today, r.applyTo);
  return `${base} · ${left === 0 ? t('lastDay') : t('daysLeft', { n: left })}`;
}

async function viewExam(root, code) {
  let families; let index; let feed; let resources;
  try {
    [{ families }, { exams: index }, feed, resources] = await Promise.all([
      getJSON('exams.json'), getJSON('rules/index.json'), getJSON('feed.json'), getJSON('resources.json')]);
  } catch (e) { fill(root, errorBox(e)); return; }
  const f = families[code];
  if (!f) { location.hash = '#exams'; return; }
  const today = todayIST();
  const entry = index.filter((r) => r.exam === code).sort((a, b) => b.examYear - a.examYear)[0];
  const rules = entry ? await getJSON(`rules/${entry.id}.json`).catch(() => null) : null;
  const notices = feed.items.filter((i) => i.exam === code);
  const examResources = Object.entries(resources.sections)
    .map(([k, items]) => [k, items.filter((i) => i.exam === code)]).filter(([, items]) => items.length);
  const followBtn = el('button', { class: 'chip small', type: 'button', 'aria-pressed': String(followed().includes(code)) },
    followed().includes(code) ? `✓ ${t('following')}` : `+ ${t('follow')}`);
  followBtn.addEventListener('click', () => toggleFollow(code, followBtn));

  const parts = [
    el('a', { href: '#exams', class: 'small' }, t('back')),
    el('h2', {}, f.name), el('div', { class: 'small', lang: 'hi' }, f.hi),
    el('div', { class: 'small' }, f.qualifications ? `${t('minQual', { q: f.qualifications.map((q) => t(`qual_${q}`)).join(' / ') })} (${f.ref})` : t('qualUnknown')),
    el('div', { class: 'actions' }, followBtn),
  ];
  const year = rules ? rules.examYear : Math.max(0, ...notices.map((i) => i.examYear ?? 0)) || null;
  if (year && !f.noTimeline) parts.push(whenBlock(timeline(rules, feed.items, code, year, today), f.name, year, feed.items));
  if (rules) parts.push(...examDetails(rules, entry, today), faqBlock(rules, f));
  else parts.push(el('p', { class: 'small' }, t('noRules')));
  const guide = (f.source ?? 'ssc') === 'ssc' ? await getJSON('guide.json').catch(() => null) : null; // SSC's how-to-apply
  if (guide) parts.push(guideBlock(guide, false));
  if (examResources.length) parts.push(el('h3', {}, t('resourcesTitle')), ...examResources.map(([k, items]) => resourceBlock(k, items, false)));
  const writeups = ((await getJSON('results/cutoffs.json').catch(() => null))?.writeups ?? []).filter((w) => w.exam === code);
  if (writeups.length) parts.push(cutoffBlock(writeups, families, false));
  parts.push(el('h3', {}, `${t('allNotices')} (${notices.length})`),
    el('ul', { class: 'list' }, ...(notices.length ? notices.slice(0, 60).map((i) => itemCard(i, { families })) : [el('li', { class: 'empty' }, t('nothing'))])));
  fill(root, ...parts);
}

function whenBlock(stages, name, year, items) {
  const byId = Object.fromEntries(items.map((i) => [i.id, i]));
  return el('div', { class: 'card' }, el('h3', {}, `${t('whenTitle')} · ${year}`),
    el('ol', { class: 'timeline' }, ...stages.map((s) => {
      const label = s.key === 'exam' ? s.label : t(`stage_${s.key}`);
      const date = s.at ? day(s.at) : s.from ? `${day(s.from)} – ${day(s.to)}` : s.when ?? t('notAnnounced');
      const item = s.itemId ? byId[s.itemId] : null;
      const pdf = item?.files.map((f) => safeUrl(f.url)).find(Boolean);
      return el('li', { class: `stage ${s.state}` },
        el('span', { class: 'stage-state' }, t(`st_${s.state}`)),
        el('strong', {}, label), el('span', { class: 'small' }, ` · ${date}`),
        pdf ? el('a', { href: pdf, rel: 'noopener', target: '_blank', class: 'small' }, ` ${t('pdf')}`) : null);
    })));
}

function examDetails(r, entry, today) {
  const d = r.dates;
  const notice = safeUrl(r.source.notice);
  const row = (label, value) => el('tr', {}, el('th', { scope: 'row' }, label), el('td', {}, value));
  const events = examEvents(r, today);
  const icsBtn = events.length ? el('button', {
    class: 'primary', type: 'button',
    onclick: () => {
      const stamp = new Date().toISOString().replace(/[-:]/g, '').replace(/\.\d+/, '');
      const blob = new Blob([buildIcs(events, stamp)], { type: 'text/calendar' });
      const a = el('a', { href: URL.createObjectURL(blob), download: `${r.id}.ics` });
      document.body.append(a); a.click(); a.remove();
    },
  }, `📅 ${t('addCalendar')}`) : null;
  const exempt = r.fee.exempt.map((x) => t(`exempt_${x}`)).join(', ');
  const relax = Object.entries(r.age.relaxation).map(([k, v]) => `${k.replace('_', ' (') + (k.includes('_') ? ')' : '')}: ${t('relaxYears', { n: v })}`);
  return [
    el('div', { class: `banner ${statusOf(entry, today)}` }, statusText(entry, today)),
    el('h3', {}, `${t('latestNotice')}: ${r.title}`),
    el('div', { class: 'card scroll' }, el('table', {}, el('caption', {}, t('importantDates')), el('tbody', {},
      row(t('applyWindow'), `${day(d.applyFrom)} – ${day(d.applyTo)}`),
      row(t('feeLastDate'), day(d.feeTo)),
      d.correction ? row(t('correctionWindow'), `${day(d.correction[0])} – ${day(d.correction[1])}`) : null,
      ...(r.examSchedule ?? []).map((s) => row(s.stage, s.from ? `${day(s.from)} – ${day(s.to)} (${t('tentative')})` : s.when))))),
    el('div', { class: 'card scroll' }, el('table', {}, el('caption', {}, t('applicationFee')), el('tbody', {},
      row(t('feeGeneral'), `₹${r.fee.amount}`), row(t('feeExempt'), exempt))),
      r.fee.correction ? el('p', { class: 'small' }, t('correctionFee', { a: r.fee.correction[0], b: r.fee.correction[1] })) : null),
    el('div', { class: 'card' }, el('strong', {}, t('ageLimit', { date: day(r.age.asOn) })),
      el('ul', {}, ...Object.entries(r.age.bands).map(([band, b]) => el('li', {}, t('ageBand', { band, from: day(b.bornFrom), to: day(b.bornTo) })))),
      el('div', { class: 'small' }, `${t('relaxation')}: ${relax.join(' · ')}`),
      el('div', { class: 'small' }, t('notComputed', { list: r.age.notComputed.join('; ') }))),
    r.vacancies ? el('p', {}, el('strong', {}, `${t('vacancies')}: `), t('vacancyCount', { n: r.vacancies.count.toLocaleString('en-IN'), tentative: t('tentative') })) : null,
    el('details', { class: 'group' }, el('summary', {}, `${t('posts')} (${r.posts.length})`),
      el('ul', { class: 'rows' }, ...r.posts.map((p) => el('li', {}, el('strong', {}, p.name),
        el('span', { class: 'small' }, ` · ${p.org} · ${p.payLevel ? t('payLevel', { l: p.payLevel, g: p.group }) : t('groupOnly', { g: p.group })} · ${p.ageBand}`),
        payText(r, p.payLevel) ? el('div', { class: 'small' }, payText(r, p.payLevel)) : null)))),
    el('div', { class: 'card' }, el('strong', {}, t('importantLinks')), el('ul', {},
      el('li', {}, el('a', { href: 'https://ssc.gov.in', rel: 'noopener', target: '_blank' }, t('applyOnline'))),
      notice ? el('li', {}, el('a', { href: notice, rel: 'noopener', target: '_blank' }, t('noticePdf'))) : null,
      ...r.source.amendments.map((a) => (safeUrl(a.url) ? el('li', {}, el('a', { href: safeUrl(a.url), rel: 'noopener', target: '_blank' }, t('amendmentPdf', { date: day(a.published) })), el('div', { class: 'small' }, a.summary)) : null)))),
    el('div', { class: 'actions' },
      el('a', { class: 'primary link-button', href: `#check/${r.id}` }, `✔ ${t('checkThis')}`), icsBtn,
      notice ? el('button', { class: 'chip', type: 'button', onclick: () => share(r.title, notice) }, `↗ ${t('share')}`) : null),
    el('p', { class: 'small' }, t('rulesFrom', { date: day(r.source.published), checked: day(r.source.checkedOn) })),
  ];
}

// ---------- Eligibility ----------
function reasonText(code, rules) {
  if (REASON[code]) return REASON[code](null, rules);
  const i = code.indexOf(':');
  const [k, v] = [code.slice(0, i), code.slice(i + 1)];
  return REASON[k] ? REASON[k](v, rules) : code;
}
const noteText = (n) => NOTE[n]?.[getLang()] ?? NOTE[n]?.en ?? n;

function profileForm({ index = null, preselect = null, submitLabel, onSubmit }) {
  const saved = store.get('profile', {});
  const sel = (id, opts, value) => {
    const x = el('select', { id }, ...opts.map(([v, label]) => el('option', { value: v }, label)));
    if (value) x.value = value;
    return x;
  };
  const field = (label, input) => el('div', { class: 'field' }, el('label', { for: input.id }, label), input);
  const box = (id, label, checked) => {
    const b = el('input', { type: 'checkbox', id });
    b.checked = Boolean(checked);
    return el('label', { class: 'check' }, b, ` ${label}`);
  };
  const dob = el('input', { type: 'date', id: 'f-dob', required: true, min: '1960-01-01', max: '2015-12-31', value: saved.dob ?? '' });
  const error = el('p', { class: 'error', role: 'alert', hidden: true });
  const form = el('form', { class: 'card', novalidate: true },
    el('div', { class: 'grid' },
      index ? field(t('examLabel'), sel('f-exam', index.map((e) => [e.id, e.title]), preselect || saved.exam)) : null,
      field(t('dob'), dob),
      field(t('category'), sel('f-cat', [['UR', t('catUR')], ['EWS', t('catEWS')], ['OBC', t('catOBC')], ['SC', t('catSC')], ['ST', t('catST')]], saved.category)),
      field(t('education'), sel('f-edu', [['10', t('edu10')], ['12', t('edu12')], ['graduate', t('eduGraduate')]], saved.education ?? '12')),
      field(t('gender'), sel('f-gender', [['male', t('male')], ['female', t('female')], ['other', t('other')]], saved.gender)),
      el('div', { class: 'field' }, box('f-appearing', t('appearing'), saved.appearing), box('f-pwbd', t('pwbd'), saved.pwbd),
        box('f-other', t('otherCategory'), saved.otherCategory))),
    error, el('button', { class: 'primary', type: 'submit' }, submitLabel),
    el('p', { class: 'small' }, t('privacy')));
  form.addEventListener('submit', (ev) => {
    ev.preventDefault();
    const profile = {
      ...saved, dob: $('f-dob').value, category: $('f-cat').value, education: $('f-edu').value,
      gender: $('f-gender').value, appearing: $('f-appearing').checked, pwbd: $('f-pwbd').checked, otherCategory: $('f-other').checked,
    };
    if (index) profile.exam = $('f-exam').value;
    if (!profile.dob) { error.textContent = t('enterDob'); error.hidden = false; $('f-dob').focus(); return; }
    error.hidden = true;
    store.set('profile', profile);
    onSubmit(profile);
  });
  return form;
}

async function viewCheck(root, preselect) {
  let index;
  try { ({ exams: index } = await getJSON('rules/index.json')); } catch (e) { fill(root, errorBox(e)); return; }
  const result = el('div', { 'aria-live': 'polite' });
  const form = profileForm({
    index, preselect, submitLabel: t('check'),
    onSubmit: async (profile) => {
      try {
        const rules = await getJSON(`rules/${profile.exam}.json`);
        renderResult(result, rules, evaluate(rules, profile, todayIST()), todayIST());
        result.scrollIntoView({ behavior: 'smooth', block: 'start' });
      } catch (e) { fill(result, errorBox(e)); }
    },
  });
  fill(root, form, result);
}

// ---------- For me: one profile, every exam ----------
const QUAL_OK = { 10: ['10'], 12: ['10', '12'], graduate: ['10', '12', 'graduate'] };
async function viewForMe(root, editing = false) {
  let index; let families; let cal; let feed;
  try {
    [{ exams: index }, { families }, cal, feed] = await Promise.all([
      getJSON('rules/index.json'), getJSON('exams.json'), getJSON('calendar.json'), getJSON('feed.json')]);
  } catch (e) { fill(root, errorBox(e)); return; }
  const profile = store.get('profile', null);
  if (!profile?.dob || editing) {
    fill(root, el('h2', {}, t('forMeTitle')), el('p', { class: 'small' }, t('forMeIntro')),
      profileForm({ submitLabel: t('saveDetails'), onSubmit: () => viewForMe(root) }));
    return;
  }
  const today = todayIST();
  const rank = { open: 0, upcoming: 1, closed: 2 };
  const results = await Promise.all(index.map(async (e) => {
    const rules = await getJSON(`rules/${e.id}.json`);
    return { e, rules, r: evaluate(rules, profile, today), status: statusOf(e, today) };
  }));
  results.sort((a, b) => rank[a.status] - rank[b.status] || b.r.counts.eligible - a.r.counts.eligible);
  const covered = new Set(index.map((e) => e.exam));
  const ok = QUAL_OK[profile.education] ?? [];
  const matching = Object.entries(families).filter(([code, f]) => code !== 'LDCE' && !covered.has(code)
    && (f.qualifications ?? []).some((q) => ok.includes(q)));
  const upcoming = cal.entries.filter((c) => c.applyTo >= today && (families[c.exam]?.qualifications ?? []).some((q) => ok.includes(q)));
  const card = ({ e, rules, r, status }) => {
    const next = timeline(rules, feed.items, rules.exam, rules.examYear, today).find((s) => s.state === 'now' || s.state === 'next');
    const nextLabel = next ? `${next.key === 'exam' ? next.label : t(`stage_${next.key}`)}: ${next.at ? day(next.at) : next.from ? `${day(next.from)} – ${day(next.to)}` : next.when ?? t('notAnnounced')}` : null;
    return el('li', { class: 'item' },
      el('h3', {}, el('a', { href: `#exam/${rules.exam}` }, e.title)),
      el('div', { class: `banner small-banner ${status}` }, statusText(e, today)),
      el('div', { class: 'summary' },
        el('span', { class: 's-eligible' }, t('canApply', { n: r.counts.eligible })),
        el('span', { class: 's-check' }, t('toCheck', { n: r.counts.check })),
        el('span', { class: 's-ineligible' }, t('notEligible', { n: r.counts.ineligible }))),
      nextLabel ? el('p', { class: 'small' }, `${t(`st_${next.state}`)} → ${nextLabel}`) : null,
      el('div', { class: 'actions' }, el('a', { class: 'chip small', href: `#check/${e.id}` }, `✔ ${t('checkThis')}`),
        el('a', { class: 'chip small', href: `#exam/${rules.exam}` }, t('importantDates'))));
  };
  fill(root, 
    el('h2', {}, t('forMeTitle')),
    el('div', { class: 'actions' }, el('button', { class: 'chip small', type: 'button', onclick: () => viewForMe(root, true) }, `✎ ${t('forMeEdit')}`)),
    el('ul', { class: 'list' }, ...results.map(card)),
    matching.length ? el('h3', {}, t('forMeMatch')) : null,
    matching.length ? el('div', { class: 'actions' }, ...matching.map(([code, f]) => el('a', { class: 'chip', href: `#exam/${code}` }, f.name))) : null,
    upcoming.length ? el('h3', {}, t('forMeCalendar')) : null,
    upcoming.length ? el('ul', { class: 'rows card' }, ...upcoming.map((c) => el('li', {}, el('strong', {}, c.title),
      el('span', { class: 'small' }, ` · ${day(c.applyFrom)} – ${day(c.applyTo)}`)))) : null);
}

// ---------- Result by roll number ----------
// Pages of SSC's result write-ups where the cut-off details start (pipeline cutoffs.py).
// The marks themselves are never copied: the link opens SSC's PDF at that page.
function pageList(pages) {
  const runs = [];
  for (const p of pages) {
    const last = runs[runs.length - 1];
    if (last && p === last[1] + 1) last[1] = p; else runs.push([p, p]);
  }
  return runs.map(([a, b]) => (a === b ? String(a) : `${a}–${b}`)).join(', ');
}
function cutoffBlock(writeups, families, withExam = true) {
  const shown = writeups.filter((w) => w.exam !== 'LDCE' && safeUrl(w.url));
  return el('div', { class: 'card' }, el('h3', {}, t('cutoffTitle')), el('p', { class: 'small' }, t('cutoffNote')),
    shown.length ? el('ul', { class: 'rows' }, ...shown.map((w) => el('li', { class: 'row' },
      withExam && w.exam ? el('strong', {}, `${families[w.exam]?.short ?? w.exam}${w.examYear ? ` ${w.examYear}` : ''}`) : null,
      el('div', { class: 'small' }, w.title),
      el('a', { href: `${safeUrl(w.url)}#page=${w.pages[0]}`, rel: 'noopener', target: '_blank' }, t('cutoffFrom', { p: w.pages[0] })),
      el('span', { class: 'small' }, ` · ${w.name} · ${t('cutoffPages', { pages: pageList(w.pages) })} · ${day(w.publishedAt.slice(0, 10))}`))))
      : el('p', { class: 'empty' }, t('noCutoffs')));
}

async function viewResult(root) {
  let lists; let feed; let families;
  try {
    [{ lists }, feed, { families }] = await Promise.all([getJSON('results/index.json'), getJSON('feed.json'), getJSON('exams.json')]);
  } catch (e) { fill(root, errorBox(e)); return; }
  const writeups = (await getJSON('results/cutoffs.json').catch(() => null))?.writeups;
  const cutoffs = writeups ? cutoffBlock(writeups, families) : null;
  if (!lists.length) { fill(root, el('h2', {}, t('resultTitle')), el('p', { class: 'empty' }, t('noLists')), cutoffs); return; }
  const groups = [];
  // One choice per exam cycle: a roll number is looked up in every list of that cycle at once
  // (CT(GD) 2026 has separate female, male and withheld lists, each its own notice).
  for (const l of lists) {
    const key = l.exam && l.examYear ? `${l.exam}|${l.examYear}` : l.item;
    let g = groups.find((x) => x.key === key);
    if (!g) { g = { key, name: l.exam && l.examYear ? `${families[l.exam]?.name ?? l.exam} ${l.examYear}` : l.notice, items: [], lists: [] }; groups.push(g); }
    g.lists.push(l);
    if (!g.items.includes(l.item)) g.items.push(l.item);
  }
  for (const g of groups) g.label = `${g.name} · ${g.lists.length} ${g.lists.length === 1 ? 'list' : 'lists'}`;
  const sel = el('select', { id: 'r-exam' }, ...groups.map((g, i) => el('option', { value: String(i) }, g.label)));
  const roll = el('input', { id: 'r-roll', type: 'text', inputmode: 'numeric', autocomplete: 'off', placeholder: '126280000002', maxlength: '20' });
  roll.value = store.get('roll', '');
  const out = el('div', { 'aria-live': 'polite' });
  const form = el('form', { class: 'card', novalidate: true },
    el('div', { class: 'grid' },
      el('div', { class: 'field' }, el('label', { for: 'r-exam' }, t('resultExam')), sel),
      el('div', { class: 'field' }, el('label', { for: 'r-roll' }, t('rollNo')), roll)),
    el('button', { class: 'primary', type: 'submit' }, t('find')),
    el('p', { class: 'small' }, t('resultNote')));
  form.addEventListener('submit', async (ev) => {
    ev.preventDefault();
    const no = roll.value.replace(/\D/g, '');
    if (!/^\d{8,14}$/.test(no)) { fill(out, el('p', { class: 'error' }, t('rollInvalid'))); return; }
    store.set('roll', no);
    const g = groups[Number(sel.value)];
    try {
      const hits = await Promise.all(g.lists.map(async (l) => ({ l, found: (await getJSON(`results/${l.id}/${no.slice(-2)}.json`)).includes(no) })));
      const items = feed.items.filter((i) => g.items.includes(i.id));
      const found = hits.filter((h) => h.found);
      fill(out, 
        ...found.map((h) => el('div', { class: 'banner open' }, t('foundIn', { list: h.l.title }))),
        ...hits.filter((h) => !h.found).map((h) => el('p', { class: 'small' }, `${t('notIn', { list: h.l.title })} (${t('listCount', { n: h.l.count.toLocaleString('en-IN') })})`)),
        found.length ? null : el('div', { class: 'banner upcoming' }, t('notFoundAny')),
        items.length ? el('div', { class: 'card' }, el('strong', {}, t('officialFiles')), el('div', { class: 'files' },
          ...[...new Map(items.flatMap((i) => i.files).map((f) => [f.url, f])).values()].map((f) => (safeUrl(f.url) ? el('a', { href: safeUrl(f.url), rel: 'noopener', target: '_blank' }, `${t('pdf')}: ${f.name}${size(f.size)}`) : null)))) : null);
    } catch (e) { fill(out, errorBox(e)); }
  });
  fill(root, el('h2', {}, t('resultTitle')), form, out, cutoffs);
}

function renderResult(box, rules, r, today) {
  const of = (s) => r.posts.filter((p) => p.status === s);
  const lines = (p) => [...p.reasons.map((c) => reasonText(c, rules)), ...p.notes.map(noteText)];
  const card = (p) => el('li', { class: `item post ${p.status}` },
    el('div', { class: 'meta' }, el('span', {}, p.payLevel ? t('payLevel', { l: p.payLevel, g: p.group }) : t('groupOnly', { g: p.group }))),
    el('h3', {}, p.name), el('div', { class: 'small' }, p.org),
    el('div', { class: 'small' }, t('bornWindow', { from: day(p.bornFrom), to: day(p.bornTo) })),
    payText(rules, p.payLevel) ? el('div', { class: 'small' }, payText(rules, p.payLevel)) : null,
    lines(p).length ? el('ul', {}, ...lines(p).map((x) => el('li', {}, x))) : null);
  // 40 CGL posts as full cards is ~15 phone screens: only "check" gets cards; the rest are short rows.
  const row = (p) => el('li', { class: `row ${p.status}` }, el('strong', {}, p.name), el('span', { class: 'small' }, ` · ${p.org}`),
    payText(rules, p.payLevel) ? el('div', { class: 'small' }, payText(rules, p.payLevel)) : null,
    lines(p).length ? el('div', { class: 'small' }, lines(p).join(' ')) : null);
  const group = (status, open) => {
    const posts = of(status);
    if (!posts.length) return null;
    return el('details', { class: `group ${status}`, open },
      el('summary', {}, `${t(`st_${status}`)} (${posts.length})`),
      status === 'check' ? el('ul', { class: 'list' }, ...posts.map(card)) : el('ul', { class: 'rows' }, ...posts.map(row)));
  };
  const entry = { applyFrom: rules.dates.applyFrom, applyTo: rules.dates.applyTo };
  fill(box, 
    el('div', { class: `banner ${r.application}` }, statusText(entry, today), r.application === 'closed' ? ` ${t('closedNote')}` : ''),
    el('div', { class: 'summary' },
      el('span', { class: 's-eligible' }, t('canApply', { n: r.counts.eligible })),
      el('span', { class: 's-check' }, t('toCheck', { n: r.counts.check })),
      el('span', { class: 's-ineligible' }, t('notEligible', { n: r.counts.ineligible }))),
    el('p', { class: 'small' }, t('feeLine', { fee: r.fee ? `₹${r.fee}` : t('feeNone'), n: r.relaxationYears, date: day(rules.age.asOn) }), ' ',
      el('a', { href: `#exam/${rules.exam}` }, t('importantDates'))),
    group('check', true), group('eligible', of('eligible').length <= 8),
    group('ineligible', !of('eligible').length && !of('check').length),
    el('p', { class: 'small' }, t('rulesFrom', { date: day(rules.source.published), checked: day(rules.source.checkedOn) })));
}

// ---------- How to apply, documents, helplines (from ssc_guide.json) ----------
const L = (entry) => entry[getLang()] ?? entry.en;
function guideBlock(g, open) {
  return el('details', { class: 'group', open },
    el('summary', {}, `${t('howToApply')} · ${t('documents')}`),
    el('div', { class: 'inner' },
      el('ol', {}, ...g.steps.map((x) => el('li', {}, L(x), ' ', el('span', { class: 'ref' }, `(${x.ref})`),
        x.tool === 'signature' ? el('span', {}, ' ', el('a', { href: '#more' }, `→ ${t('sigTitle')}`)) : null))),
      el('strong', {}, t('documents')),
      el('ul', {}, ...g.documents.map((x) => el('li', {}, L(x), ' ', el('span', { class: 'ref' }, `(${x.ref})`)))),
      el('p', { class: 'ref' }, t('sourceRef', { ref: g.source.title }), ' · ',
        el('a', { href: safeUrl(g.source.notice) ?? 'https://ssc.gov.in', rel: 'noopener', target: '_blank' }, t('noticePdf')))));
}
function helplineBlock(g) {
  return el('details', { class: 'group' }, el('summary', {}, `☎ ${t('helplineTitle')}`),
    el('div', { class: 'inner' }, el('p', { class: 'small' }, t('helplineNote')),
      el('ul', { class: 'rows' }, ...g.helplines.list.map((h) => el('li', {}, el('strong', {}, h.office), el('br'),
        ...h.phones.flatMap((ph, i) => [i ? ' · ' : '', el('a', { href: `tel:${ph.replace(/\s/g, '')}` }, ph)])))),
      el('p', { class: 'ref' }, t('sourceRef', { ref: g.helplines.ref }))));
}

// ---------- FAQ (answers only from the rules file) ----------
function faqBlock(r, fam) {
  const name = fam.short ?? r.title;
  const qa = [
    [t('faqLast', { exam: name }), t('faqLastA', { date: day(r.dates.applyTo), fee: day(r.dates.feeTo) })],
    [t('faqAge', { exam: name }), Object.entries(r.age.bands).map(([b, x]) => t('ageBand', { band: b, from: day(x.bornFrom), to: day(x.bornTo) })).join('; ')],
    [t('faqFee'), t('faqFeeA', { amount: r.fee.amount, exempt: r.fee.exempt.map((x) => t(`exempt_${x}`)).join(', ') })],
  ];
  if (r.payScales) {
    const levels = [...new Set(r.posts.flatMap((p) => String(p.payLevel ?? '').split('/')).filter(Boolean))].sort((x, y) => x - y);
    qa.push([t('faqSalary'), levels.map((l) => payText(r, l)).filter(Boolean).join(' · ')]);
  }
  if (r.examSchedule?.length) {
    qa.push([t('faqExam', { exam: name }), r.examSchedule.map((x) => `${x.stage}: ${x.from ? `${day(x.from)} – ${day(x.to)} (${t('tentative')})` : x.when}`).join('; ')]);
  }
  if (r.vacancies) qa.push([t('faqVacancy'), t('vacancyCount', { n: r.vacancies.count.toLocaleString('en-IN'), tentative: t('tentative') })]);
  if (fam.qualifications) qa.push([t('faqQual'), `${fam.qualifications.map((q) => t(`qual_${q}`)).join(' / ')} (${fam.ref})`]);
  return el('details', { class: 'group' }, el('summary', {}, t('faqTitle')),
    el('div', { class: 'inner' }, ...qa.map(([q, a]) => el('div', {}, el('h3', {}, q), el('p', {}, a)))));
}

// ---------- tools ----------
function signatureTool() {
  const spec = { minKB: 10, maxKB: 20, widthCm: 6, heightCm: 2 }; // every 2026 SSC Notice checked (CHSL, CGL, Steno, SI)
  const out = el('div', { 'aria-live': 'polite' });
  const file = el('input', { type: 'file', accept: 'image/*', 'aria-label': t('sigPick') });
  file.addEventListener('change', async () => {
    if (!file.files[0]) return;
    fill(out, el('p', { class: 'small' }, t('loading')));
    try {
      const res = await makeSignature(file.files[0], spec.minKB, spec.maxKB);
      if (!res) { fill(out, el('p', { class: 'error' }, t('sigFail', { min: spec.minKB, max: spec.maxKB }))); return; }
      const url = URL.createObjectURL(res.blob);
      fill(out, el('img', { src: url, alt: '', class: 'sig' }),
        el('p', {}, t('sigResult', { kb: (res.blob.size / 1024).toFixed(1), w: res.width, h: res.height })),
        el('a', { class: 'primary link-button', href: url, download: 'signature.jpg' }, `⬇ ${t('sigDownload')}`));
    } catch (e) { fill(out, errorBox(e)); }
  });
  return el('section', { class: 'card', id: 'tool-signature' }, el('h3', {}, t('sigTitle')),
    el('p', {}, t('sigRule', { min: spec.minKB, max: spec.maxKB, w: spec.widthCm, h: spec.heightCm })), file, out,
    el('p', { class: 'small' }, `${t('photoTitle')}: ${t('photoRule')}`), el('p', { class: 'ref' }, t('photoSource')));
}

function ageTool() {
  const dob = el('input', { type: 'date', id: 'age-dob' });
  const on = el('input', { type: 'date', id: 'age-on', value: todayIST() });
  const out = el('p', { 'aria-live': 'polite' });
  const calc = () => {
    if (!dob.value || !on.value) { out.textContent = ''; return; }
    const a = ageOn(dob.value, on.value);
    out.textContent = a ? t('ageResult', a) : t('ageInvalid');
  };
  dob.addEventListener('input', calc);
  on.addEventListener('input', calc);
  return el('section', { class: 'card' }, el('h3', {}, t('ageTitle')),
    el('div', { class: 'grid' }, el('div', { class: 'field' }, el('label', { for: 'age-dob' }, t('dob')), dob),
      el('div', { class: 'field' }, el('label', { for: 'age-on' }, t('ageOn')), on)), out);
}

function typingTool(g) {
  const tests = g.skillTests.tests.filter((x) => !x.id.endsWith('-hi')); // Hindi needs a Hindi layout: not practised here
  const testSel = el('select', { id: 'ty-test' }, ...tests.map((x) => el('option', { value: x.id }, x.name)));
  const minSel = el('select', { id: 'ty-min' });
  const text = el('div', { class: 'typing-text', lang: 'en' });
  const box = el('textarea', { id: 'ty-box', spellcheck: 'false', autocapitalize: 'off', autocomplete: 'off', 'aria-label': t('typingTitle') });
  const status = el('p', { 'aria-live': 'polite', class: 'small' });
  const out = el('div', { 'aria-live': 'polite' });
  let passage = ''; let started = 0; let timer = null; let n = 0;
  const test = () => tests.find((x) => x.id === testSel.value);
  const fillMinutes = () => {
    const full = test().minutes;
    fill(minSel, ...[...new Set([1, 2, 5, full])].map((m) => el('option', { value: String(m) }, m === full ? t('typingFull', { n: m }) : `${m} min`)));
    minSel.value = String(full);
  };
  const finish = () => {
    clearInterval(timer); timer = null; box.readOnly = true;
    const secs = Math.min((Date.now() - started) / 1000, Number(minSel.value) * 60);
    const sc = typingScore(passage, box.value, secs);
    const tt = test();
    fill(out, el('div', { class: `banner ${sc.kdph >= tt.kdph ? 'open' : 'upcoming'}` },
      t('typingResult', { kdph: sc.kdph.toLocaleString('en-IN'), wpm: sc.wpm, accuracy: sc.accuracy, correct: sc.correct, typed: sc.typed }), el('br'),
      t(sc.kdph >= tt.kdph ? 'typingMeets' : 'typingBelow', { name: tt.name, kdph: tt.kdph.toLocaleString('en-IN') })));
  };
  const reset = () => {
    clearInterval(timer); timer = null; started = 0;
    passage = practiceText(n++);
    text.textContent = passage;
    box.value = ''; box.readOnly = false;
    status.textContent = `${t('typingMinutes')}: ${minSel.value}:00`;
    fill(out);
  };
  box.addEventListener('input', () => {
    if (!started) {
      started = Date.now();
      const limit = Number(minSel.value) * 60;
      timer = setInterval(() => {
        const left = Math.max(0, limit - Math.floor((Date.now() - started) / 1000));
        status.textContent = `${t('typingMinutes')}: ${Math.floor(left / 60)}:${String(left % 60).padStart(2, '0')}`;
        if (left === 0) { status.textContent = t('typingDone'); finish(); }
      }, 250);
    }
    if (box.value.length >= passage.length) finish();
  });
  testSel.addEventListener('change', () => { fillMinutes(); reset(); });
  minSel.addEventListener('change', reset);
  fillMinutes(); reset();
  return el('section', { class: 'card', id: 'tool-typing' }, el('h3', {}, t('typingTitle')), el('p', { class: 'small' }, t('typingIntro')),
    el('div', { class: 'grid' }, el('div', { class: 'field' }, el('label', { for: 'ty-test' }, t('typingTest')), testSel),
      el('div', { class: 'field' }, el('label', { for: 'ty-min' }, t('typingMinutes')), minSel)),
    el('h3', {}, status), text, box,
    el('div', { class: 'actions' }, el('button', { class: 'chip', type: 'button', onclick: reset }, `↺ ${t('typingStart')}`)), out,
    el('p', { class: 'ref' }, t('typingNote'), ' ', t('sourceRef', { ref: g.skillTests.ref })));
}

// ---------- More: tools, guide, helplines, saved, settings, about ----------
async function viewMore(root) {
  let g = null; let formats = [];
  try { g = await getJSON('guide.json'); } catch { /* tools still work */ }
  try { formats = (await getJSON('resources.json')).sections.certificate_formats; } catch { /* optional */ }
  const themeSel = el('select', { id: 'set-theme' }, ...['auto', 'light', 'dark'].map((v) => el('option', { value: v }, t(`theme${v[0].toUpperCase()}${v.slice(1)}`))));
  themeSel.value = store.get('theme', 'auto');
  themeSel.addEventListener('change', () => applyTheme(themeSel.value));
  const langSel = el('select', { id: 'set-lang' }, el('option', { value: 'en' }, 'English'), el('option', { value: 'hi' }, 'हिन्दी'));
  langSel.value = getLang();
  langSel.addEventListener('change', () => { setLang(langSel.value); store.set('lang', getLang()); paintShell(); route(); });
  fill(root,
    el('div', { class: 'tiles' },
      el('a', { class: 'tile', href: '#saved' }, el('strong', {}, t('savedLink')), el('span', { class: 'small' }, t('followedExams'))),
      el('a', { class: 'tile', href: '#forme' }, el('strong', {}, `✎ ${t('forMeEdit')}`), el('span', { class: 'small' }, t('forMeTitle')))),
    el('h2', {}, t('toolsTitle')), signatureTool(), g ? typingTool(g) : null, ageTool(),
    g ? el('h2', {}, t('howToApply')) : null, g ? guideBlock(g, true) : null, g ? helplineBlock(g) : null,
    formats.length ? el('details', { class: 'group' }, el('summary', {}, t('docsTitle')),
      el('div', { class: 'inner' }, el('p', { class: 'small' }, t('docsNote')), resourceBlock('certificate_formats', formats, false))) : null,
    el('h2', {}, t('settings')),
    el('div', { class: 'card grid' },
      el('div', { class: 'field' }, el('label', { for: 'set-lang' }, t('language')), langSel),
      el('div', { class: 'field' }, el('label', { for: 'set-theme' }, t('theme')), themeSel)),
    el('h2', {}, t('aboutTitle')), el('div', { class: 'card' }, el('p', {}, t('aboutText')), el('p', { class: 'small' }, t('disclaimer'))));
}

// ---------- Home: what is open, what is coming, what is new ----------
async function viewHome(root) {
  let index; let cal; let feed;
  try {
    [{ exams: index }, cal, feed] = await Promise.all([getJSON('rules/index.json'), getJSON('calendar.json'), getJSON('feed.json')]);
  } catch (e) { fill(root, errorBox(e)); return; }
  const today = todayIST();
  const rulesList = await Promise.all(index.map((e) => getJSON(`rules/${e.id}.json`).catch(() => null)));
  const open = index.filter((e) => statusOf(e, today) === 'open');
  const in30 = new Date(Date.parse(`${today}T00:00:00Z`) + 30 * 864e5).toISOString().slice(0, 10);
  const coming = rulesList.filter(Boolean).flatMap((r) => (r.examSchedule ?? [])
    .filter((x) => x.from && x.to >= today && x.from <= in30).map((x) => ({ r, x })))
    .sort((a, b) => (a.x.from < b.x.from ? -1 : 1));
  const nextPlan = cal.entries.filter((c) => c.applyFrom > today).sort((a, b) => (a.applyFrom < b.applyFrom ? -1 : 1))[0];
  const latestBox = el('div');
  fill(root,
    store.get('profile', null)?.dob ? null : el('a', { class: 'banner upcoming cta', href: '#forme' }, `${t('forMeNoProfile')} →`),
    el('h2', {}, t('homeOpen')),
    open.length ? el('div', { class: 'tiles' }, ...open.map((e) => el('a', { class: 'tile', href: `#exam/${e.exam}` },
      el('strong', {}, e.title), el('span', { class: 'count open' }, statusText(e, today)))))
      : el('div', { class: 'card' }, el('p', {}, t('homeNoneOpen')),
        nextPlan ? el('p', { class: 'small' }, t('homeNextPlan', { title: nextPlan.title, from: day(nextPlan.applyFrom), to: day(nextPlan.applyTo) })) : null),
    el('h2', {}, t('homeComing')),
    coming.length ? el('div', { class: 'tiles' }, ...coming.map(({ r, x }) => {
      const now = x.from <= today;
      const n = daysBetween(today, x.from);
      return el('a', { class: 'tile', href: `#exam/${r.exam}` }, el('strong', {}, `${r.title}`), el('span', { class: 'small' }, `${x.stage} · ${day(x.from)} – ${day(x.to)}`),
        el('br'), el('span', { class: `count ${now ? 'now' : 'soon'}` }, now ? t('onNow') : n === 0 ? t('today') : t('inDays', { n })));
    })) : el('p', { class: 'card small' }, t('homeNoneComing')),
    el('h2', {}, t('homeLatest')), latestBox);
  await viewLatest(latestBox, feed);
}

// ---------- Saved ----------
async function viewSaved(root) {
  const items = Object.values(savedItems()).sort((a, b) => (a.publishedAt < b.publishedAt ? 1 : -1));
  let families = {};
  try { ({ families } = await getJSON('exams.json')); } catch { /* names fall back to codes */ }
  const f = followed();
  fill(root, 
    f.length ? el('div', { class: 'card' }, el('strong', {}, t('followedExams')),
      el('div', { class: 'actions' }, ...f.map((c) => el('a', { class: 'chip', href: `#exam/${c}` }, families[c]?.name ?? c)))) : null,
    items.length ? el('ul', { class: 'list' }, ...items.map((i) => itemCard(i, { families }))) : el('p', { class: 'empty' }, t('savedEmpty')));
}

// ---------- shell ----------
// Bottom navigation, ordered by what people search for (jobs/research/NEEDS.md): what is new and open,
// which exams suit me, my result, exam details; tools and help under More.
const ICON = {
  home: 'M3 11l9-7 9 7v9a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1z',
  forme: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zm-7 9a7 7 0 0 1 14 0',
  result: 'M10 17a7 7 0 1 1 5-2l5 5M10 7v6m-3-3h6',
  exams: 'M7 3h10v18H7zM10 7h4M10 11h4M10 15h2',
  more: 'M5 12h.01M12 12h.01M19 12h.01',
  auto: 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM12 3v18',
  light: 'M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8zM12 2v2M12 20v2M2 12h2M20 12h2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4',
  dark: 'M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z',
};
const TABS = [['home', 'tabHome'], ['forme', 'tabForMe'], ['result', 'tabResult'], ['exams', 'tabExams'], ['more', 'tabMore']];
const PARENT = { exam: 'exams', check: 'forme', saved: 'more', latest: 'home', tools: 'more' };
function icon(path) {
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.setAttribute('viewBox', '0 0 24 24');
  svg.setAttribute('aria-hidden', 'true');
  const p = document.createElementNS('http://www.w3.org/2000/svg', 'path');
  p.setAttribute('d', path);
  p.setAttribute('fill', 'none');
  p.setAttribute('stroke', 'currentColor');
  p.setAttribute('stroke-width', '2');
  p.setAttribute('stroke-linecap', 'round');
  p.setAttribute('stroke-linejoin', 'round');
  svg.append(p);
  return svg;
}
function applyTheme(mode) {
  store.set('theme', mode);
  if (mode === 'light' || mode === 'dark') document.documentElement.dataset.theme = mode;
  else delete document.documentElement.dataset.theme;
  $('theme').setAttribute('aria-label', `${t('theme')}: ${t(`theme${mode[0].toUpperCase()}${mode.slice(1)}`)}`);
  fill($('theme'), icon(ICON[mode]));
}
function paintShell() {
  document.title = t('appName');
  $('app-name').textContent = t('appName');
  $('disclaimer').textContent = t('shortDisclaimer');
  $('footer-disclaimer').textContent = t('disclaimer');
  $('lang').textContent = t('langToggle');
  $('offline').textContent = t('offline');
  applyTheme(store.get('theme', 'auto'));
  fill($('tabs'), ...TABS.map(([id, key]) => el('a', { href: `#${id}`, id: `tab-${id}` }, icon(ICON[id]), el('span', {}, t(key)))));
}
const skeleton = () => [1, 2, 3].map(() => el('div', { class: 'skeleton' }));
async function route() {
  const [hashView, arg] = location.hash.slice(1).split('/');
  const view = hashView || 'home';
  const tab = PARENT[view] ?? (TABS.some(([id]) => id === view) ? view : 'home');
  for (const [id] of TABS) {
    if (id === tab) $(`tab-${id}`).setAttribute('aria-current', 'page'); else $(`tab-${id}`).removeAttribute('aria-current');
  }
  const root = $('view');
  root.setAttribute('aria-busy', 'true');
  fill(root, ...skeleton());
  window.scrollTo(0, 0);
  if (view === 'exam' && arg) await viewExam(root, decodeURIComponent(arg));
  else if (tab === 'exams') await viewExams(root);
  else if (view === 'check') await viewCheck(root, arg && decodeURIComponent(arg));
  else if (view === 'saved') await viewSaved(root);
  else if (tab === 'forme') await viewForMe(root);
  else if (tab === 'result') await viewResult(root);
  else if (tab === 'more') await viewMore(root);
  else await viewHome(root);
  root.removeAttribute('aria-busy');
  $('offline').hidden = navigator.onLine !== false;
}

setLang(store.get('lang', navigator.language?.startsWith('hi') ? 'hi' : 'en'));
paintShell();
$('lang').addEventListener('click', () => {
  setLang(getLang() === 'hi' ? 'en' : 'hi');
  store.set('lang', getLang());
  paintShell();
  route();
});
$('theme').addEventListener('click', () => {
  const order = ['auto', 'light', 'dark'];
  applyTheme(order[(order.indexOf(store.get('theme', 'auto')) + 1) % order.length]);
});
window.addEventListener('hashchange', route);
window.addEventListener('online', () => { $('offline').hidden = true; });
window.addEventListener('offline', () => { $('offline').hidden = false; });
route();
if ('serviceWorker' in navigator) navigator.serviceWorker.register('sw.js').catch(() => { /* site works without it */ });
