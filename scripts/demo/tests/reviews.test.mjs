import test from "node:test";
import assert from "node:assert/strict";
import {
  createActions,
  filters,
} from "../../../src/main/resources/static/demo/js/workspaces/reviews.js";
test("role restriction, current version and conflict without implicit retry", async () => {
  let version = 3;
  const calls = [];
  const ctx = {
    role: "REVIEWER",
    confirmWrite: async () => true,
    request: async (p, o = {}) => {
      calls.push([p, o]);
      if (o.method === "PATCH")
        throw Object.assign(new Error("conflict"), {
          status: 409,
          code: "REVIEW_VERSION_CONFLICT",
        });
      return { data: { id: 1, status: "PENDING", version } };
    },
  };
  const a = createActions(ctx);
  const row = (await a.get(1)).data,
    note = "keep my note";
  await assert.rejects(a.decide(row, "CONFIRMED", note));
  assert.deepEqual(calls[1][1].json, {
    decision: "CONFIRMED",
    version: 3,
    note,
  });
  assert.equal(calls.filter((c) => c[1].method === "PATCH").length, 1);
  version = 4;
  assert.equal((await a.get(1)).data.version, 4);
  await assert.rejects(
    a.decide({ ...row, status: "CONFIRMED" }, "IGNORED", note),
  );
  ctx.role = "ADMIN";
  await assert.rejects(a.context(1));
  await assert.rejects(a.decide(row, "IGNORED", note));
  assert.throws(() =>
    filters({ resultType: "UNMATCHED", ruleCode: "LARGE_AMOUNT" }),
  );
  assert.equal(filters({ ruleCode: "LARGE_AMOUNT" }).sourceType, "RISK_HIT");
});
