// SSC Job Alerts website. Reads the static JSON that jobs/pipeline publishes next to this page:
//   v1/feed.json, exams.json, calendar.json, resources.json, rules/index.json, rules/<id>.json
// Every string from SSC goes into the page through textContent, never innerHTML.
import { evaluate, todayIST } from './eligibility.js';
import { EN, LANGS, getLang, guessLang, locale, setLang, t } from './i18n.js';
import { ageOn, buildIcs, examEvents, makeSignature, typingScore } from './tools.js';
import { timeline } from './tracker.js';

const DATA = new URL('v1/', document.baseURI);
const KINDS = [
  ['all', 'kindAll'], ['recruitment', 'kindRecruitment'], ['result', 'kindResult'], ['admit_card', 'kindAdmit'],
  ['answer_key', 'kindAnswer'], ['amendment', 'kindAmendment'], ['vacancy', 'kindVacancy'],
];
const QUALS = ['10', '12', 'diploma', 'graduate', 'postgraduate'];
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
// Links open only to government domains and to the recruiters' own sites (exams.json "sources",
// e.g. ongcindia.com): a headline can never send anyone to a look-alike site.
const officialHosts = new Set();
function safeUrl(u) {
  try {
    const x = new URL(u);
    return x.protocol === 'https:' && (/(^|\.)(gov|nic)\.in$/.test(x.hostname) || officialHosts.has(x.hostname)) ? x.href : null;
  } catch { return null; }
}
function payText(rules, level) {
  if (!level || !rules.payScales) return null;
  const inr = (n) => n.toLocaleString('en-IN');
  return String(level).split('/').filter((l) => rules.payScales[l])
    .map((l) => t('payRange', { from: inr(rules.payScales[l][0]), to: inr(rules.payScales[l][1]), l })).join(' · ') || null;
}
const daysBetween = (a, b) => Math.round((Date.parse(`${b}T00:00:00Z`) - Date.parse(`${a}T00:00:00Z`)) / 864e5);

// One request per file, shared by everyone asking at once; a failed one is tried again next time.
const cache = {};
const early = window.early ?? {}; // started in index.html before this file loaded
async function load(path) {
  const pre = early[path];
  delete early[path];
  const res = (pre && await pre) || await fetch(new URL(path, DATA), { cache: 'no-cache' });
  if (!res.ok) throw new Error(`${path}: HTTP ${res.status}`);
  const data = await res.json();
  if (path === 'exams.json') {
    for (const src of Object.values(data.sources ?? {})) { try { officialHosts.add(new URL(src.site).hostname); } catch { /* skip */ } }
  }
  return data;
}
function getJSON(path) {
  cache[path] ??= load(path).catch((e) => { delete cache[path]; throw e; });
  return cache[path];
}
const errorBox = (e) => { console.error(e); return el('p', { class: 'error' }, t('loadError')); };

let toastTimer;
function toast(msg) {
  document.querySelector('.toast')?.remove();
  clearTimeout(toastTimer);
  const n = el('div', { class: 'toast', role: 'status' }, msg);
  document.body.append(n);
  toastTimer = setTimeout(() => n.remove(), msg.length > 40 ? 4500 : 2200); // time to read a longer hint
}

// ---------- saved items and followed exams (this device only) ----------
const savedItems = () => store.get('saved', {});
const followed = () => store.get('follow', []);
function toggleSave(item, btn) {
  const s = savedItems();
  if (s[item.id]) delete s[item.id]; else s[item.id] = item;
  store.set('saved', s);
  toast(t(s[item.id] ? 'toastSaved' : 'toastRemoved'));
  const label = btn.querySelector('span'); // icon-only buttons have none
  if (label) label.textContent = t(s[item.id] ? 'saved' : 'save');
  btn.setAttribute('aria-label', t(s[item.id] ? 'saved' : 'save'));
  btn.setAttribute('aria-pressed', String(Boolean(s[item.id])));
}
function toggleFollow(code, btn) {
  const f = new Set(followed());
  if (f.has(code)) f.delete(code); else f.add(code);
  store.set('follow', [...f]);
  syncAlerts();
  toast(t(!f.has(code) ? 'toastUnfollow' : store.get('alerts', false) ? 'toastFollow' : 'toastFollowAlerts'));
  btn.textContent = f.has(code) ? `✓ ${t('following')}` : `+ ${t('follow')}`;
  btn.setAttribute('aria-pressed', String(f.has(code)));
}
const appUrl = () => new URL('./', location.href).href;
async function share(title, url) {
  const text = t('shareText', { title, url, app: t('appName') });
  if (navigator.share) {
    try { await navigator.share({ title, text, url }); } catch { /* cancelled */ }
    return;
  }
  shareWhatsApp(text);
}
// WhatsApp is how most people in India pass a link on: its own button, not hidden in the share sheet.
const shareWhatsApp = (text) => window.open(`https://wa.me/?text=${encodeURIComponent(text)}`, '_blank', 'noopener');

// ---------- shared pieces ----------
const KIND_ICON = {
  recruitment: 'briefcase', result: 'clipboardCheck', admit_card: 'fileCheck', answer_key: 'key',
  amendment: 'pencil', vacancy: 'users', schedule: 'calendar', notice: 'bell',
};
const sourceName = (i, families, sources) => {
  const fam = i.exam ? families[i.exam] : null;
  // SSC's codes (CGL, CHSL) are what candidates say; other boards' codes are ours, so their names are shown.
  if (fam) return `${fam.source ? fam.short : i.exam}${i.examYear ? ` ${i.examYear}` : ''}`;
  return sources?.[i.source ?? 'ssc']?.short ?? (i.source ?? 'SSC').toUpperCase();
};
// Some boards write every headline in capitals ("RECRUITMENT OF SPECIALIST CADRE OFFICERS ON CONTRACT BASIS"), which
// reads as shouting on a phone. Only for display: short all-capital words stay as they are (SBI, CRPD, TGT, PO).
const ACRONYMS = new Set(['ITI', 'AIIMS', 'IIT', 'NIT', 'UGC', 'NET', 'OBC', 'EWS', 'PET', 'PST', 'CBT', 'ESIC', 'IOCL', 'ONGC', 'NTPC', 'DRDO',
  'ISRO', 'BECIL', 'UPPSC', 'TNPSC', 'APPSC', 'HPPSC', 'CGPSC', 'AAI', 'NIACL', 'ICAR', 'NEET', 'JEE', 'CUET', 'TNTET', 'CTET', 'TET', 'CAPF', 'ITBP',
  'CISF', 'NABARD', 'EPFO', 'UPSSSC', 'OSSSC', 'TGPSC', 'UKPSC', 'MPPSC', 'NDRF', 'UPESSC', 'SLPRB', 'TGPRB', 'WBPSC', 'WBPRB', 'PSSSB', 'DSSSB', 'UKSSSC',
  'OPSC', 'OSSC', 'KPSC', 'GPSC', 'PPSC', 'HPSC', 'HSSC', 'BPSC', 'BSSC', 'RPSC', 'RSSB', 'IBPS', 'POWERGRID', 'NHPC', 'BPCL', 'GDS', 'MTS', 'PDF', 'SBI', 'UPSC', 'SCO', 'SO', 'PO', 'BEL', 'HAL', 'SAIL', 'BHEL', 'GAIL', 'LIC', 'NIA',
  'NCERT', 'CBSE', 'KVS', 'NVS', 'EMRS', 'IDBI', 'UCO', 'BOB', 'PNB', 'RBI', 'SEBI', 'SIDBI', 'EXIM', 'IREL', 'NALCO', 'MECON', 'RITES', 'IRCON', 'IRCTC',
  'CEL', 'ECIL', 'BARC', 'NPCIL', 'JIPMER', 'PGIMER', 'NHM', 'ANM', 'GNM', 'ASI', 'SI', 'HC', 'TGT', 'PGT', 'PRT', 'DA', 'TA', 'UP', 'MP', 'HP',
  'AP', 'TN', 'UK', 'II', 'III', 'IV', 'VI', 'VII', 'VIII', 'IX', 'XI', 'XII', 'OTR', 'DV', 'DME', 'AE', 'JE', 'AEE', 'NHAI', 'CAG', 'IAS', 'IPS', 'IFS']);
