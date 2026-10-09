import assert from "node:assert/strict";
import { open, scenario, pass } from "./helpers.mjs";
import { scenarioNames } from "../../../src/main/resources/static/demo/js/scenarios.js";
export async function run(ctx) {
  const page = await open(ctx),
    results = [];
  try {
    const names =
      ctx.variant === "rules-disabled"
        ? [
            "LARGE_AMOUNT_BOUNDARY",
            "POSSIBLE_DUPLICATE",
            "FREQUENT_TRANSACTION",
          ]
        : ctx.variant === "high-threshold"
          ? ["LARGE_AMOUNT_BOUNDARY"]
          : scenarioNames;
    for (const name of names) {
      const r = await scenario(page, name),
        facts = r.evidence.find((e) => e.step === "facts")?.actual;
      const imp = r.evidence.find((e) => e.step === "import").actual;
      if (name === "IMPORT_FILE_FAILED") {
        assert.equal(imp.status, "FAILED");
        assert.equal(imp.fileErrorCode, "INVALID_HEADER");
      } else if (name === "IMPORT_PARTIAL") {
        assert.equal(imp.status, "PARTIAL_SUCCESS");
        assert.equal(imp.failedRows, 1);
      } else assert.equal(imp.status, "SUCCESS");
      if (r.expected.reasonCode)
        assert.ok(
          facts.results.some((x) => x.reasonCode === r.expected.reasonCode),
          `${name}: missing actual reason`,
        );
      if (r.expected.ruleCode) {
        if (ctx.variant === "baseline")
          assert.ok(
            facts.riskRuleCodes.includes(r.expected.ruleCode),
            `${name}: missing actual risk`,
          );
        else
          assert.equal(
            facts.riskRuleCodes.includes(r.expected.ruleCode),
            false,
            `${name}: unexpected configured risk`,
          );
      }
      if (ctx.variant === "rules-disabled")
        assert.deepEqual(facts.riskRuleCodes, []);
      if (r.expected.noRule)
        assert.equal(facts.riskRuleCodes.includes(r.expected.noRule), false);
      if (name === "ROW_DUPLICATE")
        assert.equal(
          r.evidence.find((e) => e.step === "secondImport").actual
            .duplicateRows,
          1,
        );
      if (["CONFIRMED", "IGNORED"].includes(name))
        assert.equal(
          r.evidence.find((e) => e.step === "decision").actual.status,
          name,
        );
      if (["VERSION_CONFLICT", "TERMINAL_DECISION"].includes(name))
        assert.equal(
          r.evidence.find((e) => e.step === "rejectedDecision").httpStatus,
          409,
        );
      if (name === "VERSION_CONFLICT")
        assert.equal(
          r.evidence.find((e) => e.step === "rejectedDecision").actual.code,
          "REVIEW_VERSION_CONFLICT",
        );
      if (name.endsWith("_401") || name.endsWith("_403"))
        assert.equal(
          r.evidence.find((e) => e.step === "permission").httpStatus,
          name.endsWith("_401") ? 401 : 403,
        );
      results.push(
        pass(`${ctx.variant}/${name}`, {
          taskIds: r.ids,
          httpStatus: r.evidence.map((e) => e.httpStatus),
          reasons: facts?.results.map((x) => x.reasonCode),
          rules: facts?.riskRuleCodes,
          importStatus: imp.status,
        }),
      );
      console.log(`PASS ${ctx.variant}/${name}`);
    }
    return results;
  } finally {
    await page.close();
  }
}
