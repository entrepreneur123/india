// SSC Job Alerts website. Reads the static JSON that jobs/pipeline publishes next to this page:
//   v1/feed.json, v1/calendar.json, v1/rules/index.json, v1/rules/<id>.json
// Every string from SSC goes into the page through textContent, never innerHTML.
import { evaluate, todayIST } from './eligibility.js';

const DATA = new URL('v1/', document.baseURI);
const KINDS = [
  ['all', 'All'], ['recruitment', 'New jobs'], ['result', 'Results'], ['admit_card', 'Admit cards'],
  ['answer_key', 'Answer keys'], ['amendment', 'Changes'], ['vacancy', 'Vacancies'],
];
const KIND_LABEL = {
  recruitment: 'New job', amendment: 'Change', result: 'Result', answer_key: 'Answer key',
  admit_card: 'Admit card', vacancy: 'Vacancy', schedule: 'Schedule', notice: 'Notice',
};
const EXAM_LABEL = {
  CGL: 'CGL', CHSL: 'CHSL', MTS: 'MTS', CTGD: 'GD Constable', CAPF: 'SI (Delhi Police & CAPF)', JE: 'JE',
  STENO: 'Stenographer', JHT: 'Hindi Translator', RHQ: 'Selection Post', DP: 'Delhi Police', LDCE: 'Departmental',
};
const REASON = {
  age_below_min: (n, r) => `Too young: you must be at least ${n} on ${day(r.age.asOn)}.`,
  age_above_max: (n, r) => `Over the age limit: you must be under ${n} on ${day(r.age.asOn)} (relaxation included).`,
  age_other_relaxation: (_, r) => `Over the general age limit. Your ex-serviceman / Govt employee / widow relaxation may cover it: see Notice ${r.age.ref}.`,
  education_needs: (lvl) => (lvl === 'graduate' ? "Needs a bachelor's degree." : 'Needs Class 12 pass.'),
  result_needed_by: (d) => `Your result must be declared by ${day(d)}.`,
  'requires:state_language': () => "Needs proficiency in the State's official/regional language (Secondary School level certificate).",
  'requires:jso_maths_or_statistics': () => 'Needs 60% in Mathematics at Class 12, or Statistics as a subject in your degree.',
  'requires:sig2_degree_subject': () => 'Degree must be in Statistics, Mathematics, Economics, Demography, Population Studies, Operations Research, IT, Computer Science/Engineering/Applications, Data Science or AI.',
};
const NOTE = {
  physical_standards: 'Physical and medical standards apply (see the Notice).',
  bro_male_only: 'Border Roads Organisation vacancies: male candidates only, physical standards apply.',
  deo_science_maths_some: 'In some offices (Consumer Affairs & Food, SSC, Culture) this post needs Class 12 Science with Mathematics.',
};
const STATUS_LABEL = { eligible: 'You can apply', check: 'Check one condition', ineligible: 'Not eligible' };

const $ = (id) => document.getElementById(id);
const store = {
  get(key, fallback) { try { return JSON.parse(localStorage.getItem(key)) ?? fallback; } catch { return fallback; } },
  set(key, value) { try { localStorage.setItem(key, JSON.stringify(value)); } catch { /* private mode */ } },
};

function el(tag, attrs = {}, ...children) {
  const node = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs)) {
    if (k === 'class') node.className = v;
    else if (v !== undefined && v !== null && v !== false) node.setAttribute(k, v === true ? '' : v);
  }
  for (const c of children.flat()) if (c !== null && c !== undefined) node.append(c);
  return node;
}

