export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message = 'The request could not be completed. Please try again.',
    readonly requestId?: string,
    readonly fieldErrors?: unknown,
  ) { super(message); this.name = 'ApiError' }
}

interface CsrfToken { token: string; headerName: string }
let csrf: CsrfToken | undefined
let csrfGeneration = 0

export function resetCsrf() { csrf = undefined; csrfGeneration++ }

export interface ApiResponse<T> {
  data: T
  status: number
  headers: Headers
}

// Keep the token in memory. Cookies and authentication remain browser/server owned.
async function getCsrf(signal?: AbortSignal | null) {
  if (csrf) return csrf
  const generation = csrfGeneration
  const token = await apiRequest<CsrfToken>('/auth/csrf', { signal })
  if (!token?.token || token.headerName !== 'X-CSRF-TOKEN') {
    throw new ApiError(0, 'INVALID_RESPONSE', 'The session could not be secured. Please try again.')
  }
  if (generation !== csrfGeneration) throw new DOMException('Session changed', 'AbortError')
  csrf = token
  return token
}

export async function apiRequest<T>(path: string, options: RequestInit = {}): Promise<T> {
  return (await apiRequestWithMetadata<T>(path, options)).data
}

/** Same-origin API request with response metadata for flows that must verify HTTP headers. */
export async function apiRequestWithMetadata<T>(path: string, options: RequestInit = {}): Promise<ApiResponse<T>> {
  // Prevent accidentally sending session/CSRF material to a different origin.
  if (!/^\/[a-zA-Z0-9][^\\]*$/.test(path) || path.includes('://')) throw new Error('Use a relative API path')
  const method = (options.method ?? 'GET').toUpperCase()
  const write = !['GET', 'HEAD', 'OPTIONS'].includes(method)
  const headers = new Headers(options.headers)
  headers.set('Accept', 'application/json, application/problem+json')
  if (options.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  if (write) {
    const token = await getCsrf(options.signal)
    headers.set(token.headerName, token.token)
  }
  options.signal?.throwIfAborted()
  const response = await fetch(`/api/v1${path}`, { ...options, method, headers, credentials: 'same-origin', cache: 'no-store' })
  options.signal?.throwIfAborted()
  const text = response.status === 204 ? '' : await response.text()
  options.signal?.throwIfAborted()
  let data: unknown
  try { data = text ? JSON.parse(text) : undefined } catch { /* Proxy errors may contain HTML. Never display that body. */ }
  if (!response.ok) {
    const problem = data && typeof data === 'object' ? data as Record<string, unknown> : {}
    if (response.status === 401 || problem.code === 'CSRF_INVALID') resetCsrf()
    throw new ApiError(response.status, typeof problem.code === 'string' ? problem.code : 'HTTP_ERROR',
      typeof problem.detail === 'string' ? problem.detail : undefined,
      typeof problem.requestId === 'string' ? problem.requestId : response.headers.get('X-Request-ID') ?? undefined,
      problem.fieldErrors)
  }
  if (write && ['/auth/login', '/auth/logout', '/demo/sessions'].includes(path)) resetCsrf()
  if (text && data === undefined) throw new ApiError(response.status, 'INVALID_RESPONSE', 'The server returned an unreadable response. Please try again.')
  return { data: data as T, status: response.status, headers: response.headers }
}
