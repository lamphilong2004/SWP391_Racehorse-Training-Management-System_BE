import test from 'node:test';
import assert from 'node:assert/strict';
import { Blocked, assertTestTarget, classifyHttp, controllerOperations, envelope, operations, positiveId, testUrl } from './core037-lib.mjs';

test('test target fails closed for absent or unsafe configuration', () => {
  assert.throws(() => assertTestTarget({}), Blocked);
  assert.throws(() => assertTestTarget({ CORE037_TEST_ENV: 'isolated-test', CORE037_BACKEND_URL: 'http://production.example' }), Blocked);
  assert.equal(assertTestTarget({ CORE037_TEST_ENV: 'isolated-test', CORE037_BACKEND_URL: 'http://127.0.0.1:8080/' }), 'http://127.0.0.1:8080');
  assert.throws(() => testUrl('CORE037_BACKEND_URL', { CORE037_BACKEND_URL: 'https://user:secret@example.test/' }), Blocked);
  assert.throws(() => positiveId('X', { X: '0' }), Blocked);
});

test('contract operation parser compares real methods and paths', () => {
  const yaml = '  /api/incidents:\n    post:\n      operationId: reportIncident\n    get:\n';
  const java = '@PostMapping("/api/incidents")\n@GetMapping("/api/incidents")';
  assert.deepEqual([...operations(yaml).keys()], [...controllerOperations(java).keys()]);
});

test('unavailable endpoints cannot pass and envelopes must be real', () => {
  assert.equal(classifyHttp(404, 401), 'BLOCKED');
  assert.equal(classifyHttp(503, 200), 'BLOCKED');
  assert.equal(classifyHttp(403, 401), 'FAIL');
  assert.equal(envelope({ success: false, error: { code: 'UNAUTHORIZED', message: 'Denied' } }, false), true);
  assert.equal(envelope({ success: true }, true), false);
});
