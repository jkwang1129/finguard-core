import test from 'node:test';
import assert from 'node:assert/strict';
let api={}; try { api=await import('../../../src/main/resources/static/demo/js/api.js'); } catch {}
function client(options){ assert.equal(typeof api.createClient,'function','API client must exist'); return api.createClient(options); }
const response=(data,status=200)=>new Response(JSON.stringify(data),{status,headers:{'Content-Type':'application/json'}});
test('write failures are never retried and server 401 remains an API error',async()=>{
  let calls=0; const c=client({fetch:async()=>{calls++; return response({code:'AUTHENTICATION_REQUIRED',message:'Please login'},401);}});
  await assert.rejects(c.request('/api/accounts',{method:'POST',json:{}}),e=>e.status===401&&e.code==='AUTHENTICATION_REQUIRED'); assert.equal(calls,1);
});
test('job polling reads only, preserves 202 receipt separately and stops at terminal',async()=>{
  const methods=[]; const states=['PENDING','PROCESSING','SUCCESS']; let tick=0;
  const c=client({fetch:async(_,o)=>{methods.push(o.method);return response({id:7,status:states.shift()});},now:()=>tick,sleep:async ms=>{tick+=ms;}});
  const updates=[]; const result=await c.pollJob('/api/import-jobs/7',{terminalStatuses:['SUCCESS'],onUpdate:r=>updates.push(r.data.status)});
  assert.equal(result.data.status,'SUCCESS'); assert.deepEqual(methods,['GET','GET','GET']); assert.deepEqual(updates,['PENDING','PROCESSING','SUCCESS']);
});
test('poll timeout retains last observed state',async()=>{
  let tick=0;const c=client({fetch:async()=>response({id:7,status:'PENDING'}),now:()=>tick,sleep:async ms=>{tick+=ms;}});
  await assert.rejects(c.pollJob('/api/import-jobs/7',{terminalStatuses:['SUCCESS'],timeoutMs:2000,intervalMs:1000}),e=>e.code==='POLL_TIMEOUT'&&e.last.data.status==='PENDING');
});
test('all pages includes item 101 and rejects malformed pagination',async()=>{
  const urls=[];const c=client({fetch:async url=>{urls.push(url);const page=Number(new URL(url,'http://local').searchParams.get('page'));return response({page,size:100,total:101,pages:2,records:page===1?Array.from({length:100},(_,id)=>({id})): [{id:100}]});}});
  assert.equal((await c.readAllPages('/api/audit-logs')).length,101);assert.equal(urls.length,2);
  const bad=client({fetch:async()=>response({records:[]})});await assert.rejects(bad.readAllPages('/api/audit-logs'),e=>e.code==='INVALID_PAGE');
});
test('abort prevents stale result even if transport ignores signal',async()=>{
  let resolve;const c=client({fetch:()=>new Promise(r=>resolve=r)});const controller=new AbortController();
  const pending=c.request('/api/accounts',{signal:controller.signal});controller.abort();resolve(response({id:1}));
  await assert.rejects(pending,e=>e.name==='AbortError');
});
test('multipart has no manual content-type and credentials are omitted',async()=>{
  let options;const c=client({authorization:()=> 'Bearer temporary',fetch:async(_,o)=>{options=o;return response({id:7,duplicateFile:true});}});
  const form=new FormData();form.append('file',new Blob(['a']), 'a.csv');const r=await c.request('/api/import-jobs',{method:'POST',role:'ADMIN',form});
  assert.equal(r.status,200);assert.equal(r.data.duplicateFile,true);assert.equal(options.headers['Content-Type'],undefined);assert.equal(options.credentials,'omit');
});
test('amount precision is preserved when parsing server numeric JSON',async()=>{
  const c=client({fetch:async()=>new Response('{"amount":12345678901234567.10,"description":"a \\"amount\\":88"}',{status:200})});
  const r=await c.request('/api/transactions/1');assert.equal(r.data.amount,'12345678901234567.10');assert.equal(r.data.description,'a "amount":88');
});
