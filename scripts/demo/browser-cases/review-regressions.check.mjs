import assert from "node:assert/strict";
import { chromium } from "playwright";
import fs from "node:fs/promises";
import path from "node:path";
const browser = await chromium.launch({ channel: "chrome" });
let failed = 0;
async function pageFor() {
  const page = await browser.newPage();
  await page.route("http://console.test/**", async (route) => {
    const url = new URL(route.request().url());
    if (!url.pathname.startsWith("/demo/"))
      return route.fulfill({
        status: 401,
        contentType: "application/json",
        body: '{"code":"AUTHENTICATION_REQUIRED"}',
      });
    const file = path.resolve(
      "src/main/resources/static",
      url.pathname.slice(1),
    );
    await route.fulfill({
      status: 200,
      body: await fs.readFile(file),
      contentType: file.endsWith(".js")
        ? "text/javascript"
        : file.endsWith(".css")
          ? "text/css"
          : "text/html",
    });
  });
  await page.goto("http://console.test/demo/index.html");
  return page;
}
async function check(name, run) {
  const page = await pageFor();
  try {
    await run(page);
    console.log(`PASS ${name}`);
  } catch (e) {
    failed++;
    console.error(`FAIL ${name}: ${e.message}`);
  } finally {
    await page.close();
  }
}
async function mountDetail(page, workspace, { race = false } = {}) {
  await page.evaluate(
    async ([workspace, race]) => {
      const { mount } = await import(`/demo/js/workspaces/${workspace}.js`);
      const root = document.createElement("div");
      root.id = "regression-root";
      document.querySelector("#workspace").replaceChildren(root);
      const isAccount = workspace === "accounts";
      const row = (id) =>
        isAccount
          ? {
              id,
              accountNo: `DEMO-${id}`,
              accountName: id === 1 ? "OLD-1" : "NEW-2",
              accountType: "BANK",
              status: "ACTIVE",
            }
          : {
              id,
              accountId: 1,
              externalTransactionNo: `DEMO-${id}`,
              description: id === 1 ? "OLD-1" : "NEW-2",
              direction: "EXPENSE",
              amount: "0.10",
              transactionTime: race
                ? "2026-10-01T12:34:00"
                : "2026-10-01T12:34:56.123",
              source: "MANUAL",
            };
      let release;
      const pending = new Promise((r) => (release = r));
      window.regression = { release, oldReturned: false, puts: [] };
      mount(root, {
        role: "ADMIN",
        params: { id: "1" },
        signal: new AbortController().signal,
        identity: () => ({ role: "ADMIN" }),
        confirmWrite: async () => true,
        navigate: () => {},
        request: async (url, options = {}) => {
          if (options.method === "PUT") {
            window.regression.puts.push(options.json);
            return { status: 200, data: row(1) };
          }
          if (options.method === "POST") return { status: 201, data: row(2) };
          if (url.endsWith("/1")) {
            if (race) await pending;
            window.regression.oldReturned = true;
            return { status: 200, data: row(1) };
          }
          if (url.endsWith("/2")) return { status: 200, data: row(2) };
          return {
            status: 200,
            data: { page: 1, size: 20, total: 0, pages: 0, records: [] },
          };
        },
      });
    },
    [workspace, race],
  );
}
try {
  await check("non-localhost HTTP mounts default GUIDED", async (page) => {
    assert.equal(await page.evaluate(() => isSecureContext), false);
    assert.equal(
      await page.evaluate(() => typeof crypto.randomUUID),
      "undefined",
    );
    await page
      .getByRole("form", { name: "预览场景" })
      .waitFor({ timeout: 2500 });
  });
  for (const workspace of ["accounts", "transactions"])
    await check(
      `${workspace} late detail cannot replace new edit target`,
      async (page) => {
        await mountDetail(page, workspace, { race: true });
        const f = page
          .locator("#regression-root")
          .getByRole("form", {
            name: workspace === "accounts" ? "创建账户" : "创建人工交易",
            exact: true,
          });
        await f.evaluate((form, workspace) => {
          const values =
            workspace === "accounts"
              ? { accountNo: "DEMO-2", accountName: "NEW-2" }
              : {
                  accountId: "1",
                  externalTransactionNo: "DEMO-2",
                  amount: "0.10",
                  transactionTime: "2026-10-01T12:34",
                  description: "NEW-2",
                };
          for (const [key, value] of Object.entries(values))
            form.elements.namedItem(key).value = value;
        }, workspace);
        await f.locator("button[type=submit]").click();
        const edit = page
          .locator("#regression-root")
          .getByRole("form", {
            name: workspace === "accounts" ? "修改账户名称" : "替换人工交易",
            exact: true,
          });
        await edit.waitFor();
        const input = edit.locator(
          `[name=${workspace === "accounts" ? "accountName" : "description"}]`,
        );
        assert.equal(await input.inputValue(), "NEW-2");
        await page.evaluate(() => window.regression.release());
        await page.waitForFunction(() => window.regression.oldReturned);
        assert.equal(await input.inputValue(), "NEW-2");
      },
    );
  await check(
    "existing sub-minute transaction submits unchanged time",
    async (page) => {
      await mountDetail(page, "transactions");
      const edit = page
        .locator("#regression-root")
        .getByRole("form", { name: "替换人工交易" });
      await edit.waitFor();
      await edit.locator("[name=description]").fill("updated");
      assert.equal(
        await edit
          .locator("[name=transactionTime]")
          .evaluate((n) => n.validity.stepMismatch),
        false,
      );
      await edit.getByRole("button", { name: "保存交易" }).click();
      await page.waitForFunction(() => window.regression.puts.length === 1, {
        timeout: 2500,
      });
      assert.equal(
        await page.evaluate(() => window.regression.puts[0].transactionTime),
        "2026-10-01T12:34:56.123",
      );
    },
  );
  await check(
    "replacement recipe cancels stale poll and evidence",
    async (page) => {
      await page.evaluate(async () => {
        crypto.randomUUID ??= () => "12345678-1234-1234-1234-123456789012";
        const { mount } = await import("/demo/js/workspaces/guided.js");
        const root = document.createElement("div");
        root.id = "regression-root";
        document.querySelector("#workspace").replaceChildren(root);
        let release;
        const pending = new Promise((r) => (release = r));
        window.regression = {
          release,
          pollStarted: false,
          pollFinished: false,
          pollSignal: null,
        };
        mount(root, {
          signal: new AbortController().signal,
          confirmWrite: async () => true,
          navigate: () => {},
          request: async () => ({ status: 201, data: { id: 19 } }),
          pollJob: async (url, options) => {
            window.regression.pollStarted = true;
            window.regression.pollSignal = options.signal;
            await pending;
            window.regression.pollFinished = true;
            return { status: 200, data: { id: 19, status: "SUCCESS" } };
          },
          readAllPages: async () => [],
        });
      });
      const root = page.locator("#regression-root");
      const preview = root.getByRole("form", { name: "预览场景" });
      await preview.getByLabel("业务场景").selectOption("IMPORT_SUCCESS");
      await preview.locator("button[type=submit]").click();
      for (let i = 0; i < 2; i++) {
        await root
          .getByRole("button", { name: "执行下一步", exact: true })
          .click();
        await page.waitForFunction(
          () =>
            !document.querySelector("#regression-root .toolbar button")?.dataset
              .busy,
        );
      }
      await root
        .getByRole("button", { name: "执行下一步", exact: true })
        .click();
      await page.waitForFunction(() => window.regression.pollStarted);
      await preview.getByLabel("业务场景").selectOption("EXACT");
      await preview.locator("button[type=submit]").click();
      assert.equal(
        await page.evaluate(() => window.regression.pollSignal?.aborted),
        true,
      );
      await page.evaluate(() => window.regression.release());
      await page.waitForFunction(() => window.regression.pollFinished);
      assert.equal((await root.innerText()).includes("已完成 0/"), false);
      const actual = root
        .locator("section")
        .filter({ has: page.getByRole("heading", { name: "实际步骤证据" }) });
      assert.equal((await actual.innerText()).includes("SUCCESS"), false);
    },
  );
  await check(
    "review filters expose only supported result types",
    async (page) => {
      await page.evaluate(async () => {
        const { mount } = await import("/demo/js/workspaces/reviews.js");
        const root = document.createElement("div");
        root.id = "regression-root";
        document.querySelector("#workspace").replaceChildren(root);
        mount(root, {
          role: "ADMIN",
          params: {},
          signal: new AbortController().signal,
          identity: () => null,
          request: async () => ({
            data: { records: [], total: 0, page: 1, size: 20, pages: 0 },
          }),
        });
      });
      assert.equal(
        (
          await page
            .locator("#regression-root [name=resultType] option")
            .evaluateAll((ns) => ns.map((n) => n.value))
        ).includes("MATCHED"),
        false,
      );
    },
  );
} finally {
  await browser.close();
}
if (failed) process.exitCode = 1;
