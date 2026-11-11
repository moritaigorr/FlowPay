export function updateListFilters(current: URLSearchParams, changes: Record<string, string>) {
  const next = new URLSearchParams(current)
  for (const [key, value] of Object.entries(changes)) {
    if (value) next.set(key, value)
    else next.delete(key)
  }
  next.set('page', '0')
  return next
}
