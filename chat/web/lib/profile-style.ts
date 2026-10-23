// Profile style (033_profiles_feed.sql): the lists and limits the server checks, the accent palette, and the
// clean-up of what the editor sends and what the server returns. No imports but types, so the unit tests run it
// directly (node --test).
import type { Accent, ProfilePrompt, ProfileStyle, ProfileStyleInput, PromptQ, SpokenLang } from './backend'

export const STATUS_MAX = 60
export const LANGUAGES_MAX = 6
export const PROMPTS_MAX = 3
export const ANSWER_MAX = 120

export const ACCENTS: Accent[] = ['rose', 'violet', 'ocean', 'mint', 'sunset', 'gold', 'berry', 'slate']

/**
 * Each accent: the ring colour (`via`), a dark fill under white text (`to`, text posts), a deep tone for text and
 * icons on light cards (`ink`, ≥ 4.5:1 on white) and a light one for night screens (`night`). `null` is the brand's
 * own: saffron, black text-post tiles.
 */
export const ACCENT_COLORS: Record<Accent | 'brand', { via: string; to: string; ink: string; night: string }> = {
  brand: { via: '#ff8a00', to: '#111111', ink: '#b45309', night: '#ff8a00' },
  rose: { via: '#f43f5e', to: '#be123c', ink: '#be123c', night: '#fb7185' },
  violet: { via: '#8b5cf6', to: '#5b21b6', ink: '#6d28d9', night: '#a78bfa' },
  ocean: { via: '#3b82f6', to: '#1e40af', ink: '#1d4ed8', night: '#60a5fa' },
  mint: { via: '#10b981', to: '#047857', ink: '#047857', night: '#34d399' },
  sunset: { via: '#fb923c', to: '#e11d48', ink: '#c2410c', night: '#fb923c' },
  gold: { via: '#f59e0b', to: '#b45309', ink: '#a16207', night: '#fbbf24' },
  berry: { via: '#db2777', to: '#831843', ink: '#9d174d', night: '#f472b6' },
  slate: { via: '#64748b', to: '#1e293b', ink: '#334155', night: '#94a3b8' },
}

/** CSS variables for an accent: --acc-via/to, --acc-ink (text on light), --acc-night (text at night). */
export function accentVars(accent: Accent | null | undefined): Record<string, string> {
  const c = ACCENT_COLORS[accent && accent in ACCENT_COLORS ? accent : 'brand']
  return { '--acc-via': c.via, '--acc-to': c.to, '--acc-ink': c.ink, '--acc-night': c.night }
}

/** Spoken languages, written in their own script (shown as is in every app language). */
export const SPOKEN_LANGS: { id: SpokenLang; native: string }[] = [
  { id: 'en', native: 'English' },
  { id: 'hi', native: 'हिन्दी' },
  { id: 'bn', native: 'বাংলা' },
  { id: 'ta', native: 'தமிழ்' },
  { id: 'te', native: 'తెలుగు' },
  { id: 'mr', native: 'मराठी' },
  { id: 'gu', native: 'ગુજરાતી' },
  { id: 'kn', native: 'ಕನ್ನಡ' },
  { id: 'ml', native: 'മലയാളം' },
  { id: 'pa', native: 'ਪੰਜਾਬੀ' },
  { id: 'ur', native: 'اردو' },
  { id: 'or', native: 'ଓଡ଼ିଆ' },
  { id: 'as', native: 'অসমীয়া' },
  { id: 'pt', native: 'Português' },
  { id: 'vi', native: 'Tiếng Việt' },
]
const LANG_IDS = new Set<string>(SPOKEN_LANGS.map((l) => l.id))
export const nativeName = (id: SpokenLang) => SPOKEN_LANGS.find((l) => l.id === id)?.native ?? id

export const PROMPT_QS: PromptQ[] = ['weekend', 'food', 'song', 'travel', 'talk', 'laugh', 'goal', 'secret']
const Q_IDS = new Set<string>(PROMPT_QS)

export const isAccent = (v: unknown): v is Accent => typeof v === 'string' && (ACCENTS as string[]).includes(v)

/** profile_style's row (or the profiles columns) as the app keeps it; anything the app doesn't know is dropped. */
export function parseStyle(row: { accent?: unknown; status?: unknown; languages?: unknown; prompts?: unknown } | null | undefined): ProfileStyle {
  const langs = Array.isArray(row?.languages) ? row!.languages : []
  const prompts = Array.isArray(row?.prompts) ? row!.prompts : []
  const status = typeof row?.status === 'string' && row.status.trim() ? row.status.trim() : null
  return {
    accent: isAccent(row?.accent) ? row!.accent : null,
    status,
    languages: [...new Set(langs.filter((l): l is SpokenLang => typeof l === 'string' && LANG_IDS.has(l)))].slice(0, LANGUAGES_MAX),
    prompts: prompts
      .filter((p): p is ProfilePrompt => !!p && typeof p === 'object' && Q_IDS.has((p as ProfilePrompt).q) && typeof (p as ProfilePrompt).a === 'string' && !!(p as ProfilePrompt).a.trim())
      .map((p) => ({ q: p.q, a: p.a.trim() }))
      .slice(0, PROMPTS_MAX),
  }
}

export const EMPTY_STYLE: ProfileStyle = { accent: null, status: null, languages: [], prompts: [] }

export type StyleProblem = 'status' | 'answer' | 'languages' | 'prompts' | null

/**
 * What the editor has, as set_profile_style will take it: trimmed, an empty status is none, languages without
 * repeats, prompts without an answer left out and each question once. `problem`: why it can't be saved yet.
 */
export function styleInput(draft: ProfileStyleInput): { input: ProfileStyleInput; problem: StyleProblem } {
  const status = draft.status?.trim() || null
  const languages = [...new Set(draft.languages.filter((l) => LANG_IDS.has(l)))]
  const seen = new Set<string>()
  const prompts: ProfilePrompt[] = []
  for (const p of draft.prompts) {
    const a = p.a.trim()
    if (!a || !Q_IDS.has(p.q) || seen.has(p.q)) continue
    seen.add(p.q)
    prompts.push({ q: p.q, a })
  }
  let problem: StyleProblem = null
  if (status && [...status].length > STATUS_MAX) problem = 'status'
  else if (languages.length > LANGUAGES_MAX) problem = 'languages'
  else if (prompts.length > PROMPTS_MAX) problem = 'prompts'
  else if (prompts.some((p) => [...p.a].length > ANSWER_MAX)) problem = 'answer'
  return { input: { status, languages, prompts }, problem }
}

/** The next question not answered yet (for "Add a prompt"), null when all are used or 3 are answered. */
export function nextPrompt(used: PromptQ[]): PromptQ | null {
  if (used.length >= PROMPTS_MAX) return null
  return PROMPT_QS.find((q) => !used.includes(q)) ?? null
}

/** Adds or removes a language, keeping at most 6 (a 7th is refused: false). */
export function toggleLanguage(list: SpokenLang[], id: SpokenLang): SpokenLang[] | false {
  if (list.includes(id)) return list.filter((l) => l !== id)
  if (list.length >= LANGUAGES_MAX) return false
  return [...list, id]
}
