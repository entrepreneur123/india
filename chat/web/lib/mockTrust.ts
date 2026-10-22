// The demo's identity checks, badges and manners (035), in their own chunk: fetched the first time the demo asks
// (after sign-in), so the demo's first screen doesn't grow.
import { BackendError, type KycDemoOutcome, type KycResult, type Message, type MyTrust, type Profile, type Trust } from './backend'
import { checkRating, GOOD_TAGS } from './trust'

const wait = (ms: number) => new Promise<void>((r) => setTimeout(r, ms))
const DAY = 24 * 3_600_000

// Manners of the demo people: most are well liked, a few are new (under 3 ratings).
const TRUST: Record<string, Omit<Trust, 'id' | 'verified'>> = {
  u_ananya: { manner: 86, ratings: 24, tags: [{ tag: 'kind', n: 12 }, { tag: 'funny', n: 7 }, { tag: 'good_listener', n: 5 }] },
  u_kabir: { manner: 72, ratings: 11, tags: [{ tag: 'respectful', n: 6 }, { tag: 'interesting', n: 4 }] },
  u_sofia: { manner: 93, ratings: 31, tags: [{ tag: 'kind', n: 15 }, { tag: 'funny', n: 11 }, { tag: 'interesting', n: 8 }] },
  u_mateo: { manner: 64, ratings: 9, tags: [{ tag: 'funny', n: 5 }] },
  u_yuki: { manner: 81, ratings: 14, tags: [{ tag: 'interesting', n: 7 }, { tag: 'kind', n: 6 }] },
  u_amara: { manner: 77, ratings: 8, tags: [{ tag: 'interesting', n: 5 }, { tag: 'respectful', n: 3 }] },
  u_lucas: { manner: 55, ratings: 2, tags: [] },
  u_leila: { manner: 88, ratings: 19, tags: [{ tag: 'good_listener', n: 9 }, { tag: 'kind', n: 8 }] },
  u_arjun: { manner: 69, ratings: 12, tags: [{ tag: 'funny', n: 6 }, { tag: 'kind', n: 4 }] },
  u_chloe: { manner: 74, ratings: 6, tags: [{ tag: 'respectful', n: 3 }] },
  u_omar: { manner: 41, ratings: 5, tags: [] },
  u_riya: { manner: 90, ratings: 4, tags: [{ tag: 'kind', n: 3 }] },
}

/** People of the demo's rooms: a steady made-up badge per id. */
function simTrust(id: string): Trust {
  let h = 0
  for (const ch of id) h = (h * 31 + ch.charCodeAt(0)) | 0
  h = Math.abs(h)
  return { id, verified: h % 3 !== 0, manner: 48 + (h % 47), ratings: h % 22, tags: h % 22 >= 3 ? [{ tag: GOOD_TAGS[h % 5], n: 2 + (h % 6) }] : [] }
}

export interface TrustHost {
  flags: { required: boolean; kycOff: boolean; manner: number | null }
  me: () => Profile | null
  verified: (id: string) => boolean
  setMe: (p: Profile) => void
  match: (id: string) => { kind: 'random' | 'thread'; partnerId: string; messages: Message[] } | null
}

export class TrustSim {
  private kyc: { result: KycResult; pendingUntil: number; tries: number[]; at: number } = { result: 'none', pendingUntil: 0, tries: [], at: 0 }
  private mine: { manner: number; ratings: number }
  private rated = new Map<string, number>()
  /** The demo people's and room people's trust, made on first sight. */
  private people = new Map<string, Trust>()

  constructor(private host: TrustHost) {
    this.mine = { manner: host.flags.manner ?? 50, ratings: host.flags.manner === null ? 0 : 9 }
  }

  private person(id: string): Trust {
    let t = this.people.get(id)
    if (!t) {
      const known = TRUST[id]
      // The demo people's verified flag lives on their profiles (lib/mockBackend.ts), so posts show it without this chunk.
      t = known ? { id, verified: this.host.verified(id), ...known, tags: known.tags.map((x) => ({ ...x })) } : simTrust(id)
      this.people.set(id, t)
    }
    return t
  }

