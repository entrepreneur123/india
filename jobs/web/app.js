// SSC Job Alerts website. Reads the static JSON that jobs/pipeline publishes next to this page:
//   v1/feed.json, exams.json, calendar.json, resources.json, rules/index.json, rules/<id>.json
// Every string from SSC goes into the page through textContent, never innerHTML.
import { evaluate, todayIST } from './eligibility.js';
import { getLang, setLang, t } from './i18n.js';
import { ageOn, buildIcs, examEvents, makeSignature } from './tools.js';

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

// ---------- saved items and followed exams (this device only) ----------
const savedItems = () => store.get('saved', {});
const followed = () => store.get('follow', []);
function toggleSave(item, btn) {
  const s = savedItems();
  if (s[item.id]) delete s[item.id]; else s[item.id] = item;
  store.set('saved', s);
  btn.textContent = s[item.id] ? `★ ${t('saved')}` : `☆ ${t('save')}`;
  btn.setAttribute('aria-pressed', String(Boolean(s[item.id])));
}
function toggleFollow(code, btn) {
  const f = new Set(followed());
  if (f.has(code)) f.delete(code); else f.add(code);
  store.set('follow', [...f]);
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
      i.exam ? el('a', { class: 'badge', href: `#exam/${i.exam}`, title: fam?.name ?? '' }, `${i.exam}${i.examYear ? ` ${i.examYear}` : ''}`) : null,
      el('time', { datetime: i.publishedAt }, `${when(i.publishedAt)} ${t('ist')}`)),
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

