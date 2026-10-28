// The first-run tour, shown once per browser.
export const TOUR_KEY = 'yaari_tour'

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

/** Whether the tour is still to be shown (the shell loads it only then). */
export const onboardingDue = () => read(TOUR_KEY) === null