const dayFmt = new Intl.DateTimeFormat('en-IN', { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' });
const timeFmt = new Intl.DateTimeFormat('en-IN', {
  day: 'numeric', month: 'short', year: 'numeric', hour: 'numeric', minute: '2-digit', timeZone: 'Asia/Kolkata',
});
function day(iso) { return dayFmt.format(new Date(`${iso}T00:00:00Z`)); }
function size(bytes) {
  if (!Number.isFinite(bytes)) return '';
  return bytes >= 1e6 ? ` · ${(bytes / 1e6).toFixed(1)} MB` : ` · ${Math.max(1, Math.round(bytes / 1e3))} KB`;
}
function safeUrl(u) {
  try { const x = new URL(u); return x.protocol === 'https:' && /(^|\.)(gov|nic)\.in$/.test(x.hostname) ? x.href : null; } catch { return null; }
}

async function getJSON(path) {
  const res = await fetch(new URL(path, DATA), { cache: 'no-cache' });
  if (!res.ok) throw new Error(`${path}: HTTP ${res.status}`);
  return res.json();
}

// ---------- tabs ----------
const TABS = ['latest', 'eligibility', 'calendar'];
function show(tab) {
  if (!TABS.includes(tab)) tab = 'latest';
  for (const t of TABS) {
    $(`tab-${t}`).setAttribute('aria-selected', String(t === tab));
    $(t).hidden = t !== tab;
  }
  if (tab === 'eligibility') loadRulesIndex();
  if (tab === 'calendar') loadCalendar();
}
for (const t of TABS) $(`tab-${t}`).addEventListener('click', () => { history.replaceState(null, '', `#${t}`); show(t); });
window.addEventListener('hashchange', () => show(location.hash.slice(1)));

// ---------- latest ----------
const filters = store.get('filters', { kind: 'all', exam: 'all', lang: 'en', dept: false });
let feed = null;

function renderFilters() {
  const box = $('kind-filters');
  box.replaceChildren(...KINDS.map(([k, label]) => {
    const b = el('button', { class: 'chip', type: 'button', 'aria-pressed': String(filters.kind === k) }, label);
    b.addEventListener('click', () => { filters.kind = k; renderFilters(); renderFeed(); });
    return b;
  }));
  $('lang-filter').value = filters.lang;
  $('dept-filter').checked = filters.dept;
}

function renderExamOptions() {
  const exams = [...new Set(feed.items.map((i) => i.exam).filter((e) => e && e !== 'LDCE'))].sort();
  $('exam-filter').replaceChildren(
    el('option', { value: 'all' }, 'All exams'),
    ...exams.map((e) => el('option', { value: e }, EXAM_LABEL[e] ?? e)));
  $('exam-filter').value = exams.includes(filters.exam) ? filters.exam : 'all';
}

function renderFeed() {
  store.set('filters', filters);
  if (!feed) return;
  const items = feed.items.filter((i) =>
    (filters.kind === 'all' || i.kind === filters.kind) &&
    (filters.exam === 'all' || i.exam === filters.exam) &&
    (filters.lang === 'all' || i.lang === filters.lang) &&
    (filters.dept || i.exam !== 'LDCE'));
  const list = $('feed');
  if (!items.length) {
    list.replaceChildren(el('li', { class: 'empty' }, 'Nothing here in the last 120 days.'));
    return;
  }
  list.replaceChildren(...items.slice(0, 150).map((i) => el('li', { class: 'item' },
    el('div', { class: 'meta' },
      el('span', { class: `badge ${i.kind}` }, KIND_LABEL[i.kind] ?? 'Notice'),
      i.exam ? el('span', { class: 'badge' }, `${EXAM_LABEL[i.exam] ?? i.exam}${i.examYear ? ` ${i.examYear}` : ''}`) : null,
      el('time', { datetime: i.publishedAt }, `${timeFmt.format(new Date(i.publishedAt))} IST`)),
    el('h3', { lang: i.lang }, i.title),
    el('div', { class: 'files' },
      ...i.files.map((f) => {
        const href = safeUrl(f.url);
        return href ? el('a', { href, rel: 'noopener', target: '_blank' }, `PDF: ${f.name}${size(f.size)}`) : null;
      }),
      i.link && safeUrl(i.link) ? el('a', { href: safeUrl(i.link), rel: 'noopener', target: '_blank' }, 'Official page') : null))));
}

async function loadFeed() {
  try {
    feed = await getJSON('feed.json');
    $('feed-status').textContent = `Updated ${timeFmt.format(new Date(feed.generatedAt))} IST · source: ssc.gov.in`;
    renderExamOptions();
    renderFeed();
  } catch (e) {
    $('feed').replaceChildren(el('li', { class: 'error' },
      "Couldn't load the latest notices. Check your connection, or see ",
      el('a', { href: 'https://ssc.gov.in', rel: 'noopener' }, 'ssc.gov.in'), '.'));
    console.error(e);
  }
}

$('exam-filter').addEventListener('change', (e) => { filters.exam = e.target.value; renderFeed(); });
$('lang-filter').addEventListener('change', (e) => { filters.lang = e.target.value; renderFeed(); });
$('dept-filter').addEventListener('change', (e) => { filters.dept = e.target.checked; renderFeed(); });

// ---------- eligibility ----------
let rulesIndex = null;
const rulesCache = {};

async function loadRulesIndex() {
  if (rulesIndex) return;
  try {
    rulesIndex = (await getJSON('rules/index.json')).exams;
    $('f-exam').replaceChildren(...rulesIndex.map((e) => el('option', { value: e.id }, `${e.title}`)));
    const saved = store.get('profile', null);
    if (saved) {
      for (const [id, key] of [['f-exam', 'exam'], ['f-dob', 'dob'], ['f-cat', 'category'], ['f-edu', 'education'], ['f-gender', 'gender']]) {
        if (saved[key]) $(id).value = saved[key];
      }
      $('f-appearing').checked = Boolean(saved.appearing);
      $('f-pwbd').checked = Boolean(saved.pwbd);
      $('f-other').checked = Boolean(saved.otherCategory);
    }
  } catch (e) {
    $('elig-result').replaceChildren(el('p', { class: 'error' }, "Couldn't load the exam rules. Please try again."));
    console.error(e);
  }
}

function reasonText(code, rules) {
  if (REASON[code]) return REASON[code](null, rules);
  const [k, v] = code.split(':');
  return REASON[k] ? REASON[k](v, rules) : code;
}

function renderResult(rules, r) {
  const d = rules.dates;
  const banner = {
    open: `Applications open till ${day(d.applyTo)} (fee till ${day(d.feeTo)}).`,
    upcoming: `Applications open on ${day(d.applyFrom)}.`,
    closed: `Applications closed on ${day(d.applyTo)}. Shown so you can prepare for the next cycle.`,
  }[r.application];
  const notice = safeUrl(rules.source.notice);
  const of = (s) => r.posts.filter((p) => p.status === s);
  const lines = (p) => [...p.reasons.map((c) => reasonText(c, rules)), ...p.notes.map((n) => NOTE[n] ?? n)];
  const card = (p) => el('li', { class: `item post ${p.status}` },
    el('div', { class: 'meta' }, el('span', {}, `Pay Level ${p.payLevel} · Group ${p.group}`)),
    el('h3', {}, p.name),
    el('div', { class: 'small' }, p.org),
    el('div', { class: 'small' }, `Born between ${day(p.bornFrom)} and ${day(p.bornTo)} (for you)`),
    lines(p).length ? el('ul', {}, ...lines(p).map((t) => el('li', {}, t))) : null);
  // 40 CGL posts as full cards is ~15 phone screens: only "check" gets cards; the rest are short rows.
  const row = (p) => el('li', { class: `row ${p.status}` },
    el('strong', {}, p.name), el('span', { class: 'small' }, ` · ${p.org} · Level ${p.payLevel}`),
    lines(p).length ? el('div', { class: 'small' }, lines(p).join(' ')) : null);
  const group = (status, title, open) => {
    const posts = of(status);
    if (!posts.length) return null;
    const body = status === 'check' ? el('ul', { class: 'list' }, ...posts.map(card))
      : el('ul', { class: 'rows' }, ...posts.map(row));
    return el('details', { class: `group ${status}`, open }, el('summary', {}, `${title} (${posts.length})`), body);
  };
  $('elig-result').replaceChildren(...[
    el('div', { class: `banner ${r.application}` }, banner),
    el('div', { class: 'summary' },
      el('span', { class: 's-eligible' }, `${r.counts.eligible} can apply`),
      el('span', { class: 's-check' }, `${r.counts.check} to check`),
      el('span', { class: 's-ineligible' }, `${r.counts.ineligible} not eligible`)),
    el('p', { class: 'small' },
      `Fee: ${r.fee ? `₹${r.fee}` : 'none (exempt)'}. Age relaxation applied: ${r.relaxationYears} years. `,
      `Age is counted on ${day(rules.age.asOn)}. `,
      notice ? el('a', { href: notice, rel: 'noopener', target: '_blank' }, 'Official Notice (PDF)') : null,
      rules.source.amendments.length ? ` · ${rules.source.amendments.length} later change(s) applied` : ''),
    group('check', STATUS_LABEL.check, true),
    group('eligible', STATUS_LABEL.eligible, of('eligible').length <= 8),
    group('ineligible', STATUS_LABEL.ineligible, !of('eligible').length && !of('check').length),
    el('p', { class: 'small' }, `Rules copied from the Notice dated ${day(rules.source.published)} (checked ${day(rules.source.checkedOn)}). `,
      'Ex-servicemen, Govt employee and widow relaxations are not calculated here.'),
  ].filter(Boolean));
}

$('elig-form').addEventListener('submit', async (ev) => {
  ev.preventDefault();
  const profile = {
    exam: $('f-exam').value, dob: $('f-dob').value, category: $('f-cat').value, education: $('f-edu').value,
    gender: $('f-gender').value, appearing: $('f-appearing').checked, pwbd: $('f-pwbd').checked,
    otherCategory: $('f-other').checked,
  };
  if (!profile.dob) { $('f-dob').focus(); $('elig-result').replaceChildren(el('p', { class: 'error' }, 'Enter your date of birth.')); return; }
  store.set('profile', profile);
  try {
    rulesCache[profile.exam] ??= await getJSON(`rules/${profile.exam}.json`);
    renderResult(rulesCache[profile.exam], evaluate(rulesCache[profile.exam], profile, todayIST()));
    $('elig-result').scrollIntoView({ behavior: 'smooth', block: 'start' });
  } catch (e) {
    $('elig-result').replaceChildren(el('p', { class: 'error' }, `Couldn't check: ${e.message}`));
    console.error(e);
  }
});

// ---------- calendar ----------
let calendarLoaded = false;
async function loadCalendar() {
  if (calendarLoaded) return;
  calendarLoaded = true;
  try {
    const cal = await getJSON('calendar.json');
    $('cal').replaceChildren(el('div', { class: 'card' }, el('table', {},
      el('thead', {}, el('tr', {}, el('th', {}, 'Exam'), el('th', {}, 'Apply (tentative)'), el('th', {}, 'Exam'))),
      el('tbody', {}, ...cal.entries.map((e) => el('tr', {},
        el('td', {}, e.title, e.stage ? el('div', { class: 'small' }, e.stage) : null),
        el('td', {}, `${day(e.applyFrom)} – ${day(e.applyTo)}`),
        el('td', {}, e.examWindow ?? '')))))));
  } catch (e) {
    calendarLoaded = false;
    $('cal').replaceChildren(el('p', { class: 'error' }, "Couldn't load the calendar."));
    console.error(e);
  }
}

renderFilters();
show(location.hash.slice(1));
loadFeed();
