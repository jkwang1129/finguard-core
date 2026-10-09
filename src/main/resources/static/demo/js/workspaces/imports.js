import {
  panel,
  el,
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
export const canReconcile = (job) =>
  ["SUCCESS", "PARTIAL_SUCCESS"].includes(job.status);
export async function preview(file) {
  return (await file.slice(0, 16384).text())
    .replace(/^\uFEFF/, "")
    .replace(/\r\n/g, "\n");
}
export function createActions(ctx) {
  let busy = false,
    savedFile,
    receipt;
  const path = (id) => `/api/import-jobs/${positiveId(id)}`;
  async function send(file, repeated = false) {
    if (busy) return null;
    busy = true;
    try {
      const form = new FormData();
      form.append("file", file, file.name);
      const r = await write(ctx, "/api/import-jobs", {
        form,
        action: repeated ? "主动重复上传验证" : "上传 CSV",
        target: file.name,
      });
      if (r) {
        if (
          repeated &&
          (r.data.id !== receipt.data.id || !r.data.duplicateFile)
        )
          throw new Error("重复响应没有复用原任务，请核对服务端事实");
        savedFile = file;
        receipt = r;
      }
      return r;
    } finally {
      busy = false;
    }
  }
  return {
    query: (f, s) => query(ctx, "/api/import-jobs", f, s),
    get: (id, signal) => ctx.request(path(id), { signal }),
    upload: (file) => send(file),
    repeat: () => {
      if (!savedFile) throw new Error("当前会话没有原始文件");
      return send(savedFile, true);
    },
    errors: (id, { page = 1, size = 20 }, signal) =>
      ctx.request(`${path(id)}/errors?${queryString({ page, size })}`, {
        signal,
      }),
    poll: (id, options = {}) =>
      ctx.pollJob(path(id), {
        terminalStatuses: ["SUCCESS", "PARTIAL_SUCCESS", "FAILED"],
        ...options,
      }),
  };
}
export function mount(root, ctx) {
  const a = createActions(ctx),
    manager = panel(
      root,
      "导入历史",
      "重复文件复用任务；不同文件里的同业务行会计入 duplicateRows。行错误接口只支持页号和页大小。",
    );
  taskLookup(manager, ctx, "imports");
  const current = panel(root, "导入任务"),
    { status, details } = output(current),
    controls = el("div", null, { class: "toolbar" }),
    errors = el("div");
  current.append(controls, errors);
  let selected, selectedSignal, errorList;
  function render(r) {
    detail(details, r.data);
    setFeedback(status, {
      status: r.status,
      message: `任务 ${r.data.id} · ${r.data.status} · duplicateFile=${r.data.duplicateFile}`,
    });
    controls.replaceChildren(
      button("继续查询（GET）", (b) =>
        perform(ctx, b, () => show(r.data.id), status),
      ),
    );
    if (canReconcile(r.data))
      controls.append(
        button("发起对账", () =>
          ctx.navigate("reconciliation", { importJobId: r.data.id }),
        ),
      );
  }
  async function show(id, receipt) {
    selected?.abort();
    errorList?.dispose();
    selected = new AbortController();
    selectedSignal = AbortSignal.any([ctx.signal, selected.signal]);
    if (receipt) render(receipt);
    else render(await a.get(id, selectedSignal));
    errors.replaceChildren();
    errorList = list(errors, ctx, {
      title: "导入行错误",
      query: (f, s) => a.errors(id, f, AbortSignal.any([s, selectedSignal])),
      columns: [
        ["rowNumber", "行号"],
        ["field", "字段"],
        ["errorCode", "错误码"],
        ["rejectedValue", "原值"],
        ["message", "原因"],
      ],
    });
    if (
      !["SUCCESS", "PARTIAL_SUCCESS", "FAILED"].includes(receipt?.data.status)
    )
      try {
        await a.poll(id, { signal: selectedSignal, onUpdate: render });
      } catch (error) {
        if (error.code === "POLL_TIMEOUT" && error.last) render(error.last);
        throw error;
      }
    errorList.load();
  }
  const listing = list(manager, ctx, {
    title: "筛选导入历史",
    fields: [
      {
        name: "status",
        label: "状态",
        options: [
          common.all,
          "PENDING",
          "PROCESSING",
          "SUCCESS",
          "PARTIAL_SUCCESS",
          "FAILED",
        ],
      },
      { name: "createdBy", label: "创建者 ID" },
    ],
    query: a.query,
    columns: [
      ["id", "ID"],
      ["originalFileName", "文件名"],
      ["status", "状态"],
      ["successRows", "成功行"],
      ["failedRows", "错误行"],
      ["duplicateRows", "重复行"],
      ["createdAt", "创建时间"],
    ],
    actions: [
      { label: "详情", run: (row) => ctx.navigate("imports", { id: row.id }) },
    ],
  });
  if (ctx.role === "ADMIN") {
    const upload = panel(
        root,
        "上传任意 CSV",
        "预览最多 16 KiB；上传保留原始文件字节，不会将预览内容重新编码。",
      ),
      label = el("label", "选择 CSV", { for: "csv-file" }),
      file = el("input", null, {
        id: "csv-file",
        type: "file",
        accept: ".csv,text/csv",
        "aria-label": "选择 CSV",
      }),
      text = el("pre");
    upload.append(label, file, text);
    let previewGeneration = 0;
    file.addEventListener("change", async () => {
      const active = ++previewGeneration;
      try {
        const value = file.files[0] ? await preview(file.files[0]) : "";
        if (active === previewGeneration && !ctx.signal.aborted)
          text.textContent = value;
      } catch (e) {
        setFeedback(status, e);
      }
    });
    const repeat = button(
      "主动重复提交验证",
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
    upload.append(
      button("上传 CSV", (b) =>
        perform(
          ctx,
          b,
          async () => {
            if (!file.files[0]) throw new Error("请先选择文件");
            const r = await a.upload(file.files[0]);
            if (r) {
              repeat.disabled = false;
              await show(r.data.id, r);
              listing.load();
            }
          },
          status,
        ),
      ),
      repeat,
    );
  }
  if (ctx.params.id && ctx.identity(ctx.role))
    show(ctx.params.id).catch((e) => {
      if (e.name !== "AbortError" && !ctx.signal.aborted)
        setFeedback(status, e);
    });
  return {
    dispose() {
      listing.dispose();
      selected?.abort();
      errorList?.dispose();
    },
  };
}
