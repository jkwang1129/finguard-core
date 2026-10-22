import test from "node:test";
import assert from "node:assert/strict";
import {
  createActions,
  preview,
  canReconcile,
} from "../../../src/main/resources/static/demo/js/workspaces/imports.js";
test("raw BOM/CRLF file preserved, double click guarded, explicit duplicate compares receipts", async () => {
  const bytes = "\uFEFFaccount_no\r\nabc\r\n",
    file = new File([bytes], "x.csv");
  const calls = [];
  let release;
  const wait = new Promise((r) => (release = r));
  const a = createActions({
    role: "ADMIN",
    confirmWrite: async () => true,
    request: async (p, o) => {
      calls.push(o);
      await wait;
      return {
        status: calls.length === 1 ? 202 : 200,
        data: { id: 7, duplicateFile: calls.length > 1 },
      };
    },
  });
  const first = a.upload(file);
  await a.upload(file);
  release();
  await first;
  assert.equal(calls.length, 1);
  await a.repeat();
  assert.equal(calls.length, 2);
  assert.deepEqual(
    new Uint8Array(await calls[0].form.get("file").arrayBuffer()),
    new TextEncoder().encode(bytes),
  );
  assert.deepEqual(
    new Uint8Array(await calls[1].form.get("file").arrayBuffer()),
    new TextEncoder().encode(bytes),
  );
  assert.equal(await preview(file), "account_no\nabc\n");
  assert.equal(canReconcile({ status: "FAILED" }), false);
  assert.equal(canReconcile({ status: "PARTIAL_SUCCESS" }), true);
});
test("errors use only supported pagination, continuation only GET", async () => {
  const calls = [];
  const a = createActions({
    request: async (p, o) => {
      calls.push([p, o]);
      return { data: {} };
    },
    pollJob: async (p, o) => {
      calls.push([p, o]);
      return { data: { status: "SUCCESS" } };
    },
  });
  await a.errors(7, { page: 2, size: 20, errorCode: "FAKE" });
  assert.equal(calls[0][0], "/api/import-jobs/7/errors?page=2&size=20");
  await a.poll(7);
  assert.equal(calls[1][0], "/api/import-jobs/7");
});
