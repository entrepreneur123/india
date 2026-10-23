'use client'

// The moderation console at /admin (migration 021): the reports queue with evidence, ban / unban, the auto
// nudity flags, and a dashboard. Every action is checked and written down by the server (admin_* functions);
// this page only shows what they return. Labels are Korean with the English term where it helps.
import { createClient, type SupabaseClient } from '@supabase/supabase-js'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { DEMO_MODE, SUPABASE_ANON_KEY, SUPABASE_URL } from '@/lib/config'

type Card = {
  id: string; name: string; gender?: string; age?: number; city?: string; bio?: string; photo_path: string | null
  country?: string | null; created_at?: string; banned_until: string | null
}
type ReportRow = {
  id: number; created_at: string; reason: string; reviewed: boolean; outcome: string | null; reviewed_at: string | null
  match_id: string | null; reporter: { id: string; name: string } | null; reported: Card | null; open_against: number; has_evidence: boolean
}
type Line = { by?: string; room?: string; body: string | null; media: string | null; at: string }
type ReportDetail = Omit<ReportRow, 'reporter' | 'reported' | 'open_against' | 'has_evidence'> & {
  reviewed_by: string | null
  evidence: { name?: string; source?: string; chat?: Line[]; voice?: Line[]; rooms?: Line[] }
  reporter: Card | null; reported: Card | null; reports_24h: number; reports_total: number
  history: { id: number; reason: string; created_at: string; reviewed: boolean; outcome: string | null }[]
  actions: { action: string; at: string; by: string | null; detail: Record<string, unknown> | null }[]
}
type Dashboard = {
  reports_24h: Record<string, number>; reports_7d: Record<string, number>; unreviewed: number; active_rooms: number
  in_rooms: number; searching: number; in_chats: number; banned: number; errors_24h: number
  top_errors: { name: string; message: string; n: number; users: number; last: string; platform: string | null; version: string | null }[]
  events_24h: Record<string, number>
  recent_actions: { action: string; at: string; report: number | null; target: string | null; by: string | null }[]
}

const REASONS: Record<string, string> = {
  nudity: '노출 (nudity)', minor: '미성년자 (minor)', harassment: '괴롭힘 (harassment)', fake: '가짜 계정 (fake)',
  other: '기타 (other)', auto_nudity: '자동 노출 감지 (auto)',
}
const FILTERS: [string, string][] = [
  ['unreviewed', '미검토'], ['all', '전체'], ['auto_nudity', '자동 노출 감지'], ['manual', '사용자 신고'],
  ['nudity', '노출'], ['minor', '미성년자'], ['harassment', '괴롭힘'], ['fake', '가짜 계정'], ['other', '기타'],
]
const OUTCOMES: Record<string, string> = { reviewed: '검토 완료', dismissed: '기각', banned: '정지' }
const ACTIONS: Record<string, string> = { reviewed: '검토 완료', dismiss: '기각', ban: '정지', unban: '정지 해제' }

