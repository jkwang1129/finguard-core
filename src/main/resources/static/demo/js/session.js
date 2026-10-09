export function createSession({ request } = {}) {
  const sessions = new Map(), generations = new Map(), listeners = new Set(); let transport = request;
  function notify(role) { for (const listener of listeners) listener(role); }
  function logout(role) { generations.set(role, (generations.get(role) ?? 0) + 1); sessions.delete(role); notify(role); }
  async function login(role, username, password, { signal } = {}) {
    if (!['ADMIN','REVIEWER'].includes(role)) throw new Error('请选择正确角色');
    logout(role); const generation = generations.get(role);
    const current = () => { signal?.throwIfAborted(); if (generation !== generations.get(role)) throw new DOMException('Cancelled', 'AbortError'); };
    try {
      const result = await transport('/api/auth/login', { method: 'POST', json: { username, password }, signal }); current();
      if (!result.data?.accessToken) throw new Error('登录响应缺少令牌');
      sessions.set(role, { token: result.data.accessToken, user: null });
      const me = await transport('/api/auth/me', { role, signal }); current();
      if (!me.data?.roles?.includes(role)) throw new Error(`当前账户没有 ${role} 角色`);
      sessions.set(role, { token: result.data.accessToken, user: me.data }); notify(role); return me.data;
    } catch (error) { if (generation === generations.get(role)) logout(role); throw error; }
  }
  return { login, logout, identity: role => sessions.get(role)?.user ?? null,
    authorization: role => sessions.get(role) ? `Bearer ${sessions.get(role).token}` : null,
    subscribeSession: listener => { listeners.add(listener); return () => listeners.delete(listener); },
    configure: fn => { transport = fn; } };
}
const session = createSession();
export const { login, logout, identity, authorization, subscribeSession } = session;
export const configureSession = session.configure;
