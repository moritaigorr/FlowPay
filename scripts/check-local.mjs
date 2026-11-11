import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const base = new URL(process.env.FLOWPAY_BASE_URL ?? 'http://127.0.0.1:8080')
assert(['127.0.0.1', 'localhost', '[::1]'].includes(base.hostname), 'This smoke check only targets a local environment.')
const passed = []
const request = (path, options) => fetch(new URL(path, base), { signal: AbortSignal.timeout(15_000), ...options })

const shell = await request('/payments')
assert.equal(shell.status, 200)
assert.match(await shell.text(), /id="root"/)
passed.push('SPA entry and direct route')

const health = await request('/actuator/health')
assert.equal(health.status, 200)
assert.deepEqual(await health.json(), { status: 'UP' })
passed.push('Bounded Java health through proxy')

const schema = await request('/api/openapi.yaml')
assert.equal(schema.status, 200)
assert.match(schema.headers.get('content-type'), /yaml/)
assert.equal(await schema.text(), await readFile(new URL('../docs/api/openapi.yaml', import.meta.url), 'utf8'))
passed.push('Canonical OpenAPI bytes through proxy')

const docs = await request('/api/docs')
assert.equal(docs.status, 200)
const docsHtml = await docs.text()
assert.match(docsHtml, /swagger-ui/)
for (const asset of [...docsHtml.matchAll(/(?:src|href)="(\/swagger-ui\/[^"#]+)"/g)].map(match => match[1])) {
  assert.equal((await request(asset)).status, 200, `Documentation asset ${asset}`)
}
const initializer = await request('/swagger-ui/flowpay-init.js')
const initializerSource = await initializer.text()
assert.match(initializerSource, /supportedSubmitMethods:\s*\[\]/)
assert.match(initializerSource, /validatorUrl:\s*null/)
passed.push('Read-only documentation and same-origin assets')

const legacyDocs = await request('/swagger-ui.html', { redirect: 'manual' })
assert.equal(legacyDocs.status, 302)
assert.match(legacyDocs.headers.get('location'), /\/api\/docs$/)
const missingAsset = await request('/swagger-ui/missing-flowpay-asset.css')
assert.equal(missingAsset.status, 404)
assert.match(missingAsset.headers.get('content-type'), /application\/problem\+json/)

for (const path of ['/api/v1/auth/me', '/api/v1/not-a-route']) {
  const denied = await request(path)
  assert.equal(denied.status, 401)
  assert.match(denied.headers.get('content-type'), /application\/problem\+json/)
  assert.match(denied.headers.get('cache-control'), /no-store/)
  assert.equal((await denied.json()).code, 'UNAUTHENTICATED')
}
assert.equal((await request('/actuator/env')).status, 404)
passed.push('API paths fail closed instead of returning SPA HTML')

for (const path of ['/api/v1/auth/login', '/api/v1/demo/sessions']) {
  let limited = false
  for (let attempt = 0; attempt < 9; attempt++) {
    const response = await request(path, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}' })
    if (response.status === 429) {
      assert(Number(response.headers.get('retry-after')) > 0)
      limited = true
      break
    }
    assert.equal(response.status, 403, 'Missing CSRF must prevent any entry mutation.')
  }
  assert(limited, `Expected bounded attempt limiting for ${path}`)
}
passed.push('CSRF denial and login/demo proxy attempt limits')
console.log(JSON.stringify({ base: base.origin, passed }, null, 2))