const when = (ts: string | null | undefined) =>
  ts ? new Date(ts).toLocaleString('ko-KR', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' }) : '—'
const bannedText = (until: string | null) =>
  !until || Date.parse(until) < Date.now() ? null : until.startsWith('infinity') ? '영구 정지' : `${when(until)}까지 정지`
const errorOf = (e: unknown) => {
  const text = e && typeof e === 'object' && 'message' in e ? String((e as { message: unknown }).message) : String(e)
  return text.includes('not_allowed') ? '권한이 없습니다 (not_allowed)' : text
}

export function AdminConsole() {
  const client = useMemo<SupabaseClient | null>(
    () => (DEMO_MODE ? null : createClient(SUPABASE_URL, SUPABASE_ANON_KEY, { auth: { persistSession: true, storageKey: 'yaari_auth' } })),
    [],
  )
  const [stage, setStage] = useState<'loading' | 'signin' | 'denied' | 'ready'>('loading')

  const check = useCallback(async () => {
    if (!client) return setStage('denied')
    const { data } = await client.auth.getSession()
    if (!data.session) return setStage('signin')
    const { data: ok } = await client.rpc('is_admin')
    setStage(ok ? 'ready' : 'denied')
  }, [client])
  useEffect(() => void check(), [check])

  return (
    <div className="min-h-dvh bg-muted/40 text-foreground">
      <header className="sticky top-0 z-10 flex h-14 items-center gap-3 border-b border-border bg-background px-4 sm:px-6">
        <span className="text-lg font-bold">Yaari</span>
        <span className="text-sm text-muted-foreground">운영 콘솔 · Moderation</span>
        {stage === 'ready' && client && (
          <button className="ms-auto rounded-md px-3 py-1.5 text-sm text-muted-foreground hover:bg-muted"
            onClick={() => void client.auth.signOut().then(check)}>로그아웃</button>
        )}
      </header>
      {stage === 'loading' && <p className="p-6 text-sm text-muted-foreground">불러오는 중…</p>}
      {stage === 'signin' && client && <SignIn client={client} done={check} />}
      {stage === 'denied' && (
        <div className="mx-auto mt-16 max-w-sm rounded-xl border border-border bg-background p-6 text-center">
          <p className="font-semibold">권한이 없습니다</p>
          <p className="mt-1 text-sm text-muted-foreground">
            {DEMO_MODE ? '데모 빌드에는 운영 콘솔이 없습니다.' : '이 계정은 운영자(admins)가 아닙니다.'}
          </p>
          {client && (
            <button className="mt-4 text-sm text-primary-text underline" onClick={() => void client.auth.signOut().then(check)}>
              다른 계정으로 로그인
            </button>
          )}
        </div>
      )}
      {stage === 'ready' && client && <Console client={client} />}
    </div>
  )
}

function SignIn({ client, done }: { client: SupabaseClient; done: () => void }) {
  const [phone, setPhone] = useState('')
  const [code, setCode] = useState('')
  const [sent, setSent] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const digits = phone.replace(/\D/g, '')
  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    const { error: err } = sent
      ? await client.auth.verifyOtp({ phone: `+${digits}`, token: code.trim(), type: 'sms' })
      : await client.auth.signInWithOtp({ phone: `+${digits}` })
    setBusy(false)
    if (err) return setError(err.message)
    if (sent) done()
    else setSent(true)
  }
  return (
    <form onSubmit={submit} className="mx-auto mt-16 flex max-w-sm flex-col gap-3 rounded-xl border border-border bg-background p-6">
      <p className="font-semibold">운영자 로그인</p>
      <label className="text-sm text-muted-foreground" htmlFor="admin-phone">전화번호 (국가 번호 포함, 예: 919000000001)</label>
      <input id="admin-phone" className="rounded-md border border-border bg-background px-3 py-2" inputMode="tel" value={phone}
        onChange={(e) => setPhone(e.target.value)} disabled={sent} autoComplete="tel" />
      {sent && (
        <>
          <label className="text-sm text-muted-foreground" htmlFor="admin-code">인증 코드 (OTP)</label>
          <input id="admin-code" className="rounded-md border border-border bg-background px-3 py-2 tracking-widest" inputMode="numeric"
            autoComplete="one-time-code" value={code} onChange={(e) => setCode(e.target.value)} />
        </>
      )}
      {error && <p className="text-sm text-destructive" role="alert">{error}</p>}
      <button type="submit" disabled={busy || digits.length < 8 || (sent && code.trim().length < 6)}
        className="rounded-md bg-primary px-4 py-2 font-semibold text-primary-foreground disabled:opacity-50">
        {sent ? '로그인' : '코드 받기'}
      </button>
    </form>
  )
}