const SMALL_WORDS = new Set(['of', 'the', 'for', 'and', 'in', 'on', 'to', 'at', 'by', 'from', 'with', 'as', 'a', 'an', 'or', 'under', 'into']);
function tidyTitle(title, lang) {
  const letters = title.replace(/[^A-Za-z]/g, '');
  if (lang !== 'en' || title.length < 25 || letters.length < 15 || letters.replace(/[^A-Z]/g, '').length < letters.length * 0.7) return title;
  let first = true;
  return title.replace(/[A-Za-z][A-Za-z'’]*/g, (w) => {
    const low = w.toLowerCase();
    // Words already in mixed case ("Notification") are kept; only all-capital ones are softened. Acronyms stay:
    // known ones (AIIMS, SBI, CTET) and words without a vowel (CRPD, SSC, RRB).
    const keep = ACRONYMS.has(w) || !/[AEIOUY]/.test(w);
    const out = w !== w.toUpperCase() ? w : !first && SMALL_WORDS.has(low) ? low : keep ? w : w[0] + w.slice(1).toLowerCase();
    first = false;
    return out;
  });
}
function itemCard(i, { isNew = false, families = {}, sources = {} } = {}) {
  const files = i.files.map((f) => ({ ...f, href: safeUrl(f.url) })).filter((f) => f.href);
  const page = i.link ? safeUrl(i.link) : null;
  const official = files[0]?.href ?? page;
  const isSaved = Boolean(savedItems()[i.id]);
  const saveBtn = el('button', { class: 'ibtn', type: 'button', 'aria-pressed': String(isSaved), 'aria-label': t(isSaved ? 'saved' : 'save') },
    icon(ICON.bookmark));
  saveBtn.addEventListener('click', () => toggleSave(i, saveBtn));
  const text = () => t('shareText', { title: i.title, url: official, app: t('appName') });
  const title = tidyTitle(i.title, i.lang);
  const acts = el('div', { class: 'iacts' }, saveBtn,
    official ? el('button', { class: 'ibtn wa', type: 'button', 'aria-label': t('shareWhatsapp'), onclick: () => shareWhatsApp(text()) }, icon(ICON.whatsapp)) : null);
  return el('li', { class: `item k-${i.kind}` },
    el('div', { class: 'ihead' },
      el('div', { class: 'meta' },
        isNew ? el('span', { class: 'badge-new' }, t('newBadge')) : null,
        el('span', { class: 'label' }, t(`kind_${i.kind}`)),
        i.exam && i.exam !== 'LDCE' ? el('a', { href: `#exam/${i.exam}`, title: families[i.exam]?.name ?? '' }, sourceName(i, families, sources))
          : el('span', {}, sourceName(i, families, sources)),
        // Some boards give only the day; showing midnight as a time would be invented.
        el('time', { datetime: i.dateOnly ? i.publishedAt.slice(0, 10) : i.publishedAt }, i.dateOnly ? day(i.publishedAt.slice(0, 10)) : `${when(i.publishedAt)} ${t('ist')}`),
        files.length === 1 && !page ? el('span', { class: 'ftype' }, `${t('pdf')}${size(files[0].size)}`) : null),
      acts),
    // The headline opens the official file (or page): one clear action instead of a row of buttons.
    el('h3', { lang: i.lang, title: title !== i.title ? i.title : null },
      official ? el('a', { href: official, rel: 'noopener', target: '_blank' }, title) : title),
    files.length > 1 || (files.length && page) ? el('div', { class: 'files' },
      ...files.map((f, n) => el('a', { href: f.href, rel: 'noopener', target: '_blank' }, icon(ICON.file),
        `${t('pdf')}${files.length > 1 ? ` ${n + 1}` : ''}${size(f.size)}`)),
      page ? el('a', { href: page, rel: 'noopener', target: '_blank' }, icon(ICON.external), t('officialPage')) : null) : null);
}

// ---------- All updates, filtered (#jobs, #jobs/kind/result, #jobs/sector/police, #jobs/state/BR) ----------
const filters = {
  board: 'all', sector: 'all', state: 'all',
  ...store.get('filters', { kind: 'all', exam: 'all', qual: 'all', lang: 'en', dept: false, mine: false, q: '' }),
};
const SECTOR_ORDER = ['ssc', 'police', 'defence', 'railway', 'bank', 'psu', 'science', 'state', 'teaching'];
const sectorsOf = (i, families) => families[i.exam]?.sectors ?? ((i.source ?? 'ssc') === 'ssc' ? ['ssc'] : []);
const stateOf = (i, sources) => sources[i.source ?? 'ssc']?.state ?? null;
// SSC posts each notice in English and Hindi; one copy is enough unless asked. Other boards post one copy.
const stateName = (x) => (`state_${x}` in EN ? t(`state_${x}`) : x); // a new board's state before its name is translated
const oneCopy = (i) => (i.source ?? 'ssc') !== 'ssc' || i.lang === 'en';

