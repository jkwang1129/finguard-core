import {el, form, feedback, setFeedback, perform, table, renderPage, queryString, common} from '../ui.js';
import {createLatest} from '../lifecycle.js';
export async function write(ctx, path, {method='POST', json, form, action='写入', target=path, destructive=false} = {}) {
  if (ctx.role !== 'ADMIN') throw new Error('此操作需要 ADMIN 身份');
  if (!await ctx.confirmWrite({action,target,destructive})) return null;
  return ctx.request(path,{method,json,form});
}
export function list(root, ctx, {title,fields=[],query,columns,actions=[],initial={}}) {
  const status=feedback(root), results=el('div'), pager=el('div',null,{class:'pager'}); let filters={page:1,size:20,...initial};
  const latest=createLatest(response=>{table(results,response.data.records,columns,actions);renderPage(pager,response.data,{onPage:page=>load({page}),onSize:size=>load({size,page:1})});});
  async function load(changes={}) {filters={...filters,...changes};try {await latest.run(signal=>query(filters,signal));}catch(error){if(error.name!=='AbortError'&&!ctx.signal.aborted)setFeedback(status,error);}}
  form(root,title,[...fields,common.size],(values,target)=>perform(ctx,target,()=>load({...values,page:1}),status)); root.append(results,pager);
  ctx.signal.addEventListener('abort',()=>latest.dispose(),{once:true}); if(ctx.identity(ctx.role))load();
  return {load,dispose:()=>latest.dispose()};
}
export function query(ctx,path,filters,signal){return ctx.request(`${path}?${queryString(filters)}`,{signal});}
export function output(root) {const status=feedback(root),details=el('div');root.append(details);return {status,details};}