  private myNow(): Trust {
    const me = this.host.me()
    const tags: Trust['tags'] = this.mine.ratings >= 3 && this.mine.manner >= 40 ? [{ tag: 'kind', n: 4 }, { tag: 'funny', n: 2 }] : []
    return { id: me?.id ?? 'u_me', verified: !!me?.verified, manner: this.mine.manner, ratings: this.mine.ratings, tags }
  }

  async myTrust(): Promise<MyTrust> {
    await wait(200)
    this.settle()
    const k = this.kyc
    return {
      ...this.myNow(),
      required: this.host.flags.required,
      kyc: k.result === 'none' ? null : { status: k.result === 'pending' ? 'In Review' : k.result === 'verified' ? 'Approved' : 'Declined', result: k.result, at: k.at },
      rated: [...this.rated].filter(([, at]) => Date.now() - at < DAY).map(([id]) => id),
    }
  }

  async trust(ids: string[]): Promise<Trust[]> {
    await wait(120)
    const me = this.host.me()
    return [...new Set(ids)].slice(0, 100).map((id) => (me && id === me.id ? this.myNow() : { ...this.person(id) }))
  }

  async kycStart(): Promise<string> {
    await wait(600)
    if (this.host.flags.kycOff) throw new BackendError('kyc_unavailable')
    if (this.host.me()?.verified) throw new BackendError('already_verified')
    const now = Date.now()
    this.kyc.tries = this.kyc.tries.filter((t) => now - t < DAY)
    if (this.kyc.tries.length >= 5) throw new BackendError('slow_down')
    this.kyc.tries.push(now)
    return 'demo:kyc'
  }

  /** A "still checking" result turns into approved after a few seconds. */
  private settle() {
    if (this.kyc.result === 'pending' && this.kyc.pendingUntil && Date.now() > this.kyc.pendingUntil) this.apply('verified')
  }

  private apply(result: KycResult) {
    this.kyc = { ...this.kyc, result, at: Date.now(), pendingUntil: result === 'pending' ? Date.now() + 5000 : 0 }
    // The document's facts replace what was typed at sign-up (here: the same gender, age and country).
    const me = this.host.me()
    if (result === 'verified' && me) this.host.setMe({ ...me, verified: true })
  }

  async kycDemo(outcome: KycDemoOutcome) {
    await wait(400)
    this.apply(outcome)
  }

  async kycCheck(): Promise<KycResult> {
    await wait(500)
    if (this.host.flags.kycOff) throw new BackendError('kyc_unavailable')
    this.settle()
    return this.host.me()?.verified ? 'verified' : this.kyc.result
  }

  async rateChat(matchId: string, good: boolean, tags: string[]) {
    await wait(350)
    const m = this.host.match(matchId)
    if (!m || m.kind !== 'random') throw new BackendError('not_found')
    if (!m.messages.some((x) => x.fromMe) || !m.messages.some((x) => !x.fromMe)) throw new BackendError('not_allowed')
    if (checkRating(good, tags)) throw new BackendError('bad_request')
    if (this.rated.has(matchId)) throw new BackendError('already_rated')
    this.rated.set(matchId, Date.now())
    const t = this.person(m.partnerId)
    // Roughly what manner_refresh does: each rating moves the score less as ratings add up; a bad one weighs double.
    t.manner = Math.round(good ? t.manner + (100 - t.manner) / (t.ratings + 10) : t.manner - (2 * t.manner) / (t.ratings + 10))
    t.ratings++
    if (!good) return
    for (const tag of tags) {
      const x = t.tags.find((y) => y.tag === tag)
      if (x) x.n++
      else t.tags.push({ tag: tag as Trust['tags'][number]['tag'], n: 1 })
    }
    t.tags = t.tags.sort((a, b) => b.n - a.n).slice(0, 3)
  }
}
