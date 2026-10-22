import assert from "node:assert/strict";
import { chromium } from "playwright";
import fs from "node:fs/promises";
import path from "node:path";
const browser = await chromium.launch({ channel: "chrome" });
try {
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
    const body = await fs.readFile(file);
    await route.fulfill({
      status: 200,
      body,
      contentType: file.endsWith(".js")
        ? "text/javascript"
        : file.endsWith(".css")
          ? "text/css"
          : "text/html",
    });
  });
  await page.goto("http://console.test/demo/index.html");
  await page.getByRole("form", { name: "预览场景" }).waitFor();
  assert.equal(
    await page
      .getByRole("form", { name: "预览场景" })
      .getByLabel("业务场景")
      .inputValue(),
    "GUIDED",
  );
  await page.getByRole("link", { name: "账户管理", exact: true }).click();
  await page.getByRole("form", { name: "创建账户", exact: true }).waitFor();
  assert.equal(
    await page
      .getByRole("form", { name: "创建账户", exact: true })
      .getByLabel("账户类型")
      .inputValue(),
    "BANK",
  );
  await page.getByRole("link", { name: "交易管理", exact: true }).click();
  await page.getByRole("form", { name: "筛选交易" }).waitFor();
  assert.ok(
    (
      await page
        .getByRole("form", { name: "筛选交易" })
        .getByLabel("来源", { exact: true })
        .locator("option")
        .evaluateAll((nodes) => nodes.map((n) => n.value))
    ).includes("CSV_IMPORT"),
  );
  console.log("PASS real browser form defaults and source enum");
} finally {
  await browser.close();
}
