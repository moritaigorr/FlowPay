const baseUrl = process.env.FLOWPAY_PERF_HOST_BASE_URL ?? 'http://127.0.0.1:8080'
const email = process.env.FLOWPAY_PERF_EMAIL
const password = process.env.FLOWPAY_PERF_PASSWORD

if (!email || !password) throw new Error('Set the temporary R21 operator credentials in the process environment.')

const cookies = new Map()
function saveCookies(response) {
  for (const cookie of response.headers.getSetCookie()) {
    const pair = cookie.split(';', 1)[0]
    const separator = pair.indexOf('=')
    if (separator < 1) continue
    const name = pair.slice(0, separator)
    const value = pair.slice(separator + 1)
    if (!value) cookies.delete(name)
    else cookies.set(name, value)
  }
}

async function request(path, { method = 'GET', body, headers = {} } = {}) {
  const requestHeaders = new Headers({ Accept: 'application/json, application/problem+json', ...headers })
  if (body !== undefined) requestHeaders.set('Content-Type', 'application/json')
  if (cookies.size) requestHeaders.set('Cookie', [...cookies].map(([name, value]) => `${name}=${value}`).join('; '))
  const response = await fetch(`${baseUrl}/api/v1${path}`, {
    method,
    headers: requestHeaders,
    body: body === undefined ? undefined : JSON.stringify(body),
    redirect: 'manual',
  })
  saveCookies(response)
  return response
}

const initialCsrfResponse = await request('/auth/csrf')
if (initialCsrfResponse.status !== 200) throw new Error(`Initial CSRF read returned HTTP ${initialCsrfResponse.status}.`)
const initialCsrf = await initialCsrfResponse.json()
if (!initialCsrf.token || initialCsrf.headerName !== 'X-CSRF-TOKEN') throw new Error('Initial CSRF contract was incomplete.')

const loginResponse = await request('/auth/login', {
  method: 'POST',
  headers: { [initialCsrf.headerName]: initialCsrf.token },
  body: { email, password },
})
if (loginResponse.status !== 200) throw new Error(`Operator login returned HTTP ${loginResponse.status}.`)
const identity = await loginResponse.json()
if (identity.company.demo || cookies.size === 0) throw new Error('R21 requires an authenticated conventional synthetic tenant.')

const paymentsResponse = await request('/payments?page=0&size=100')
if (paymentsResponse.status !== 200) throw new Error(`Synthetic payment list returned HTTP ${paymentsResponse.status}.`)
const payments = await paymentsResponse.json()
const pending = payments.items.find(payment => payment.status === 'PENDING')
if (!pending) throw new Error('The seeded synthetic tenant has no pending payment for the detail route.')

process.stdout.write(JSON.stringify({
  cookieHeader: [...cookies].map(([name, value]) => `${name}=${value}`).join('; '),
  paymentId: pending.id,
}) + '\n')
