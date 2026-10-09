import test from 'node:test';import assert from 'node:assert/strict';
let mod={};try{mod=await import('../../../src/main/resources/static/demo/js/lifecycle.js');}catch{}
test('late A cannot overwrite B; disposal prevents all updates',async()=>{
 assert.equal(typeof mod.createLatest,'function');let a,b;const seen=[];const l=mod.createLatest(v=>seen.push(v));
 const pa=l.run(()=>new Promise(r=>a=r));const pb=l.run(()=>new Promise(r=>b=r));b('B');await pb;a('A');await pa;assert.deepEqual(seen,['B']);
 let resolve;const p=l.run(()=>new Promise(r=>resolve=r));l.dispose();resolve('late');await p;assert.deepEqual(seen,['B']);
});
