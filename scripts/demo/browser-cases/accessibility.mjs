import assert from "node:assert/strict";
import path from "node:path";
import { open, go, pass } from "./helpers.mjs";
export async function run(ctx) {
  const page = await open(ctx);
  try {
    await go(page, "guided");
    const form = page.getByRole("form", { name: "预览场景" });
    await form.getByRole("button", { name: "生成并预览场景" }).click();
    await page
      .getByRole("button", { name: "执行下一步", exact: true })
      .waitFor();
    let steps = 0;
    while (
      !(await page
        .getByRole("button", { name: "执行下一步", exact: true })
        .isDisabled())
    ) {
      await page
        .getByRole("button", { name: "执行下一步", exact: true })
        .click();
      await page.waitForFunction(
        () => !document.querySelector(".toolbar button")?.dataset.busy,
      );
      const feedback = await page.locator(".feedback").allTextContents();
      assert.equal(
        feedback.some(
          (x) =>
            x.includes("未知场景") ||
            x.includes("HTTP 400") ||
            x.includes("Error"),
        ),
        false,
      );
      steps++;
      assert.ok(steps < 20);
    }
    assert.ok(steps >= 8);
    await page.screenshot({
      path: path.join(ctx.evidenceDir, "desktop.png"),
      fullPage: true,
    });
    await page.setViewportSize({ width: 390, height: 844 });
    await go(page, "accounts");
    await page
      .getByRole("heading", { name: "账户管理", exact: true, level: 2 })
      .waitFor();
    assert.ok(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= window.innerWidth,
      ),
    );
    await page
      .getByRole("form", { name: "创建账户", exact: true })
      .getByLabel("账号", { exact: true })
      .focus();
    await page.keyboard.press("Tab");
    assert.equal(
      await page.evaluate(() => document.activeElement?.name),
      "accountName",
    );
    await page.screenshot({
      path: path.join(ctx.evidenceDir, "mobile-390.png"),
      fullPage: true,
    });
    const unlabeled = await page.evaluate(
      () =>
        [...document.querySelectorAll("input,select,textarea")].filter(
          (n) => !n.labels?.length && !n.getAttribute("aria-label"),
        ).length,
    );
    assert.equal(unlabeled, 0);
    return [
      pass("原调参演示迁移与单步真实闭环", { steps }),
      pass("桌面、390px、键盘与表单标签", {
        screenshots: ["desktop.png", "mobile-390.png"],
      }),
    ];
  } finally {
    await page.close();
  }
}
