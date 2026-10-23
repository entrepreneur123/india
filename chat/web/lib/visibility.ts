/** True while the browser tab is in the background (polling and heartbeats pause then, to save data and battery). */
export function pageHidden() {
  return typeof document !== 'undefined' && document.visibilityState === 'hidden'
}

/** Calls `fn(visible)` whenever the tab is hidden or shown again; returns the unsubscribe function. */
export function onPageVisibility(fn: (visible: boolean) => void) {
  const handler = () => fn(!pageHidden())
  document.addEventListener('visibilitychange', handler)
  return () => document.removeEventListener('visibilitychange', handler)
}
