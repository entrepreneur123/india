// Tools that run on the phone: age calculator, calendar (.ics) file, signature resizer.
// ageOn and buildIcs are pure (tested with node); makeSignature needs a browser canvas.

const DATE = /^(\d{4})-(\d{2})-(\d{2})$/;
function parts(iso) {
  const m = DATE.exec(iso);
  if (!m) throw new RangeError(`${JSON.stringify(iso)} is not YYYY-MM-DD`);
  return [Number(m[1]), Number(m[2]), Number(m[3])];
}
const daysIn = (y, m) => new Date(Date.UTC(y, m, 0)).getUTCDate(); // m is 1-12

/** Completed years, months and days from dob to `on` (both YYYY-MM-DD); null if dob is after `on`.
 * Counts whole months to the last monthly anniversary on or before `on` (a 31st falls back to
 * the month's last day), then the days after it: 31-01-2004 to 01-03-2024 is 20 y 1 m 1 d. */
export function ageOn(dob, on) {
  if (dob > on) return null;
  const [by, bm, bd] = parts(dob);
  const [oy, om, od] = parts(on);
  let months = (oy - by) * 12 + (om - bm);
  if (od < bd) months -= 1;
  const ay = by + Math.floor((bm - 1 + months) / 12);
  const am = ((bm - 1 + months) % 12) + 1;
  const anniversary = Date.UTC(ay, am - 1, Math.min(bd, daysIn(ay, am)));
  const d = Math.round((Date.UTC(oy, om - 1, od) - anniversary) / 864e5);
  return { y: Math.floor(months / 12), m: months % 12, d };
}

function icsText(s) {
  return String(s).replace(/\\/g, '\\\\').replace(/;/g, '\\;').replace(/,/g, '\\,').replace(/\r?\n/g, '\\n');
}
/** RFC 5545 folding: lines longer than 75 octets continue on the next line after a space. */
function fold(line) {
  const out = [];
  let cur = '';
  let bytes = 0;
  for (const ch of line) {
    const n = new TextEncoder().encode(ch).length;
    if (bytes + n > 75) { out.push(cur); cur = ' '; bytes = 1; }
    cur += ch;
    bytes += n;
  }
  out.push(cur);
  return out.join('\r\n');
}
const compact = (iso) => iso.replaceAll('-', '');
function nextDay(iso) {
  const [y, m, d] = parts(iso);
  return new Date(Date.UTC(y, m - 1, d + 1)).toISOString().slice(0, 10);
}

/**
 * events: [{uid, title, from: 'YYYY-MM-DD', to?: 'YYYY-MM-DD', url?}] as all-day events
 * with a reminder the day before. stamp: 'YYYYMMDDTHHMMSSZ'.
 */
export function buildIcs(events, stamp) {
  const lines = ['BEGIN:VCALENDAR', 'VERSION:2.0', 'PRODID:-//SSC Job Alerts//EN', 'CALSCALE:GREGORIAN'];
  for (const e of events) {
    lines.push('BEGIN:VEVENT', `UID:${e.uid}@ssc-job-alerts`, `DTSTAMP:${stamp}`,
      `DTSTART;VALUE=DATE:${compact(e.from)}`, `DTEND;VALUE=DATE:${compact(nextDay(e.to ?? e.from))}`,
      `SUMMARY:${icsText(e.title)}`);
    if (e.url) lines.push(`URL:${icsText(e.url)}`, `DESCRIPTION:${icsText(`Official: ${e.url}`)}`);
    lines.push('BEGIN:VALARM', 'ACTION:DISPLAY', `DESCRIPTION:${icsText(e.title)}`, 'TRIGGER:-P1D', 'END:VALARM', 'END:VEVENT');
  }
  lines.push('END:VCALENDAR');
  return lines.map(fold).join('\r\n') + '\r\n';
}

