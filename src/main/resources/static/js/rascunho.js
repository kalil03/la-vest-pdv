/**
 * Memória de rascunho: guarda o que está sendo preenchido (venda, etiquetas,
 * cadastros) em localStorage e devolve ao reabrir a aba. O rascunho só some
 * quando a ação é finalizada (venda fechada, produto salvo, lista limpa) — não
 * expira sozinho. Ao recuperar, mostra um aviso discreto com a opção "Limpar".
 */
const Rascunho = (function () {
  const PREFIXO = 'pdv.rascunho.';
  const timers = {};

  function salvar(nome, dados) {
    try { localStorage.setItem(PREFIXO + nome, JSON.stringify({ ts: Date.now(), dados })); } catch (e) { /* cota cheia */ }
  }

  function carregar(nome) {
    try {
      const r = JSON.parse(localStorage.getItem(PREFIXO + nome) || 'null');
      return r ? r.dados : null;
    } catch (e) { return null; }
  }

  function limpar(nome) { localStorage.removeItem(PREFIXO + nome); }

  /** Devolve uma função que salva com debounce — chame-a a cada mudança. */
  function autoSave(nome, coletar, temConteudo, ms = 400) {
    return function () {
      clearTimeout(timers[nome]);
      timers[nome] = setTimeout(() => {
        const d = coletar();
        if (d && temConteudo(d)) salvar(nome, d); else limpar(nome);
      }, ms);
    };
  }

  /** Aviso discreto no canto: "<texto> · Limpar". Some sozinho em ~7s. */
  function aviso(texto, onLimpar) {
    const el = document.createElement('div');
    el.style.cssText = 'position:fixed;left:24px;bottom:24px;z-index:200;display:flex;align-items:center;gap:12px;'
      + 'background:var(--foreground,#111);color:var(--background,#fff);padding:10px 14px;border-radius:8px;'
      + 'font-size:13px;box-shadow:0 10px 25px rgba(0,0,0,.18);max-width:360px;';
    const txt = document.createElement('span');
    txt.textContent = texto;
    el.appendChild(txt);
    if (onLimpar) {
      const btn = document.createElement('button');
      btn.textContent = 'Limpar';
      btn.style.cssText = 'background:transparent;border:1px solid currentColor;color:inherit;border-radius:6px;'
        + 'padding:3px 10px;font-size:12px;cursor:pointer;font-weight:600;white-space:nowrap;';
      btn.addEventListener('click', () => { try { onLimpar(); } finally { el.remove(); } });
      el.appendChild(btn);
    }
    document.body.appendChild(el);
    setTimeout(() => { el.remove(); }, 7000);
  }

  return { salvar, carregar, limpar, autoSave, aviso };
})();
