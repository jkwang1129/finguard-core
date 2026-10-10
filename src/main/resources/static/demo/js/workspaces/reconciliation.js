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
  queryString,
} from "../ui.js";
import { write, list, query, output } from "./kit.js";
export function createActions(ctx) {
  let original;
  const path = (id) => `/api/reconciliation-jobs/${positiveId(id)}`;
  async function create(importJobId, repeated = false) {
    positiveId(importJobId);
    const job = await ctx.request(`/api/import-jobs/${importJobId}`);
    if (!["SUCCESS", "PARTIAL_SUCCESS"].includes(job.data.status))
      throw new Error("导入尚未成功，不能发起对账");
    const r = await write(ctx, "/api/reconciliation-jobs", {
      json: { importJobId: String(importJobId) },
      action: repeated ? "主动重复对账验证" : "创建对账",
      target: importJobId,
    });
    if (r) {
      if (repeated && (r.data.id !== original.id || !r.data.duplicateRequest))
        throw new Error("响应未复用原对账任务");
      original = { id: r.data.id, importJobId };
    }
    return r;
  }
  return {
    query: (f, s) => query(ctx, "/api/reconciliation-jobs", f, s),
    get: (id, signal) => ctx.request(path(id), { signal }),
    create,
    repeat: () => {
      if (!original) throw new Error("请先在当前工作区创建对账");
      return create(original.importJobId, true);
    },
    results: (id, { page = 1, size = 20, resultType }, signal) =>
      ctx.request(
        `${path(id)}/results?${queryString({ page, size, resultType })}`,
        { signal },
      ),
    poll: (id, options = {}) =>
      ctx.pollJob(path(id), {
        terminalStatuses: ["COMPLETED", "FAILED"],
        ...options,
      }),
  };
}
export function mount(root, ctx) {
  const a = createActions(ctx),
    manager = panel(
      root,
      "对账历史",
      "匹配方法与原因只作事实展示。结果列表支持 resultType 服务端筛选。",
    );
  taskLookup(manager, ctx, "reconciliation");
  const current = panel(root, "对账任务"),
    { status, details } = output(current),
    controls = el("div", null, { class: "toolbar" }),
    results = el("div");
  current.append(controls, results);
  let selected, resultList;
  function render(r) {
    detail(details, r.data);
    setFeedback(status, {
      status: r.status,
      message: `任务 ${r.data.id} · ${r.data.status} · duplicateRequest=${r.data.duplicateRequest}`,
    });
    controls.replaceChildren(
      button("继续查询（GET）", (b) =>
        perform(ctx, b, () => show(r.data.id), status),
      ),
      button("查看导入", () =>
        ctx.navigate("imports", { id: r.data.importJobId }),
      ),
    );
  }
  async function show(id, receipt) {
    selected?.abort();
    resultList?.dispose();
    selected = new AbortController();
    const signal = AbortSignal.any([ctx.signal, selected.signal]);
    render(receipt ?? (await a.get(id, signal)));
    results.replaceChildren();
    resultList = list(results, ctx, {
      title: "筛选逐笔结果",
      fields: [
        {
          name: "resultType",
          label: "结果类型",
          options: [
            common.all,
            "MATCHED",
            "UNMATCHED",
            "DUPLICATE",
            "SUSPICIOUS",
          ],
        },
      ],
      query: (f, s) => a.results(id, f, AbortSignal.any([s, signal])),
      columns: [
        ["id", "ID"],
        ["resultType", "结果"],
        ["matchMethod", "匹配方法"],
        ["reasonCode", "原因"],
        ["csvTransactionId", "CSV 交易"],
        ["manualTransactionId", "人工交易"],
      ],
      actions: [
        {
          label: "CSV 详情",
          run: (row) =>
            ctx.navigate("transactions", { id: row.csvTransactionId }),
        },
        {
          label: "人工详情",
          when: (row) => row.manualTransactionId,
          run: (row) =>
            ctx.navigate("transactions", { id: row.manualTransactionId }),
        },
      ],
    });
    if (!["COMPLETED", "FAILED"].includes(receipt?.data.status))
      try {
        await a.poll(id, { signal, onUpdate: render });
      } catch (e) {
        if (e.code === "POLL_TIMEOUT" && e.last) render(e.last);
        throw e;
      }
    resultList.load();
  }
  const listing = list(manager, ctx, {
    title: "筛选对账历史",
    fields: [
      {
        name: "status",
        label: "状态",
        options: [common.all, "PENDING", "PROCESSING", "COMPLETED", "FAILED"],
      },
      { name: "importJobId", label: "导入任务 ID" },
      { name: "createdBy", label: "创建者 ID" },
    ],
    query: a.query,
    columns: [
      ["id", "ID"],
      ["importJobId", "导入任务"],
      ["status", "状态"],
      ["matchedCount", "匹配"],
      ["unmatchedCount", "未匹配"],
      ["duplicateCount", "重复"],
      ["suspiciousCount", "可疑"],
    ],
    actions: [
      {
        label: "详情",
        run: (row) => ctx.navigate("reconciliation", { id: row.id }),
      },
    ],
  });
  if (ctx.role === "ADMIN") {
    const create = panel(root, "从导入创建对账");
    const repeat = button(
      "主动重复创建验证",
      (b) =>
        perform(
          ctx,
          b,
          async () => {
            const r = await a.repeat();
            if (r) await show(r.data.id, r);
          },
          status,
        ),
      { disabled: true },
    );
    form(
      create,
      "创建对账",
      [
        {
          name: "importJobId",
          label: "成功导入任务 ID",
          value: ctx.params.importJobId,
          required: true,
        },
      ],
      (v, b) =>
        perform(
          ctx,
          b,
          async () => {
            const r = await a.create(v.importJobId);
            if (r) {
              repeat.disabled = false;
              await show(r.data.id, r);
              listing.load();
            }
          },
          status,
        ),
      { submit: "创建对账" },
    );
    create.append(repeat);
  }
  if (ctx.params.id && ctx.identity(ctx.role))
    show(ctx.params.id).catch((e) => {
      if (e.name !== "AbortError" && !ctx.signal.aborted)
        setFeedback(status, e);
    });
  return {
    dispose() {
      selected?.abort();
      resultList?.dispose();
      listing.dispose();
    },
  };
}
