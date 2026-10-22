// The last my_trust().required seen on this browser, for the sign-up screen's wording (it can't ask the server
// before sign-in). On its own so the sign-up screen carries nothing else of the trust code.
const KEY = 'yaari_kyc_required'

export function requiredCached() {
  try {
    return localStorage.getItem(KEY) === '1'
  } catch {
    return false
  }
}

export function rememberRequired(on: boolean) {
  try {
    if (on) localStorage.setItem(KEY, '1')
    else localStorage.removeItem(KEY)
  } catch {
    /* blocked */
  }
}
