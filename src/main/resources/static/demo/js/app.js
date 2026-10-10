import { request, pollJob, readAllPages } from "./api.js";
import {
  login,
  logout,
  identity,
  subscribeSession,
  configureSession,
} from "./session.js";
import { el, button, form, feedback, setFeedback } from "./ui.js";
configureSession(request);
const workspaces = {
  guided: "逐步演示",
  accounts: "账户管理",
  transactions: "交易管理",
  imports: "CSV 导入",
  reconciliation: "对账调查",
  reviews: "人工审核",
  insights: "审计与统计",
  engineering: "工程与证据",
};
const root = document.querySelector("#workspace"),
  events = document.querySelector("#events");
let role = "ADMIN",
  controller,
  dispose,
  generation = 0;
function record(text) {
  events.prepend(el("li", `${new Date().toLocaleTimeString()} · ${text}`));
  while (events.children.length > 60) events.lastChild.remove();
}
export function navigate(workspace, params = {}) {
  if (!Object.hasOwn(workspaces, workspace)) return;
  const safe = Object.entries(params).filter(
    ([key, value]) =>
      ["id", "accountId", "importJobId"].includes(key) &&
      /^[1-9]\d*$/.test(String(value)),
  );
  const next = `#${workspace}${safe.length ? "?" + new URLSearchParams(safe) : ""}`;
  if (location.hash === next) activate();
  else location.hash = next;
}
async function activate() {
  controller?.abort();
  dispose?.();
  controller = new AbortController();
  dispose = null;
  const active = ++generation;
  const [name, query] = location.hash.slice(1).split("?"),
    workspace = Object.hasOwn(workspaces, name) ? name : "guided",
    params = {};
  for (const [key, value] of new URLSearchParams(query))
    if (
      ["id", "accountId", "importJobId"].includes(key) &&
      /^[1-9]\d*$/.test(value)
    )
      params[key] = value;
  const signal = controller.signal,
    selectedRole = role;
  root.replaceChildren();
  document.querySelector("#workspace-title").textContent =
    workspaces[workspace];
  for (const link of document.querySelectorAll("#navigation a"))
    link.setAttribute(
      "aria-current",
      link.dataset.workspace === workspace ? "page" : "false",
    );
  const wrap =
    (fn) =>
    async (path, options = {}) => {
      const result = await fn(path, {
        ...options,
        role: options.role ?? selectedRole,
        signal: options.signal
          ? AbortSignal.any([signal, options.signal])
          : signal,
      });
      signal.throwIfAborted();
      record(
        `${options.method ?? "GET"} ${path.split("?")[0]}${result.status ? " · HTTP " + result.status : ""}`,
      );
      return result;
    };
  const ctx = {
    role: selectedRole,
    params,
    signal,
    request: wrap(request),
    pollJob: wrap(pollJob),
    readAllPages: wrap(readAllPages),
    identity,
    navigate,
    confirmWrite: async ({ action, target, destructive = false }) => {
      if (!document.querySelector("#persistence-ack").checked) {
        window.alert("请先确认演示数据会写入当前数据库");
        return false;
      }
      return (
        !destructive ||
        window.confirm(
          `${action}：${target}\n此操作会修改持久化数据。删除要求账户已停用且无交易历史。`,
        )
      );
    },
  };
  try {
    const module = await import(`./workspaces/${workspace}.js`);
    if (active === generation) dispose = module.mount(root, ctx)?.dispose;
  } catch (error) {
    if (active === generation) {
      setFeedback(feedback(root), error);
    }
  }
}
for (const [name, title] of Object.entries(workspaces)) {
  const link = el("a", title, { href: `#${name}`, "data-workspace": name });
  document.querySelector("#navigation").append(link);
}
for (const sessionRole of ["ADMIN", "REVIEWER"]) {
  const box = el("details"),
    summary = el("summary", `${sessionRole} 登录`),
    user = el("p", "未登录", { class: "muted" });
  box.append(summary, user);
  const status = feedback(box);
  form(
    box,
    `${sessionRole} 登录`,
    [
      { name: "username", label: `${sessionRole} 用户名`, required: true },
      {
        name: "password",
        label: `${sessionRole} 密码`,
        type: "password",
        required: true,
      },
    ],
    async (values, target, node) => {
      target.disabled = true;
      try {
        await login(sessionRole, values.username, values.password);
        node.elements.password.value = "";
        role = sessionRole;
        document.querySelector("#active-role").value = role;
        setFeedback(status, { message: "已验证服务端身份" });
        activate();
      } catch (error) {
        if (error.name !== "AbortError") setFeedback(status, error);
      } finally {
        target.disabled = false;
      }
    },
    { submit: "登录" },
  );
  box.append(button("注销", () => logout(sessionRole)));
  subscribeSession((changed) => {
    if (changed === sessionRole)
      user.textContent = identity(sessionRole)
        ? `${identity(sessionRole).username} · ${identity(sessionRole).roles.join(", ")}`
        : "未登录";
  });
  document.querySelector("#sessions").append(box);
}
subscribeSession(() => activate());
document.querySelector("#active-role").addEventListener("change", (event) => {
  role = event.target.value;
  activate();
});
window.addEventListener("hashchange", activate);
activate();
