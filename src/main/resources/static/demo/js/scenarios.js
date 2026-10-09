import { decimal } from "./ui.js";
export const scenarioNames = [
  "GUIDED",
  "IMPORT_SUCCESS",
  "IMPORT_PARTIAL",
  "IMPORT_FILE_FAILED",
  "FILE_DUPLICATE",
  "ROW_DUPLICATE",
  "EXACT",
  "STRONG_TOLERANCE",
  "WEAK_TOLERANCE",
  "NO_CANDIDATE",
  "MULTIPLE_CANDIDATES",
  "MANUAL_ALREADY_MATCHED",
  "DIRECTION_MISMATCH",
  "AMOUNT_MISMATCH",
  "TIME_OUT_OF_RANGE",
  "LARGE_AMOUNT",
  "LARGE_AMOUNT_BELOW",
  "LARGE_AMOUNT_BOUNDARY",
  "POSSIBLE_DUPLICATE",
  "DUPLICATE_OUTSIDE_WINDOW",
  "FREQUENT_TRANSACTION",
  "FREQUENT_BELOW",
  "CONFIRMED",
  "IGNORED",
  "VERSION_CONFLICT",
  "TERMINAL_DECISION",
  "ANONYMOUS_401",
  "REVIEWER_WRITE_403",
  "ADMIN_DECISION_403",
];
const header =
  "account_no,external_transaction_no,direction,amount,transaction_time,description";
