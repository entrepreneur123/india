// Whom random chat meets (055): people of my country, or everyone. Remembered in this browser; storage can be
// blocked, then the choice lasts for the visit.
const KEY = 'yaari_meet_global'

export function savedGlobal(): boolean {
  try {
    return localStorage.getItem(KEY) === '1'
  } catch {
    return false
  }
}
export function saveGlobal(on: boolean) {
  try {
    localStorage.setItem(KEY, on ? '1' : '0')
  } catch {
    /* blocked: lasts for this visit */
  }
}
