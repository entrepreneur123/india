// node --test jobs/web/eligibility.test.mjs  — same cases as jobs/pipeline/tests/test_eligibility.py
import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';
import { test } from 'node:test';
import { bornWindow, evaluate, shiftYears, todayIST } from './eligibility.js';

const root = new URL('../pipeline/', import.meta.url);
const read = (rel) => JSON.parse(readFileSync(new URL(rel, root), 'utf8'));
const rules = Object.fromEntries(
  readdirSync(new URL('rules/', root)).filter((n) => n.endsWith('.json'))
    .map((n) => [n.slice(0, -5), read(`rules/${n}`)]));

for (const c of read('tests/eligibility_cases.json').cases) {
  test(c.name, () => {
    const r = evaluate(rules[c.rules], c.profile, c.today);
    const want = c.expect;
    for (const key of ['application', 'fee', 'relaxationYears', 'counts']) {
      if (key in want) assert.deepEqual(r[key], want[key], key);
    }
    if (want.posts) assert.deepEqual(r.posts.map((p) => p.status), want.posts);
    if (want.reasons0) assert.deepEqual(r.posts[0].reasons, want.reasons0);
    for (const [key, status] of Object.entries(want.byName ?? {})) {
      const [name, org] = key.split('|');
      const found = r.posts.filter((p) => p.name === name && (!org || p.org === org));
      assert.equal(found.length, 1, key);
      assert.equal(found[0].status, status, key);
    }
  });
}

test('every band in the rule files matches the formula (same check as the Python loader)', () => {
  for (const r of Object.values(rules)) {
    for (const band of Object.values(r.age.bands)) {
      assert.deepEqual(bornWindow(r.age.asOn, band.min, band.max), [band.bornFrom, band.bornTo], r.id);
    }
  }
});

test('leap day and bad input', () => {
  assert.equal(shiftYears('2024-02-29', -1), '2023-02-28');
  assert.equal(shiftYears('2024-02-29', -4), '2020-02-29');
  const p = { dob: '2004-01-01', category: 'UR', education: '12' };
  assert.throws(() => evaluate(rules['ssc-chsl-2026'], { ...p, dob: '20040101' }, '2026-09-10'));
  assert.throws(() => evaluate(rules['ssc-chsl-2026'], { ...p, dob: '2004-02-30' }, '2026-09-10'));
  assert.throws(() => evaluate(rules['ssc-chsl-2026'], { ...p, category: 'GEN' }, '2026-09-10'));
});

test('todayIST rolls over at midnight India time, not UTC', () => {
  assert.equal(todayIST(Date.parse('2026-10-20T18:29:59Z')), '2026-10-20');
  assert.equal(todayIST(Date.parse('2026-10-20T18:30:00Z')), '2026-10-21');
});
