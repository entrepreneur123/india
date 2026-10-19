import { Logo } from '@/components/ui/bits'

/** First paint (also the prerendered HTML) while the language file and the backend load. CSS only, no JS needed. */
export function Splash({ label = 'Loading yaari' }: { label?: string }) {
  return (
    <div className="theme-night grid min-h-dvh place-items-center bg-background text-foreground">
      <div className="animate-pulse motion-reduce:animate-none">
        <Logo />
      </div>
      <span className="sr-only" role="status">
        {label}
      </span>
    </div>
  )
}
