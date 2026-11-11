import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiRequest, ApiError, resetCsrf } from './http'

afterEach(() => { vi.unstubAllGlobals(); resetCsrf() })
describe('HTTP contract', () => {
  it('accepts 204 and an empty success body', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(new Response(null, { status: 204 })).mockResolvedValueOnce(new Response('')))
    expect(await apiRequest('/auth/me')).toBeUndefined()
    expect(await apiRequest('/auth/me')).toBeUndefined()
  })
  it.each(['<html>Bad gateway</html>', '{broken'])('handles non-JSON problem responses safely', async (body) => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(body, { status: 502, headers: { 'X-Request-ID': 'trace-1' } })))
    await expect(apiRequest('/company')).rejects.toMatchObject({ status: 502, code: 'HTTP_ERROR', requestId: 'trace-1' })
  })
  it('preserves structured problem details', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: 'DEMO_EXPIRED', requestId: 'trace-2', detail: 'Expired' }), { status: 401 })))
    await expect(apiRequest('/company')).rejects.toMatchObject({ code: 'DEMO_EXPIRED', requestId: 'trace-2', message: 'Expired' })
  })
  it('uses the server CSRF header and refreshes it after authentication', async () => {
    const fetch = vi.fn().mockResolvedValueOnce(Response.json({ token: 'before', headerName: 'X-CSRF-TOKEN' }))
      .mockResolvedValueOnce(Response.json({ ok: true }))
      .mockResolvedValueOnce(Response.json({ token: 'after', headerName: 'X-CSRF-TOKEN' }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetch)
    await apiRequest('/auth/login', { method: 'POST', body: '{}' })
    await apiRequest('/auth/logout', { method: 'POST' })
    expect(fetch.mock.calls.map(([path]) => path)).toEqual(['/api/v1/auth/csrf', '/api/v1/auth/login', '/api/v1/auth/csrf', '/api/v1/auth/logout'])
    expect(new Headers(fetch.mock.calls[1][1].headers).get('X-CSRF-TOKEN')).toBe('before')
    expect(new Headers(fetch.mock.calls[3][1].headers).get('X-CSRF-TOKEN')).toBe('after')
  })
  it('forwards cancellation and rejects a late response after abort', async () => {
    const controller = new AbortController()
    vi.stubGlobal('fetch', vi.fn().mockImplementation(async (_url, options) => {
      expect(options.signal).toBe(controller.signal)
      controller.abort()
      return Response.json({ private: true })
    }))
    await expect(apiRequest('/company', { signal: controller.signal })).rejects.toMatchObject({ name: 'AbortError' })
  })
  it('does not expose a malformed successful payload as typed data', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('<html>proxy fallback</html>')))
    await expect(apiRequest('/company')).rejects.toBeInstanceOf(ApiError)
  })
})
