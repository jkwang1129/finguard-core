import test from "node:test";
import assert from "node:assert/strict";
import {
  buildScenario,
  scenarioNames,
  csv,
  executeScenarioStep,
} from "../../../src/main/resources/static/demo/js/scenarios.js";
test("recipes construct strong/weak, reserved candidate and risk windows", () => {
  const o = { runId: "test", anchorTime: "2026-10-09T12:00:00" };
  for (const n of scenarioNames) assert.ok(buildScenario(n, o).steps.length);
  const exact = buildScenario("EXACT", o);
  assert.equal(
    exact.manual[0].externalTransactionNo,
    exact.rows[0].externalTransactionNo,
  );
  const weak = buildScenario("WEAK_TOLERANCE", o);
  assert.notEqual(
    weak.manual[0].externalTransactionNo,
    weak.rows[0].externalTransactionNo,
  );
  assert.equal(buildScenario("MULTIPLE_CANDIDATES", o).manual.length, 2);
  const reserved = buildScenario("MANUAL_ALREADY_MATCHED", o);
  assert.equal(
    reserved.rows[0].externalTransactionNo,
    reserved.manual[0].externalTransactionNo,
  );
  const f = buildScenario("FREQUENT_TRANSACTION", o);
  assert.equal(f.rows.filter((r) => r.direction === "EXPENSE").length, 5);
  const d = buildScenario("POSSIBLE_DUPLICATE", o);
  assert.equal(d.rows[0].amount, d.rows[1].amount);
  assert.notEqual(
    d.rows[0].externalTransactionNo,
    d.rows[1].externalTransactionNo,
  );
  assert.match(
    csv("DEMO-A", [
      {
        externalTransactionNo: "x",
        direction: "EXPENSE",
        amount: "0.10",
        transactionTime: o.anchorTime,
        description: "",
      },
    ]),
    /0.10/,
  );
});
test("execution returns actual server state and never invents risk facts", async () => {
  const p = buildScenario("LARGE_AMOUNT", {
    runId: "x",
    anchorTime: "2026-10-09T12:00:00",
  });
  const r = await executeScenarioStep(p, 0, {
    confirmWrite: async () => true,
    request: async () => ({ status: 201, data: { id: 7, status: "ACTIVE" } }),
  });
  assert.equal(r.taskIds.accountId, 7);
  assert.equal(r.httpStatus, 201);
  assert.equal(r.actual.status, "ACTIVE");
});

test('version conflict probes pending task before a terminal decision',()=>{const p=buildScenario('VERSION_CONFLICT',{runId:'version'});assert.ok(p.steps.findIndex(s=>s.kind==='rejectedDecision')<p.steps.findIndex(s=>s.kind==='decision'));});

test('default recipes stay inside server CSV future-time limit including +4 day cases',()=>{for(const name of scenarioNames){const plan=buildScenario(name);for(const row of plan.rows)assert.ok(Date.parse(row.transactionTime+'+08:00')<=Date.now()+5*60000,`${name}: future CSV time`);}});