/** Calendar events for one exam's rules file: only dates from `today` on. */
export function examEvents(rules, today) {
  const d = rules.dates;
  const name = rules.title;
  const ev = [
    { uid: `${rules.id}-open`, title: `${name}: applications open`, from: d.applyFrom },
    { uid: `${rules.id}-last`, title: `${name}: LAST DATE to apply`, from: d.applyTo },
    { uid: `${rules.id}-fee`, title: `${name}: last date for fee payment`, from: d.feeTo },
  ];
  if (d.correction) ev.push({ uid: `${rules.id}-corr`, title: `${name}: form correction window`, from: d.correction[0], to: d.correction[1] });
  for (const [i, s] of (rules.examSchedule ?? []).entries()) {
    if (s.from) ev.push({ uid: `${rules.id}-exam${i}`, title: `${name}: ${s.stage}`, from: s.from, to: s.to });
  }
  return ev.filter((e) => (e.to ?? e.from) >= today).map((e) => ({ ...e, url: rules.source.notice }));
}

// ---------- signature (browser only) ----------
// A clean signature on a whitened background compresses very well: measured in Chromium, a
// 900 px wide one was under SSC's 10 KB minimum even at quality 0.95, so the sizes go up to 1800.
const WIDTHS = [360, 480, 600, 750, 900, 1050, 1200, 1500, 1800];
const QUALITIES = [0.98, 0.95, 0.9, 0.85, 0.8, 0.75, 0.7, 0.6, 0.5, 0.4];

function toBlob(canvas, q) {
  return new Promise((resolve) => canvas.toBlob(resolve, 'image/jpeg', q));
}

/**
 * A 3:1 (≈ 6 cm × 2 cm) white-background JPEG of the signature photo, between minKB and maxKB.
 * Paper-grey is lifted to white so the ink stands out; the signature is centred, not stretched.
 * Returns {blob, width, height} or null when no size/quality lands in the range.
 */
export async function makeSignature(file, minKB, maxKB) {
  const bmp = await createImageBitmap(file);
  for (const w of WIDTHS) {
    const h = Math.round(w / 3);
    const c = document.createElement('canvas');
    c.width = w;
    c.height = h;
    const g = c.getContext('2d');
    g.fillStyle = '#fff';
    g.fillRect(0, 0, w, h);
    const scale = Math.min(w / bmp.width, h / bmp.height);
    const dw = bmp.width * scale;
    const dh = bmp.height * scale;
    g.drawImage(bmp, (w - dw) / 2, (h - dh) / 2, dw, dh);
    const img = g.getImageData(0, 0, w, h);
    const px = img.data;
    for (let i = 0; i < px.length; i += 4) {
      const l = 0.299 * px[i] + 0.587 * px[i + 1] + 0.114 * px[i + 2];
      const v = l > 170 ? 255 : Math.max(0, l - 40); // paper -> white, ink -> darker
      px[i] = px[i + 1] = px[i + 2] = v;
    }
    g.putImageData(img, 0, 0);
    for (const q of QUALITIES) {
      const blob = await toBlob(c, q);
      const kb = blob.size / 1024;
      if (kb >= minKB && kb <= maxKB) return { blob, width: w, height: h };
      if (kb < minKB) break; // lower quality only gets smaller: try a bigger image
    }
  }
  return null;
}

/**
 * Typing / data-entry score for a timed practice run.
 * SSC counts "key depressions" and states 35 w.p.m. ≈ 10,500 key depressions an hour
 * (CHSL 2026 Notice para 13.9.13.7), i.e. one word = 5 key depressions. Here a key depression
 * counts as correct when the character typed matches the passage at the same position.
 * SSC's own evaluation guidelines (error rules) are stricter; this is a speed practice, not a mark.
 */
export function typingScore(passage, typed, seconds) {
  const n = typed.length;
  let correct = 0;
  for (let i = 0; i < n && i < passage.length; i++) if (typed[i] === passage[i]) correct += 1;
  const hours = Math.max(seconds, 1) / 3600;
  const kdph = Math.round(correct / hours);
  return {
    typed: n,
    correct,
    accuracy: n ? Math.round((correct / n) * 1000) / 10 : 0,
    kdph,
    wpm: Math.round(kdph / 300),
  };
}
