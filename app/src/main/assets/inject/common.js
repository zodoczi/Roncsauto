(function(){
  function nativeSet(el, value){
    var proto = el instanceof HTMLTextAreaElement
      ? window.HTMLTextAreaElement.prototype
      : window.HTMLInputElement.prototype;
    var setter = Object.getOwnPropertyDescriptor(proto, 'value');
    if (setter && setter.set) setter.set.call(el, value);
    else el.value = value;
    el.dispatchEvent(new Event('input', {bubbles:true}));
    el.dispatchEvent(new Event('change', {bubbles:true}));
    try {
      el.dispatchEvent(new InputEvent('input', {bubbles:true, data:value, inputType:'insertText'}));
    } catch (e) {}
  }
  function docs(){
    var out = [];
    function walk(doc){
      if (!doc || out.indexOf(doc) >= 0) return;
      out.push(doc);
      var frames = doc.querySelectorAll('iframe');
      for (var i=0;i<frames.length;i++){
        try { if (frames[i].contentDocument) walk(frames[i].contentDocument); } catch(e){}
      }
    }
    walk(document);
    return out;
  }
  function labelText(el){
    var id = el.id;
    if (id){
      var lab = document.querySelector('label[for="'+id.replace(/"/g,'\\"')+'"]');
      if (lab) return (lab.textContent||'').trim();
    }
    var p = el.closest('label');
    return p ? (p.textContent||'').trim() : '';
  }
  function byLabel(docsList, needle){
    needle = needle.toLowerCase();
    for (var d=0;d<docsList.length;d++){
      var inputs = docsList[d].querySelectorAll('input, textarea');
      for (var i=0;i<inputs.length;i++){
        var el = inputs[i];
        var t = (labelText(el)+' '+(el.getAttribute('aria-label')||'')+' '+(el.placeholder||'')+' '+(el.name||'')+' '+(el.id||'')).toLowerCase();
        if (t.indexOf(needle) >= 0) return el;
      }
    }
    return null;
  }
  function byCss(docsList, selectors){
    for (var d=0;d<docsList.length;d++){
      for (var s=0;s<selectors.length;s++){
        var el = docsList[d].querySelector(selectors[s]);
        if (el) return el;
      }
    }
    return null;
  }
  window.__roncs = {
    nativeSet: nativeSet,
    docs: docs,
    byLabel: byLabel,
    byCss: byCss,
    fill: function(el, value){
      if (!el) return false;
      nativeSet(el, value);
      try { el.focus(); } catch(e){}
      return true;
    },
    bodyText: function(){
      var parts = [];
      var list = docs();
      for (var i=0;i<list.length;i++){
        try { parts.push(list[i].body ? list[i].body.innerText : ''); } catch(e){}
      }
      return parts.join('\n');
    }
  };
})();