async function viewJobs(root, field = null, value = null) {
  let feed; let families; let sources;
  try {
    [feed, { families, sources = {} }] = await Promise.all([getJSON('feed.json'), getJSON('exams.json')]);
  } catch (e) { fill(root, errorBox(e)); return; }
  // A link from Home sets one filter and clears the others it would fight with.
  if (field === 'kind') Object.assign(filters, { kind: value, sector: 'all', state: 'all', board: 'all', exam: 'all' });
  if (field === 'sector') Object.assign(filters, { kind: 'all', sector: value, state: 'all', board: 'all', exam: 'all' });
  if (field === 'state') Object.assign(filters, { kind: 'all', sector: 'all', state: value, board: 'all', exam: 'all' });
  const lastSeen = store.get('lastSeen', null);
  const newest = feed.items.reduce((m, i) => (i.publishedAt > m ? i.publishedAt : m), '');
  const isNew = (i) => lastSeen !== null && i.publishedAt > lastSeen;
  const newCount = feed.items.filter((i) => isNew(i) && oneCopy(i)).length;

  const title = el('h1', { class: 'page' });
  const count = el('p', { class: 'small', 'aria-live': 'polite' });
  const list = el('ul', { class: 'rowlist' });
  const summary = el('summary');
  const render = () => {
    store.set('filters', filters);
    const active = ['board', 'exam', 'qual', 'sector', 'state'].filter((k) => filters[k] !== 'all').length
      + ['mine', 'dept'].filter((k) => filters[k]).length + (filters.lang !== 'en' ? 1 : 0);
    summary.textContent = `${t('filters')}${active ? ` (${active})` : ''}`;
    title.textContent = filters.sector !== 'all' ? t('jobsSector', { name: t(`sector_${filters.sector}`) })
      : filters.state === 'central' ? t('centralJobs')
        : filters.state !== 'all' ? t('jobsState', { name: stateName(filters.state) })
          : filters.kind !== 'all' ? t('jobsKind', { name: t(KINDS.find(([k]) => k === filters.kind)?.[1] ?? 'kindAll') }) : t('homeLatest');
    const q = filters.q.trim().toLowerCase();
    const mine = new Set(followed());
    const items = feed.items.filter((i) =>
      (filters.kind === 'all' || i.kind === filters.kind) &&
      (filters.board === 'all' || (i.source ?? 'ssc') === filters.board) &&
      (filters.sector === 'all' || sectorsOf(i, families).includes(filters.sector)) &&
      (filters.state === 'all' || (filters.state === 'central' ? !stateOf(i, sources) : stateOf(i, sources) === filters.state)) &&
      (filters.exam === 'all' || i.exam === filters.exam) &&
      (filters.qual === 'all' || (families[i.exam]?.qualifications ?? []).includes(filters.qual)) &&
      (filters.lang === 'all' || i.lang === filters.lang || (i.source ?? 'ssc') !== 'ssc') &&
      (filters.dept || i.exam !== 'LDCE') &&
      (!filters.mine || mine.has(i.exam)) &&
      // Headlines from some boards are Hindi only: the exam's and board's names are searched too ("up police", "bihar", "isro").
      (!q || `${i.title} ${i.exam ?? ''} ${families[i.exam]?.short ?? ''} ${families[i.exam]?.name ?? ''} ${sources[i.source ?? 'ssc']?.name ?? ''}`.toLowerCase().includes(q)));
    count.textContent = `${t('updated', { time: when(feed.generatedAt) })} · ${items.length}`;
    fill(list, ...(items.length ? items.slice(0, 150).map((i) => itemCard(i, { isNew: isNew(i), families, sources }))
      : [el('li', { class: 'empty' }, filters.sector !== 'all' || filters.state !== 'all' ? t('notCovered') : t('nothing'))]));
  };
  const chips = el('div', { class: 'pills', role: 'group' });
  const drawChips = () => fill(chips, ...KINDS.map(([k, key]) => el('button', {
    class: 'pill', type: 'button', 'aria-pressed': String(filters.kind === k),
    onclick: () => { filters.kind = k; drawChips(); render(); },
  }, t(key))));
  drawChips();
  const select = (key, label, options) => {
    const x = el('select', { 'aria-label': label, onchange: (e) => { filters[key] = e.target.value; render(); } },
      el('option', { value: 'all' }, label), ...options.map(([v, text]) => el('option', { value: v }, text)));
    x.value = options.some(([v]) => v === filters[key]) ? filters[key] : 'all';
    if (x.value === 'all') filters[key] = 'all';
    return x;
  };
  const exams = [...new Set(feed.items.map((i) => i.exam).filter((e) => e && e !== 'LDCE'))].sort();
  const boards = [...new Set(feed.items.map((i) => i.source ?? 'ssc'))];
  // The same sectors and states as Home offers, even when one has nothing right now ("nothing yet" beats a silent reset).
  const sectors = SECTOR_ORDER.filter((x) => Object.entries(families).some(([c, f]) => c !== 'LDCE' && (f.sectors ?? []).includes(x)));
  const states = [...new Set(Object.values(sources).map((x) => x.state).filter(Boolean))].sort();
  const search = el('input', { type: 'search', placeholder: t('search'), 'aria-label': t('search'), value: filters.q });
  search.addEventListener('input', () => { filters.q = search.value; render(); });
  const check = (key, label) => {
    const box = el('input', { type: 'checkbox' });
    box.checked = Boolean(filters[key]);
    box.addEventListener('change', () => { filters[key] = box.checked; render(); });
    return el('label', { class: 'check' }, box, ` ${label}`);
  };
  fill(root,
    title, el('label', { class: 'searchbox' }, icon(ICON.search), search), el('div', { style: 'height:10px' }), chips,
    el('details', { class: 'filter-box' }, summary,
      el('div', { class: 'filters' },
        select('sector', t('anySector'), sectors.map((x) => [x, t(`sector_${x}`)])),
        select('state', t('anyState'), [['central', t('centralJobs')], ...states.map((x) => [x, stateName(x)])]),
        boards.length > 1 ? select('board', t('allBoards'), boards.map((b) => [b, sources[b]?.short ?? b])) : null,
        select('exam', t('allExams'), exams.map((e) => [e, families[e]?.name ?? e])),
        select('qual', t('anyQualification'), QUALS.map((q) => [q, t(`qual_${q}`)])),
        el('select', { 'aria-label': t('language'), onchange: (e) => { filters.lang = e.target.value; render(); } },
          ...[['en', 'English'], ['hi', 'हिन्दी'], ['all', 'English + हिन्दी']].map(([v, l]) => {
            const o = el('option', { value: v }, l); if (filters.lang === v) o.selected = true; return o;
          })),
        check('mine', t('myExams')), check('dept', t('departmental'))),
      el('p', { class: 'small' }, t('titlesNote'))),
    count, newCount ? el('p', { class: 'small' }, el('strong', {}, t('newSince', { n: newCount }))) : null,
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
    return el('li', { class: 'box' },
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
// Reason codes from eligibility.js ("age_below_min:18", "requires:state_language", ...) in words.
function reasonText(code, rules) {
  const i = code.indexOf(':');
  const [k, v] = i < 0 ? [code, null] : [code.slice(0, i), code.slice(i + 1)];
  const asOn = day(rules.age.asOn);
  switch (k) {
    case 'age_below_min': case 'age_above_max': return t(`r_${k}`, { n: v, date: asOn });
    case 'age_other_relaxation': return t('r_age_other_relaxation', { ref: rules.age.ref });
    case 'education_needs': return t(`r_education_${v === 'graduate' || v === '12' ? v : '10'}`);
    case 'result_needed_by': case 'appearing_not_stated': return t(`r_${k}`, { date: day(v) });
    case 'pwbd_not_in_notice': return t('r_pwbd_not_in_notice');
    case 'gender_only': return v === 'male' || v === 'female' ? t(`r_gender_${v}`)
      : t('r_gender_other', { g: v.split('/').map((g) => (['male', 'female', 'other'].includes(g) ? t(g) : g)).join(' / ') });
    case 'requires': return `r_${v}` in EN ? t(`r_${v}`) : code;
    default: return code;
  }
}
const noteText = (n) => (`note_${n}` in EN ? t(`note_${n}`) : n);

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
    return el('li', { class: 'box' },
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
  for (const g of groups) g.label = `${g.name} · ${t('listWord', { n: g.lists.length })}`;
  const sel = el('select', { id: 'r-exam' }, ...groups.map((g, i) => el('option', { value: String(i) }, g.label)));
  const roll = el('input', { id: 'r-roll', type: 'text', inputmode: 'numeric', autocomplete: 'off', placeholder: t('rollPlaceholder'), maxlength: '20' }); // not a real roll number: those are people's
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
  const card = (p) => el('li', { class: `box post ${p.status}` },
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
  const reset = async () => {
    clearInterval(timer); timer = null;
    box.readOnly = true; // nothing to type against until the passage is here
    let practiceText; // passages.js: only this tool needs it
    try { ({ practiceText } = await import('./passages.js')); } catch { toast(t('offline')); return; }
    started = 0;
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
  const langSel = el('select', { id: 'set-lang' }, ...LANGS.map((l) => el('option', { value: l.code }, l.name)));
  langSel.value = getLang();
  langSel.addEventListener('change', () => changeLang(langSel.value));
  let sources = {};
  try { ({ sources = {} } = await getJSON('exams.json')); } catch { /* list left out */ }
  fill(root,
    el('div', { class: 'tiles' },
      el('a', { class: 'tile', href: '#saved' }, el('strong', {}, t('savedLink')), el('span', { class: 'small' }, t('followedExams'))),
      el('a', { class: 'tile', href: '#forme' }, el('strong', {}, `✎ ${t('forMeEdit')}`), el('span', { class: 'small' }, t('forMeTitle')))),
    el('h2', {}, t('toolsTitle')), signatureTool(), g ? typingTool(g) : null, ageTool(),
    g ? el('h2', {}, t('howToApply')) : null, g ? guideBlock(g, true) : null, g ? helplineBlock(g) : null,
    formats.length ? el('details', { class: 'group' }, el('summary', {}, t('docsTitle')),
      el('div', { class: 'inner' }, el('p', { class: 'small' }, t('docsNote')), resourceBlock('certificate_formats', formats, false))) : null,
    el('h2', {}, t('settings')),
    alertsCard(), installCard(),
    el('div', { class: 'card grid' },
      el('div', { class: 'field' }, el('label', { for: 'set-lang' }, t('language')), langSel),
      el('div', { class: 'field' }, el('label', { for: 'set-theme' }, t('theme')), themeSel)),
    el('h2', {}, t('recruitersTitle')), el('p', { class: 'small' }, t('recruitersNote')),
    el('ul', { class: 'rowlist' }, ...Object.values(sources).map((x) => (safeUrl(x.site) ? el('li', {},
      el('a', { class: 'drow', href: safeUrl(x.site), rel: 'noopener', target: '_blank' },
        el('span', { class: 'grow' }, el('strong', {}, x.short), el('div', { class: 'small' }, `${x.name} · ${x.state ? stateName(x.state) : t('centralJobs')}`)),
        icon(ICON.external, 'chev'))) : null))),
    el('h2', {}, t('aboutTitle')), el('div', { class: 'card' }, el('p', {}, t('aboutText')), el('p', { class: 'small' }, t('disclaimer'))));
}

// ---------- Alerts and install (see alerts.js, sw.js) ----------
// The worker can't read localStorage, so what it needs (followed exams, their names, the alert
// texts in the user's language) is written where it can read it: the Cache API.
async function alertConfig() {
  let families = {};
  try { ({ families } = await getJSON('exams.json')); } catch { /* codes are shown instead */ }
  const exams = followed();
  return {
    exams,
    names: Object.fromEntries(exams.map((c) => [c, families[c]?.short ?? families[c]?.name ?? c])),
    text: {
      newItem: t('alertNew', { exam: '{exam}', kind: '{kind}' }), more: t('alertMore', { n: '{n}' }),
      lastDays: t('alertLastDays', { title: '{title}', n: '{n}' }), lastDay: t('alertLastDay', { title: '{title}' }),
      kinds: Object.fromEntries(['recruitment', 'amendment', 'result', 'answer_key', 'admit_card', 'vacancy', 'schedule', 'notice'].map((k) => [k, t(`kind_${k}`)])),
    },
  };
}
// Two ways to alert. With a push server (India server, site.json "push"): instant, any browser
// with Web Push (Android, desktop, iPhone once added to the home screen). Without one: the
// installed app's background check (alerts.js), Android Chrome only.
const siteInfo = () => getJSON('site.json').catch(() => ({}));
const pushServer = async () => {
  const site = await siteInfo();
  return site.push && /^https:\/\/[a-z0-9.-]+(:\d{2,5})?\/push$/.test(site.push.url) && /^[A-Za-z0-9_-]{87}$/.test(site.push.key) ? site.push : null;
};
const b64urlBytes = (s) => Uint8Array.from(atob(s.replace(/-/g, '+').replace(/_/g, '/') + '='.repeat((4 - (s.length % 4)) % 4)), (c) => c.charCodeAt(0));
async function postPush(server, path, body) {
  const res = await fetch(`${server.url}/${path}`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
  if (!res.ok) throw new Error(`push ${path}: HTTP ${res.status}`);
}
async function syncAlerts() {
  if (!store.get('alerts', false) || !('caches' in window)) return;
  try {
    // The worker words notifications from this (names, kinds, language).
    await (await caches.open('jobs-alerts')).put('alerts/config', new Response(JSON.stringify(await alertConfig()),
      { headers: { 'content-type': 'application/json' } }));
    if (store.get('alertsMode', '') === 'push') {
      const [server, reg] = await Promise.all([pushServer(), swReady()]);
      const sub = await reg?.pushManager?.getSubscription();
      if (server && sub) await postPush(server, 'subscribe', { subscription: sub.toJSON(), exams: followed(), lang: getLang() });
    }
  } catch { /* offline or storage blocked: the next change sends it again */ }
}
// navigator.serviceWorker.ready never settles when the worker could not be registered.
const swReady = () => ('serviceWorker' in navigator
  ? Promise.race([navigator.serviceWorker.ready, new Promise((r) => { setTimeout(() => r(null), 3000); })]) : Promise.resolve(null));
async function alertsSupport() {
  if (!('Notification' in window)) return 'unsupported';
  const [reg, server] = await Promise.all([swReady(), pushServer()]);
  if (server && reg?.pushManager) return 'push';
  return reg?.periodicSync ? 'sync' : 'unsupported';
}
function alertsCard() {
  const status = el('p', { class: 'small', 'aria-live': 'polite' });
  const btn = el('button', { class: 'primary', type: 'button' });
  const unsupported = async () => t((await pushServer()) ? 'alertsUnsupportedPush' : 'alertsUnsupported');
  const paint = async () => {
    const on = store.get('alerts', false);
    btn.textContent = t(on ? 'alertsOff' : 'alertsOn');
    if (on) status.textContent = t(store.get('alertsMode', '') === 'push' ? 'alertsActivePush' : 'alertsActive', { n: followed().length });
    else if ('Notification' in window && Notification.permission === 'denied') status.textContent = t('alertsBlocked');
    else status.textContent = (await alertsSupport()) === 'unsupported' ? await unsupported() : '';
  };
  btn.addEventListener('click', async () => {
    btn.disabled = true;
    try { await toggle(); } finally { btn.disabled = false; }
  });
  async function toggle() {
    const reg = await swReady();
    if (store.get('alerts', false)) {
      store.set('alerts', false);
      try {
        const sub = await reg?.pushManager?.getSubscription();
        const server = await pushServer();
        if (sub && server) await postPush(server, 'unsubscribe', { endpoint: sub.endpoint }).catch(() => null);
        await sub?.unsubscribe();
        await reg?.periodicSync?.unregister('jobs-alerts');
        await (await caches.open('jobs-alerts')).delete('alerts/config');
      } catch { /* already gone */ }
      paint(); return;
    }
    if (!followed().length) { status.textContent = t('alertsNoExams'); return; }
    const mode = await alertsSupport();
    if (mode === 'unsupported') { status.textContent = await unsupported(); return; }
    if ((await Notification.requestPermission()) !== 'granted') { status.textContent = t('alertsBlocked'); return; }
    try {
      if (mode === 'push') {
        const server = await pushServer();
        const sub = await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: b64urlBytes(server.key) });
        await postPush(server, 'subscribe', { subscription: sub.toJSON(), exams: followed(), lang: getLang() });
      } else {
        // Chrome only grants this to an installed app; anything else throws.
        await reg.periodicSync.register('jobs-alerts', { minInterval: 6 * 3600e3 });
      }
    } catch { status.textContent = t(mode === 'push' ? 'alertsFailed' : 'alertsUnsupported'); return; }
    store.set('alerts', true);
    store.set('alertsMode', mode);
    await syncAlerts();
    if (mode === 'sync') reg.active?.postMessage('check-alerts'); // records what is already there
    paint();
  }
  paint();
  // Works on every phone: the Telegram channel, when the site has one.
  const telegram = el('p');
  siteInfo().then((site) => {
    if (typeof site.telegram === 'string' && /^https:\/\/t\.me\/[A-Za-z0-9_]{5,32}$/.test(site.telegram)) {
      fill(telegram, el('a', { href: site.telegram, rel: 'noopener', target: '_blank' }, t('telegramJoin')));
    }
  });
  return el('div', { class: 'card' }, el('h3', {}, t('alertsTitle')), el('p', { class: 'small' }, t('alertsIntro')), btn, status, telegram);
}
let installPrompt = null;
window.addEventListener('beforeinstallprompt', (e) => { e.preventDefault(); installPrompt = e; });
window.addEventListener('appinstalled', () => { installPrompt = null; });
function installCard() {
  const standalone = window.matchMedia?.('(display-mode: standalone)').matches || navigator.standalone;
  if (standalone) return null;
  const ios = /iPhone|iPad|iPod/.test(navigator.userAgent);
  if (!installPrompt && !ios) return null;
  return el('div', { class: 'card' }, el('h3', {}, t('installTitle')), el('p', { class: 'small' }, t('installNote')),
    installPrompt ? el('button', { class: 'primary', type: 'button', onclick: async (e) => {
      installPrompt.prompt();
      await installPrompt.userChoice.catch(() => null);
      installPrompt = null; e.target.closest('.card').remove();
    } }, t('installBtn')) : el('p', {}, t('installIos')));
}

// ---------- Home: search, shortcuts, sectors and states, what closes soon, exam dates, latest ----------
// Work people take up for money besides government jobs (gig and platform work: 77 lakh in 2020-21, NITI Aayog 2022):
// earn.json, built from the pipeline's earn.json, quotes each company's or ministry's own page.
const earnHead = (o) => o.says.find((x) => x.includes('₹')) ?? o.says[0] ?? '';
const earnHref = (o) => (o.says.length ? `#earn/${o.id}` : o.url);
function earnRow(o) {
  const out = !o.says.length;
  return el('li', {}, el('a', { class: 'drow', href: earnHref(o), ...(out ? { target: '_blank', rel: 'noopener' } : {}) },
    el('span', { class: 'grow' }, el('h3', {}, o.name),
      el('span', { class: 'small' }, [t(o.role), earnHead(o)].filter(Boolean).join(' · '))),
    icon(out ? ICON.external : ICON.chevron, 'chev')));
}
function fakeBox() {
  return el('div', { class: 'warnbox col', role: 'note' }, el('strong', {}, icon(ICON.shieldCheck), t('earnFakeTitle')),
    el('ul', {}, ...[1, 2, 3, 4, 5].map((n) => el('li', {}, t(`earnFake${n}`)))), el('span', {}, t('earnReport')));
}
async function viewEarn(root, id) {
  let earn;
  try { earn = await getJSON('earn.json'); } catch (e) { fill(root, errorBox(e)); return; }
  const group = earn.groups.find((g) => g.options.some((o) => o.id === id));
  const o = group?.options.find((x) => x.id === id);
  if (o) {
    const app = o.url.startsWith('https://play.google.com/');
    const steps = o.steps ?? (['delivery', 'driver'].includes(group.id) ? [1, 2, 3, 4].map((n) => t(`earnStep${n}`)) : []);
    fill(root,
      el('a', { class: 'more-link back', href: '#earn' }, t(group.key)),
      el('h1', { class: 'page' }, o.name), el('p', { class: 'small' }, t(o.role)),
      el('a', { class: 'btn primary wide', href: o.url, target: '_blank', rel: 'noopener' }, icon(ICON.external), t(app ? 'earnOpenApp' : 'earnOpenSite')),
      o.downloads ? el('p', { class: 'small' }, `${o.app} · ${o.developer} · ${t('earnDownloads', { n: o.downloads })}`) : null,
      el('h2', {}, t('earnSays')), el('ul', { class: 'facts' }, ...o.says.map((x) => el('li', {}, x))),
      el('p', { class: 'small' }, t('earnChecked', { date: day(earn.checked) })),
      steps.length ? el('h2', {}, t('earnHowJoin')) : null, steps.length ? el('ol', { class: 'facts' }, ...steps.map((x) => el('li', {}, x))) : null,
      fakeBox());
    return;
  }
  fill(root,
    el('h1', { class: 'page' }, t('earnTitle')),
    el('p', { class: 'small' }, t('earnIntro')),
    el('p', { class: 'warnbox', role: 'note' }, icon(ICON.shieldCheck), t('earnWarn')),
    ...earn.groups.flatMap((g) => [el('h2', {}, t(g.key)), el('ul', { class: 'rowlist' }, ...g.options.map(earnRow))]),
    el('p', { class: 'small' }, t('earnChecked', { date: day(earn.checked) })),
    fakeBox());
}
// Home: the best-known apps first (Play downloads 1 crore+ and their own pay figures), each opening its page here.
function earnCards(earn) {
  const pick = (id) => earn.groups.find((g) => g.id === id)?.options.filter((o) => o.says.length >= 2) ?? [];
  return [...pick('delivery').slice(0, 5), ...pick('driver')].map((o) => el('a', { class: 'hcard earn-card', href: earnHref(o) },
    el('h3', {}, o.name), el('div', { class: 'small' }, t(o.role)), el('div', { class: 'earn-fact' }, earnHead(o))));
}

async function viewHome(root) {
  let index; let families; let sources;
  // Only the newest notices (latest.json, a tenth of feed.json); started now, drawn last. A site published
  // before latest.json existed still has feed.json.
  const feedP = getJSON('latest.json').catch(() => getJSON('feed.json'));
  feedP.catch(() => { /* shown by the latest box */ });
  try {
    [{ exams: index }, { families, sources = {} }] = await Promise.all([getJSON('rules/index.json'), getJSON('exams.json')]);
  } catch (e) { fill(root, errorBox(e)); return; }
  const today = todayIST();

  const search = el('input', { type: 'search', placeholder: t('search'), 'aria-label': t('search'), enterkeyhint: 'search' });
  // A search from Home looks through everything, not inside a sector or state picked earlier.
  search.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') { Object.assign(filters, { q: search.value, kind: 'all', sector: 'all', state: 'all', board: 'all', exam: 'all' }); location.hash = '#jobs'; }
  });
  const quick = [
    ['#earn', 'wallet', 'qaEarn'], ['#jobs/kind/recruitment', 'briefcase', 'qaLatest'], ['#jobs/kind/result', 'clipboardCheck', 'qaResults'],
    ['#jobs/kind/admit_card', 'fileCheck', 'qaAdmit'], ['#jobs/kind/answer_key', 'key', 'qaAnswer'],
    ['#forme', 'shieldCheck', 'qaEligibility'], ['#result', 'search', 'qaRoll'],
  ];
  const sectors = SECTOR_ORDER.filter((x) => Object.entries(families).some(([c, f]) => c !== 'LDCE' && (f.sectors ?? []).includes(x)));
  const states = [...new Set(Object.values(sources).map((x) => x.state).filter(Boolean))].sort();
  const closing = index.filter((e) => statusOf(e, today) === 'open').sort((a, b) => (a.applyTo < b.applyTo ? -1 : 1));
  const in30 = new Date(Date.parse(`${today}T00:00:00Z`) + 30 * 864e5).toISOString().slice(0, 10);
  const coming = index.flatMap((r) => (r.examSchedule ?? [])
    .filter((x) => x.from && x.to >= today && x.from <= in30).map((x) => ({ r, x })))
    .sort((a, b) => (a.x.from < b.x.from ? -1 : 1));
  const monthDay = (iso) => {
    const d = new Date(`${iso}T00:00:00Z`);
    return [new Intl.DateTimeFormat(locale(), { day: 'numeric', timeZone: 'UTC' }).format(d),
      new Intl.DateTimeFormat(locale(), { month: 'short', timeZone: 'UTC' }).format(d)];
  };
  const latestBox = el('div', {}, ...skeleton());
  const topBox = el('div', {}, ...skeleton());
  const earnBox = el('div', { class: 'hscroll' });
  getJSON('earn.json').then((earn) => fill(earnBox, ...earnCards(earn))).catch(() => earnBox.remove());
  const channels = el('div');
  fill(root,
    el('label', { class: 'searchbox' }, icon(ICON.search), search),
    el('nav', { class: 'pills quick-row', 'aria-label': t('homeLatest') }, ...quick.map(([href, ic, key]) => el('a', { class: 'pill', href }, icon(ICON[ic]), t(key)))),
    // Ways to earn now (delivery, driving), then what most people come for: the sectors with the most applicants.
    el('div', { class: 'sec-head' }, el('h2', {}, t('homeEarn')), el('a', { class: 'more-link', href: '#earn' }, t('seeAll'), icon(ICON.chevron))),
    earnBox,
    el('h2', {}, t('homeTop')), topBox,
    closing.length ? el('h2', {}, t('homeClosing')) : null,
    closing.length ? el('div', { class: 'hscroll' }, ...closing.map((e) => {
      const left = daysBetween(today, e.applyTo);
      return el('a', { class: 'hcard', href: `#exam/${e.exam}` },
        el('span', { class: `days${left > 7 ? ' ok' : ''}` }, left === 0 ? t('lastDay') : t('daysLeft', { n: left })),
        el('h3', {}, e.title), el('div', { class: 'small' }, t('status_open', { date: day(e.applyTo) })));
    })) : null,
    coming.length ? el('h2', {}, t('homeComing')) : null,
    coming.length ? el('ul', { class: 'rowlist' }, ...coming.map(({ r, x }) => {
      const [d, m] = monthDay(x.from);
      const now = x.from <= today;
      return el('li', {}, el('a', { class: 'drow', href: `#exam/${r.exam}` },
        el('span', { class: 'dblock', 'aria-hidden': 'true' }, el('strong', {}, d), el('span', {}, m)),
        el('span', { class: 'grow' }, el('h3', {}, r.title),
          el('span', { class: 'small' }, `${x.stage} · ${day(x.from)} – ${day(x.to)} · `,
            el('strong', {}, now ? t('onNow') : daysBetween(today, x.from) === 0 ? t('today') : t('inDays', { n: daysBetween(today, x.from) })))),
        icon(ICON.chevron, 'chev')));
    })) : null,
    sectors.length ? el('h2', {}, t('homeSectors')) : null,
    sectors.length ? el('div', { class: 'pills' }, ...sectors.map((x) => el('a', { class: 'pill', href: `#jobs/sector/${x}` }, t(`sector_${x}`)))) : null,
    el('h2', {}, t('homeStates')),
    el('div', { class: 'pills' }, el('a', { class: 'pill', href: '#jobs/state/central' }, t('centralJobs')),
      ...states.map((x) => el('a', { class: 'pill', href: `#jobs/state/${x}` }, stateName(x)))),
    el('div', { class: 'sec-head' }, el('h2', {}, t('homeLatest')),
      el('a', { class: 'more-link', href: '#jobs/kind/all' }, t('moreUpdates'), icon(ICON.chevron))),
    latestBox,
    el('h2', {}, t('homeChannels')), channels,
    el('p', { class: 'trust' }, icon(ICON.shieldCheck), t('trustLine')));

  siteInfo().then((site) => {
    const tg = typeof site.telegram === 'string' && /^https:\/\/t\.me\/[A-Za-z0-9_]{5,32}$/.test(site.telegram) ? site.telegram : null;
    const wa = typeof site.whatsapp === 'string' && /^https:\/\/(www\.)?whatsapp\.com\/channel\/[A-Za-z0-9]{10,40}$/.test(site.whatsapp) ? site.whatsapp : null;
    fill(channels, el('div', { class: 'card channels' },
      el('span', { class: 'ic', 'aria-hidden': 'true' }, icon(ICON.send)),
      el('div', { class: 'grow' }, el('strong', {}, t('homeChannelsNote'))),
      el('div', { class: 'btns' },
        wa ? el('a', { class: 'btn wa', href: wa, rel: 'noopener', target: '_blank' }, icon(ICON.whatsapp), t('whatsappJoin'))
          // No channel yet: the next best thing is passing the app on in WhatsApp.
          : el('button', { class: 'btn wa', type: 'button', onclick: () => shareWhatsApp(`${t('appName')}: ${t('trustLine')}\n${appUrl()}`) }, icon(ICON.whatsapp), t('shareWhatsapp')),
        tg ? el('a', { class: 'btn primary', href: tg, rel: 'noopener', target: '_blank' }, icon(ICON.send), t('telegramJoin')) : null)));
  });
  try {
    const feed = await feedP;
    const lastSeen = store.get('lastSeen', null);
    const isNew = (i) => lastSeen !== null && i.publishedAt > lastSeen;
    // latest.json "top": [{sector, items}] in the order of how many people apply (build.TOP_SECTORS); an older site has none.
    const groups = (feed.top ?? []).filter((g) => g.items?.length);
    fill(topBox, ...(groups.length ? groups.map((g) => el('section', { class: 'topgrp' },
      el('div', { class: 'sec-head small-head' }, el('h3', {}, t(`sector_${g.sector}`)),
        el('a', { class: 'more-link', href: `#jobs/sector/${g.sector}` }, t('seeAll'), icon(ICON.chevron))),
      el('ul', { class: 'rowlist' }, ...g.items.map((i) => itemCard(i, { isNew: isNew(i), families, sources })))))
      : [el('p', { class: 'small' }, t('nothing'))]));
    const latest = feed.items.filter((i) => oneCopy(i) && i.exam !== 'LDCE').slice(0, 8);
    fill(latestBox, el('ul', { class: 'rowlist' },
      ...latest.map((i) => itemCard(i, { isNew: isNew(i), families, sources }))));
  } catch (e) { fill(latestBox, errorBox(e)); fill(topBox); }
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
// Line icons (24 px grid, drawn for this app in the style of the v0 concept).
const ICON = {
  home: 'M3 10.5L12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z',
  forme: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM5 21a7 7 0 0 1 14 0',
  result: 'M9 3h6v3H9zM7 4.5H5V21h14V4.5h-2M9 14l2 2 4-4',
  exams: 'M2 9l10-5 10 5-10 5zM6 11v5c3.5 2.5 8.5 2.5 12 0v-5M22 9v6',
  more: 'M4 6h16M4 12h16M4 18h16',
  auto: 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM12 3v18',
  light: 'M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8zM12 2v2M12 20v2M2 12h2M20 12h2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4',
  dark: 'M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z',
  landmark: 'M3 21h18M5 21v-9M9.5 21v-9M14.5 21v-9M19 21v-9M2.5 9.5L12 4l9.5 5.5z',
  globe: 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM3 12h18M12 3c2.4 2.6 3.6 5.6 3.6 9s-1.2 6.4-3.6 9c-2.4-2.6-3.6-5.6-3.6-9S9.6 5.6 12 3z',
  search: 'M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14zM21 21l-5-5',
  briefcase: 'M3 7.5h18V20H3zM8.5 7.5V4.5h7v3M3 12.5h18',
  clipboardCheck: 'M9 3h6v3H9zM7 4.5H5V21h14V4.5h-2M9 14l2 2 4-4',
  fileCheck: 'M14 3H6v18h12V7zM14 3v4h4M9 15l2 2 4-4',
  key: 'M14.5 4a5.5 5.5 0 1 1-4.9 8L3 18.6V21h3v-2h2v-2h2l1.4-1.4A5.5 5.5 0 0 1 14.5 4zM16 8.5h.01',
  shieldCheck: 'M12 3l8 3v6c0 4.8-3.4 8-8 9-4.6-1-8-4.2-8-9V6zM9 12l2 2 4-4',
  pencil: 'M4 20h4L19 9l-4-4L4 16zM13.5 6.5l4 4',
  users: 'M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM2 21a7 7 0 0 1 14 0M16 3.5a4 4 0 0 1 0 7.5M22 21a7 7 0 0 0-4-6.3',
  calendar: 'M4 5h16v16H4zM16 3v4M8 3v4M4 10h16',
  bell: 'M6 16V11a6 6 0 1 1 12 0v5l2 2H4zM10 21h4',
  bookmark: 'M6 3h12v18l-6-4.5L6 21z',
  share: 'M18 8a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM6 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM18 22a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM8.6 13.5l6.8 4M15.4 6.5l-6.8 4',
  whatsapp: 'M3.5 20.5l1.4-4.3A8.5 8.5 0 1 1 8 19.2zM9 8.5c0 3.5 3 6.5 6.5 6.5l1-1.6-2-1-1 .9c-1.2-.5-2.3-1.6-2.8-2.8l.9-1-1-2z',
  send: 'M21.5 2.5L10.5 13.5M21.5 2.5l-7 19-4-8-8-4z',
  chevron: 'M9 6l6 6-6 6',
  file: 'M14 3H6v18h12V7zM14 3v4h4',
  external: 'M14 4h6v6M20 4l-9 9M18 14v6H4V6h6',
  close: 'M6 6l12 12M18 6L6 18',
  wallet: 'M3 7h16a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H3zM3 7V5.5A1.5 1.5 0 0 1 4.5 4H17v3M16.5 13.5h.01',
};
const TABS = [['home', 'tabHome'], ['forme', 'tabForMe'], ['result', 'tabResult'], ['exams', 'tabExams'], ['more', 'tabMore']];
const PARENT = { exam: 'exams', check: 'forme', saved: 'more', jobs: 'home', tools: 'more', earn: 'home' };
function icon(path, cls = '') {
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.setAttribute('viewBox', '0 0 24 24');
  svg.setAttribute('aria-hidden', 'true');
  svg.setAttribute('class', `i ${cls}`.trim());
  const p = document.createElementNS('http://www.w3.org/2000/svg', 'path');
  p.setAttribute('d', path);
  p.setAttribute('fill', 'none');
  p.setAttribute('stroke', 'currentColor');
  p.setAttribute('stroke-width', '1.9');
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
let langRequest = 0;
async function changeLang(code) {
  const mine = ++langRequest;
  await setLang(code);
  if (mine !== langRequest) return; // a later tap won
  // Offline before that language's first download: English is shown, the choice is not overwritten.
  if (getLang() === code) store.set('lang', code); else toast(t('offline'));
  syncAlerts();
  paintShell();
  route();
}
const closeSheet = (sheet) => (typeof sheet.close === 'function' ? sheet.close() : sheet.removeAttribute('open')); // no <dialog>: iOS < 15.4
function openLangSheet() {
  const sheet = $('lang-sheet');
  fill(sheet,
    el('div', { class: 'sheet-head' }, el('h2', { id: 'lang-title' }, t('chooseLanguage')),
      el('button', { class: 'act', type: 'button', 'aria-label': '×', onclick: () => closeSheet(sheet) }, icon(ICON.close))),
    el('div', { class: 'langs' }, ...LANGS.map((l) => el('button', {
      type: 'button', lang: l.code, dir: l.dir ?? 'ltr', 'aria-current': String(l.code === getLang()),
      onclick: async () => { closeSheet(sheet); await changeLang(l.code); },
    }, l.name, el('span', { class: 'code' }, l.code.toUpperCase())))),
    el('p', { class: 'small', style: 'padding:10px 18px 18px;margin:0' }, t('titlesNote')));
  if (typeof sheet.showModal === 'function') sheet.showModal(); else sheet.setAttribute('open', '');
}
function paintShell() {
  document.title = t('appName');
  $('app-name').textContent = t('appName');
  fill($('logo'), icon(ICON.landmark));
  $('footer-disclaimer').textContent = t('disclaimer');
  // Plain pages written by the build (pipeline sarkari/sitegen.py); contact only when an address is set.
  const footLinks = (contact) => fill($('footer-links'), ...[['about/', 'aboutTitle'], ['privacy/', 'privacyPolicy'], ['terms/', 'termsOfUse']]
    .concat(contact ? [['contact/', 'contactUs']] : []).map(([href, key]) => el('a', { href }, t(key))));
  footLinks(false);
  siteInfo().then((site) => { if (typeof site.contact === 'string') footLinks(true); });
  fill($('lang'), icon(ICON.globe), el('span', {}, getLang().toUpperCase()));
  $('lang').setAttribute('aria-label', `${t('language')}: ${LANGS.find((l) => l.code === getLang()).name}`);
  $('offline').textContent = t('offline');
  applyTheme(store.get('theme', 'auto'));
  fill($('tabs'), ...TABS.map(([id, key]) => el('a', { href: `#${id}`, id: `tab-${id}` }, icon(ICON[id]), el('span', {}, t(key)))));
}
const skeleton = () => [1, 2, 3].map(() => el('div', { class: 'skeleton' }));
async function route() {
  const [hashView, arg, arg2] = location.hash.slice(1).split('/');
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
  else if (view === 'jobs') await viewJobs(root, ['kind', 'sector', 'state'].includes(arg) ? arg : null, arg2 ? decodeURIComponent(arg2) : null);
  else if (tab === 'exams') await viewExams(root);
  else if (view === 'check') await viewCheck(root, arg && decodeURIComponent(arg));
  else if (view === 'saved') await viewSaved(root);
  else if (view === 'earn') await viewEarn(root, arg && decodeURIComponent(arg));
  else if (tab === 'forme') await viewForMe(root);
  else if (tab === 'result') await viewResult(root);
  else if (tab === 'more') await viewMore(root);
  else await viewHome(root);
  root.removeAttribute('aria-busy');
  $('offline').hidden = navigator.onLine !== false;
}

await setLang(store.get('lang', null) ?? guessLang(navigator.languages ?? [navigator.language]));
paintShell();
$('lang').addEventListener('click', openLangSheet);
$('lang-sheet').addEventListener('click', (e) => { if (e.target === e.currentTarget) closeSheet(e.currentTarget); }); // tap outside closes
$('theme').addEventListener('click', () => {
  const order = ['auto', 'light', 'dark'];
  applyTheme(order[(order.indexOf(store.get('theme', 'auto')) + 1) % order.length]);
});
window.addEventListener('hashchange', route);
window.addEventListener('online', () => { $('offline').hidden = true; });
window.addEventListener('offline', () => { $('offline').hidden = false; });
route();
if ('serviceWorker' in navigator) navigator.serviceWorker.register('sw.js').catch(() => { /* site works without it */ });
