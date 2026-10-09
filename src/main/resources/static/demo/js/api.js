import { authorization } from "./session.js";

export class ApiError extends Error {
  constructor(status, code, message, extra = {}) {
    super(message);
    this.name = "ApiError";
    Object.assign(this, { status, code }, extra);
  }
}
// Tokenize whole JSON strings before numbers: text inside descriptions is never rewritten.
export function parseJson(text) {
  let money = false;
  const preserved = text.replace(
    /"(?:[^"\\]|\\.)*"(?:\s*:\s*)?|-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?/g,
    (token) => {
      if (token.startsWith('"')) {
        money = /^"amount"\s*:/.test(token);
        return token;
      }
      const result = money ? JSON.stringify(token) : token;
      money = false;
      return result;
    },
  );
  return JSON.parse(preserved);
}
export function createClient({
  fetch = (...args) => globalThis.fetch(...args),
  authorization: auth = () => null,
  now = () => Date.now(),
  sleep = (ms, signal) =>
    new Promise((resolve, reject) => {
      const timer = setTimeout(done, ms);
      function done() {
        signal?.removeEventListener("abort", abort);
        resolve();
      }
      function abort() {
        clearTimeout(timer);
        reject(new DOMException("Cancelled", "AbortError"));
      }
      if (signal?.aborted) abort();
      else signal?.addEventListener("abort", abort, { once: true });
    }),
} = {}) {
  async function request(
    path,
    { method = "GET", role, json, form, signal, timeoutMs = 10000 } = {},
  ) {
    if (!/^\/(api\/|actuator\/)/.test(path))
      throw new ApiError(0, "INVALID_PATH", "请求地址无效");
    signal?.throwIfAborted();
    const controller = new AbortController();
    let timedOut = false;
    const cancel = () => controller.abort(signal.reason);
    signal?.addEventListener("abort", cancel, { once: true });
    const timer = setTimeout(() => {
      timedOut = true;
      controller.abort();
    }, timeoutMs);
    const headers = { Accept: "application/json, text/plain, */*" };
    const bearer = auth(role);
    if (bearer) headers.Authorization = bearer;
    if (json !== undefined) headers["Content-Type"] = "application/json";
    try {
      const response = await fetch(path, {
        method,
        headers,
        credentials: "omit",
        body: form ?? (json === undefined ? undefined : JSON.stringify(json)),
        signal: controller.signal,
      });
      signal?.throwIfAborted();
      if (timedOut)
        throw new ApiError(
          0,
          "REQUEST_TIMEOUT",
          "请求超时，写入结果请从历史查询",
        );
      const text = await response.text();
      signal?.throwIfAborted();
      let data = text;
      if (
        text &&
        (response.headers.get("content-type")?.includes("json") ||
          /^[\[{]/.test(text.trim()))
      ) {
        try {
          data = parseJson(text);
        } catch {
          throw new ApiError(
            response.status,
            "INVALID_RESPONSE",
            "服务端返回内容无法解析",
          );
        }
      }
      const retryAfter = response.headers.get("Retry-After");
      if (!response.ok)
        throw new ApiError(
          response.status,
          data?.code ?? "HTTP_ERROR",
          data?.message ?? `HTTP ${response.status}`,
          { fieldErrors: data?.fieldErrors, retryAfter },
        );
      return { status: response.status, data, retryAfter };
    } catch (error) {
      if (signal?.aborted) throw new DOMException("Cancelled", "AbortError");
      if (error instanceof ApiError) throw error;
      if (timedOut)
        throw new ApiError(
          0,
          "REQUEST_TIMEOUT",
          "请求超时，写入结果请从历史查询",
        );
      throw new ApiError(
        0,
        "NETWORK_ERROR",
        "网络不可达；写入不会自动重试，请查询历史确认结果",
      );
    } finally {
      clearTimeout(timer);
      signal?.removeEventListener("abort", cancel);
    }
  }
  async function pollJob(
    path,
    {
      role,
      terminalStatuses,
      signal,
      onUpdate = () => {},
      timeoutMs = 90000,
      intervalMs = 1000,
    },
  ) {
    const start = now();
    let last = null;
    while (now() - start < timeoutMs) {
      signal?.throwIfAborted();
      try {
        last = await request(path, {
          role,
          signal,
          timeoutMs: Math.min(10000, timeoutMs - (now() - start)),
        });
      } catch (error) {
        if (error.code === "REQUEST_TIMEOUT" && now() - start >= timeoutMs)
          break;
        throw error;
      }
      onUpdate(last);
      if (terminalStatuses.includes(last.data?.status)) return last;
      await sleep(Math.min(intervalMs, timeoutMs - (now() - start)), signal);
    }
    throw new ApiError(
      0,
      "POLL_TIMEOUT",
      "轮询达到时限；保留最后状态，可继续查询",
      { last },
    );
  }
  async function readAllPages(
    path,
    { role, query = {}, signal, onPage = () => {} } = {},
  ) {
    const records = [];
    let page = 1;
    while (true) {
      const params = new URLSearchParams({
        ...query,
        page: String(page),
        size: "100",
      });
      const result = await request(`${path}?${params}`, { role, signal });
      const data = result.data;
      if (
        !data ||
        !Array.isArray(data.records) ||
        !Number.isInteger(data.total) ||
        data.total < 0 ||
        data.page !== page ||
        !Number.isInteger(data.pages) ||
        !Number.isInteger(data.size) ||
        data.size < 1 ||
        data.size > 100
      )
        throw new ApiError(
          0,
          "INVALID_PAGE",
          "分页响应无效，不能确认已加载全部记录",
        );
      records.push(...data.records);
      onPage({ page, total: data.total, loaded: records.length });
      if (page >= data.pages) return records;
      if (!data.records.length)
        throw new ApiError(0, "INVALID_PAGE", "分页提前结束，未加载完整记录");
      page++;
    }
  }
  return { request, pollJob, readAllPages };
}
const client = createClient({ authorization });
export const { request, pollJob, readAllPages } = client;
