import {
  panel,
  el,
  form,
  button,
  detail,
  perform,
  setFeedback,
} from "../ui.js";
import { output } from "./kit.js";
export function monitorUrl(value) {
  const url = new URL(value);
  if (
    !["http:", "https:"].includes(url.protocol) ||
    url.username ||
    url.password
  )
    throw new Error("监控地址只接受无凭据的 http/https URL");
  return url.href;
}
export function sanitizeEvidence(value) {
  const result = {};
  for (const key of ["name", "step", "httpStatus", "code", "observedAt"])
    if (["string", "number"].includes(typeof value[key]))
      result[key] = value[key];
  result.taskIds = {};
  for (const key of [
    "accountId",
    "importJobId",
    "secondImportJobId",
    "reconciliationJobId",
    "reviewTaskIds",
    "manualTransactionIds",
  ]) {
    const v = value.taskIds?.[key];
    if (Array.isArray(v) && v.every((n) => Number.isSafeInteger(n) && n > 0))
      result.taskIds[key] = v;
    else if (Number.isSafeInteger(v) && v > 0) result.taskIds[key] = v;
  }
  result.states = {};
  for (const [key, v] of Object.entries(value.states ?? {}))
    if (
      [
        "account",
        "import",
        "secondImport",
        "reconciliation",
        "decision",
      ].includes(key) &&
      /^[A-Z_]{1,30}$/.test(v)
    )
      result.states[key] = v;
  result.counts = {};
  for (const key of [
    "totalRows",
    "successRows",
    "failedRows",
    "duplicateRows",
    "totalCount",
    "matchedCount",
    "unmatchedCount",
    "duplicateCount",
    "suspiciousCount",
  ])
    if (Number.isSafeInteger(value.actual?.[key]))
      result.counts[key] = value.actual[key];
  return result;
}
export const evidenceCards = [
  {
    title: "MQ / Outbox / ACK / 重试 / 死信 / 消费者幂等",
    mechanism:
      "事务 Outbox 保存待发布事件；确认发布与消费者处理分离。重试/DLQ 必须用任务、队列与数据库共同核对。",
    command:
      "powershell -NoProfile -File scripts/drills/invoke-messaging-retry-drill.ps1 -EnvironmentFile <独立临时.env> -ProjectName finguard-day6 -BaseUrl http://127.0.0.1:18080",
    signal:
      "Outbox SENT、消费去重记录、任务终态、retry/DLQ 队列及 finguard 指标",
    doc: "docs/review/week6-day6-fault-drills.md",
    historicalDate: "2026-08-12 至 2026-08-13（原报告执行窗口）",
    verified: false,
  },
  {
    title: "Redis 缓存 / 失效 / 故障恢复",
    mechanism:
      "先读统计，再写业务并读统计；相同响应不能证明缓存命中。须核对 Redis key/TTL、失效事件与降级后的恢复。",
    command:
      "powershell -NoProfile -File scripts/drills/invoke-redis-outage-drill.ps1 -EnvironmentFile <独立临时.env> -ProjectName finguard-day6 -BaseUrl http://127.0.0.1:18080",
    signal: "Redis 不可达时业务行为、缓存命中/TTL、恢复后的健康与统计",
    doc: "docs/review/week6-day6-fault-drills.md",
    historicalDate:
      "2026-08-12 至 2026-08-13（Day 6 原报告窗口；具体项目见正文）",
    verified: false,
  },
  {
    title: "幂等 / 权限 / 乐观锁 / 限流",
    mechanism:
      "文件哈希复用、业务行去重、对账任务复用、疑似重复风险是四种机制。按钮限制不能证明后端权限。",
    command:
      "逐步演示：FILE_DUPLICATE / ROW_DUPLICATE / VERSION_CONFLICT / ANONYMOUS_401 / REVIEWER_WRITE_403 / ADMIN_DECISION_403。限流在独立环境按部署文档配置小阈值后受控触发。",
    signal:
      "实际 200/202、相同 ID、duplicate 标记、401/403/409/429 和 Retry-After",
    doc: "docs/SECURITY_TEST_REPORT.md",
    historicalDate:
      "2026-08-12 至 2026-08-13（Day 6 原报告窗口；具体项目见正文）",
    verified: false,
  },
  {
    title: "SQL / 认证压测",
    mechanism:
      "先确保同一业务请求 HTTP 200，再收集执行计划、吞吐、p95 和错误率。全 401 样本不是性能证据。",
    command:
      "按 docs/PERFORMANCE_REPORT.md 复现 JMeter 与 EXPLAIN ANALYZE；使用隔离数据库和有效的进程令牌。",
    signal: "请求成功率、执行计划与测试人口/环境/采集时间",
    doc: "docs/PERFORMANCE_REPORT.md",
    historicalDate: "2026-08-12（Day 5 验收）",
    verified: false,
  },
  {
    title: "安全 / CI/CD / 部署 / 回滚",
    mechanism:
      "代码测试通过、CI 构建成功、线上进程运行和数据恢复是独立证据。此页面不启动服务器命令。",
    command:
      "mvn.cmd -B -ntp clean test；按 docs/DEPLOYMENT.md 做部署与回滚；核对 .github/workflows 的当次运行。",
    signal: "测试/CI 运行 ID、镜像版本、health、备份与实际恢复记录",
    doc: "docs/DEPLOYMENT.md",
    historicalDate:
      "2026-08-12 至 2026-08-13（Day 6 原报告窗口；具体项目见正文）",
    verified: false,
  },
];
export async function readSignals(ctx) {
  async function read(path, kind) {
    try {
      const r = await ctx.request(path);
      const verified =
        kind === "health"
          ? r.data?.status === "UP"
          : typeof r.data === "string" &&
            r.data
              .split("\n")
              .some((line) =>
                /^[a-zA-Z_:][a-zA-Z0-9_:]*(?:\{.*\})?\s+[-+\d.]/.test(line),
              );
      return {
        status: r.status,
        verified,
        actual: r.data,
        observedAt: new Date().toISOString(),
      };
    } catch (e) {
      if (e.name === "AbortError") throw e;
      return {
        status: e.status ?? 0,
        verified: false,
        code: e.code,
        message: e.message,
        observedAt: new Date().toISOString(),
      };
    }
  }
  const [health, metrics] = await Promise.all([
    read("/actuator/health", "health"),
    read("/actuator/prometheus", "metrics"),
  ]);
  return { health, metrics };
}
export function mount(root, ctx) {
  const live = panel(
      root,
      "实时健康与指标",
      "仅当前采集成功的信号标为已验证。指标可达不等于 MQ/缓存/故障演练已通过。",
    ),
    { status, details } = output(live);
  live.append(
    button("采集健康与指标", (b) =>
      perform(
        ctx,
        b,
        async () => {
          const r = await readSignals(ctx);
          detail(details, {
            health: { ...r.health },
            metrics: {
              status: r.metrics.status,
              verified: r.metrics.verified,
              observedAt: r.metrics.observedAt,
              message: r.metrics.message,
            },
          });
          const metrics = el("details");
          metrics.append(
            el("summary", "查看实际指标（最多 150 行）"),
            el(
              "pre",
              typeof r.metrics.actual === "string"
                ? r.metrics.actual
                    .split("\n")
                    .filter(
                      (x) =>
                        x.includes("finguard") ||
                        x.startsWith("jvm_") ||
                        x.startsWith("http_"),
                    )
                    .slice(0, 150)
                    .join("\n")
                : "无指标内容",
            ),
          );
          details.append(metrics);
          setFeedback(status, {
            message: `健康：${r.health.verified ? "已采集 UP" : "未验证"}；指标：${r.metrics.verified ? "已采集" : "未验证"}`,
          });
        },
        status,
      ),
    ),
  );
  const monitors = panel(
      root,
      "监控入口",
      "链接由你配置；跨端口直接打开，不代理任意 URL。未打开与核对前保持未验证。",
    ),
    links = el("div");
  monitors.append(links);
  form(
    monitors,
    "配置监控地址",
    [
      {
        name: "prometheus",
        label: "Prometheus URL",
        value: "http://127.0.0.1:9090",
      },
      { name: "grafana", label: "Grafana URL", value: "http://127.0.0.1:3000" },
    ],
    (v, b) =>
      perform(
        ctx,
        b,
        async () => {
          const entries = Object.entries(v).map(([key, value]) => [
            key,
            monitorUrl(value),
          ]);
          links.replaceChildren();
          for (const [key, url] of entries)
            links.append(
              el("a", `${key} · 入口未验证`, {
                href: url,
                target: "_blank",
                rel: "noopener noreferrer",
              }),
              el("br"),
            );
        },
        status,
      ),
    { submit: "设置监控链接" },
  );
  const isolation = panel(
    root,
    "维护者隔离演练边界",
    "浏览器不获取 Docker、数据库或服务器凭据。已有演练脚本只允许 finguard-day6，须创建独立临时环境与不同端口，不能与控制台栈共享消费者。",
  );
  isolation.append(
    el(
      "pre",
      "1. 生成仅进程使用的随机凭据与临时 .env。\n2. FINGUARD_CONTAINER_PREFIX=finguard-day6，设置独立端口；先检查名称/端口归属。\n3. docker compose --project-name finguard-day6 --env-file <临时.env> up -d --wait\n4. 运行下列脚本，保存实际队列/任务/数据库与恢复证据。\n5. 核对 Compose labels 后，只清理该隔离项目；删除自有临时凭据文件。",
    ),
  );
  for (const card of evidenceCards) {
    const node = panel(root, card.title, card.mechanism);
    node.append(
      el("pre", card.command),
      el("p", `观察：${card.signal}`),
      el(
        "p",
        `当前演练：未验证。历史资料日期：${card.historicalDate}；以报告正文环境为准。`,
      ),
      el("a", "打开仓库历史资料", {
        href: `https://github.com/jkwang1129/finguard-core/blob/main/${card.doc}`,
        target: "_blank",
        rel: "noopener noreferrer",
      }),
    );
  }
  return { dispose() {} };
}
