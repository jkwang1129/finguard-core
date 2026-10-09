import {panel,el,form,button,detail,positiveId,taskLookup,perform,setFeedback,common} from '../ui.js';
import {write,list,query,output} from './kit.js';
export function createActions(ctx) {
  const path=id=>`/api/accounts/${positiveId(id)}`;
  return {query:(f,s)=>query(ctx,'/api/accounts',f,s),get:id=>ctx.request(path(id)),
    create:json=>write(ctx,'/api/accounts',{json,action:'创建账户'}),
    rename:(id,accountName)=>write(ctx,`${path(id)}/name`,{method:'PATCH',json:{accountName},action:'修改账户名称',target:id}),
    status:(id,status)=>write(ctx,`${path(id)}/status`,{method:'PATCH',json:{status},action:'修改账户状态',target:id,destructive:status==='DISABLED'}),
    remove:id=>write(ctx,path(id),{method:'DELETE',action:'删除账户',target:id,destructive:true})};
}
export function mount(root,ctx) {
  const a=createActions(ctx),manager=panel(root,'账户管理','REVIEWER 可读取。账户删除要求已停用且没有交易历史；后端会验证约束。');taskLookup(manager,ctx,'accounts','账户 ID');
  const current=panel(root,'账户详情'),{status,details}=output(current); let listing;
  async function show(id){const r=await a.get(id);detail(details,r.data);const row=r.data;details.append(button('查看账户交易',()=>ctx.navigate('transactions',{accountId:row.id})));
    if(ctx.role==='ADMIN') {form(details,'修改账户名称',[{name:'accountName',label:'新名称',value:row.accountName,required:true}],(v,b)=>perform(ctx,b,async()=>{await a.rename(row.id,v.accountName);await show(row.id);listing.load();},status),{submit:'保存名称'});
      for(const state of ['ACTIVE','DISABLED'])details.append(button(state==='ACTIVE'?'启用账户':'停用账户',b=>perform(ctx,b,async()=>{const r=await a.status(row.id,state);if(r){await show(row.id);listing.load();}},status)));
      details.append(button('删除账户',b=>perform(ctx,b,async()=>{const r=await a.remove(row.id);if(r){details.replaceChildren(el('p','账户已删除'));listing.load();}},status)));}}
  listing=list(manager,ctx,{title:'筛选账户',fields:[{name:'keyword',label:'关键词'},{name:'status',label:'状态',options:[common.all,'ACTIVE','DISABLED']},{name:'accountType',label:'类型',options:[common.all,'BANK','CASH','PAYMENT_PLATFORM']}],query:a.query,columns:[['id','ID'],['accountNo','账号'],['accountName','名称'],['accountType','类型'],['status','状态']],actions:[{label:'详情',run:(row,b)=>perform(ctx,b,()=>show(row.id),status)}]});
  if(ctx.role==='ADMIN'){const create=panel(root,'创建账户');form(create,'创建账户',[{name:'accountNo',label:'账号',required:true},{name:'accountName',label:'账户名称',required:true},{name:'accountType',label:'账户类型',options:['BANK','CASH','PAYMENT_PLATFORM']}],(v,b)=>perform(ctx,b,async()=>{const r=await a.create(v);if(r){setFeedback(status,{status:r.status,message:`已创建账户 ${r.data.id}`});await show(r.data.id);listing.load();}},status),{submit:'创建账户'});}
  if(ctx.params.id&&ctx.identity(ctx.role))show(ctx.params.id).catch(e=>setFeedback(status,e));return listing;
}
