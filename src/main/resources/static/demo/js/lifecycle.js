export function createLatest(onResult) {
  let generation=0, disposed=false, controller=null;
  return {
    async run(loader) {
      controller?.abort(); controller=new AbortController(); const current=++generation;
      try { const value=await loader(controller.signal); if(!disposed && current===generation) onResult(value); return value; }
      catch(error) { if(current===generation && !disposed && error.name!=='AbortError') throw error; }
    },
    dispose() { disposed=true; generation++; controller?.abort(); }
  };
}
