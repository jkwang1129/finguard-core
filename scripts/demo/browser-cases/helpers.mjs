import assert from "node:assert/strict";
export const unique = () =>
  `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 7)}`;
export async function open({ browser, baseUrl, credentials }) {
  const page = await browser.newPage({
    viewport: { width: 1440, height: 1000 },
  });
  page.setDefaultTimeout(15000);
  page.on("dialog", (dialog) => dialog.accept());
  await page.goto(`${baseUrl}/demo/index.html`);
  await page.getByRole("heading", { name: "可执行场景配方" }).waitFor();
  await authenticate(page, credentials);
  await page.getByLabel("我已了解演示会写入当前数据库").check();
  return page;
}
export async function authenticate(page, credentials) {
  for (const role of ["ADMIN", "REVIEWER"]) {
    const box = page
      .locator("#sessions details")
      .filter({ has: page.locator("summary", { hasText: `${role} 登录` }) });
    await box.evaluate((node) => (node.open = true));
    const f = box.getByRole("form", { name: `${role} 登录` });
    await f.getByLabel(`${role} 用户名`).fill(credentials[role].username);
    await f.getByLabel(`${role} 密码`).fill(credentials[role].password);
    await f.getByRole("button", { name: "登录", exact: true }).click();
    await box.getByText("已验证服务端身份", { exact: true }).waitFor();
    assert.equal(await f.getByLabel(`${role} 密码`).inputValue(), "");
  }
  await page.getByLabel("当前操作角色").selectOption("ADMIN");
}
export async function go(page, name, params = {}) {
  await page.evaluate(
    ([name, params]) => {
      location.hash =
        name +
        (Object.keys(params).length ? "?" + new URLSearchParams(params) : "");
    },
    [name, params],
  );
  await page
    .locator(`#navigation a[data-workspace="${name}"][aria-current="page"]`)
    .waitFor();
}
export async function api(page, path, options = {}) {
  return page.evaluate(
    async ([path, options]) => {
      const { request } = await import("/demo/js/api.js");
      return request(path, { role: "ADMIN", ...options });
    },
    [path, options],
  );
}
export async function poll(page, path, terminalStatuses) {
  return page.evaluate(
    async ([path, terminalStatuses]) => {
      const { pollJob } = await import("/demo/js/api.js");
      return pollJob(path, { role: "ADMIN", terminalStatuses });
    },
    [path, terminalStatuses],
  );
}
export async function pages(page, path, options = {}) {
  return page.evaluate(
    async ([path, options]) => {
      const { readAllPages } = await import("/demo/js/api.js");
      return readAllPages(path, { role: "ADMIN", ...options });
    },
    [path, options],
  );
}
export async function statusOf(page, path, options = {}) {
  return page.evaluate(
    async ([path, options]) => {
      const { request } = await import("/demo/js/api.js");
      try {
        return (await request(path, { role: "ADMIN", ...options })).status;
      } catch (e) {
        return e.status;
      }
    },
    [path, options],
  );
}
export async function scenario(page, name) {
  return page.evaluate(async (name) => {
    const { buildScenario, executeScenarioStep } =
      await import("/demo/js/scenarios.js");
    const { request, pollJob, readAllPages } = await import("/demo/js/api.js");
    const { identity } = await import("/demo/js/session.js");
    const plan = buildScenario(name);
    const ctx = {
      request,
      pollJob,
      readAllPages,
      identity,
      confirmWrite: async () =>
        document.querySelector("#persistence-ack").checked,
    };
    for (let i = 0; i < plan.steps.length; i++)
      await executeScenarioStep(plan, i, ctx);
    return {
      name,
      expected: plan.expected,
      ids: plan.state.ids,
      evidence: plan.state.evidence,
    };
  }, name);
}
export function pass(name, evidence, limitation = null) {
  return { name, status: "PASS", evidence, limitation };
}
export async function account(page, accountNo = `DEMO-${unique()}`) {
  return (
    await api(page, "/api/accounts", {
      method: "POST",
      json: { accountNo, accountName: accountNo, accountType: "BANK" },
    })
  ).data;
}
