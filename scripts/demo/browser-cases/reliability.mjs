import assert from "node:assert/strict";
import {
  open,
  go,
  api,
  account,
  unique,
  pass,
  pages,
  scenario,
} from "./helpers.mjs";
export async function run(ctx) {
  const page = await open(ctx),
    results = [];
  try {
    const prefix = `DEMO-PAGE-${unique()}`,
      ids = [];
    for (let i = 0; i < 101; i++)
      ids.push((await account(page, `${prefix}-${i}`)).id);
    await go(page, "accounts");
    const filter = page.getByRole("form", { name: "筛选账户" });
    await filter.getByLabel("关键词").fill(prefix);
    await filter.getByLabel("每页条数").selectOption("100");
    await filter.getByRole("button", { name: "查询", exact: true }).click();
    await page.getByText("第 1 / 2 页 · 共 101 条", { exact: true }).waitFor();
    const first = await page
      .locator("#workspace tbody tr")
      .evaluateAll((rows) => rows.map((r) => Number(r.dataset.id)));
    await page.getByRole("button", { name: "下一页", exact: true }).click();
    await page.getByText("第 2 / 2 页 · 共 101 条", { exact: true }).waitFor();
    const second = await page
      .locator("#workspace tbody tr")
      .evaluateAll((rows) => rows.map((r) => Number(r.dataset.id)));
    assert.equal(first.length, 100);
    assert.equal(second.length, 1);
    assert.deepEqual(new Set([...first, ...second]), new Set(ids));
    results.push(pass("真实 101 条分页", { count: 101 }));
    // Ensure the audit corpus itself spans a second page; account CRUD is not audited.
    const auditCount = (await api(page, "/api/audit-logs?page=1&size=1")).data
      .total;
    const needed = Math.max(0, 121 - auditCount);
    if (needed) {
      const fixture = await account(page);
      await page.evaluate(
        async ({ fixture, needed }) => {
          const { request, pollJob } = await import("/demo/js/api.js");
          const receipts = [];
          for (let i = 0; i < needed; i++) {
            const form = new FormData();
            form.append(
              "file",
              new File(
                [
                  `account_no,external_transaction_no,direction,amount,transaction_time,description\n${fixture.accountNo},audit-${i},EXPENSE,1.00,2026-10-09 12:00:00,audit-fixture\n`,
                ],
                `audit-${i}.csv`,
              ),
            );
            receipts.push(
              (
                await request("/api/import-jobs", {
                  role: "ADMIN",
                  method: "POST",
                  form,
                })
              ).data.id,
            );
          }
          for (const id of receipts) {
            const r = await pollJob(`/api/import-jobs/${id}`, {
              role: "ADMIN",
              terminalStatuses: ["SUCCESS", "PARTIAL_SUCCESS", "FAILED"],
            });
            if (r.data.status !== "SUCCESS")
              throw new Error("Audit fixture import failed");
          }
        },
        { fixture, needed },
      );
    }
    const audits = await api(page, "/api/audit-logs?page=2&size=100");
    assert.ok(
      audits.data.records.length,
      "scenario corpus must generate second-page audit",
    );
    const target = audits.data.records.find((r) => r.importJobId);
    assert.ok(target);
    await go(page, "insights");
    const batch = page.getByRole("form", { name: "批次审计" });
    await batch.getByLabel("导入任务 ID").fill(String(target.importJobId));
    await batch.getByRole("button", { name: "遍历完整审计" }).click();
    await page.getByText("完整遍历完成", { exact: false }).waitFor();
    assert.ok(
      await page
        .locator(
          `section:has(h2:text-is("完整批次审计")) tr[data-id="${target.id}"]`,
        )
        .count(),
    );
    results.push(
      pass("第二页批次审计不丢失", {
        auditId: target.id,
        importJobId: target.importJobId,
      }),
    );
    const a = await account(page);
    await go(page, "imports");
    await page.getByLabel("选择 CSV").setInputFiles({
      name: "double.csv",
      mimeType: "text/csv",
      buffer: Buffer.from(
        `account_no,external_transaction_no,direction,amount,transaction_time,description\n${a.accountNo},${unique()},EXPENSE,1.00,2026-10-09 12:00:00,double\n`,
      ),
    });
    let writes = 0;
    const count = (r) => {
      if (r.url().endsWith("/api/import-jobs") && r.method() === "POST")
        writes++;
    };
    page.on("request", count);
    await page
      .getByRole("button", { name: "上传 CSV", exact: true })
      .dblclick();
    await page.getByText(/任务 \d+ · SUCCESS/).waitFor();
    page.off("request", count);
    assert.equal(writes, 1);
    results.push(pass("普通双击写入只一次", { writes }));
    const slow = ids[0],
      fresh = ids[1];
    let pending;
    const routePath = `**/api/accounts/${slow}`;
    await page.route(routePath, async (route) => {
      pending = route;
    });
    await go(page, "accounts", { id: slow });
    await page.waitForFunction(
      (id) => location.hash.includes(`id=${id}`),
      slow,
    );
    await go(page, "accounts", { id: fresh });
    await page
      .locator(".detail dd")
      .filter({ hasText: `${prefix}-1` })
      .first()
      .waitFor();
    if (pending)
      await pending
        .fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            id: slow,
            accountNo: "LATE-OLD",
            accountName: "LATE-OLD",
            status: "ACTIVE",
          }),
        })
        .catch(() => {});
    assert.equal(
      (await page.locator("#workspace").textContent()).includes("LATE-OLD"),
      false,
    );
    await page.unroute(routePath);
    results.push(pass("旧任务晚响应不能覆盖新任务", { selectedId: fresh }));
    const timeout = await page.evaluate(async () => {
      const { createClient } = await import("/demo/js/api.js");
      let count = 0;
      const c = createClient({
        fetch: async (_p, { signal }) => {
          count++;
          return new Promise((_r, reject) =>
            signal.addEventListener(
              "abort",
              () => reject(new DOMException("aborted", "AbortError")),
              { once: true },
            ),
          );
        },
      });
      try {
        await c.request("/api/accounts", { timeoutMs: 10 });
      } catch (e) {
        return { code: e.code, count };
      }
    });
    assert.deepEqual(timeout, { code: "REQUEST_TIMEOUT", count: 1 });
    results.push(pass("GET 超时区分且不重发", timeout));
    const before = await page.evaluate(async () => {
      const { logout, authorization } = await import("/demo/js/session.js");
      logout("ADMIN");
      return authorization("ADMIN");
    });
    assert.equal(before, null);
    const anon = await page.evaluate(async () => {
      const { request } = await import("/demo/js/api.js");
      try {
        await request("/api/accounts", { role: "ADMIN" });
      } catch (e) {
        return e.status;
      }
    });
    assert.equal(anon, 401);
    assert.equal(
      await page.evaluate(() => localStorage.length + sessionStorage.length),
      0,
    );
    const events = await page.locator("#events").textContent();
    for (const credential of Object.values(ctx.credentials))
      assert.equal(events.includes(credential.password), false);
    assert.equal(/Bearer |eyJ[a-zA-Z0-9_-]+\./.test(events), false);
    results.push(pass("注销、存储和操作记录没有凭据", {}));
    return results;
  } finally {
    await page.close();
  }
}
