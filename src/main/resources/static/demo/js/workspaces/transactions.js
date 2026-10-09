import {
  panel,
  el,
  form,
  button,
  detail,
  positiveId,
  decimal,
  taskLookup,
  perform,
  setFeedback,
  common,
} from "../ui.js";
import { write, list, query, output } from "./kit.js";
import { createLatest } from "../lifecycle.js";
export const isEditable = (row) => row.source === "MANUAL";
export function body(v) {
  return {
    direction: v.direction,
    amount: decimal(v.amount),
    transactionTime: v.transactionTime,
    description: v.description || null,
  };
}
export function createActions(ctx) {
  const path = (id) => `/api/transactions/${positiveId(id)}`;
  return {
    query: (f, s) => query(ctx, "/api/transactions", f, s),
    get: (id, signal) => ctx.request(path(id), { signal }),
    create: (v) =>
      write(ctx, "/api/transactions", {
        json: {
          accountId: positiveId(v.accountId),
          externalTransactionNo: v.externalTransactionNo,
          ...body(v),
        },
        action: "创建人工交易",
      }),
    update: async (row, v) => {
      if (!isEditable(row)) throw new Error("CSV 来源交易不可修改");
      return write(ctx, path(row.id), {
        method: "PUT",
        json: body(v),
        action: "替换人工交易",
        target: row.id,
      });
    },
    remove: async (row) => {
      if (!isEditable(row)) throw new Error("CSV 来源交易不可删除");
      return write(ctx, path(row.id), {
        method: "DELETE",
        action: "删除人工交易",
        target: row.id,
        destructive: true,
      });
    },
  };
}
const fields = (row) => [
  {
    name: "direction",
    label: "方向",
    options: ["INCOME", "EXPENSE"],
    value: row?.direction ?? "EXPENSE",
  },
  {
    name: "amount",
    label: "金额（十进制）",
    value: row?.amount ?? "0.10",
    required: true,
  },
  {
    name: "transactionTime",
    label: "业务时间（Asia/Shanghai，无 UTC 转换）",
    type: "datetime-local",
    step: "any",
    value:
      row?.transactionTime ??
      new Date(Date.now() + 8 * 3600000).toISOString().slice(0, 16),
    required: true,
  },
  {
    name: "description",
    label: "描述",
    type: "textarea",
    value: row?.description ?? "",
  },
];
export function mount(root, ctx) {
  const a = createActions(ctx),
    manager = panel(
      root,
      "交易管理",
      "MANUAL 可修改与删除；CSV 保留导入事实。时间采用业务本地时间，服务端仍验证全部约束。",
    );
  taskLookup(manager, ctx, "transactions", "交易 ID");
  const current = panel(root, "交易详情"),
    { status, details } = output(current);
  let listing;
  const detailRead = createLatest(renderDetail);
  function show(id) {
    return detailRead.run((signal) => a.get(id, signal));
  }
  function renderDetail(r) {
    detail(details, r.data);
    const row = r.data;
    details.append(
      button("查看账户", () => ctx.navigate("accounts", { id: row.accountId })),
    );
    if (ctx.role === "ADMIN" && isEditable(row)) {
      form(
        details,
        "替换人工交易",
        fields(row),
        (v, b) =>
          perform(
            ctx,
            b,
            async () => {
              const r = await a.update(row, v);
              if (r) {
                await show(row.id);
                listing.load();
              }
            },
            status,
          ),
        { submit: "保存交易" },
      );
      details.append(
        button("删除交易", (b) =>
          perform(
            ctx,
            b,
            async () => {
              const r = await a.remove(row);
              if (r) {
                details.replaceChildren(el("p", "交易已删除"));
                listing.load();
              }
            },
            status,
          ),
        ),
      );
    }
  }
  listing = list(manager, ctx, {
    title: "筛选交易",
    fields: [
      { name: "accountId", label: "账户 ID", value: ctx.params.accountId },
      {
        name: "direction",
        label: "方向",
        options: [common.all, "INCOME", "EXPENSE"],
      },
      {
        name: "source",
        label: "来源",
        options: [common.all, "MANUAL", "CSV_IMPORT"],
      },
      { name: "externalTransactionNo", label: "外部流水号" },
      { name: "startTime", label: "起始时间", type: "datetime-local" },
      { name: "endTime", label: "结束时间", type: "datetime-local" },
    ],
    initial: ctx.params.accountId ? { accountId: ctx.params.accountId } : {},
    query: a.query,
    columns: [
      ["id", "ID"],
      ["accountId", "账户"],
      ["externalTransactionNo", "流水号"],
      ["direction", "方向"],
      ["amount", "金额"],
      ["transactionTime", "业务时间"],
      ["source", "来源"],
    ],
    actions: [
      {
        label: "详情",
        run: (row) => ctx.navigate("transactions", { id: row.id }),
      },
    ],
  });
  if (ctx.role === "ADMIN") {
    const create = panel(root, "创建人工交易");
    form(
      create,
      "创建人工交易",
      [
        {
          name: "accountId",
          label: "账户 ID",
          required: true,
          value: ctx.params.accountId,
        },
        { name: "externalTransactionNo", label: "外部流水号", required: true },
        ...fields(),
      ],
      (v, b) =>
        perform(
          ctx,
          b,
          async () => {
            const r = await a.create(v);
            if (r) {
              setFeedback(status, {
                status: r.status,
                message: `已创建交易 ${r.data.id}`,
              });
              await show(r.data.id);
              listing.load();
            }
          },
          status,
        ),
      { submit: "创建交易" },
    );
  }
  if (ctx.params.id && ctx.identity(ctx.role))
    show(ctx.params.id).catch((e) => setFeedback(status, e));
  return {
    ...listing,
    dispose() {
      detailRead.dispose();
      listing.dispose();
    },
  };
}
