import { chromium } from "playwright";
import fs from "node:fs/promises";
import path from "node:path";
const args = Object.fromEntries(
  process.argv
    .slice(2)
    .reduce(
      (pairs, arg, i, all) =>
        arg.startsWith("--") ? [...pairs, [arg.slice(2), all[i + 1]]] : pairs,
      [],
    ),
);
const baseUrl = args["base-url"] ?? "http://127.0.0.1:28080",
  variant = args.variant ?? "baseline",
  groups = (args.cases ?? "business,scenarios,reliability,accessibility").split(
    ",",
  );
if (
  !groups.every((g) =>
    ["business", "scenarios", "reliability", "accessibility"].includes(g),
  )
)
  throw new Error("Unknown cases");
const evidenceDir = path.resolve(
  args["evidence-dir"] ?? "scripts/demo/results/manual",
);
await fs.mkdir(evidenceDir, { recursive: true });
const credentials = {
  ADMIN: {
    username: process.env.FINGUARD_CONSOLE_ADMIN_USERNAME,
    password: process.env.FINGUARD_CONSOLE_ADMIN_PASSWORD,
  },
  REVIEWER: {
    username: process.env.FINGUARD_CONSOLE_REVIEWER_USERNAME,
    password: process.env.FINGUARD_CONSOLE_REVIEWER_PASSWORD,
  },
};
if (Object.values(credentials).some((c) => !c.username || !c.password))
  throw new Error("Missing process-only acceptance credentials");
const browser = await chromium.launch({
  channel: args["browser-channel"] || undefined,
});
const results = [];
try {
  for (const group of groups) {
    try {
      const module = await import(`./browser-cases/${group}.mjs`);
      results.push(
        ...(await module.run({
          browser,
          baseUrl,
          credentials,
          evidenceDir,
          variant,
        })),
      );
    } catch (error) {
      let message = String(error.stack ?? error);
      for (const value of Object.values(credentials).flatMap((c) =>
        Object.values(c),
      ))
        message = message.replaceAll(value, "<redacted>");
      message = message.replace(/Bearer\s+[^\s"']+/g, "Bearer <redacted>");
      results.push({ name: group, status: "FAIL", evidence: message });
      console.error(`FAIL ${group}: ${message}`);
      process.exitCode = 1;
      break;
    }
  }
} finally {
  await browser.close();
  await fs.writeFile(
    path.join(evidenceDir, "browser-results.json"),
    JSON.stringify(
      { observedAt: new Date().toISOString(), variant, results },
      null,
      2,
    ),
  );
}
console.log(
  `Browser acceptance: ${results.filter((r) => r.status === "PASS").length} passed, ${results.filter((r) => r.status === "FAIL").length} failed`,
);
