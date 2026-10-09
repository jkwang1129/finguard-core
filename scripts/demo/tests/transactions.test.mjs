import test from "node:test";
import assert from "node:assert/strict";
import {
  createActions,
  body,
  isEditable,
} from "../../../src/main/resources/static/demo/js/workspaces/transactions.js";
test("decimal string, complete replacement, filters and CSV protection", async () => {
  const calls = [];
  const a = createActions({
    role: "ADMIN",
    request: async (p, o) => {
      calls.push([p, o]);
      return { data: { id: 2 } };
    },
    confirmWrite: async () => true,
  });
  const v = {
    accountId: "1",
    externalTransactionNo: "X",
    amount: "0.10",
    direction: "EXPENSE",
    transactionTime: "2026-10-09T12:00",
    description: "<script>x</script>",
  };
  await a.create(v);
  assert.equal(calls[0][1].json.amount, "0.10");
  await a.update({ id: 2, source: "MANUAL" }, v);
  assert.deepEqual(Object.keys(calls[1][1].json), [
    "direction",
    "amount",
    "transactionTime",
    "description",
  ]);
  await assert.rejects(a.update({ id: 2, source: "CSV_IMPORT" }, v));
  assert.equal(isEditable({ source: "CSV_IMPORT" }), false);
  await a.query({
    page: 2,
    size: 20,
    accountId: 1,
    startTime: v.transactionTime,
    endTime: v.transactionTime,
  });
  assert.match(calls[2][0], /accountId=1/);
  assert.throws(() => body({ ...v, amount: "NaN" }));
});

