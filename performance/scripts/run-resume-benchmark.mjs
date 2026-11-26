import assert from 'node:assert/strict';
import { setTimeout as sleep } from 'node:timers/promises';

const base = 'http://127.0.0.1:18080';
const run = `BM-${Date.now().toString(36)}`;
const report = (metric, data) => console.log(JSON.stringify({ run, metric, ...data }));

async function request(path, { method = 'GET', body, token } = {}) {
  const headers = token ? { Authorization: `Bearer ${token}` } : {};
  if (body && !(body instanceof FormData)) {
    headers['Content-Type'] = 'application/json';
    body = JSON.stringify(body);
  }
  const response = await fetch(`${base}/api${path}`, { method, headers, body, signal: AbortSignal.timeout(60_000) });
  const text = await response.text();
  let data;
  try { data = JSON.parse(text); } catch { data = text; }
  return { status: response.status, data, path };
}

function expect(result, ...statuses) {
  assert.ok(statuses.includes(result.status), `${result.path}: HTTP ${result.status} ${result.path === '/auth/login' ? '' : JSON.stringify(result.data)}`);
  return result.data;
}

async function login(role) {
  const prefix = `FINGUARD_AUTH_BOOTSTRAP_${role}`;
  const username = process.env[`${prefix}_USERNAME`];
  const password = process.env[`${prefix}_PASSWORD`];
  assert.ok(username?.startsWith('bench-') && password, `${role}: synthetic benchmark credentials required`);
  const data = expect(await request('/auth/login', { method: 'POST', body: { username, password } }), 200);
  assert.ok(data.accessToken, 'Missing login token');
  return data.accessToken;
}

const admin = await login('ADMIN');
const reviewer = await login('REVIEWER');
const asAdmin = (path, options) => request(path, { ...options, token: admin });

async function account(suffix) {
  const accountNo = `${run}-${suffix}`;
  const { id } = expect(await asAdmin('/accounts', {
    method: 'POST', body: { accountNo, accountName: accountNo, accountType: 'BANK' },
  }), 200, 201);
  assert.ok(id, 'Missing account ID');
  return { id, accountNo };
}

function csv(target, rows) {
  return ['account_no,external_transaction_no,direction,amount,transaction_time,description',
    ...rows.map(({ key, amount = 100, description = 'benchmark' }) =>
      `${target.accountNo},${key},EXPENSE,${amount},2026-08-13 10:00:00,${description}`), ''].join('\n');
}

async function upload(contents, name, status = 202) {
  const body = new FormData();
  body.append('file', new Blob([contents], { type: 'text/csv' }), `${name}.csv`);
  return expect(await asAdmin('/import-jobs', { method: 'POST', body }), status);
}

function jobId(job) {
  const id = job.id ?? job.jobId ?? job.importJobId ?? job.reconciliationJobId;
  assert.ok(id, 'Missing job ID');
  return id;
}

async function terminal(path, statuses) {
  const deadline = performance.now() + 180_000;
  while (performance.now() < deadline) {
    const data = expect(await asAdmin(path), 200);
    if (statuses.includes(data.status)) return data;
    await sleep(150);
  }
  throw new Error(`Timed out: ${path}`);
}

const importDone = id => terminal(`/import-jobs/${id}`, ['SUCCESS', 'PARTIAL_SUCCESS', 'FAILED']);
const imported = async target => expect(await asAdmin(`/transactions?accountId=${target.id}&source=CSV_IMPORT&size=100`), 200);
const counts = job => Object.fromEntries(['id', 'status', 'totalRows', 'successRows', 'failedRows', 'duplicateRows'].map(field => [field, job[field]]));
const keys = (prefix, count) => Array.from({ length: count }, (_, index) => ({ key: `${run}-${prefix}-${index}` }));

