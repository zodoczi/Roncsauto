(function(){
  if (!window.__roncs) return JSON.stringify({ok:false, reason:'no_common'});
  var plate = window.__roncsPlate || '';
  var list = __roncs.docs();
  var needle1 = 'g\u00e9pj\u00e1rm\u0171 rendsz\u00e1ma';
  var needle2 = 'rendsz\u00e1m';
  var el = __roncs.byLabel(list, needle1)
        || __roncs.byLabel(list, 'gepjarmu rendszama')
        || __roncs.byLabel(list, needle2)
        || __roncs.byLabel(list, 'rendszam')
        || __roncs.byCss(list, [
             'input[name*="rendszam" i]',
             'input[id*="rendszam" i]',
             'input[name*="plate" i]',
             'input[aria-label*="rendsz" i]',
             'input[placeholder*="rendsz" i]'
           ]);
  if (!el) return JSON.stringify({ok:false, reason:'field_not_found', docs:list.length});
  var ok = __roncs.fill(el, plate);
  return JSON.stringify({ok:ok, reason: ok?'filled':'set_failed', tag:el.tagName, id:el.id||'', name:el.name||''});
})();