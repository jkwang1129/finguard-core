import assert from "node:assert/strict";
import { chromium } from "playwright";
import fs from "node:fs/promises";
import path from "node:path";
const browser = await chromium.launch({ channel: "chrome" });
try {
  const page = await browser.newPage({ viewport: { width: 390, height: 844 } });
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
  await page.goto("http://console.test/demo/index.html#accounts");
  await page.getByRole("form", { name: "创建账户", exact: true }).waitFor();
  const sizes = await page.evaluate(() => ({
    width: innerWidth,
    scroll: document.documentElement.scrollWidth,
    overflow: [
      ...document.querySelectorAll(
        "aside,nav,main,.topbar,.session-panels,.card",
      ),
    ].map((n) => ({
      tag: n.tagName,
      class: n.className,
      width: n.getBoundingClientRect().width,
      right: n.getBoundingClientRect().right,
    })),
  }));
  console.log(JSON.stringify(sizes));
  assert.ok(
    sizes.scroll <= sizes.width,
    "390px layout must contain horizontal overflow within navigation/table",
  );
  console.log("PASS 390px layout");
} finally {
  await browser.close();
}