if (process.argv[2] === 'fault-submit') {
  const target = await account('fault');
  const rows = keys('fault', 1);
  report('fault-submit', { accountId: target.id, id: jobId(await upload(csv(target, rows), 'fault')), key: rows[0].key });
} else if (process.argv[2] === 'fault-check') {
  const id = Number(process.argv[3]);
  const accountId = Number(process.argv[4]);
  assert.ok(id > 0 && accountId > 0);
  const result = await importDone(id);
  assert.equal(result.status, 'SUCCESS');
  assert.equal(result.successRows, 1);
  const page = await imported({ id: accountId });
  assert.equal(page.total, 1);
  report('fault-check', { id, accountId, job: counts(result), persistedRows: page.total });
} else {
  const throughput = await account('speed');
  for (const [sample, size] of [1000, 1000, 1000, 1000, 1000, 5000, 5000, 5000, 10000, 10000, 10000].entries()) {
    const contents = csv(throughput, keys(`speed${sample}`, size));
    const start = performance.now();
    const id = jobId(await upload(contents, `speed-${sample}`));
    const job = await importDone(id);
    const elapsedMs = Math.round(performance.now() - start);
    assert.equal(job.status, 'SUCCESS');
    assert.equal(job.successRows, size);
    assert.equal(job.failedRows, 0);
    assert.equal(job.duplicateRows, 0);
    report('csv-import', { id, warmup: sample < 2, rows: size, elapsedMs, rowsPerSecond: Math.round(size * 1000 / elapsedMs), ...counts(job) });
  }

  const duplicates = await account('dup');
  const seed = keys('seed', 60);
  const firstCsv = csv(duplicates, seed);
  const firstId = jobId(await upload(firstCsv, 'dup-original'));
  assert.equal((await importDone(firstId)).successRows, 60);
  const same = await upload(firstCsv, 'dup-renamed', 200);
  assert.equal(jobId(same), firstId);
  assert.equal(same.duplicateFile, true);
  const reordered = await importDone(jobId(await upload(csv(duplicates, [...seed.toReversed(), ...keys('new', 3)]), 'dup-reordered')));
  assert.equal(reordered.successRows, 3);
  assert.equal(reordered.duplicateRows, 60);
  const overlap = keys('overlap', 60);
  const submissions = await Promise.all(Array.from({ length: 4 }, (_, index) =>
    upload(csv(duplicates, overlap.map(row => ({ ...row, description: `file-${index}` }))), `dup-parallel-${index}`)));
  const settled = await Promise.all(submissions.map(job => importDone(jobId(job))));
  const sum = field => settled.reduce((total, job) => total + job[field], 0);
  const persisted = await imported(duplicates);
  report('deduplication', { firstId, sameFileId: jobId(same), reordered: counts(reordered), parallel: settled.map(counts), persistedRows: persisted.total });
  assert.equal(sum('successRows'), 60);
  assert.equal(sum('duplicateRows'), 180);
  assert.equal(persisted.total, 123);

  const reconciliation = await account('match');
  const truth = new Map();
  const fixtures = [
    ['MATCHED', 'EXACT_MATCH'], ['MATCHED', 'TOLERANCE_MATCH'], ['UNMATCHED', 'NO_CANDIDATE'],
    ['SUSPICIOUS', 'AMOUNT_MISMATCH'], ['DUPLICATE', 'MULTIPLE_CANDIDATES'],
  ];
  async function manual(key, amount) {
    const txn = expect(await asAdmin('/transactions', { method: 'POST', body: {
      accountId: reconciliation.id, externalTransactionNo: key, direction: 'EXPENSE', amount,
      transactionTime: '2026-08-13T10:00:00', description: 'benchmark',
    } }), 200, 201);
    assert.ok(txn.id, 'Missing manual transaction ID');
    return txn.id;
  }
  const fixtureRows = [];
  for (const [index, row] of keys('match', 100).entries()) {
    const fixture = index % fixtures.length;
    const [type, reason] = fixtures[fixture];
    const amount = 100 + index * 10;
    let manualId;
    if (fixture === 0) manualId = await manual(row.key, amount);
    if (fixture === 1) manualId = await manual(`${row.key}-M`, amount);
    if (fixture === 3) await manual(row.key, amount + 1_000);
    if (fixture === 4) {
      await manual(`${row.key}-M1`, amount);
      await manual(`${row.key}-M2`, amount);
    }
    truth.set(row.key, { type, reason, manualId });
    fixtureRows.push({ ...row, amount });
  }
  const importId = jobId(await upload(csv(reconciliation, fixtureRows), 'match'));
  const matchImport = await importDone(importId);
  assert.equal(matchImport.successRows, 100);
  const recId = jobId(expect(await asAdmin('/reconciliation-jobs', { method: 'POST', body: { importJobId: importId } }), 202));
  assert.equal((await terminal(`/reconciliation-jobs/${recId}`, ['COMPLETED', 'FAILED'])).status, 'COMPLETED');
  const page = expect(await asAdmin(`/reconciliation-jobs/${recId}/results?size=100`), 200);
  const tx = await imported(reconciliation);
  assert.equal(tx.total, 100);
  const byId = new Map(tx.records.map(row => [row.id, row.externalTransactionNo]));
  const breakdown = Object.fromEntries(fixtures.map(([, reason]) => [reason, { expected: 20, correct: 0 }]));
  const mismatches = [];
  const seen = new Set();
  for (const result of page.records) {
    const key = byId.get(result.csvTransactionId);
    const expected = truth.get(key);
    if (expected && !seen.has(key) && result.resultType === expected.type && result.reasonCode === expected.reason && (!expected.manualId || result.manualTransactionId === expected.manualId)) {
      breakdown[expected.reason].correct++;
    } else {
      mismatches.push({ key, expected, result });
    }
    seen.add(key);
  }
  report('reconciliation', { importId, recId, expectedRows: truth.size, resultRows: page.total, breakdown, mismatches });
  assert.equal(page.total, 100);
  assert.equal(seen.size, 100);
  assert.equal(mismatches.length, 0);

  const reviewGet = path => request(`/review-tasks${path}`, { token: reviewer });
  const tasks = expect(await reviewGet('?size=100'), 200);
  const resultIds = new Set(page.records.map(row => row.id));
  const selected = tasks.records.filter(row => resultIds.has(row.reconciliationResultId)).slice(0, 3);
  assert.equal(selected.length, 3);
  for (const task of selected) {
    const start = performance.now();
    const replies = await Promise.all(Array.from({ length: 20 }, async (_, index) => {
      const note = `${run}-decision-${index}`;
      const result = await request(`/review-tasks/${task.id}/decision`, { token: reviewer, method: 'PATCH', body: { decision: 'IGNORED', version: task.version, note } });
      return { ...result, note };
    }));
    const statusCounts = {};
    for (const { status } of replies) statusCounts[status] = (statusCounts[status] ?? 0) + 1;
    const after = expect(await reviewGet(`/${task.id}`), 200);
    report('review-conflict', { id: task.id, statusCounts, elapsedMs: Math.round(performance.now() - start), versionBefore: task.version, versionAfter: after.version, statusAfter: after.status, sampleError: replies.find(reply => ![200, 409].includes(reply.status))?.data });
    assert.deepEqual(statusCounts, { 200: 1, 409: 19 });
    assert.equal(after.version, task.version + 1);
    assert.equal(after.status, 'IGNORED');
    assert.equal(after.decisionNote, replies.find(reply => reply.status === 200).note);
    assert.ok(after.reviewedBy && after.reviewedAt, 'Missing review audit fields');
  }
}
