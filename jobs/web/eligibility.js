// Port of jobs/pipeline/sarkari/eligibility.py (evaluate and its helpers). Keep the two in step:
// jobs/pipeline/tests/eligibility_cases.json runs against both (pytest and `node --test`).
// Dates are plain 'YYYY-MM-DD' strings: no Date objects in local time, so no time-zone drift.

export const CATEGORIES = ['UR', 'EWS', 'OBC', 'SC', 'ST'];
export const EDUCATION_LEVELS = ['10', '12', 'graduate'];
const STATUSES = ['eligible', 'check', 'ineligible'];

function parse(s, what) {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(String(s));
  if (m) {
    const [y, mo, d] = [Number(m[1]), Number(m[2]), Number(m[3])];
    const t = new Date(Date.UTC(y, mo - 1, d));
    if (t.getUTCFullYear() === y && t.getUTCMonth() === mo - 1 && t.getUTCDate() === d) return { y, m: mo, d };
  }
  throw new RangeError(`${what}: ${JSON.stringify(s)} is not YYYY-MM-DD`);
}

const pad = (n, w) => String(n).padStart(w, '0');
const fmt = ({ y, m, d }) => `${pad(y, 4)}-${pad(m, 2)}-${pad(d, 2)}`;
const isLeap = (y) => (y % 4 === 0 && y % 100 !== 0) || y % 400 === 0;

/** Same day and month `years` later (negative: earlier). 29 Feb -> 28 Feb. */
export function shiftYears(iso, years) {
  const { y, m, d } = parse(iso, 'date');
  const y2 = y + years;
  return fmt({ y: y2, m, d: m === 2 && d === 29 && !isLeap(y2) ? 28 : d });
}

function addDays(iso, days) {
  const { y, m, d } = parse(iso, 'date');
  const t = new Date(Date.UTC(y, m - 1, d + days));
  return fmt({ y: t.getUTCFullYear(), m: t.getUTCMonth() + 1, d: t.getUTCDate() });
}

/** [earliest, latest] date of birth allowed, both inclusive (SSC: turning `max` on asOn is out). */
export function bornWindow(asOn, minAge, maxAge, relaxYears = 0) {
  return [addDays(shiftYears(asOn, -(maxAge + relaxYears)), 1), shiftYears(asOn, -minAge)];
}

export function relaxationYears(rules, category, pwbd) {
  const r = rules.age.relaxation;
  if (pwbd) return r[`PwBD_${category}`] ?? r.PwBD;
  return r[category] ?? 0;
}

export function applicationStatus(rules, today) {
  parse(today, 'today');
  if (today < rules.dates.applyFrom) return 'upcoming';
  return today <= rules.dates.applyTo ? 'open' : 'closed';
}

export function fee(rules, profile) {
  const exempt = new Set(rules.fee.exempt);
  if ((profile.gender === 'female' && exempt.has('female')) || exempt.has(profile.category) ||
      (profile.pwbd && exempt.has('PwBD'))) return 0;
  return rules.fee.amount;
}

export function checkProfile(profile) {
  if (!CATEGORIES.includes(profile.category)) throw new RangeError(`category must be one of ${CATEGORIES}`);
  if (!EDUCATION_LEVELS.includes(profile.education)) throw new RangeError(`education must be one of ${EDUCATION_LEVELS}`);
  parse(profile.dob, 'dob');
}

export function evaluate(rules, profile, today) {
  checkProfile(profile);
  parse(today, 'today');
  const dob = profile.dob;
  const relax = relaxationYears(rules, profile.category, Boolean(profile.pwbd));
  const eduBy = rules.education.asOn;
  const level = EDUCATION_LEVELS.indexOf(profile.education);

  const posts = rules.posts.map((p) => {
    const band = rules.age.bands[p.ageBand];
    const [earliest, latest] = bornWindow(rules.age.asOn, band.min, band.max, relax);
    let status = 'eligible';
    const reasons = [];
    const worse = (next, reason) => {
      if (STATUSES.indexOf(next) > STATUSES.indexOf(status)) status = next;
      reasons.push(reason);
    };
    if (dob > latest) {
      worse('ineligible', `age_below_min:${band.min}`);
    } else if (dob < earliest) {
      if (profile.otherCategory) worse('check', 'age_other_relaxation');
      else worse('ineligible', `age_above_max:${band.max + relax}`);
    }
    const need = EDUCATION_LEVELS.indexOf(p.education);
    if (level < need) {
      worse('ineligible', `education_needs:${p.education}`);
    } else if (level === need && profile.appearing) {
      if (!rules.education.appearingAllowed || today > eduBy) worse('ineligible', `result_needed_by:${eduBy}`);
      else worse('check', `result_needed_by:${eduBy}`);
    }
    for (const req of p.requires ?? []) worse('check', `requires:${req}`);
    return {
      name: p.name, org: p.org, payLevel: p.payLevel, group: p.group,
      status, reasons, notes: [...(p.notes ?? [])], bornFrom: earliest, bornTo: latest,
    };
  });
  const counts = Object.fromEntries(STATUSES.map((s) => [s, posts.filter((p) => p.status === s).length]));
  return {
    id: rules.id,
    application: applicationStatus(rules, today),
    fee: fee(rules, profile),
    relaxationYears: relax,
    counts,
    posts,
  };
}

/** Today's date in India (IST, UTC+5:30), whatever the device's time zone. */
export function todayIST(now = Date.now()) {
  return new Date(now + 330 * 60 * 1000).toISOString().slice(0, 10);
}
