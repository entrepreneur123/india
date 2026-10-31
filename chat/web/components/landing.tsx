import { isRtl, LANGS, type FullDict } from '@/lib/i18n'

/**
 * What Yaari is, in one language, from the app's own strings: the page of each language (app/[lang]) and the first
 * paint of / for anyone not signed in (server-rendered, so search engines and a slow first visit get real text).
 */
export function Landing({ lang, t }: { lang: string; t: FullDict }) {
  const plain = (s: string) => s.replace(/[[\]]/g, '')
  return (
    <main lang={lang} dir={isRtl(lang as never) ? 'rtl' : 'ltr'} className="mx-auto flex min-h-dvh max-w-2xl flex-col gap-8 px-5 py-12 sm:py-20">
      <p className="text-sm font-semibold text-primary-text">yaari · {t['auth.badge']}</p>
      <h1 className="text-4xl leading-tight font-bold tracking-tight text-balance sm:text-5xl">{plain(t['auth.headline'])}</h1>
      <p className="text-lg leading-relaxed text-pretty text-muted-foreground">{t['auth.intro']}</p>
      <a href={`/?lang=${lang}`} className="w-fit rounded-full bg-primary px-8 py-3.5 text-lg font-semibold text-primary-foreground">
        {t['lobby.start']}
      </a>
      <section className="grid gap-4 sm:grid-cols-2">
        {(['lobby', 'rooms'] as const).map((k) => (
          <div key={k} className="rounded-2xl bg-muted p-5">
            <h2 className="text-lg font-bold">{t[`${k}.title`]}</h2>
            <p className="mt-1 text-muted-foreground">{t[`${k}.subtitle`]}</p>
          </div>
        ))}
      </section>
      <nav className="flex flex-wrap gap-x-4 gap-y-2 text-sm" aria-label="Languages">
        {LANGS.map((l) => (
          <a key={l.id} href={l.id === 'en' ? '/' : `/${l.id}`} lang={l.id} aria-current={l.id === lang ? 'page' : undefined}
            className={l.id === lang ? 'font-semibold' : 'text-muted-foreground hover:text-foreground'}>
            {l.native}
          </a>
        ))}
      </nav>
      <footer className="mt-auto flex gap-4 text-sm text-muted-foreground">
        <a href="/privacy.html">{t['settings.privacy']}</a>
        <a href="/terms.html">{t['settings.terms']}</a>
      </footer>
    </main>
  )
}
