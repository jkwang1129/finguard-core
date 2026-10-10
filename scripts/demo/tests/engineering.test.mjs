import test from "node:test";
import assert from "node:assert/strict";
import {
  monitorUrl,
  sanitizeEvidence,
  readSignals,
  evidenceCards,
} from "../../../src/main/resources/static/demo/js/workspaces/engineering.js";
test("URLs, exports and historical evidence are bounded", () => {
  assert.throws(() => monitorUrl("javascript:alert(1)"));
  assert.equal(monitorUrl("http://localhost:9090"), "http://localhost:9090/");
  const safe = sanitizeEvidence({
    name: "x",
    taskIds: { importJobId: 3, token: "secret" },
    httpStatus: 202,
    actual: { jwt: "secret" },
    token: "secret",
    observedAt: "now",
  });
  assert.equal(JSON.stringify(safe).includes("secret"), false);
  assert.ok(evidenceCards.some((c) => c.historicalDate));
  assert.ok(evidenceCards.every((c) => c.verified === false));
});
test("empty metrics and failed health stay unverified", async () => {
  const r = await readSignals({
    request: async (p) => {
      if (p.endsWith("health"))
        throw Object.assign(new Error("down"), { status: 503 });
      return { status: 200, data: "" };
    },
  });
  assert.equal(r.health.verified, false);
  assert.equal(r.health.status, 503);
  assert.equal(r.metrics.verified, false);
});
