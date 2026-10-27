// 51: how loud each person is for me in rooms (0-1, 1 by default), remembered in this browser for every room.
// Storage can be blocked: then it lasts for the visit. At most 200 people are kept (the latest changes).
const KEY = 'yaari_voice_volumes'
const MAX = 200
let cache: Map<string, number> | null = null

function load(): Map<string, number> {
  if (cache) return cache
  try {
    const raw = JSON.parse(localStorage.getItem(KEY) ?? '{}') as Record<string, unknown>
    cache = new Map(Object.entries(raw).filter((e): e is [string, number] => typeof e[1] === 'number' && e[1] >= 0 && e[1] <= 1))
  } catch {
    cache = new Map()
  }
  return cache
}

export const volumeOf = (userId: string) => load().get(userId) ?? 1
export const savedVolumes = () => [...load()]

export function saveVolume(userId: string, volume: number) {
  const m = load()
  m.delete(userId)
  if (volume < 1) m.set(userId, volume)
  while (m.size > MAX) m.delete(m.keys().next().value!)
  try {
    localStorage.setItem(KEY, JSON.stringify(Object.fromEntries(m)))
  } catch {
    /* blocked: lasts for this visit */
  }
}
