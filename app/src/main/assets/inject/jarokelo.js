(function(){
  if (!window.__roncs) return JSON.stringify({ok:false, reason:'no_common'});
  var text = window.__roncsDescription || '';
  var list = __roncs.docs();
  var needle1 = 'probl\u00e9ma le\u00edr\u00e1sa';
  var needle2 = 'le\u00edr\u00e1s';
  var el = __roncs.byCss(list, ['textarea#description', 'textarea[name="description"]'])
        || __roncs.byLabel(list, needle1)
        || __roncs.byLabel(list, 'problema leirasa')
        || __roncs.byLabel(list, needle2)
        || __roncs.byLabel(list, 'leiras');
  if (!el) return JSON.stringify({ok:false, reason:'field_not_found', docs:list.length});
  var ok = __roncs.fill(el, text);
  return JSON.stringify({ok:ok, reason: ok?'filled':'set_failed', id:el.id||'', name:el.name||''});
})();