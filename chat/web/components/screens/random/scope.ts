// Lobby choices remembered in this browser: everyone or my country only (055), anonymous or with my profile (070).
// Storage can be blocked, then the choice lasts for the visit.
export type LobbyFlag = 'yaari_meet_global' | 'yaari_meet_anon'

export function savedFlag(key: LobbyFlag): boolean {
  try {
    return localStorage.getItem(key) === '1'
  } catch {
    return false
  }
}
export function saveFlag(key: LobbyFlag, on: boolean) {
  try {
    localStorage.setItem(key, on ? '1' : '0')
  } catch {
    /* blocked: lasts for this visit */
  }
}
