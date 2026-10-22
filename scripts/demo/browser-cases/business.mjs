import assert from "node:assert/strict";
import {
  open,
  go,
  api,
  poll,
  account,
  unique,
  pass,
  statusOf,
  pages,
  scenario,
} from "./helpers.mjs";
export async function run(ctx) {
  const page = await open(ctx),
    results = [];
  try {
    await go(page, "accounts");
    const create = page.getByRole("form", { name: "创建账户", exact: true }),
      number = `DEMO-${unique()}`,
      literal = '<img src=x onerror="window.demoInjection=1">';
    await create.getByLabel("账号", { exact: true }).fill(number);
    await create.getByLabel("账户名称", { exact: true }).fill(literal);
    const createdPromise = page.waitForResponse(
      (r) =>
        r.url().endsWith("/api/accounts") && r.request().method() === "POST",
    );
    await create.getByRole("button", { name: "创建账户", exact: true }).click();
    const created = await createdPromise;
    assert.equal(
      created.status(),
      201,
      "default account type must be selected",
    );
    const row = await created.json();
    await page.getByRole("form", { name: "修改账户名称" }).waitFor();
    assert.equal(await page.evaluate(() => window.demoInjection), undefined);
    assert.equal(await page.locator("#workspace img").count(), 0);
    const edit = page.getByRole("form", { name: "修改账户名称" });
    await edit.getByLabel("新名称").fill(number);
    await edit.getByRole("button", { name: "保存名称" }).click();
    await page.waitForFunction(
      async (id) =>
        (
          await (
            await fetch(`/api/accounts/${id}`, {
              headers: {
                Authorization: (
                  await import("/demo/js/session.js")
                ).authorization("ADMIN"),
              },
            })
          ).json()
        ).accountName.startsWith("DEMO-"),
      row.id,
    );
    let response = page.waitForResponse((r) =>
      r.url().endsWith(`/accounts/${row.id}/status`),
    );
    await page.getByRole("button", { name: "停用账户", exact: true }).click();
    assert.equal((await response).status(), 200);
    response = page.waitForResponse((r) =>
      r.url().endsWith(`/accounts/${row.id}/status`),
    );
    await page.getByRole("button", { name: "启用账户", exact: true }).click();
    assert.equal((await response).status(), 200);
    response = page.waitForResponse((r) =>
      r.url().endsWith(`/accounts/${row.id}/status`),
    );
    await page.getByRole("button", { name: "停用账户", exact: true }).click();
    await response;
    response = page.waitForResponse(
      (r) =>
        r.url().endsWith(`/accounts/${row.id}`) &&
        r.request().method() === "DELETE",
    );
    await page.getByRole("button", { name: "删除账户", exact: true }).click();
    assert.equal((await response).status(), 204);
    assert.equal(await statusOf(page, `/api/accounts/${row.id}`), 404);
    results.push(pass("账户六操作及安全文本", { accountId: row.id }));
    const a = await account(page);
    await go(page, "transactions", { accountId: a.id });
    const txForm = page.getByRole("form", {
      name: "创建人工交易",
      exact: true,
    });
    await txForm.getByLabel("外部流水号", { exact: true }).fill(unique());
    await txForm.getByLabel("金额（十进制）").fill("0.10");
    response = page.waitForResponse(
      (r) =>
        r.url().endsWith("/api/transactions") &&
        r.request().method() === "POST",
    );
    await txForm.getByRole("button", { name: "创建交易" }).click();
    const txResponse = await response;
    assert.equal(txResponse.status(), 201);
    const transaction = await txResponse.json();
    await page.getByRole("form", { name: "替换人工交易" }).waitFor();
    assert.equal(
      (await api(page, `/api/transactions/${transaction.id}`)).data.amount,
      "0.10",
    );
    const txEdit = page.getByRole("form", { name: "替换人工交易" });
    await txEdit.getByLabel("金额（十进制）").fill("0.20");
    response = page.waitForResponse(
      (r) =>
        r.url().endsWith(`/transactions/${transaction.id}`) &&
        r.request().method() === "PUT",
    );
    await txEdit.getByRole("button", { name: "保存交易" }).click();
    assert.equal((await response).status(), 200);
    response = page.waitForResponse(
      (r) =>
        r.url().endsWith(`/transactions/${transaction.id}`) &&
        r.request().method() === "DELETE",
    );
    await page.getByRole("button", { name: "删除交易" }).click();
    assert.equal((await response).status(), 204);
    assert.equal(
      await statusOf(page, `/api/transactions/${transaction.id}`),
      404,
    );
    results.push(
      pass("人工交易五操作与精确金额", { transactionId: transaction.id }),
    );
    await go(page, "imports");
    const payload =
      "\uFEFFaccount_no,external_transaction_no,direction,amount,transaction_time,description\r\n" +
      `${a.accountNo},${unique()},EXPENSE,0.10,2026-10-09 12:00:00,"<b>text</b>"\r\n` +
      `${a.accountNo},${unique()},EXPENSE,invalid,2026-10-09 12:01:00,error\r\n`;
    await page.getByLabel("选择 CSV").setInputFiles({
      name: "arbitrary-bom.csv",
      mimeType: "text/csv",
      buffer: Buffer.from(payload),
    });
    response = page.waitForResponse(
      (r) =>
        r.url().endsWith("/api/import-jobs") && r.request().method() === "POST",
    );
    await page.getByRole("button", { name: "上传 CSV", exact: true }).click();
    const upload = await response;
    assert.equal(upload.status(), 202);
    const job = await upload.json();
    const final = await poll(page, `/api/import-jobs/${job.id}`, [
      "SUCCESS",
      "PARTIAL_SUCCESS",
      "FAILED",
    ]);
    assert.equal(final.data.status, "PARTIAL_SUCCESS");
    assert.equal(final.data.successRows, 1);
    assert.equal(final.data.failedRows, 1);
    await page.getByText("INVALID_AMOUNT", { exact: true }).waitFor();
    const errors = await api(
      page,
      `/api/import-jobs/${job.id}/errors?page=1&size=20`,
    );
    assert.equal(errors.data.total, 1);
    response = page.waitForResponse(
      (r) =>
        r.url().endsWith("/api/import-jobs") && r.request().method() === "POST",
    );
    await page.getByRole("button", { name: "主动重复提交验证" }).click();
    const repeated = await response;
    assert.equal(repeated.status(), 200);
    const repeatedJob = await repeated.json();
    assert.equal(repeatedJob.id, job.id);
    assert.equal(repeatedJob.duplicateFile, true);
    await page.reload();
    assert.equal(
      await page.evaluate(async () =>
        (await import("/demo/js/session.js")).identity("ADMIN"),
      ),
      null,
    );
    await (await import("./helpers.mjs")).authenticate(page, ctx.credentials);
    await page.getByLabel("我已了解演示会写入当前数据库").check();
    await go(page, "imports", { id: job.id });
    await page
      .locator(".detail dd")
      .filter({ hasText: "PARTIAL_SUCCESS" })
      .first()
      .waitFor();
    assert.ok(
      (await api(page, "/api/import-jobs")).data.records.some(
        (r) => r.id === job.id,
      ),
    );
    results.push(
      pass("任意 CSV、错误分页、文件幂等与刷新找回", { importJobId: job.id }),
    );
    await go(page, "reconciliation", { importJobId: job.id });
    const recForm = page.getByRole("form", { name: "创建对账" });
    response = page.waitForResponse(
      (r) =>
        r.url().endsWith("/api/reconciliation-jobs") &&
        r.request().method() === "POST",
    );
    await recForm
      .getByRole("button", { name: "创建对账", exact: true })
      .click();
    const recResponse = await response;
    assert.equal(recResponse.status(), 202);
    const rec = await recResponse.json();
    const recFinal = await poll(page, `/api/reconciliation-jobs/${rec.id}`, [
      "COMPLETED",
      "FAILED",
    ]);
    assert.equal(recFinal.data.status, "COMPLETED");
    await page.getByText("NO_CANDIDATE", { exact: true }).waitFor();
    response = page.waitForResponse(
      (r) =>
        r.url().endsWith("/api/reconciliation-jobs") &&
        r.request().method() === "POST",
    );
    await page.getByRole("button", { name: "主动重复创建验证" }).click();
    const recRepeated = await response;
    assert.equal(recRepeated.status(), 200);
    assert.equal((await recRepeated.json()).id, rec.id);
    const recResults = await pages(
      page,
      `/api/reconciliation-jobs/${rec.id}/results`,
    );
    await go(page, "transactions", { id: recResults[0].csvTransactionId });
    await page
      .locator(".detail dd")
      .filter({ hasText: /^CSV_IMPORT$/ })
      .first()
      .waitFor();
    assert.equal(
      await page.getByRole("button", { name: "保存交易" }).count(),
      0,
    );
    results.push(
      pass("对账历史、逐笔结果与幂等、CSV 只读", {
        reconciliationJobId: rec.id,
      }),
    );
    const fixture = await scenario(page, "NO_CANDIDATE"),
      reviewId = fixture.ids.reviewTaskIds[0];
    await page.getByLabel("当前操作角色").selectOption("REVIEWER");
    await go(page, "reviews", { id: reviewId });
    await page.getByRole("button", { name: "确认审核", exact: true }).waitFor();
    await page.getByLabel("审核备注").fill("keep-on-409");
    const old = (await api(page, `/api/review-tasks/${reviewId}`)).data;
    await api(page, `/api/review-tasks/${reviewId}/decision`, {
      role: "REVIEWER",
      method: "PATCH",
      json: { decision: "IGNORED", version: old.version, note: "concurrent" },
    });
    response = page.waitForResponse((r) =>
      r.url().endsWith(`/review-tasks/${reviewId}/decision`),
    );
    await page.getByRole("button", { name: "确认审核", exact: true }).click();
    assert.equal((await response).status(), 409);
    assert.equal(await page.getByLabel("审核备注").inputValue(), "keep-on-409");
    await page.getByRole("button", { name: "重新加载当前版本" }).click();
    await page.waitForFunction(() =>
      document.querySelector("#workspace")?.textContent.includes("IGNORED"),
    );
    results.push(pass("审核上下文与 409 备注保留", { reviewTaskId: reviewId }));
    await page.getByLabel("当前操作角色").selectOption("ADMIN");
    await go(page, "insights");
    await page.getByText("统计采集完成", { exact: false }).waitFor();
    await page
      .getByRole("heading", { name: "审计日志", exact: true })
      .waitFor();
    assert.equal((await api(page, "/api/statistics/overview")).status, 200);
    assert.equal((await api(page, "/api/audit-logs")).status, 200);
    results.push(pass("审计与全库统计", {}));
    await go(page, "engineering");
    await page.getByRole("button", { name: "采集健康与指标" }).click();
    await page
      .getByText("健康：已采集 UP；指标：已采集", { exact: true })
      .waitFor();
    for (const url of [
      "http://127.0.0.1:29090/api/v1/targets",
      "http://127.0.0.1:23000/api/health",
    ]) {
      const r = await fetch(url);
      assert.equal(r.status, 200);
      if (url.includes("targets"))
        assert.ok(
          (await r.json()).data.activeTargets.some((t) => t.health === "up"),
        );
    }
    results.push(pass("六服务监控事实", {}));
    return results;
  } finally {
    await page.close();
  }
}
