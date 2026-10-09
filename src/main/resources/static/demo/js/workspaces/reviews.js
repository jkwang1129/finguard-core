import {
  panel,
  el,
  form,
  button,
  detail,
  positiveId,
  taskLookup,
  perform,
  setFeedback,
  common,
} from "../ui.js";
import { list, query, output } from "./kit.js";
export function filters(f) {
  if (f.resultType && f.ruleCode)
    throw new Error("结果类型与规则码不能同时筛选");
  if (
    f.resultType &&
    f.sourceType &&
    f.sourceType !== "RECONCILIATION_EXCEPTION"
  )
    throw new Error("结果类型要求对账异常来源");
  if (f.ruleCode && f.sourceType && f.sourceType !== "RISK_HIT")
    throw new Error("规则码要求风险来源");
  return {
    ...f,
    sourceType: f.ruleCode
      ? "RISK_HIT"
      : f.resultType
        ? "RECONCILIATION_EXCEPTION"
        : f.sourceType,
  };
}
export function createActions(ctx) {
  const path = (id) => `/api/review-tasks/${positiveId(id)}`;
  return {
    query: (f, s) => query(ctx, "/api/review-tasks", filters(f), s),
    get: (id) => ctx.request(path(id)),
    context: async (id) => {
      if (ctx.role !== "REVIEWER") throw new Error("上下文需要 REVIEWER");
      return ctx.request(`${path(id)}/context`);
    },
    decide: async (row, decision, note, version = row.version) => {
      if (ctx.role !== "REVIEWER") throw new Error("决定需要 REVIEWER");
      if (row.status !== "PENDING") throw new Error("任务已终结，不能重复决定");
      if (!(await ctx.confirmWrite({ action: decision, target: row.id })))
        return null;
      return ctx.request(`${path(row.id)}/decision`, {
        method: "PATCH",
        json: { decision, version, note: note || null },
      });
    },
  };
}
export function mount(root, ctx) {
  const a = createActions(ctx),
    manager = panel(
      root,
      "审核任务",
      "ADMIN 可读任务详情；REVIEWER 可读上下文并提交决定。409 会保留备注，必须主动刷新版本。",
    );
  taskLookup(manager, ctx, "reviews");
  const current = panel(root, "审核详情"),
    { status, details } = output(current),
    contextNode = el("div"),
    decisions = el("div");
  current.append(contextNode, decisions);
  let row,
    oldVersion,
    noteInput,
    saveOld,
    decisionButtons = [];
  async function load() {
    const r = await a.get(ctx.params.id);
    row = r.data;
    detail(details, row);
    details.append(
      button("查看 CSV 交易", () =>
        ctx.navigate("transactions", { id: row.csvTransactionId }),
      ),
    );
    if (ctx.role === "REVIEWER") {
      const c = await a.context(row.id);
      detail(contextNode, c.data);
      if (saveOld)
        saveOld.disabled =
          !c.data.accountSummary?.displayName?.startsWith("DEMO-");
    }
    for (const b of decisionButtons) b.disabled = row.status !== "PENDING";
    return r;
  }
  if (ctx.params.id && ctx.identity(ctx.role)) {
    if (ctx.role === "REVIEWER") {
      const noteLabel = el("label", "审核备注", { for: "review-note" });
      noteInput = el("textarea", null, { id: "review-note" });
      decisions.append(noteLabel, noteInput);
      const decide = (decision) => (b) =>
        perform(
          ctx,
          b,
          async () => {
            const r = await a.decide(row, decision, noteInput.value);
            if (r) {
              await load();
              listing.load();
              setFeedback(status, {
                status: r.status,
                message: `已提交 ${decision}`,
              });
            }
          },
          status,
        );
      decisionButtons = ["CONFIRMED", "IGNORED"].map((d) =>
        button(d === "CONFIRMED" ? "确认审核" : "忽略审核", decide(d)),
      );
      decisions.append(...decisionButtons);
      saveOld = button(
        "保存当前版本（仅 DEMO 任务）",
        () => {
          oldVersion = row.version;
          setFeedback(status, {
            message: `已保存旧版本 ${oldVersion}；先正常决定，再主动提交旧版验证`,
          });
        },
        { disabled: true },
      );
      decisions.append(
        saveOld,
        button("主动提交旧版验证", (b) =>
          perform(
            ctx,
            b,
            async () => {
              if (oldVersion == null) throw new Error("请先保存版本");
              const r = await a.decide(
                { ...row, status: "PENDING" },
                "CONFIRMED",
                noteInput.value,
                oldVersion,
              );
              if (r)
                setFeedback(status, {
                  status: r.status,
                  message: "服务端接受请求，请核对版本事实",
                });
            },
            status,
          ),
        ),
        button("重新加载当前版本", (b) =>
          perform(ctx, b, () => load(), status),
        ),
      );
    }
    load().catch((e) => {
      if (e.name !== "AbortError" && !ctx.signal.aborted)
        setFeedback(status, e);
    });
  }
  const listing = list(manager, ctx, {
    title: "筛选审核",
    fields: [
      {
        name: "status",
        label: "状态",
        options: [common.all, "PENDING", "CONFIRMED", "IGNORED"],
      },
      {
        name: "sourceType",
        label: "来源类型",
        options: [common.all, "RECONCILIATION_EXCEPTION", "RISK_HIT"],
      },
      {
        name: "resultType",
        label: "对账结果",
        options: [
          common.all,
          "UNMATCHED",
          "DUPLICATE",
          "SUSPICIOUS",
        ],
      },
      {
        name: "ruleCode",
        label: "风险规则",
        options: [
          common.all,
          "LARGE_AMOUNT",
          "POSSIBLE_DUPLICATE",
          "FREQUENT_TRANSACTION",
        ],
      },
    ],
    query: a.query,
    columns: [
      ["id", "ID"],
      ["sourceType", "来源"],
      ["resultType", "对账结果"],
      ["ruleCode", "规则"],
      ["status", "状态"],
      ["version", "版本"],
    ],
    actions: [
      {
        label: "详情与决定",
        run: (row) => ctx.navigate("reviews", { id: row.id }),
      },
    ],
  });
  return listing;
}
