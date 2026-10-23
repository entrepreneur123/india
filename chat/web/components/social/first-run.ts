// First-run steps (interests, then the tour), each shown once per browser.
export const TOUR_KEY = 'yaari_tour'
export const INTERESTS_KEY = 'yaari_interests_asked'

export const read = (k: string) => {
  try {
    return localStorage.getItem(k)
  } catch {
    return '1' // storage blocked: never nag
  }
}

export const write = (k: string) => {
  try {
    localStorage.setItem(k, '1')
  } catch {
    /* the choice lasts for this visit */
  }
}

/** Whether the first-run steps still have something to show (the shell loads them only then). */
export function onboardingDue(hasInterests: boolean) {
  return read(TOUR_KEY) === null || (!hasInterests && read(INTERESTS_KEY) === null)
}
