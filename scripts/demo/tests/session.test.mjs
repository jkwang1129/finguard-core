import test from "node:test";
import assert from "node:assert/strict";
let mod = {};
try {
  mod = await import("../../../src/main/resources/static/demo/js/session.js");
} catch {}
test("identity comes from server, logout clears authorization and notifies cancellation", async () => {
  assert.equal(typeof mod.createSession, "function");
  const calls = [];
  let changes = 0;
  const s = mod.createSession({
    request: async (path) => {
      calls.push(path);
      return {
        data: path.endsWith("/login")
          ? { accessToken: "test-memory" }
          : { username: "reviewer", roles: ["REVIEWER"] },
      };
    },
  });
  s.subscribeSession(() => changes++);
  await s.login("REVIEWER", "input-label", "password");
  assert.deepEqual(calls, ["/api/auth/login", "/api/auth/me"]);
  assert.equal(s.identity("REVIEWER").username, "reviewer");
  assert.equal(s.authorization("REVIEWER"), "Bearer test-memory");
  s.logout("REVIEWER");
  assert.equal(s.authorization("REVIEWER"), null);
  assert.equal(s.identity("REVIEWER"), null);
  assert.ok(changes >= 2);
});
test("wrong-role login and late login after logout leave no credentials", async () => {
  assert.equal(typeof mod.createSession, "function");
  let resolve;
  const s = mod.createSession({
    request: async (path) =>
      path.endsWith("/login")
        ? new Promise((r) => (resolve = r))
        : { data: { username: "reviewer", roles: ["REVIEWER"] } },
  });
  const pending = s.login("ADMIN", "x", "x");
  s.logout("ADMIN");
  resolve({ data: { accessToken: "obsolete" } });
  await assert.rejects(pending);
  assert.equal(s.authorization("ADMIN"), null);
  const other = mod.createSession({
    request: async (path) => ({
      data: path.endsWith("/login")
        ? { accessToken: "x" }
        : { username: "reviewer", roles: ["REVIEWER"] },
    }),
  });
  await assert.rejects(other.login("ADMIN", "x", "x"));
  assert.equal(other.authorization("ADMIN"), null);
});