function quote(value) {
  return `"${String(value ?? "").replaceAll('"', '""')}"`;
}
export function csv(accountNo, rows) {
  return (
    "\uFEFF" +
    [
      header,
      ...rows.map((r) =>
        [
          accountNo,
          r.externalTransactionNo,
          r.direction,
          r.amount,
          r.transactionTime.replace("T", " "),
          r.description,
        ]
          .map(quote)
          .join(","),
      ),
    ].join("\r\n") +
    "\r\n"
  );
}
function shift(time, seconds) {
  return new Date(Date.parse(time + "Z") + seconds * 1000)
    .toISOString()
    .slice(0, 19);
}
export function buildScenario(
  name,
  {
    runId = globalThis.crypto.randomUUID().slice(0, 12),
    anchorTime = new Date(Date.now() - 10 * 86400000 + 8 * 3600000)
      .toISOString()
      .slice(0, 19),
    amount = "100.00",
    csvAmount = amount,
    riskAmount = "12000.00",
    offsetDays = 0,
  } = {},
) {
  if (!scenarioNames.includes(name)) throw new Error("未知场景");
  if (!/^[\w-]{1,40}$/.test(runId)) throw new Error("场景编号无效");
  decimal(amount);
  decimal(csvAmount);
  decimal(riskAmount);
  const accountNo = `DEMO-${runId}`,
    row = (
      key = "key",
      money = amount,
      time = anchorTime,
      direction = "EXPENSE",
    ) => ({
      externalTransactionNo: `${runId}-${key}`,
      direction,
      amount: money,
      transactionTime: time,
      description: `DEMO-${name}`,
    });
  let manual = [],
    rows = [row()],
    expected = {};
  const strong = [
    "EXACT",
    "STRONG_TOLERANCE",
    "DIRECTION_MISMATCH",
    "AMOUNT_MISMATCH",
    "TIME_OUT_OF_RANGE",
  ];
  if (strong.includes(name)) {
    manual = [row()];
    if (name === "STRONG_TOLERANCE")
      rows[0].transactionTime = shift(anchorTime, 3 * 86400);
    if (name === "DIRECTION_MISMATCH") rows[0].direction = "INCOME";
    if (name === "AMOUNT_MISMATCH") rows[0].amount = "101.01";
    if (name === "TIME_OUT_OF_RANGE")
      rows[0].transactionTime = shift(anchorTime, 4 * 86400);
    expected = {
      reasonCode:
        name === "EXACT"
          ? "EXACT_MATCH"
          : name === "STRONG_TOLERANCE"
            ? "TOLERANCE_MATCH"
            : name,
    };
  }
  if (name === "WEAK_TOLERANCE") {
    manual = [row("manual")];
    rows = [row("csv")];
    expected = { reasonCode: "TOLERANCE_MATCH" };
  }
  if (name === "MULTIPLE_CANDIDATES") {
    manual = [row("m1"), row("m2")];
    rows = [row("csv")];
    expected = { reasonCode: name };
  }
  if (name === "MANUAL_ALREADY_MATCHED") {
    manual = [row("reserved")];
    rows = [row("reserved"), row("weak")];
    expected = { reasonCode: name };
  }
  if (name === "NO_CANDIDATE") expected = { reasonCode: name };
  if (name === "GUIDED") {
    manual = [row("match")];
    rows = [
      row("match", csvAmount, shift(anchorTime, Number(offsetDays) * 86400)),
      row("risk", riskAmount, shift(anchorTime, 600)),
    ];
    expected = { description: "匹配行取决于四个输入；风险依赖服务器当前配置" };
  }
  if (name === "IMPORT_PARTIAL") rows.push(row("invalid", "not-a-number"));
  if (name === "LARGE_AMOUNT" || name === "LARGE_AMOUNT_BOUNDARY") {
    rows = [
      row("large", name === "LARGE_AMOUNT_BOUNDARY" ? "10000.00" : riskAmount),
    ];
    expected = {
      ruleCode: "LARGE_AMOUNT",
      configuration: "默认阈值 10000.00；当前配置未知",
    };
  }
  if (name === "LARGE_AMOUNT_BELOW") {
    rows = [row("below", "9999.99")];
    expected = { noRule: "LARGE_AMOUNT", configuration: "默认阈值" };
  }
  if (name === "POSSIBLE_DUPLICATE" || name === "DUPLICATE_OUTSIDE_WINDOW") {
    rows = [
      row("earlier"),
      row(
        "later",
        amount,
        shift(anchorTime, name === "POSSIBLE_DUPLICATE" ? 60 : 301),
      ),
    ];
    expected =
      name === "POSSIBLE_DUPLICATE"
        ? { ruleCode: name }
        : { noRule: "POSSIBLE_DUPLICATE" };
  }
  if (name === "FREQUENT_TRANSACTION" || name === "FREQUENT_BELOW") {
    rows = Array.from(
      { length: name === "FREQUENT_TRANSACTION" ? 5 : 4 },
      (_, i) => row(`f${i}`, `${i + 1}.00`, shift(anchorTime, i * 60)),
    );
    expected =
      name === "FREQUENT_TRANSACTION"
        ? { ruleCode: name }
        : { noRule: "FREQUENT_TRANSACTION" };
  }
  if (
    ["CONFIRMED", "IGNORED", "VERSION_CONFLICT", "TERMINAL_DECISION"].includes(
      name,
    )
  )
    expected = { decision: name };
  const steps = [
    { kind: "account", label: "创建隔离演示账户" },
    ...manual.map((r, index) => ({
      kind: "manual",
      index,
      label: `创建人工交易 ${index + 1}`,
    })),
    { kind: "upload", label: "上传 CSV" },
    { kind: "import", label: "GET 导入终态" },
  ];
  if (name === "FILE_DUPLICATE")
    steps.push({ kind: "repeatFile", label: "主动重复完全相同文件" });
  if (name === "ROW_DUPLICATE")
    steps.push(
      { kind: "repeatRow", label: "上传不同文件但相同业务行" },
      { kind: "secondImport", label: "GET 第二文件终态" },
    );
  if (name !== "IMPORT_FILE_FAILED") {
    steps.push(
      { kind: "reconcile", label: "创建对账" },
      { kind: "reconciliation", label: "GET 对账终态" },
      { kind: "facts", label: "加载逐笔结果与审核/风险事实" },
    );
  }
  if (name === "VERSION_CONFLICT")
    steps.push({
      kind: "rejectedDecision",
      label: "DEMO：主动提交不一致版本，验证待审核任务的版本校验",
    });
  if (
    ["CONFIRMED", "IGNORED", "VERSION_CONFLICT", "TERMINAL_DECISION"].includes(
      name,
    )
  )
    steps.push({ kind: "decision", label: "REVIEWER 提交决定" });
  if (name === "TERMINAL_DECISION")
    steps.push({
      kind: "rejectedDecision",
      label: "主动提交旧版本/终态决定，观察拒绝",
    });
  if (
    ["ANONYMOUS_401", "REVIEWER_WRITE_403", "ADMIN_DECISION_403"].includes(name)
  )
    steps.push({ kind: "permission", label: "发送实际权限验证请求" });
  if (name !== "IMPORT_FILE_FAILED")
    steps.push({ kind: "repeatReconciliation", label: "主动重复创建对账验证" });
  steps.push({ kind: "summary", label: "读取完整批次审计与全库统计" });
  return {
    name,
    runId,
    accountNo,
    manual,
    rows,
    expected,
    steps,
    state: { next: 0, ids: {}, evidence: [], attempted: new Set() },
  };
}
export async function executeScenarioStep(plan, index, ctx) {
  const state = plan.state,
    step = plan.steps[index];
  if (!step || index !== state.next) throw new Error("请按顺序执行单步");
  if (state.attempted.has(index))
    throw new Error("此写入已经尝试，结果不确定时请从历史查询；不要隐式重放");
  const writeKinds = [
    "account",
    "manual",
    "upload",
    "repeatFile",
    "repeatRow",
    "reconcile",
    "repeatReconciliation",
    "decision",
    "rejectedDecision",
    "permission",
  ];
  if (
    writeKinds.includes(step.kind) &&
    !(await ctx.confirmWrite({ action: step.label, target: plan.accountNo }))
  )
    return null;
  if (writeKinds.includes(step.kind)) state.attempted.add(index);
  let result;
  const ids = state.ids,
    sendFile = (file) => {
      const form = new FormData();
      form.append("file", file, file.name);
      return ctx.request("/api/import-jobs", {
        method: "POST",
        role: "ADMIN",
        form,
      });
    };
  switch (step.kind) {
    case "account":
      result = await ctx.request("/api/accounts", {
        method: "POST",
        role: "ADMIN",
        json: {
          accountNo: plan.accountNo,
          accountName: plan.accountNo,
          accountType: "BANK",
        },
      });
      ids.accountId = result.data.id;
      break;
    case "manual":
      result = await ctx.request("/api/transactions", {
        method: "POST",
        role: "ADMIN",
        json: { accountId: ids.accountId, ...plan.manual[step.index] },
      });
      ids.manualTransactionIds ??= [];
      ids.manualTransactionIds.push(result.data.id);
      break;
    case "upload":
      state.file = new File(
        [
          plan.name === "IMPORT_FILE_FAILED"
            ? "wrong,header\r\n1,2\r\n"
            : csv(plan.accountNo, plan.rows),
        ],
        `${plan.accountNo}.csv`,
        { type: "text/csv" },
      );
      result = await sendFile(state.file);
      ids.importJobId = result.data.id;
      break;
    case "repeatFile":
      result = await sendFile(state.file);
      if (result.data.id !== ids.importJobId || !result.data.duplicateFile)
        throw new Error("文件幂等响应不符合原任务");
      break;
    case "repeatRow":
      result = await sendFile(
        new File(
          [
            csv(plan.accountNo, plan.rows).replaceAll(
              `DEMO-${plan.name}`,
              `DEMO-${plan.name}-different-file`,
            ),
          ],
          `${plan.accountNo}-2.csv`,
          { type: "text/csv" },
        ),
      );
      ids.secondImportJobId = result.data.id;
      break;
    case "import":
    case "secondImport":
      result = await ctx.pollJob(
        `/api/import-jobs/${step.kind === "import" ? ids.importJobId : ids.secondImportJobId}`,
        {
          role: "ADMIN",
          terminalStatuses: ["SUCCESS", "PARTIAL_SUCCESS", "FAILED"],
        },
      );
      break;
    case "reconcile": {
      const job = await ctx.request(`/api/import-jobs/${ids.importJobId}`, {
        role: "ADMIN",
      });
      if (!["SUCCESS", "PARTIAL_SUCCESS"].includes(job.data.status))
        throw new Error("导入未成功，请核对历史，不能继续创建对账");
      result = await ctx.request("/api/reconciliation-jobs", {
        method: "POST",
        role: "ADMIN",
        json: { importJobId: ids.importJobId },
      });
      ids.reconciliationJobId = result.data.id;
      break;
    }
    case "reconciliation":
      result = await ctx.pollJob(
        `/api/reconciliation-jobs/${ids.reconciliationJobId}`,
        { role: "ADMIN", terminalStatuses: ["COMPLETED", "FAILED"] },
      );
      break;
    case "repeatReconciliation":
      result = await ctx.request("/api/reconciliation-jobs", {
        method: "POST",
        role: "ADMIN",
        json: { importJobId: ids.importJobId },
      });
      if (
        result.data.id !== ids.reconciliationJobId ||
        !result.data.duplicateRequest
      )
        throw new Error("对账幂等响应没有复用原任务");
      break;
    case "facts": {
      state.results = await ctx.readAllPages(
        `/api/reconciliation-jobs/${ids.reconciliationJobId}/results`,
        { role: "ADMIN" },
      );
      const csvIds = new Set(state.results.map((r) => r.csvTransactionId));
      state.reviews = (
        await ctx.readAllPages("/api/review-tasks", { role: "ADMIN" })
      ).filter((r) => csvIds.has(r.csvTransactionId));
      ids.reviewTaskIds = state.reviews.map((r) => r.id);
      const contexts = [];
      if (ctx.identity("REVIEWER"))
        for (const task of state.reviews)
          contexts.push(
            (
              await ctx.request(`/api/review-tasks/${task.id}/context`, {
                role: "REVIEWER",
              })
            ).data,
          );
      result = {
        status: 200,
        data: {
          results: state.results,
          reviewTasks: state.reviews,
          contexts,
          riskRuleCodes: state.reviews
            .filter((r) => r.sourceType === "RISK_HIT")
            .map((r) => r.ruleCode),
        },
      };
      break;
    }
    case "decision": {
      const task = state.reviews[0];
      if (!task) throw new Error("没有可审核任务");
      state.oldVersion = task.version;
      result = await ctx.request(`/api/review-tasks/${task.id}/decision`, {
        method: "PATCH",
        role: "REVIEWER",
        json: {
          decision: plan.name === "IGNORED" ? "IGNORED" : "CONFIRMED",
          version: task.version,
          note: `DEMO-${plan.name}`,
        },
      });
      break;
    }
    case "rejectedDecision": {
      const task = state.reviews[0];
      let version =
        plan.name === "VERSION_CONFLICT" ? task.version + 1 : state.oldVersion;
      if (plan.name === "TERMINAL_DECISION")
        version = (
          await ctx.request(`/api/review-tasks/${task.id}`, {
            role: "REVIEWER",
          })
        ).data.version;
      try {
        result = await ctx.request(`/api/review-tasks/${task.id}/decision`, {
          method: "PATCH",
          role: "REVIEWER",
          json: { decision: "IGNORED", version, note: "DEMO-rejected" },
        });
      } catch (e) {
        if (
          e.status !== 409 ||
          (plan.name === "VERSION_CONFLICT" &&
            e.code !== "REVIEW_VERSION_CONFLICT")
        )
          throw e;
        result = {
          status: e.status,
          data: { code: e.code, message: e.message },
        };
      }
      break;
    }
    case "permission": {
      try {
        if (plan.name === "ANONYMOUS_401")
          result = await ctx.request("/api/accounts", { role: "ANONYMOUS" });
        else if (plan.name === "REVIEWER_WRITE_403")
          result = await ctx.request("/api/accounts", {
            role: "REVIEWER",
            method: "POST",
            json: {
              accountNo: plan.accountNo + "-forbidden",
              accountName: "DEMO-forbidden",
              accountType: "BANK",
            },
          });
        else
          result = await ctx.request(
            `/api/review-tasks/${state.reviews[0].id}/decision`,
            {
              role: "ADMIN",
              method: "PATCH",
              json: {
                decision: "CONFIRMED",
                version: state.reviews[0].version,
                note: null,
              },
            },
          );
      } catch (e) {
        if (e.status !== (plan.name === "ANONYMOUS_401" ? 401 : 403)) throw e;
        result = {
          status: e.status,
          data: { code: e.code, message: e.message },
        };
      }
      break;
    }
    case "summary": {
      const logs = await ctx.readAllPages("/api/audit-logs", { role: "ADMIN" });
      result = {
        status: 200,
        data: {
          batchAudit: logs.filter(
            (r) =>
              r.importJobId === ids.importJobId ||
              r.importJobId === ids.secondImportJobId ||
              r.reconciliationJobId === ids.reconciliationJobId ||
              ids.reviewTaskIds?.includes(r.reviewTaskId),
          ),
          overview: (
            await ctx.request("/api/statistics/overview", { role: "ADMIN" })
          ).data,
        },
      };
      break;
    }
  }
  ctx.signal?.throwIfAborted();
  const evidence = {
    name: plan.name,
    step: step.kind,
    taskIds: { ...ids },
    states: result.data?.status ? { [step.kind]: result.data.status } : {},
    httpStatus: result.status,
    actual: result.data,
    observedAt: new Date().toISOString(),
  };
  state.evidence.push(evidence);
  state.next++;
  return evidence;
}
