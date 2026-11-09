(function(){
  if (!window.__roncs) return JSON.stringify({ok:false, text:''});
  return JSON.stringify({ok:true, text: __roncs.bodyText()});
})();
