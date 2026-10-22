import test from "node:test";
import assert from "node:assert/strict";
import { buildScenario } from "../../../src/main/resources/static/demo/js/scenarios.js";
test("amount mismatch recipe remains different for custom 101.01 and arbitrary decimal strings", () => {
  for (const amount of ["101.01", "0.10", "9999999999999999.99"]) {
    const plan = buildScenario("AMOUNT_MISMATCH", { runId: "custom", amount });
    assert.notEqual(plan.manual[0].amount, plan.rows[0].amount);
  }
});
