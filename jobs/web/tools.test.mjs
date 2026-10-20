// node --test jobs/web/tools.test.mjs
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { STRINGS } from './i18n.js';
import { ageOn, buildIcs, examEvents } from './tools.js';

const rules = (id) => JSON.parse(readFileSync(new URL(`../pipeline/rules/${id}.json`, import.meta.url), 'utf8'));

test('ageOn agrees with the SSC age windows', () => {
  // Youngest allowed for "18-27 as on 01-08-2026" is exactly 18; oldest is 26y 11m 30d (turning 27 is out).
  assert.deepEqual(ageOn('2008-08-01', '2026-08-01'), { y: 18, m: 0, d: 0 });
  assert.deepEqual(ageOn('1999-08-02', '2026-08-01'), { y: 26, m: 11, d: 30 });
  assert.deepEqual(ageOn('1999-08-01', '2026-08-01'), { y: 27, m: 0, d: 0 });
});

test('ageOn month and leap-year borrowing', () => {
  assert.deepEqual(ageOn('2000-02-29', '2026-08-01'), { y: 26, m: 5, d: 3 });
  assert.deepEqual(ageOn('2004-01-31', '2024-03-01'), { y: 20, m: 1, d: 1 }); // February 2024 has 29 days
  assert.deepEqual(ageOn('2004-03-15', '2004-03-15'), { y: 0, m: 0, d: 0 });
  assert.equal(ageOn('2026-08-02', '2026-08-01'), null);
  assert.throws(() => ageOn('15-03-2004', '2026-08-01'));
});

test('ics: all-day events, reminder, escaping, CRLF and folding', () => {
  const ics = buildIcs([{ uid: 'x', title: 'CHSL, 2026; last date', from: '2026-10-07', url: 'https://ssc.gov.in/a' },
    { uid: 'y', title: 'Tier-I '.repeat(20), from: '2026-11-30', to: '2026-12-31' }], '20261020T120000Z');
  assert.ok(ics.startsWith('BEGIN:VCALENDAR\r\n') && ics.endsWith('END:VCALENDAR\r\n'));
  assert.ok(!/[^\r]\n/.test(ics), 'bare LF');
  assert.ok(ics.includes('DTSTART;VALUE=DATE:20261007\r\nDTEND;VALUE=DATE:20261008'));
  assert.ok(ics.includes('DTEND;VALUE=DATE:20270101'), 'end is exclusive: day after 31-12');
  assert.ok(ics.includes('SUMMARY:CHSL\\, 2026\\; last date'));
  assert.ok(ics.includes('TRIGGER:-P1D'));
  for (const line of ics.split('\r\n')) assert.ok(new TextEncoder().encode(line).length <= 75, line);
});

test('examEvents keeps only dates still ahead', () => {
  const ev = examEvents(rules('ssc-chsl-2026'), '2026-10-20');
  assert.deepEqual(ev.map((e) => [e.from, e.to]), [['2026-11-30', '2026-12-31']]);
  assert.equal(examEvents(rules('ssc-chsl-2026'), '2026-09-01').length, 5);
  assert.equal(examEvents(rules('ssc-steno-2026'), '2026-10-20').length, 0); // only a month name, no dates
});

test('English and Hindi have the same keys and placeholders', () => {
  const ph = (s) => [...s.matchAll(/\{(\w+)\}/g)].map((m) => m[1]).sort().join(',');
  assert.deepEqual(Object.keys(STRINGS.hi).sort(), Object.keys(STRINGS.en).sort());
  for (const [k, v] of Object.entries(STRINGS.en)) assert.equal(ph(STRINGS.hi[k]), ph(v), k);
});

