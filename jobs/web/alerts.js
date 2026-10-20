// Phone alerts for followed exams, worked out in the service worker (sw.js loads this file with
// importScripts) and tested in node (tools.test.mjs). Plain script, no import/export, so both can load it.
//
// No server is needed: on Android, Chrome wakes an installed web app now and then
// ("periodic background sync"; how often is Chrome's choice, based on how much the app is used,
// and only for an app that was installed and opened as an app), the worker fetches feed.json and
// rules/index.json from this site and shows a notification for anything new. Browsers without
// it (iPhone, desktop Firefox) get no background alerts; the app says so instead of pretending.
//
// pick() decides what to announce. It never announces:
//   * anything on the first check (otherwise switching alerts on would fire for the whole feed);
//   * an item it has seen before, or one older than 7 days (a feed that grew back in time);
//   * more than MAX notifications at once: the rest are folded into one "and n more".
(function (root) {
  const MAX = 4;
  const MAX_AGE_DAYS = 7;
  const fmt = (s, vars) => s.replace(/\{(\w+)\}/g, (_, k) => (k in vars ? String(vars[k]) : `{${k}}`));
  const daysBetween = (a, b) => Math.round((Date.parse(`${b}T00:00:00Z`) - Date.parse(`${a}T00:00:00Z`)) / 864e5);

  /**
   * feed: feed.json; index: rules/index.json (or null); state: what the last check saw (or null);
   * config: {exams: [codes], names: {code: name}, text: {newItem, more, lastDays, lastDay, kinds: {}}};
   * today: 'YYYY-MM-DD' (IST). Returns {notes: [{title, body, tag, url}], state}.
   */
  function pick(feed, index, state, config, today) {
    const exams = new Set(config.exams || []);
    const ids = feed.items.map((i) => i.id);
    const next = { seen: ids, deadlines: (state && state.deadlines) || [] };
    if (!state) return { notes: [], state: next };
    const seen = new Set(state.seen || []);
    const oldest = new Date(Date.parse(`${today}T00:00:00Z`) - MAX_AGE_DAYS * 864e5).toISOString().slice(0, 10);
    const fresh = feed.items.filter((i) => !seen.has(i.id) && exams.has(i.exam) && i.publishedAt.slice(0, 10) >= oldest);
    const text = config.text;
    const notes = fresh.slice(0, fresh.length > MAX ? MAX - 1 : MAX).map((i) => ({
      title: fmt(text.newItem, { exam: config.names[i.exam] || i.exam, kind: text.kinds[i.kind] || text.kinds.notice }),
      body: i.title, tag: i.id, url: `#exam/${i.exam}`,
    }));
    if (fresh.length > MAX) {
      notes.push({ title: fmt(text.more, { n: fresh.length - (MAX - 1) }), body: '', tag: 'more', url: '#home' });
    }
    // Last days to apply, once per exam cycle: the official closing date from the rules file.
    const done = new Set(next.deadlines);
    for (const e of (index && index.exams) || []) {
      if (!exams.has(e.exam) || today < e.applyFrom || today > e.applyTo) continue;
      const left = daysBetween(today, e.applyTo);
      const key = `${e.id}:${e.applyTo}`;
      if (left > 2 || done.has(key)) continue;
      done.add(key);
      notes.push({ title: fmt(left === 0 ? text.lastDay : text.lastDays, { title: e.title, n: left }), body: '', tag: key, url: `#exam/${e.exam}` });
    }
    next.deadlines = [...done].slice(-50);
    return { notes, state: next };
  }

  root.JobsAlerts = { pick, MAX, MAX_AGE_DAYS };
}(typeof self !== 'undefined' ? self : globalThis));