function Console({ client }: { client: SupabaseClient }) {
  const [tab, setTab] = useState<'reports' | 'dashboard'>('reports')
  return (
    <main className="mx-auto max-w-7xl p-4 sm:p-6">
      <nav className="mb-4 flex gap-1" aria-label="콘솔">
        {([['reports', '신고 (Reports)'], ['dashboard', '대시보드 (Dashboard)']] as const).map(([k, label]) => (
          <button key={k} onClick={() => setTab(k)} aria-current={tab === k ? 'page' : undefined}
            className={`rounded-full px-4 py-1.5 text-sm font-medium ${tab === k ? 'bg-primary-soft text-primary-text' : 'text-muted-foreground hover:bg-muted'}`}>
            {label}
          </button>
        ))}
      </nav>
      {tab === 'reports' ? <Reports client={client} /> : <DashboardView client={client} />}
    </main>
  )
}

/** Signed links for report photos (admins may read them, 021). */
function useSigned(client: SupabaseClient, paths: (string | null | undefined)[]) {
  const [urls, setUrls] = useState<Record<string, string>>({})
  const key = [...new Set(paths.filter((p): p is string => !!p))].sort().join('|')
  useEffect(() => {
    if (!key) return
    let alive = true
    void client.storage.from('media').createSignedUrls(key.split('|'), 600).then(({ data }) => {
      if (!alive || !data) return
      setUrls(Object.fromEntries(data.flatMap((d) => (d.signedUrl && d.path ? [[d.path, d.signedUrl]] : []))))
    })
    return () => {
      alive = false
    }
  }, [client, key])
  return urls
}

function Reports({ client }: { client: SupabaseClient }) {
  const [filter, setFilter] = useState('unreviewed')
  const [rows, setRows] = useState<ReportRow[] | null>(null)
  const [more, setMore] = useState(false)
  const [open, setOpen] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async (before?: number) => {
    const { data, error: err } = await client.rpc('admin_reports', { p_filter: filter, p_before: before ?? null, p_limit: 50 })
    if (err) return setError(errorOf(err))
    setError(null)
    const page = (data ?? []) as ReportRow[]
    setRows((prev) => (before && prev ? [...prev, ...page] : page))
    setMore(page.length === 50)
  }, [client, filter])
  useEffect(() => {
    setRows(null)
    void load()
  }, [load])
  const photos = useSigned(client, rows?.map((r) => r.reported?.photo_path) ?? [])

  return (
    <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.15fr)]">
      <section className="min-w-0">
        <div className="mb-3 flex flex-wrap gap-1.5" role="group" aria-label="필터">
          {FILTERS.map(([k, label]) => (
            <button key={k} onClick={() => setFilter(k)} aria-pressed={filter === k}
              className={`rounded-full border px-3 py-1 text-sm ${filter === k ? 'border-transparent bg-primary text-primary-foreground' : 'border-border bg-background hover:bg-muted'}`}>
              {label}
            </button>
          ))}
        </div>
        {error && <p className="mb-2 text-sm text-destructive" role="alert">{error}</p>}
        <div className="overflow-hidden rounded-xl border border-border bg-background">
          {rows === null && <p className="p-4 text-sm text-muted-foreground">불러오는 중…</p>}
          {rows?.length === 0 && <p className="p-4 text-sm text-muted-foreground">신고가 없습니다.</p>}
          <ul>
            {rows?.map((r) => (
              <li key={r.id}>
                <button onClick={() => setOpen(r.id)} data-report={r.id}
                  className={`flex w-full items-center gap-3 border-b border-border px-4 py-3 text-start last:border-0 hover:bg-muted/60 ${open === r.id ? 'bg-primary-soft' : ''}`}>
                  <Face url={r.reported?.photo_path ? photos[r.reported.photo_path] : undefined} name={r.reported?.name} />
                  <span className="min-w-0 flex-1">
                    <span className="flex items-center gap-2">
                      <span className="truncate font-medium">{r.reported?.name ?? '(탈퇴한 사용자)'}</span>
                      {bannedText(r.reported?.banned_until ?? null) && <Badge tone="red">{bannedText(r.reported?.banned_until ?? null)}</Badge>}
                    </span>
                    <span className="block truncate text-xs text-muted-foreground">
                      {REASONS[r.reason] ?? r.reason} · 신고자 {r.reporter?.name ?? '—'} · 미검토 {r.open_against}건
                    </span>
                  </span>
                  <span className="shrink-0 text-end text-xs text-muted-foreground">
                    {when(r.created_at)}
                    <span className="mt-1 block">
                      {r.reviewed ? <Badge>{OUTCOMES[r.outcome ?? ''] ?? '검토됨'}</Badge> : <Badge tone="blue">미검토</Badge>}
                    </span>
                  </span>
                </button>
              </li>
            ))}
          </ul>
          {more && rows && (
            <button className="w-full px-4 py-3 text-sm text-primary-text hover:bg-muted" onClick={() => void load(rows[rows.length - 1].id)}>
              더 보기
            </button>
          )}
        </div>
      </section>
      <section className="min-w-0">
        {open === null
          ? <p className="rounded-xl border border-dashed border-border p-6 text-sm text-muted-foreground">왼쪽에서 신고를 고르세요.</p>
          : <Detail key={open} client={client} id={open} changed={() => void load()} />}
      </section>
    </div>
  )
}

