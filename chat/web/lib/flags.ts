// Choices remembered in this browser: everyone or my country only (055), photos and videos from others blurred until
// tapped (off unless chosen, the owner 2026-10-27). Storage can be blocked, then the choice lasts for the visit.
export type Flag = 'yaari_meet_global' | 'yaari_blur_media'

export function savedFlag(key: Flag): boolean {
  try {
    return localStorage.getItem(key) === '1'
  } catch {
    return false
  }
}
export function saveFlag(key: Flag, on: boolean) {
  try {
    localStorage.setItem(key, on ? '1' : '0')
  } catch {
    /* blocked: lasts for this visit */
  }
}
