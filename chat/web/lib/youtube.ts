// "Listen together" music: YouTube links and the official IFrame player API (embedded from youtube-nocookie.com).
// The API script comes from https://www.youtube.com (iframe_api, which loads /s/player/…/www-widgetapi.js);
// vercel.json and scripts/csp-hashes.mjs allow exactly that origin.

/** The 11-character video id of a YouTube link (watch?v=, youtu.be/, shorts/, embed/, live/, music.youtube.com). */
export function youTubeId(input: string): string | null {
  const text = input.trim()
  if (!text) return null
  let url: URL
  try {
    url = new URL(/^https?:\/\//i.test(text) ? text : `https://${text}`)
  } catch {
    return null
  }
  const host = url.hostname.toLowerCase().replace(/^(www|m|music)\./, '')
  let id: string | null | undefined = null
  if (host === 'youtu.be') id = url.pathname.split('/')[1]
  else if (host === 'youtube.com' || host === 'youtube-nocookie.com')
    id = url.pathname === '/watch' ? url.searchParams.get('v') : url.pathname.match(/^\/(?:shorts|embed|live|v)\/([^/?#]+)/)?.[1]
  return id && /^[\w-]{11}$/.test(id) ? id : null
}

/**
 * The video's real title, from YouTube's oEmbed endpoint (CORS-enabled; connect-src allows www.youtube.com).
 * Empty when it can't be looked up (offline, a private video): the apps then show "YouTube video".
 */
export async function videoTitle(videoId: string, timeoutMs = 5000): Promise<string> {
  const ctl = new AbortController()
  const timer = setTimeout(() => ctl.abort(), timeoutMs)
  try {
    const watch = encodeURIComponent(`https://www.youtube.com/watch?v=${videoId}`)
    const res = await fetch(`https://www.youtube.com/oembed?url=${watch}&format=json`, { signal: ctl.signal, credentials: 'omit' })
    if (!res.ok) return ''
    const j = (await res.json()) as { title?: unknown }
    // The server keeps up to 100 characters.
    return typeof j.title === 'string' ? j.title.trim().slice(0, 100) : ''
  } catch {
    return ''
  } finally {
    clearTimeout(timer)
  }
}

export const thumbnail = (videoId: string) => `https://i.ytimg.com/vi/${videoId}/mqdefault.jpg`

/** The part of the IFrame API this app uses. */
export interface YTPlayer {
  playVideo(): void
  pauseVideo(): void
  seekTo(seconds: number, allowSeekAhead: boolean): void
  loadVideoById(o: { videoId: string; startSeconds?: number }): void
  getCurrentTime(): number
  getPlayerState(): number
  getVideoData(): { title?: string; author?: string }
  getDuration(): number
  destroy(): void
}

export interface YTNamespace {
  Player: new (
    el: HTMLElement,
    o: {
      host: string
      videoId: string
      width?: string
      height?: string
      playerVars: Record<string, string | number>
      events: {
        onReady?: () => void
        onStateChange?: (e: { data: number }) => void
        onError?: (e: { data: number }) => void
      }
    },
  ) => YTPlayer
}

export const YT_ENDED = 0
export const YT_PLAYING = 1
export const YT_PAUSED = 2
export const YT_BUFFERING = 3

let api: Promise<YTNamespace> | null = null

/** Loads the IFrame API once, on the first track (never at page start). Fails after 12 s, e.g. when YouTube is blocked. */
export function loadYouTube(): Promise<YTNamespace> {
  api ??= new Promise<YTNamespace>((resolve, reject) => {
    const w = window as unknown as { YT?: YTNamespace & { loaded?: number }; onYouTubeIframeAPIReady?: () => void }
    if (w.YT?.Player && w.YT.loaded) return resolve(w.YT)
    const timer = setTimeout(() => reject(new Error('youtube_timeout')), 12_000)
    const previous = w.onYouTubeIframeAPIReady
    w.onYouTubeIframeAPIReady = () => {
      previous?.()
      clearTimeout(timer)
      resolve(w.YT!)
    }
    const script = document.createElement('script')
    script.src = 'https://www.youtube.com/iframe_api'
    script.async = true
    script.onerror = () => {
      clearTimeout(timer)
      script.remove()
      reject(new Error('youtube_unreachable'))
    }
    document.head.appendChild(script)
  }).catch((err: unknown) => {
    api = null // the next track tries again
    throw err
  })
  return api
}
