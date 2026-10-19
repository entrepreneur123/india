/**
 * Loads a code chunk, retrying a failed download (a weak network) a couple of times before giving up. While the
 * browser is offline it waits for the connection to come back instead of failing at once.
 */
export function retryImport<T>(load: () => Promise<T>, tries = 3): Promise<T> {
  return load().catch(async (err: unknown) => {
    if (tries <= 1) throw err
    await new Promise<void>((resolve) => {
      if (navigator.onLine) setTimeout(resolve, 1500)
      else window.addEventListener('online', () => resolve(), { once: true })
    })
    return retryImport(load, tries - 1)
  })
}
