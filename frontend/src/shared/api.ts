export function apiFetch(input: RequestInfo | URL, init?: RequestInit) {
  const url = typeof input === 'string' ? input : input instanceof URL ? input.pathname : input.url
  const headers = new Headers(init?.headers)
  const tenantId = window.localStorage.getItem('flowpay.activeTenantId')
  if (url.startsWith('/api/') && !url.startsWith('/api/auth/') && tenantId) {
    headers.set('X-FlowPay-Tenant', tenantId)
  }
  return fetch(input, { ...init, headers })
}