test('ageOn: days are never negative and stay under a month, over 30 years of dates', () => {
  const iso = (t) => new Date(t).toISOString().slice(0, 10);
  const start = Date.UTC(1996, 0, 1);
  for (let i = 0; i < 4000; i++) {
    const dob = iso(start + ((i * 7919) % 11000) * 864e5);
    const on = iso(Date.parse(`${dob}T00:00:00Z`) + ((i * 104729) % 12000) * 864e5);
    const a = ageOn(dob, on);
    assert.ok(a.d >= 0 && a.d <= 30 && a.m >= 0 && a.m <= 11 && a.y >= 0, `${dob} ${on} ${JSON.stringify(a)}`);
  }
});

test('timeline: CHSL 2026 on 20-10-2026 (closed, Tier-I announced, nothing else yet)', async () => {
  const { timeline } = await import('./tracker.js');
  const items = [
    { id: 'n', kind: 'recruitment', exam: 'CHSL', examYear: 2026, lang: 'en', publishedAt: '2026-09-07T18:00:00+05:30' },
    { id: 'r25', kind: 'result', exam: 'CHSL', examYear: 2025, lang: 'en', publishedAt: '2026-10-07T19:10:47+05:30' },
  ];
  const t = timeline(rules('ssc-chsl-2026'), items, 'CHSL', 2026, '2026-10-20');
  assert.deepEqual(t.map((s) => [s.key, s.state]), [
    ['notice', 'done'], ['apply', 'done'], ['admit_card', 'next'], ['exam', 'later'],
    ['answer_key', 'later'], ['result', 'later'], ['exam', 'later']]);
  assert.equal(t[3].from, '2026-11-30');
  assert.equal(t[6].when, 'To be notified later');
});

test('timeline: CGL 2026 while Tier-I is on', async () => {
  const { timeline } = await import('./tracker.js');
  const items = [{ id: 'a', kind: 'admit_card', exam: 'CGL', examYear: 2026, lang: 'en', publishedAt: '2026-09-21T10:00:00+05:30' }];
  const t = timeline(rules('ssc-cgl-2026'), items, 'CGL', 2026, '2026-10-20');
  assert.deepEqual(t.map((s) => [s.key, s.state]), [
    ['notice', 'done'], ['apply', 'done'], ['admit_card', 'done'], ['exam', 'now'],
    ['answer_key', 'next'], ['result', 'later'], ['exam', 'later']]);
});

test('timeline: an exam with no rules file shows only what the feed has', async () => {
  const { timeline } = await import('./tracker.js');
  const items = [{ id: 'r', kind: 'result', exam: 'CTGD', examYear: 2026, lang: 'en', publishedAt: '2026-10-12T18:47:07+05:30' }];
  // Admit card and answer key came before the result, outside the 120-day feed: not shown as "next".
  assert.deepEqual(timeline(null, items, 'CTGD', 2026, '2026-10-20').map((s) => [s.key, s.state]), [['result', 'done']]);
});

test('typingScore uses SSC key depressions (35 w.p.m. = 10,500 an hour)', async () => {
  const { typingScore } = await import('./tools.js');
  const passage = 'a'.repeat(2000);
  // 1750 correct key depressions in 10 minutes = 10,500 an hour = 35 w.p.m.
  assert.deepEqual(typingScore(passage, 'a'.repeat(1750), 600), { typed: 1750, correct: 1750, accuracy: 100, kdph: 10500, wpm: 35 });
  // CGL DEST: about 2000 key depressions in 15 minutes = 8,000 an hour
  assert.equal(typingScore(passage, 'a'.repeat(2000), 900).kdph, 8000);
  const s = typingScore('The office', 'The offxce', 60);
  assert.equal(s.correct, 9);
  assert.equal(s.accuracy, 90);
  assert.equal(typingScore('abc', '', 60).accuracy, 0);
});

test('practice text is long enough for the fastest SSC standard', async () => {
  const { practiceText, PASSAGES } = await import('./passages.js');
  assert.ok(practiceText(0).length >= 15000 / 4 && practiceText(1).length >= 3750);
  assert.ok(practiceText(1).startsWith(PASSAGES[1].slice(0, 20)));
});
