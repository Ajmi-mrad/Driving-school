/**
 * Trigger a browser download for an in-memory {@link Blob}. DOM-only, so it
 * lives in `src/lib` (not `src/core`, which stays framework-agnostic for the
 * future React Native app).
 */
export function saveBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}