async function viewLatest(root) {
  let feed;
  let families;
  try {
    [feed, { families }] = await Promise.all([getJSON('feed.json'), getJSON('exams.json')]);
  } catch (e) { root.replaceChildren(errorBox(e)); return; }
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
      (filters.lang === 'all' || i.lang === filters.lang) &&
      (filters.dept || i.exam !== 'LDCE') &&
      (!filters.mine || mine.has(i.exam)) &&
      (!q || i.title.toLowerCase().includes(q)));
    list.replaceChildren(...(items.length ? items.slice(0, 150).map((i) => itemCard(i, { isNew: isNew(i), families }))
      : [el('li', { class: 'empty' }, t('nothing'))]));
  };
  const chips = el('div', { class: 'filters', role: 'group' });
  const drawChips = () => chips.replaceChildren(...KINDS.map(([k, key]) => el('button', {
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
  root.replaceChildren(
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
  } catch (e) { root.replaceChildren(errorBox(e)); return; }
  const today = todayIST();
  const mine = new Set(followed());
  const cards = Object.entries(families).filter(([c]) => c !== 'LDCE').map(([code, f]) => {
    const latest = index.filter((r) => r.exam === code).sort((a, b) => b.examYear - a.examYear)[0];
    const btn = el('button', { class: 'chip small', type: 'button', 'aria-pressed': String(mine.has(code)) },
      mine.has(code) ? `✓ ${t('following')}` : `+ ${t('follow')}`);
    btn.addEventListener('click', (ev) => { ev.preventDefault(); toggleFollow(code, btn); });
    return el('li', { class: 'item' },
      el('h3', {}, el('a', { href: `#exam/${code}` }, f.name)),
      el('div', { class: 'small', lang: 'hi' }, f.hi),
      el('div', { class: 'small' }, f.qualifications ? t('minQual', { q: f.qualifications.map((q) => t(`qual_${q}`)).join(' / ') }) : t('qualUnknown')),
      latest ? el('div', { class: `banner small-banner ${statusOf(latest, today)}` }, statusText(latest, today)) : null,
      el('div', { class: 'actions' }, btn));
  });
  root.replaceChildren(
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
  } catch (e) { root.replaceChildren(errorBox(e)); return; }
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
  if (rules) parts.push(...examDetails(rules, entry, today));
  else parts.push(el('p', { class: 'small' }, t('noRules')));
  if (examResources.length) parts.push(el('h3', {}, t('resourcesTitle')), ...examResources.map(([k, items]) => resourceBlock(k, items, false)));
  parts.push(el('h3', {}, `${t('allNotices')} (${notices.length})`),
    el('ul', { class: 'list' }, ...(notices.length ? notices.slice(0, 60).map((i) => itemCard(i, { families })) : [el('li', { class: 'empty' }, t('nothing'))])));
  root.replaceChildren(...parts);
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
      el('ul', { class: 'rows' }, ...r.posts.map((p) => el('li', {}, el('strong', {}, p.name), el('span', { class: 'small' }, ` · ${p.org} · ${p.payLevel ? t('payLevel', { l: p.payLevel, g: p.group }) : t('groupOnly', { g: p.group })} · ${p.ageBand}`))))),
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

async function viewCheck(root, preselect) {
  let index;
  try { ({ exams: index } = await getJSON('rules/index.json')); } catch (e) { root.replaceChildren(errorBox(e)); return; }
  const saved = store.get('profile', {});
  const sel = (id, opts, value) => {
    const s = el('select', { id }, ...opts.map(([v, label]) => el('option', { value: v }, label)));
    if (value) s.value = value;
    return s;
  };
  const field = (label, input) => el('div', { class: 'field' }, el('label', { for: input.id }, label), input);
  const box = (id, label, checked) => {
    const b = el('input', { type: 'checkbox', id });
    b.checked = Boolean(checked);
    return el('label', { class: 'check' }, b, ` ${label}`);
  };
  const exam = sel('f-exam', index.map((e) => [e.id, e.title]), preselect || saved.exam);
  const dob = el('input', { type: 'date', id: 'f-dob', required: true, min: '1960-01-01', max: '2015-12-31', value: saved.dob ?? '' });
  const result = el('div', { 'aria-live': 'polite' });
  const form = el('form', { class: 'card', novalidate: true },
    el('div', { class: 'grid' },
      field(t('examLabel'), exam), field(t('dob'), dob),
      field(t('category'), sel('f-cat', [['UR', t('catUR')], ['EWS', t('catEWS')], ['OBC', t('catOBC')], ['SC', t('catSC')], ['ST', t('catST')]], saved.category)),
      field(t('education'), sel('f-edu', [['10', t('edu10')], ['12', t('edu12')], ['graduate', t('eduGraduate')]], saved.education ?? '12')),
      field(t('gender'), sel('f-gender', [['male', t('male')], ['female', t('female')], ['other', t('other')]], saved.gender)),
      el('div', { class: 'field' }, box('f-appearing', t('appearing'), saved.appearing), box('f-pwbd', t('pwbd'), saved.pwbd),
        box('f-other', t('otherCategory'), saved.otherCategory))),
    el('button', { class: 'primary', type: 'submit' }, t('check')),
    el('p', { class: 'small' }, t('privacy')));
  form.addEventListener('submit', async (ev) => {
    ev.preventDefault();
    const profile = {
      exam: $('f-exam').value, dob: $('f-dob').value, category: $('f-cat').value, education: $('f-edu').value,
      gender: $('f-gender').value, appearing: $('f-appearing').checked, pwbd: $('f-pwbd').checked, otherCategory: $('f-other').checked,
    };
    if (!profile.dob) { $('f-dob').focus(); result.replaceChildren(el('p', { class: 'error' }, t('enterDob'))); return; }
    store.set('profile', profile);
    try {
      const rules = await getJSON(`rules/${profile.exam}.json`);
      renderResult(result, rules, evaluate(rules, profile, todayIST()), todayIST());
      result.scrollIntoView({ behavior: 'smooth', block: 'start' });
    } catch (e) { result.replaceChildren(errorBox(e)); }
  });
  root.replaceChildren(form, result);
}

function renderResult(box, rules, r, today) {
  const of = (s) => r.posts.filter((p) => p.status === s);
  const lines = (p) => [...p.reasons.map((c) => reasonText(c, rules)), ...p.notes.map(noteText)];
  const card = (p) => el('li', { class: `item post ${p.status}` },
    el('div', { class: 'meta' }, el('span', {}, p.payLevel ? t('payLevel', { l: p.payLevel, g: p.group }) : t('groupOnly', { g: p.group }))),
    el('h3', {}, p.name), el('div', { class: 'small' }, p.org),
    el('div', { class: 'small' }, t('bornWindow', { from: day(p.bornFrom), to: day(p.bornTo) })),
    lines(p).length ? el('ul', {}, ...lines(p).map((x) => el('li', {}, x))) : null);
  // 40 CGL posts as full cards is ~15 phone screens: only "check" gets cards; the rest are short rows.
  const row = (p) => el('li', { class: `row ${p.status}` }, el('strong', {}, p.name), el('span', { class: 'small' }, ` · ${p.org}`),
    lines(p).length ? el('div', { class: 'small' }, lines(p).join(' ')) : null);
  const group = (status, open) => {
    const posts = of(status);
    if (!posts.length) return null;
    return el('details', { class: `group ${status}`, open },
      el('summary', {}, `${t(`st_${status}`)} (${posts.length})`),
      status === 'check' ? el('ul', { class: 'list' }, ...posts.map(card)) : el('ul', { class: 'rows' }, ...posts.map(row)));
  };
  const entry = { applyFrom: rules.dates.applyFrom, applyTo: rules.dates.applyTo };
  box.replaceChildren(
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

// ---------- Tools ----------
async function viewTools(root) {
  const spec = { minKB: 10, maxKB: 20, widthCm: 6, heightCm: 2 }; // every 2026 SSC Notice checked (CHSL, CGL, Steno, SI)
  const out = el('div', { 'aria-live': 'polite' });
  const file = el('input', { type: 'file', accept: 'image/*', 'aria-label': t('sigPick') });
  file.addEventListener('change', async () => {
    if (!file.files[0]) return;
    out.replaceChildren(el('p', { class: 'small' }, '…'));
    try {
      const res = await makeSignature(file.files[0], spec.minKB, spec.maxKB);
      if (!res) { out.replaceChildren(el('p', { class: 'error' }, t('sigFail', { min: spec.minKB, max: spec.maxKB }))); return; }
      const url = URL.createObjectURL(res.blob);
      out.replaceChildren(el('img', { src: url, alt: '', class: 'sig' }),
        el('p', {}, t('sigResult', { kb: (res.blob.size / 1024).toFixed(1), w: res.width, h: res.height })),
        el('a', { class: 'primary link-button', href: url, download: 'signature.jpg' }, `⬇ ${t('sigDownload')}`));
    } catch (e) { out.replaceChildren(errorBox(e)); }
  });
  const dob = el('input', { type: 'date', 'aria-label': t('dob') });
  const on = el('input', { type: 'date', value: todayIST(), 'aria-label': t('ageOn') });
  const ageOut = el('p', { 'aria-live': 'polite' });
  const calc = () => {
    if (!dob.value || !on.value) { ageOut.textContent = ''; return; }
    const a = ageOn(dob.value, on.value);
    ageOut.textContent = a ? t('ageResult', a) : t('ageInvalid');
  };
  dob.addEventListener('input', calc);
  on.addEventListener('input', calc);
  let formats = [];
  try { formats = (await getJSON('resources.json')).sections.certificate_formats; } catch { /* the rest still works */ }
  root.replaceChildren(
    el('p', { class: 'small' }, t('toolsIntro')),
    el('section', { class: 'card' }, el('h3', {}, t('sigTitle')),
      el('p', {}, t('sigRule', { min: spec.minKB, max: spec.maxKB, w: spec.widthCm, h: spec.heightCm })), file, out),
    el('section', { class: 'card' }, el('h3', {}, t('photoTitle')), el('p', {}, t('photoRule')), el('p', { class: 'small' }, t('photoSource'))),
    el('section', { class: 'card' }, el('h3', {}, t('ageTitle')),
      el('div', { class: 'grid' }, el('div', { class: 'field' }, el('label', {}, t('dob')), dob), el('div', { class: 'field' }, el('label', {}, t('ageOn')), on)), ageOut),
    formats.length ? el('section', { class: 'card' }, el('h3', {}, t('docsTitle')), el('p', { class: 'small' }, t('docsNote')),
      resourceBlock('certificate_formats', formats, false)) : null);
}

// ---------- Saved ----------
async function viewSaved(root) {
  const items = Object.values(savedItems()).sort((a, b) => (a.publishedAt < b.publishedAt ? 1 : -1));
  let families = {};
  try { ({ families } = await getJSON('exams.json')); } catch { /* names fall back to codes */ }
  const f = followed();
  root.replaceChildren(
    f.length ? el('div', { class: 'card' }, el('strong', {}, t('followedExams')),
      el('div', { class: 'actions' }, ...f.map((c) => el('a', { class: 'chip', href: `#exam/${c}` }, families[c]?.name ?? c)))) : null,
    items.length ? el('ul', { class: 'list' }, ...items.map((i) => itemCard(i, { families }))) : el('p', { class: 'empty' }, t('savedEmpty')));
}

// ---------- shell ----------
const TABS = [['latest', 'tabLatest'], ['exams', 'tabExams'], ['check', 'tabCheck'], ['tools', 'tabTools'], ['saved', 'tabSaved']];
function paintShell() {
  document.title = `${t('appName')}`;
  $('app-name').textContent = t('appName');
  $('tagline').textContent = t('tagline');
  $('disclaimer').textContent = t('shortDisclaimer');
  $('footer-disclaimer').textContent = t('disclaimer');
  $('lang').textContent = t('langToggle');
  $('offline').textContent = t('offline');
  $('tabs').replaceChildren(...TABS.map(([id, key]) => el('a', { role: 'tab', href: `#${id}`, id: `tab-${id}` }, t(key))));
}
async function route() {
  const [view, arg] = location.hash.slice(1).split('/');
  const tab = { exam: 'exams' }[view] ?? (TABS.some(([id]) => id === view) ? view : 'latest');
  for (const [id] of TABS) $(`tab-${id}`).setAttribute('aria-selected', String(id === tab));
  const root = $('view');
  root.setAttribute('aria-busy', 'true');
  if (view === 'exam' && arg) await viewExam(root, decodeURIComponent(arg));
  else if (tab === 'exams') await viewExams(root);
  else if (tab === 'check') await viewCheck(root, arg && decodeURIComponent(arg));
  else if (tab === 'tools') await viewTools(root);
  else if (tab === 'saved') await viewSaved(root);
  else await viewLatest(root);
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
window.addEventListener('hashchange', route);
window.addEventListener('online', () => { $('offline').hidden = true; });
window.addEventListener('offline', () => { $('offline').hidden = false; });
route();
if ('serviceWorker' in navigator) navigator.serviceWorker.register('sw.js').catch(() => { /* site works without it */ });
