"""Tiny real-API driver for the local flow journeys.

Run from an output directory OUTSIDE the repository (Run-FlowSmoke.ps1 does this). Tokens are read from
./tokens.json and never printed; evidence/state JSON files are written to the same directory.
"""
import json, os, sys, urllib.request, urllib.error

ROOT = os.getcwd()
BASE = os.environ.get('RH_BASE', 'http://127.0.0.1:5173')
TOKENS = json.load(open(os.path.join(ROOT, 'tokens.json'), encoding='utf-8-sig'))
_DOCS = None
LOG = []


def call(role, method, path, body=None, expect=None):
    headers = {'Accept': 'application/json'}
    if role:
        headers['Authorization'] = 'Bearer ' + TOKENS[role]['token']
    data = None
    if body is not None:
        data = json.dumps(body).encode()
        headers['Content-Type'] = 'application/json'
    req = urllib.request.Request(BASE + path, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req) as r:
            status, raw = r.status, r.read()
    except urllib.error.HTTPError as e:
        status, raw = e.code, e.read()
    try:
        payload = json.loads(raw) if raw else None
    except ValueError:
        payload = raw.decode(errors='replace')[:300]
    ok = expect is None or status == expect or (isinstance(expect, (list, tuple)) and status in expect)
    LOG.append({'role': role, 'method': method, 'path': path, 'status': status, 'expect': expect, 'pass': ok})
    mark = 'PASS' if ok else 'FAIL'
    print(f'[{mark}] {role or "-"} {method} {path} -> {status}')
    if not ok:
        print('   ', json.dumps(payload)[:600])
    return status, (payload.get('data') if isinstance(payload, dict) and 'data' in payload else payload)


def docs():
    global _DOCS
    if _DOCS is None:
        be = os.environ.get('RH_BE_BASE', 'http://127.0.0.1:8080')
        with urllib.request.urlopen(be + '/v3/api-docs') as r:
            _DOCS = json.loads(r.read())
    return _DOCS


def schema(path, method='post'):
    DOCS = docs()
    op = DOCS['paths'][path][method]
    ref = op.get('requestBody', {}).get('content', {}).get('application/json', {}).get('schema', {})
    return resolve(ref)


def resolve(s, depth=0):
    if '$ref' in s:
        name = s['$ref'].split('/')[-1]
        s = docs()['components']['schemas'][name]
    if depth > 3:
        return s.get('type')
    if s.get('type') == 'object' or 'properties' in s:
        req = set(s.get('required', []))
        return {k + ('*' if k in req else ''): resolve(v, depth + 1) for k, v in s.get('properties', {}).items()}
    if s.get('type') == 'array':
        return [resolve(s.get('items', {}), depth + 1)]
    if 'enum' in s:
        return 'enum' + str(s['enum'])
    return s.get('format') or s.get('type')


def save(name):
    with open(os.path.join(ROOT, name), 'w', encoding='utf-8') as f:
        json.dump({'requests': len(LOG), 'pass': sum(x['pass'] for x in LOG), 'log': LOG}, f, indent=1)
    print(f'SUMMARY {sum(x["pass"] for x in LOG)}/{len(LOG)} PASS -> {name}')


if __name__ == '__main__':
    for p in sys.argv[1:]:
        m, path = p.split(' ', 1)
        print(m, path, json.dumps(schema(path, m.lower()), indent=1))
