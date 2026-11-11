import assert from 'node:assert/strict'

const base = new URL(process.env.FLOWPAY_BASE_URL ?? 'http://127.0.0.1:8080')
assert(['127.0.0.1', 'localhost', '[::1]'].includes(base.hostname), 'Only run this check against local FlowPay.')
const email = process.env.FLOWPAY_BOOTSTRAP_EMAIL
const password = process.env.FLOWPAY_BOOTSTRAP_PASSWORD
assert(email && password, 'Configure local bootstrap credentials in .env before this check.')

function sessionClient(initialCookie = '') {
  let cookie = initialCookie
  return {
    get cookie() { return cookie },
    async request(path, options = {}) {
      const headers = new Headers(options.headers)
      if (cookie) headers.set('Cookie', cookie)
      const response = await fetch(new URL(path, base), { ...options, headers, redirect: 'manual', signal: AbortSignal.timeout(15_000) })
      for (const header of response.headers.getSetCookie()) {
        if (header.startsWith('SESSION=')) cookie = header.split(';')[0]
      }
      return response
    },
  }
}

const client = sessionClient()
assert.equal((await client.request('/api/v1/auth/me')).status, 401)
const csrfResponse = await client.request('/api/v1/auth/csrf')
assert.equal(csrfResponse.status, 200)
const cookieHeader = csrfResponse.headers.getSetCookie().find(header => header.startsWith('SESSION='))
assert(cookieHeader && /HttpOnly/i.test(cookieHeader) && /SameSite=Lax/i.test(cookieHeader), 'Expected protected local session cookie.')
const beforeLogin = client.cookie
const csrfBeforeLogin = await csrfResponse.json()
assert.equal(csrfBeforeLogin.headerName, 'X-CSRF-TOKEN')
assert.equal(csrfBeforeLogin.parameterName, '_csrf')
assert.equal((await client.request('/api/v1/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}' })).status, 403)

const login = await client.request('/api/v1/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json', [csrfBeforeLogin.headerName]: csrfBeforeLogin.token },
  body: JSON.stringify({ email, password }),
})
assert.equal(login.status, 200, 'Configured local operator must authenticate.')
assert(client.cookie && client.cookie !== beforeLogin, 'Authentication must rotate the session ID.')
const identity = await login.json()
assert.deepEqual(Object.keys(identity).sort(), ['company', 'user'])
assert.deepEqual(Object.keys(identity.user).sort(), ['createdAt', 'email', 'id'])
const normalizedEmail = email.replace(/^\p{White_Space}+|\p{White_Space}+$/gu, '').toLowerCase()
assert(identity.user.email === normalizedEmail, 'Unexpected authenticated operator.')
for (const instant of [identity.user.createdAt, identity.company.createdAt]) {
  assert(typeof instant === 'string' && instant.endsWith('Z') && !Number.isNaN(Date.parse(instant)), 'Expected a UTC instant in the session response.')
}
assert(identity.company.demo === false && identity.company.expiresAt === null, 'Expected the provisioned conventional company.')
assert(!JSON.stringify(identity).includes(password), 'Response must not contain the password.')
const me = await client.request('/api/v1/auth/me')
assert.equal(me.status, 200)
assert((await me.json()).company.id === identity.company.id, 'Session company changed unexpectedly.')
const company = await client.request('/api/v1/company?companyId=00000000-0000-4000-8000-000000000001')
assert.equal(company.status, 200)
assert((await company.json()).id === identity.company.id, 'Client-supplied company must not control identity.')

assert.equal((await sessionClient(beforeLogin).request('/api/v1/auth/me')).status, 401)
const invalidLogout = await client.request('/api/v1/auth/logout', { method: 'POST', headers: { [csrfBeforeLogin.headerName]: csrfBeforeLogin.token } })
assert.equal(invalidLogout.status, 403, 'Pre-login CSRF token must be rejected after authentication.')
const renewedCsrfResponse = await client.request('/api/v1/auth/csrf')
assert.equal(renewedCsrfResponse.status, 200)
const renewedCsrf = await renewedCsrfResponse.json()
const authenticatedCookie = client.cookie
const logout = await client.request('/api/v1/auth/logout', { method: 'POST', headers: { [renewedCsrf.headerName]: renewedCsrf.token } })
assert.equal(logout.status, 204)
assert.equal((await sessionClient(authenticatedCookie).request('/api/v1/auth/me')).status, 401)

console.log(JSON.stringify({ origin: base.origin, login: 'passed', ownCompany: 'passed', cookieFlags: 'passed', sessionRotation: 'passed', previousSessionRejected: 'passed', previousCsrfRejected: 'passed', logoutInvalidation: 'passed' }, null, 2))
