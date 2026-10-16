import { execFileSync } from 'node:child_process';

export class Blocked extends Error {
  constructor(message) { super(message); this.name = 'Blocked'; }
}

export function required(name, env = process.env) {
  const value = env[name]?.trim();
  if (!value) throw new Blocked(`Missing ${name}`);
  return value;
}

export function testUrl(name, env = process.env) {
  const raw = required(name, env);
  let url;
  try { url = new URL(raw); } catch { throw new Blocked(`${name} is not an absolute URL`); }
  if (url.username || url.password || url.search || url.hash || url.pathname !== '/')
    throw new Blocked(`${name} must be an origin without credentials, path, query or fragment`);
  if (url.protocol !== 'https:' && !(url.protocol === 'http:' && ['localhost', '127.0.0.1', '[::1]'].includes(url.hostname)))
    throw new Blocked(`${name} requires HTTPS or loopback HTTP`);
  return url.origin;
}

export function positiveId(name, env = process.env) {
  const value = required(name, env);
  if (!/^[1-9]\d*$/.test(value) || !Number.isSafeInteger(Number(value)))
    throw new Blocked(`${name} must be a positive safe integer`);
  return value;
}

export function source(repo, ref, file) {
  // Scope Git's ownership exception to this read-only command; never edit global config.
  return execFileSync('git', ['-c', `safe.directory=${repo}`, '-C', repo, 'show', `${ref}:${file}`], { encoding: 'utf8', timeout: 10000 });
}

export function operations(yaml) {
  const found = new Map();
  let path;
  for (const line of yaml.split(/\r?\n/)) {
    const p = /^  (\/api\/[^:]+):\s*$/.exec(line);
    if (p) path = p[1];
    const m = /^    (get|post|put|patch|delete):\s*$/.exec(line);
    if (m && path) found.set(`${m[1].toUpperCase()} ${path}`, true);
  }
  return found;
}

export function controllerOperations(java) {
  const found = new Map();
  for (const m of java.matchAll(/@(Get|Post|Put|Patch|Delete)Mapping\("(\/api\/[^\"]+)"\)/g))
    found.set(`${m[1].toUpperCase()} ${m[2]}`, true);
  return found;
}

export function envelope(body, success) {
  if (!body || typeof body !== 'object' || body.success !== success) return false;
  return success ? Object.hasOwn(body, 'data') : Boolean(body.error && typeof body.error.code === 'string' && typeof body.error.message === 'string');
}

export function classifyHttp(status, expected) {
  if ([404, 501, 502, 503, 504].includes(status)) return 'BLOCKED';
  return status === expected ? 'PASS' : 'FAIL';
}

export function assertTestTarget(env = process.env) {
  if (env.CORE037_TEST_ENV !== 'isolated-test')
    throw new Blocked('CORE037_TEST_ENV must equal isolated-test');
  return testUrl('CORE037_BACKEND_URL', env);
}
