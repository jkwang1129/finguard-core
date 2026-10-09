import test from "node:test";
import assert from "node:assert/strict";
import { createActions } from "../../../src/main/resources/static/demo/js/workspaces/accounts.js";
test("account filters/paging, proper write verbs and cancellation", async () => {
  const calls = [];
  let allow = true;
  const a = createActions({
    role: "ADMIN",
    request: async (p, o = {}) => {
      calls.push([p, o]);
      return { data: { id: 1 } };
    },
    confirmWrite: async () => allow,
  });
  await a.query({
    page: 2,
    size: 20,
    keyword: "<img src=x>",
    status: "ACTIVE",
    accountType: "BANK",
  });
  const q = new URLSearchParams(calls[0][0].split("?")[1]);
  assert.equal(q.get("page"), "2");
  assert.equal(q.get("keyword"), "<img src=x>");
  await a.rename(1, "new");
  await a.status(1, "DISABLED");
  await a.remove(1);
  assert.deepEqual(
    calls.slice(1).map((c) => c[1].method),
    ["PATCH", "PATCH", "DELETE"],
  );
  allow = false;
  await a.remove(1);
  assert.equal(calls.length, 4);
  assert.throws(() => a.get("0"));
});
test("reviewer writes never sent", async () => {
  const a = createActions({
    role: "REVIEWER",
    request: () => assert.fail("write sent"),
  });
  await assert.rejects(a.create({}));
});
