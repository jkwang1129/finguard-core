let fieldId = 0;
export function el(tag, text, attrs = {}) {
  const node = document.createElement(tag);
  if (text != null) node.textContent = String(text);
  for (const [key, value] of Object.entries(attrs)) if (value != null && value !== false) node.setAttribute(key, value === true ? '' : String(value));
  return node;
}
export function button(label, action, attrs = {}) {
  const node = el('button', label, {type:'button', ...attrs}); node.addEventListener('click', () => action(node)); return node;
}
export function panel(root, title, note) {
  const node = el('section', null, {class:'card'}); node.append(el('h2', title)); if (note) node.append(el('p', note, {class:'muted'})); root.append(node); return node;
}
export function feedback(root) { const node = el('div', '', {class:'feedback', role:'status', 'aria-live':'polite'}); root.append(node); return node; }
export function setFeedback(root, info) {
  root.replaceChildren(); root.className = `feedback ${info instanceof Error || info.code ? 'error' : 'success'}`;
  root.append(el('p', [info.status ? `HTTP ${info.status}` : '', info.code, info.message].filter(Boolean).join(' · ')));
  if (info.fieldErrors) root.append(el('pre', JSON.stringify(info.fieldErrors, null, 2)));
  if (info.retryAfter) root.append(el('p', `Retry-After: ${info.retryAfter}`));
}
export async function perform(ctx, target, task, statusNode) {
  if (target.dataset.busy) return; target.dataset.busy = 'true'; target.disabled = true;
  try { const result = await task(); ctx.signal.throwIfAborted(); return result; }
  catch (error) { if (error.name !== 'AbortError' && !ctx.signal.aborted) setFeedback(statusNode, error); }
  finally { delete target.dataset.busy; target.disabled = false; }
}
export function form(root, title, fields, onSubmit, {submit = '查询', disabled = false} = {}) {
  const node = el('form', null, {class:'form-grid', 'aria-label':title});
  for (const field of fields) {
    const id = `field-${++fieldId}`, group = el('div'); group.append(el('label', field.label, {for:id}));
    let input;
    if (field.options) { input = el('select'); for (const option of field.options) { const [value, label] = Array.isArray(option) ? option : [option, option]; input.append(el('option', label, {value})); } }
    else input = el(field.type === 'textarea' ? 'textarea' : 'input', null, {type:field.type ?? 'text', min:field.min, max:field.max, step:field.step});
    input.id = id; input.name = field.name; input.value = field.value ?? ''; input.required = field.required ?? false; input.readOnly = field.readonly ?? false;
    if (field.type === 'password') input.autocomplete = 'current-password';
    group.append(input); node.append(group);
  }
  const submitButton = el('button', submit, {type:'submit', disabled}); node.append(submitButton);
  node.addEventListener('submit', event => { event.preventDefault(); if (!submitButton.disabled) onSubmit(Object.fromEntries(new FormData(node)), submitButton, node); });
  root.append(node); return node;
}
export function queryString(values) { return new URLSearchParams(Object.entries(values).filter(([,v]) => v !== '' && v != null)).toString(); }
export function positiveId(value) { if (!/^[1-9]\d*$/.test(String(value))) throw new Error('ID 必须是正整数'); return String(value); }
export function decimal(value) { if (!/^\d{1,17}(?:\.\d{1,2})?$/.test(value) || !/[1-9]/.test(value)) throw new Error('金额必须为正数，最多两位小数'); return value; }
export function detail(root, value) {
  root.replaceChildren(); const node = el('dl', null, {class:'detail'});
  for (const [key, val] of Object.entries(value ?? {})) node.append(el('dt', key), el('dd', val == null ? '—' : typeof val === 'object' ? JSON.stringify(val, null, 2) : val));
  root.append(node);
}
export function table(root, rows, columns, actions = []) {
  root.replaceChildren(); if (!rows.length) { root.append(el('p', '没有符合条件的记录')); return; }
  const wrap = el('div', null, {class:'table-scroll'}), node = el('table'), head = el('tr');
  for (const [key, label] of columns) head.append(el('th', label, {scope:'col'})); if (actions.length) head.append(el('th', '操作', {scope:'col'}));
  const thead = el('thead'); thead.append(head); node.append(thead); const body = el('tbody');
  for (const row of rows) { const tr = el('tr', null, {'data-id':row.id}); for (const [key] of columns) tr.append(el('td', row[key] ?? '—', {'data-field':key}));
    if (actions.length) { const cell = el('td'); for (const action of actions) if (!action.when || action.when(row)) cell.append(button(action.label, b => action.run(row, b))); tr.append(cell); } body.append(tr); }
  node.append(body); wrap.append(node); root.append(wrap);
}
export function renderPage(root, page, {onPage, onSize}) {
  root.replaceChildren(); root.append(el('p', `第 ${page.page} / ${page.pages || 1} 页 · 共 ${page.total} 条`), button('上一页', () => onPage(page.page - 1), {disabled:page.page <= 1}), button('下一页', () => onPage(page.page + 1), {disabled:page.page >= page.pages}));
  const select = el('select', null, {'aria-label':'每页条数'}); for (const size of [20,50,100]) select.append(el('option', size, {value:size})); select.value = page.size; select.addEventListener('change', () => onSize(Number(select.value))); root.append(select);
}
export function taskLookup(root, ctx, workspace, label = '任务 ID') { return form(root, '按 ID 找回', [{name:'id', label, required:true}], values => { try { ctx.navigate(workspace, {id:positiveId(values.id)}); } catch (error) { window.alert(error.message); } }); }
export const common = {all:['','全部'], page:{name:'page',label:'页号',type:'number',min:1,value:1}, size:{name:'size',label:'每页条数',options:['20','50','100'],value:'20'}};
