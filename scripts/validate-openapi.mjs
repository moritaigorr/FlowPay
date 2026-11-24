import { readdir, readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const specPath = path.join(root, 'docs', 'openapi.json');
const sourceRoot = path.join(root, 'backend', 'src', 'main', 'java', 'com', 'flowpay');
const spec = JSON.parse(await readFile(specPath, 'utf8'));
const problems = [];

if (spec.openapi !== '3.1.0') problems.push('OpenAPI version must be 3.1.0');

function resolvePointer(pointer) {
  return pointer.slice(2).split('/').map(decodeURIComponent).reduce((value, key) => value?.[key], spec);
}

function checkReferences(value, location = 'document') {
  if (!value || typeof value !== 'object') return;
  if (typeof value.$ref === 'string' && value.$ref.startsWith('#/') && !resolvePointer(value.$ref)) {
    problems.push(`${location}: unresolved reference ${value.$ref}`);
  }
  for (const [key, child] of Object.entries(value)) checkReferences(child, `${location}.${key}`);
}

async function javaFiles(directory) {
  const files = [];
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    const target = path.join(directory, entry.name);
    if (entry.isDirectory()) files.push(...await javaFiles(target));
    else if (entry.name.endsWith('Controller.java')) files.push(target);
  }
  return files;
}

checkReferences(spec);
const documented = new Set();
for (const [route, pathItem] of Object.entries(spec.paths ?? {})) {
  for (const [method, operation] of Object.entries(pathItem)) {
    if (!['get', 'post', 'put', 'patch', 'delete'].includes(method)) continue;
    const operationName = `${method.toUpperCase()} ${route}`;
    documented.add(`${method} ${route}`);
    const pathParameters = new Set((operation.parameters ?? [])
      .filter(parameter => parameter.in === 'path')
      .map(parameter => parameter.name));
    for (const [, name] of route.matchAll(/\{([^}]+)\}/g)) {
      if (!pathParameters.has(name)) problems.push(`${operationName}: missing path parameter ${name}`);
    }
    for (const name of pathParameters) {
      if (!route.includes(`{${name}}`)) problems.push(`${operationName}: unused path parameter ${name}`);
    }
    if (['post', 'put', 'patch', 'delete'].includes(method)) {
      const securityText = JSON.stringify(operation.security ?? []);
      if (!securityText.includes('csrfCookie') || !securityText.includes('csrfHeader')) {
        problems.push(`${operationName}: unsafe operation does not declare both CSRF controls`);
      }
    }
    if (route.startsWith('/api/') && !route.startsWith('/api/auth/')) {
      const hasTenant = (operation.parameters ?? []).some(parameter =>
        parameter.in === 'header' && parameter.name === 'X-FlowPay-Tenant');
      if (!hasTenant) problems.push(`${operationName}: business operation does not declare tenant selection`);
    }
    if ((operation.parameters ?? []).some(parameter => parameter.name === 'Idempotency-Key') &&
        (!operation.responses?.['200'] || !operation.responses?.['201'])) {
      problems.push(`${operationName}: idempotent operation must document create (201) and replay (200)`);
    }
  }
}

const implemented = new Set();
for (const file of await javaFiles(sourceRoot)) {
  const source = await readFile(file, 'utf8');
  const base = source.match(/@RequestMapping\s*\(\s*"([^"]+)"\s*\)/)?.[1];
  if (!base) continue;
  const mappings = /@(Get|Post|Put|Delete|Patch)Mapping(?:\s*\(\s*"([^"]*)"\s*\))?/g;
  for (const [, verb, suffix = ''] of source.matchAll(mappings)) {
    const route = `${base}${suffix}`.replace(/\/{2,}/g, '/');
    implemented.add(`${verb.toLowerCase()} ${route}`);
  }
}

// Spring Security configures logout outside an MVC controller.
implemented.add('post /api/auth/logout');
for (const route of implemented) {
  if (!documented.has(route)) problems.push(`Implemented route missing from OpenAPI: ${route}`);
}
for (const route of documented) {
  if (!implemented.has(route)) problems.push(`OpenAPI route not found in controllers/security config: ${route}`);
}

if (problems.length) {
  console.error(problems.map(problem => `- ${problem}`).join('\n'));
  process.exitCode = 1;
} else {
  console.log(`OpenAPI valid: ${documented.size} operations match controller mappings; all references and path parameters resolve.`);
}