function Detail({ client, id, changed }: { client: SupabaseClient; id: number; changed: () => void }) {
  const [r, setR] = useState<ReportDetail | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [note, setNote] = useState('')
  const [days, setDays] = useState('7')
  const [busy, setBusy] = useState(false)
  const load = useCallback(async () => {
    const { data, error: err } = await client.rpc('admin_report', { p_id: id })
    if (err) return setError(errorOf(err))
    setR(data as ReportDetail)
  }, [client, id])
  useEffect(() => void load(), [load])
  const lines = useMemo(() => [...(r?.evidence.chat ?? []), ...(r?.evidence.voice ?? []), ...(r?.evidence.rooms ?? [])], [r])
  const urls = useSigned(client, [r?.reported?.photo_path, r?.reporter?.photo_path, ...lines.map((l) => l.media)])

  const act = async (fn: string, args: Record<string, unknown>, confirmText?: string) => {
    if (confirmText && !window.confirm(confirmText)) return
    setBusy(true)
    const { error: err } = await client.rpc(fn, args)
    setBusy(false)
    if (err) return setError(errorOf(err))
    setError(null)
    setNote('')
    await load()
    changed()
  }
  if (error && !r) return <p className="text-sm text-destructive" role="alert">{error}</p>
  if (!r) return <p className="text-sm text-muted-foreground">불러오는 중…</p>
  const ban = bannedText(r.reported?.banned_until ?? null)
  const resolve = (action: string, d: number | null = null, confirmText?: string) =>
    void act('admin_resolve', { p_id: r.id, p_action: action, p_days: d, p_note: note.trim() || null }, confirmText)

  return (
    <article className="flex flex-col gap-4 rounded-xl border border-border bg-background p-4 sm:p-5" aria-label={`신고 ${r.id}`}>
      <div className="flex flex-wrap items-center gap-2">
        <h2 className="text-lg font-bold">신고 #{r.id}</h2>
        <Badge tone={r.reason === 'auto_nudity' ? 'red' : undefined}>{REASONS[r.reason] ?? r.reason}</Badge>
        {r.reviewed ? <Badge>{OUTCOMES[r.outcome ?? ''] ?? '검토됨'} · {r.reviewed_by ?? ''} {when(r.reviewed_at)}</Badge> : <Badge tone="blue">미검토</Badge>}
        <span className="ms-auto text-xs text-muted-foreground">{when(r.created_at)}</span>
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        <Person title="신고된 사람 (Reported)" c={r.reported} url={r.reported?.photo_path ? urls[r.reported.photo_path] : undefined}
          extra={`최근 24시간 신고 ${r.reports_24h}건 · 전체 ${r.reports_total}건`} ban={ban} />
        <Person title="신고한 사람 (Reporter)" c={r.reporter} url={r.reporter?.photo_path ? urls[r.reporter.photo_path] : undefined} />
      </div>

      <div>
        <h3 className="mb-2 text-sm font-semibold">증거 (Evidence)</h3>
        {r.reason === 'auto_nudity' && (
          <p className="mb-2 rounded-md bg-muted px-3 py-2 text-sm">
            영상 통화 중 상대 기기의 모델이 노출을 감지했습니다 (source: {r.evidence.source ?? 'video_model'}). 영상은 저장되지 않습니다.
          </p>
        )}
        {lines.length === 0 && r.reason !== 'auto_nudity' && <p className="text-sm text-muted-foreground">저장된 대화가 없습니다.</p>}
        {lines.length > 0 && (
          <ol className="flex max-h-96 flex-col gap-1.5 overflow-y-auto rounded-md bg-muted/60 p-3 text-sm">
            {lines.map((l, i) => (
              <li key={i} className={`max-w-[85%] rounded-lg px-3 py-1.5 ${l.by === 'reporter' ? 'self-end bg-background' : 'bg-primary-soft'}`}>
                <span className="block text-[11px] text-muted-foreground">
                  {l.by === 'reporter' ? '신고자' : l.by === 'reported' ? '신고된 사람' : `방 ${l.room ?? ''}`} · {when(l.at)}
                </span>
                {l.body && <span className="whitespace-pre-wrap break-words">{l.body}</span>}
                {l.media && (urls[l.media]
                  ? <a href={urls[l.media]} target="_blank" rel="noreferrer"><img src={urls[l.media]} alt="첨부 사진" className="mt-1 max-h-48 rounded-md" /></a>
                  : <span className="block text-xs text-muted-foreground">(사진: 더 이상 없음)</span>)}
              </li>
            ))}
          </ol>
        )}
      </div>

      <div className="flex flex-col gap-2 rounded-md border border-border p-3">
        <h3 className="text-sm font-semibold">조치 (Actions)</h3>
        <input className="rounded-md border border-border bg-background px-3 py-2 text-sm" placeholder="메모 (선택, 500자 이내)" maxLength={500}
          value={note} onChange={(e) => setNote(e.target.value)} aria-label="메모" />
        {error && <p className="text-sm text-destructive" role="alert">{error}</p>}
        <div className="flex flex-wrap gap-2">
          <ActionButton disabled={busy} onClick={() => resolve('reviewed')}>검토 완료</ActionButton>
          <ActionButton disabled={busy} onClick={() => resolve('dismiss')}>기각 (문제 없음)</ActionButton>
          <span className="flex items-center gap-1">
            <select className="rounded-md border border-border bg-background px-2 py-1.5 text-sm" value={days} onChange={(e) => setDays(e.target.value)} aria-label="정지 기간">
              <option value="1">1일</option><option value="7">7일</option><option value="30">30일</option>
              <option value="365">1년</option><option value="forever">영구</option>
            </select>
            <ActionButton danger disabled={busy || !r.reported}
              onClick={() => resolve('ban', days === 'forever' ? null : Number(days),
                `${r.reported?.name ?? ''} 님을 ${days === 'forever' ? '영구' : `${days}일`} 정지할까요? 전화번호도 기억됩니다.`)}>
              정지 (Ban)
            </ActionButton>
          </span>
          {ban && r.reported && (
            <ActionButton disabled={busy} onClick={() => void act('admin_unban', { p_user: r.reported!.id, p_note: note.trim() || null }, '정지를 해제할까요?')}>
              정지 해제
            </ActionButton>
          )}
        </div>
      </div>

      {r.history.length > 0 && (
        <div>
          <h3 className="mb-1 text-sm font-semibold">같은 사람에 대한 다른 신고</h3>
          <ul className="text-sm">
            {r.history.map((h) => (
              <li key={h.id} className="flex gap-2 border-b border-border py-1.5 last:border-0">
                <span className="text-muted-foreground">#{h.id}</span><span>{REASONS[h.reason] ?? h.reason}</span>
                <span className="ms-auto text-xs text-muted-foreground">{when(h.created_at)} · {h.reviewed ? OUTCOMES[h.outcome ?? ''] ?? '검토됨' : '미검토'}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
      {r.actions.length > 0 && (
        <div>
          <h3 className="mb-1 text-sm font-semibold">조치 기록 (Audit)</h3>
          <ul className="text-sm">
            {r.actions.map((a, i) => (
              <li key={i} className="flex gap-2 py-1">
                <span>{ACTIONS[a.action] ?? a.action}</span>
                <span className="text-muted-foreground">{a.by ?? ''}</span>
                {a.detail?.note ? <span className="truncate text-muted-foreground">“{String(a.detail.note)}”</span> : null}
                <span className="ms-auto text-xs text-muted-foreground">{when(a.at)}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </article>
  )
}

function DashboardView({ client }: { client: SupabaseClient }) {
  const [d, setD] = useState<Dashboard | null>(null)
  const [error, setError] = useState<string | null>(null)
  useEffect(() => {
    void client.rpc('admin_dashboard').then(({ data, error: err }) => (err ? setError(errorOf(err)) : setD(data as Dashboard)))
  }, [client])
  if (error) return <p className="text-sm text-destructive" role="alert">{error}</p>
  if (!d) return <p className="text-sm text-muted-foreground">불러오는 중…</p>
  const tiles: [string, number][] = [
    ['미검토 신고', d.unreviewed], ['활성 방', d.active_rooms], ['방 접속자', d.in_rooms], ['매칭 대기', d.searching],
    ['채팅 중', d.in_chats], ['정지된 계정', d.banned], ['앱 오류 (24시간)', d.errors_24h],
  ]
  const reasons = [...new Set([...Object.keys(d.reports_7d), ...Object.keys(d.reports_24h)])]
  return (
    <div className="flex flex-col gap-4">
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 lg:grid-cols-7">
        {tiles.map(([label, n]) => (
          <div key={label} className="rounded-xl border border-border bg-background p-4">
            <p className="text-xs text-muted-foreground">{label}</p>
            <p className="mt-1 text-2xl font-bold tabular-nums">{n.toLocaleString('ko-KR')}</p>
          </div>
        ))}
      </div>
      <div className="grid gap-4 lg:grid-cols-2">
        <Panel title="사유별 신고">
          <table className="w-full text-sm">
            <thead><tr className="text-start text-muted-foreground"><th className="py-1 font-normal">사유</th><th className="font-normal">24시간</th><th className="font-normal">7일</th></tr></thead>
            <tbody>
              {reasons.length === 0 && <tr><td className="py-2 text-muted-foreground" colSpan={3}>없음</td></tr>}
              {reasons.map((k) => (
                <tr key={k} className="border-t border-border"><td className="py-1.5">{REASONS[k] ?? k}</td>
                  <td className="tabular-nums">{d.reports_24h[k] ?? 0}</td><td className="tabular-nums">{d.reports_7d[k] ?? 0}</td></tr>
              ))}
            </tbody>
          </table>
        </Panel>
        <Panel title="주요 지표 (24시간, events)">
          <ul className="text-sm">
            {Object.keys(d.events_24h).length === 0 && <li className="text-muted-foreground">없음</li>}
            {Object.entries(d.events_24h).sort((a, b) => b[1] - a[1]).map(([k, n]) => (
              <li key={k} className="flex border-t border-border py-1.5 first:border-0"><span>{k}</span><span className="ms-auto tabular-nums">{n}</span></li>
            ))}
          </ul>
        </Panel>
      </div>
      <Panel title="최근 앱 오류 (24시간, client errors)">
        <ul className="text-sm">
          {d.top_errors.length === 0 && <li className="text-muted-foreground">없음</li>}
          {d.top_errors.map((e, i) => (
            <li key={i} className="border-t border-border py-2 first:border-0">
              <span className="flex gap-2"><span className="font-medium">{e.name}</span>
                <span className="ms-auto shrink-0 text-xs text-muted-foreground">{e.n}회 · {e.users}명 · {e.platform ?? ''} {e.version ?? ''} · {when(e.last)}</span></span>
              <span className="block break-words text-muted-foreground">{e.message}</span>
            </li>
          ))}
        </ul>
      </Panel>
      <Panel title="최근 조치">
        <ul className="text-sm">
          {d.recent_actions.length === 0 && <li className="text-muted-foreground">없음</li>}
          {d.recent_actions.map((a, i) => (
            <li key={i} className="flex gap-2 border-t border-border py-1.5 first:border-0">
              <span>{ACTIONS[a.action] ?? a.action}</span><span className="text-muted-foreground">{a.target ?? ''}{a.report ? ` · 신고 #${a.report}` : ''}</span>
              <span className="ms-auto text-xs text-muted-foreground">{a.by ?? ''} · {when(a.at)}</span>
            </li>
          ))}
        </ul>
      </Panel>
    </div>
  )
}

function Panel({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="rounded-xl border border-border bg-background p-4">
      <h3 className="mb-2 text-sm font-semibold">{title}</h3>
      {children}
    </section>
  )
}

function Person({ title, c, url, extra, ban }: { title: string; c: Card | null; url?: string; extra?: string; ban?: string | null }) {
  return (
    <div className="flex gap-3 rounded-md border border-border p-3">
      {url ? <a href={url} target="_blank" rel="noreferrer"><Face url={url} name={c?.name} big /></a> : <Face name={c?.name} big />}
      <div className="min-w-0 text-sm">
        <p className="text-xs text-muted-foreground">{title}</p>
        <p className="font-semibold">{c?.name ?? '(탈퇴한 사용자)'}</p>
        {c && <p className="text-muted-foreground">{[c.gender, c.age, c.city, c.country].filter(Boolean).join(' · ')}</p>}
        {c?.bio && <p className="break-words">{c.bio}</p>}
        {c?.created_at && <p className="text-xs text-muted-foreground">가입 {when(c.created_at)}</p>}
        {extra && <p className="text-xs text-muted-foreground">{extra}</p>}
        {ban && <Badge tone="red">{ban}</Badge>}
      </div>
    </div>
  )
}

function Face({ url, name, big }: { url?: string; name?: string; big?: boolean }) {
  const size = big ? 'size-14' : 'size-9'
  return url
    ? <img src={url} alt="" className={`${size} shrink-0 rounded-full object-cover`} />
    : <span className={`${size} grid shrink-0 place-items-center rounded-full bg-muted font-semibold text-muted-foreground`}>{name?.[0] ?? '?'}</span>
}

function Badge({ children, tone }: { children: React.ReactNode; tone?: 'red' | 'blue' }) {
  const color = tone === 'red' ? 'bg-destructive/10 text-destructive' : tone === 'blue' ? 'bg-primary-soft text-primary-text' : 'bg-muted text-muted-foreground'
  return <span className={`inline-block rounded-full px-2 py-0.5 text-[11px] font-medium ${color}`}>{children}</span>
}

function ActionButton({ children, danger, ...props }: React.ButtonHTMLAttributes<HTMLButtonElement> & { danger?: boolean }) {
  return (
    <button {...props}
      className={`rounded-md px-3 py-1.5 text-sm font-medium disabled:opacity-50 ${danger ? 'bg-destructive text-white hover:opacity-90' : 'border border-border hover:bg-muted'}`}>
      {children}
    </button>
  )
}
