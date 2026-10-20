// "When is it?" for one exam cycle (e.g. CHSL 2026): which stages SSC has already published and
// what comes next. Only official facts: dates from the rules file (copied from the Notice and
// SSC's schedule notices) and notices in the feed. A stage with no announced date says so;
// nothing here predicts a date.

/**
 * rules: rules file for that cycle, or null. items: feed items. today: 'YYYY-MM-DD' (IST).
 * Returns [{key, state: 'done'|'now'|'next'|'later', from?, to?, when?, at?, itemId?}] in order.
 */
export function timeline(rules, items, exam, year, today) {
  // SSC posts each notice twice (English, Hindi): English is used. Boards with Hindi only use that.
  const cycle = items.filter((i) => i.exam === exam && i.examYear === year);
  const english = cycle.filter((i) => i.lang === 'en');
  const mine = english.length ? english : cycle;
  const latest = (kind) => mine.filter((i) => i.kind === kind).sort((a, b) => (a.publishedAt < b.publishedAt ? 1 : -1))[0];
  const stages = [];
  const notice = latest('recruitment');
  if (rules || notice) {
    stages.push({ key: 'notice', at: rules ? rules.source.published : notice.publishedAt.slice(0, 10), itemId: notice?.id });
  }
  if (rules) {
    stages.push({ key: 'apply', from: rules.dates.applyFrom, to: rules.dates.applyTo });
    for (const s of rules.examSchedule ?? []) {
      stages.push(s.from ? { key: 'exam', label: s.stage, from: s.from, to: s.to } : { key: 'exam', label: s.stage, when: s.when });
    }
  }
  for (const kind of ['admit_card', 'answer_key', 'result']) {
    const it = latest(kind);
    stages.push(it ? { key: kind, at: it.publishedAt.slice(0, 10), itemId: it.id } : { key: kind });
  }
  for (const s of stages) {
    if (s.at) s.state = 'done';
    else if (s.from) s.state = today > s.to ? 'done' : today >= s.from ? 'now' : 'later';
    else s.state = 'later';
  }
  // Published stages sort by date. Stages still waiting follow the usual order of a cycle:
  // admit card, the exam stages with announced dates, answer key, result, then stages SSC has
  // only named (e.g. "Tier-II: December 2026").
  const key = (s) => s.at ?? s.from;
  const done = stages.filter((s) => s.state !== 'later').sort((a, b) => (key(a) < key(b) ? -1 : key(a) > key(b) ? 1 : 0));
  const later = stages.filter((s) => s.state === 'later');
  // A stage missing before one already published (an admit card when the result is out) happened
  // outside the feed's window: leaving it as "next" would be wrong, so it is left out.
  const RANK = { notice: 0, apply: 1, admit_card: 2, exam: 3, answer_key: 4, result: 5 };
  const reached = Math.max(-1, ...done.map((s) => RANK[s.key]));
  const pending = later.filter((s) => (s.key === 'exam' ? !s.from || RANK.exam > reached : RANK[s.key] > reached));
  const waiting = [
    ...pending.filter((s) => s.key === 'admit_card'),
    ...pending.filter((s) => s.key === 'exam' && s.from).sort((a, b) => (a.from < b.from ? -1 : 1)),
    ...pending.filter((s) => s.key === 'answer_key' || s.key === 'result'),
    ...pending.filter((s) => s.key === 'exam' && !s.from),
  ];
  if (waiting.length) waiting[0].state = 'next';
  return [...done, ...waiting];
}
