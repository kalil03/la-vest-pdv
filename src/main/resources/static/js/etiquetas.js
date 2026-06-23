/**
 * Emissão de etiquetas em lote para impressora térmica de etiquetas (ex.: Xprinter XP-365B).
 * Mesma filosofia do recibo: HTML dimensionado para a etiqueta + window.print(), sem ESC/POS.
 * O código de barras é Code 128 do código do produto, gerado como SVG embutido (JsBarcode),
 * para o documento de impressão não depender de JS nem de rede.
 *
 * Uma etiqueta = uma "página" (@page do tamanho da etiqueta) — casa com a mídia die-cut/gap
 * da impressora, que avança uma etiqueta por página.
 */

const $ = (id) => document.getElementById(id);
const fmt = (v) => Number(v).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
const esc = (s) => String(s ?? '').replace(/[&<>"]/g, (c) =>
  ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));

// id -> { produto, qtd }
const lista = new Map();

const PRESETS = {
  '40x30': [40, 30], '50x30': [50, 30], '33x22': [33, 22], '60x40': [60, 40],
};

// ---------- configuração (persistida) ----------
function carregarConfig() {
  let cfg = {};
  try { cfg = JSON.parse(localStorage.getItem('pdv.etiqueta') || '{}'); } catch (e) { /* corrompido */ }
  const larg = cfg.larg || 40, alt = cfg.alt || 30;
  const preco = cfg.preco !== false;
  $('larg').value = larg;
  $('alt').value = alt;
  $('mostrar-preco').checked = preco;
  $('preset').value = presetDe(larg, alt);
}

function presetDe(larg, alt) {
  for (const [k, [w, h]] of Object.entries(PRESETS)) if (w === larg && h === alt) return k;
  return 'custom';
}

function config() {
  return {
    larg: Math.max(20, Math.min(80, parseInt($('larg').value, 10) || 40)),
    alt: Math.max(10, Math.min(120, parseInt($('alt').value, 10) || 30)),
    preco: $('mostrar-preco').checked,
  };
}

function salvarConfig() {
  localStorage.setItem('pdv.etiqueta', JSON.stringify(config()));
}

// ---------- busca de produto ----------
let buscaTimer = null;
let resultados = [];
let ativo = -1;

$('busca').addEventListener('input', () => {
  clearTimeout(buscaTimer);
  const q = $('busca').value.trim();
  if (!q) { fecharDropdown(); return; }
  buscaTimer = setTimeout(() => buscar(q), 200);
});

async function buscar(q) {
  try {
    resultados = await (await fetch(`/api/produtos?q=${encodeURIComponent(q)}`)).json();
  } catch (e) {
    resultados = [];
  }
  resultados = resultados.slice(0, 12);
  renderDropdown();
}

function renderDropdown() {
  const dd = $('busca-dropdown');
  if (!resultados.length) {
    dd.innerHTML = '<li class="cb-empty">Nenhum produto encontrado</li>';
  } else {
    dd.innerHTML = resultados.map((p, i) =>
      `<li data-idx="${i}"><span>${esc(p.nome)}</span><span class="cb-cod">${esc(p.codigo || '—')}</span></li>`).join('');
  }
  ativo = -1;
  dd.hidden = false;
  dd.querySelectorAll('li[data-idx]').forEach((li) => {
    li.addEventListener('mousedown', (e) => { e.preventDefault(); adicionar(resultados[+li.dataset.idx]); });
  });
}

function fecharDropdown() { $('busca-dropdown').hidden = true; ativo = -1; }

$('busca').addEventListener('keydown', (e) => {
  const itens = $('busca-dropdown').querySelectorAll('li[data-idx]');
  if (e.key === 'ArrowDown' && itens.length) { e.preventDefault(); ativo = Math.min(ativo + 1, itens.length - 1); marcarAtivo(itens); }
  else if (e.key === 'ArrowUp' && itens.length) { e.preventDefault(); ativo = Math.max(ativo - 1, 0); marcarAtivo(itens); }
  else if (e.key === 'Enter') {
    e.preventDefault();
    const idx = ativo >= 0 ? ativo : 0;
    if (resultados[idx]) adicionar(resultados[idx]);
  } else if (e.key === 'Escape') { fecharDropdown(); }
});

function marcarAtivo(itens) {
  itens.forEach((li, i) => li.classList.toggle('active', i === ativo));
}

document.addEventListener('click', (e) => {
  if (!e.target.closest('#busca') && !e.target.closest('#busca-dropdown')) fecharDropdown();
});

// ---------- lista (carrinho de etiquetas) ----------
function adicionar(p) {
  if (!p) return;
  const item = lista.get(p.id);
  if (item) item.qtd += 1;
  else lista.set(p.id, { produto: p, qtd: 1 });
  $('busca').value = '';
  fecharDropdown();
  $('busca').focus();
  render();
  toast(`${p.nome} adicionado`, 'ok');
}

function remover(id) { lista.delete(id); render(); }

function setQtd(id, qtd) {
  const item = lista.get(id);
  if (!item) return;
  item.qtd = Math.max(1, parseInt(qtd, 10) || 1);
  atualizarTotal();
  salvarEtiquetas();
}

// ---------- memória de rascunho (a lista volta ao reabrir a aba) ----------
function salvarEtiquetas() {
  if (typeof Rascunho === 'undefined') return;
  if (lista.size === 0) { Rascunho.limpar('etiquetas'); return; }
  Rascunho.salvar('etiquetas', [...lista.values()].map(({ produto, qtd }) => ({ produto, qtd })));
}

function restaurarEtiquetas() {
  if (typeof Rascunho === 'undefined') return false;
  const d = Rascunho.carregar('etiquetas');
  if (!Array.isArray(d) || !d.length) return false;
  d.forEach(({ produto, qtd }) => {
    if (produto && produto.id != null) lista.set(produto.id, { produto, qtd: Math.max(1, qtd | 0) });
  });
  render();
  Rascunho.aviso('Lista de etiquetas recuperada', () => { lista.clear(); render(); });
  return true;
}

function render() {
  const tbody = $('lista');
  $('vazio').hidden = lista.size > 0;
  tbody.innerHTML = [...lista.values()].map(({ produto: p, qtd }) => `
    <tr class="border-b border-border">
      <td class="px-6 py-2.5 text-[13px]" style="font-family:'JetBrains Mono',monospace;color:var(--muted-foreground)">${esc(p.codigo || '—')}</td>
      <td class="px-4 py-2.5 text-[13px] text-foreground">${esc(p.nome)}</td>
      <td class="px-4 py-2.5 text-[13px] text-foreground text-right">${fmt(p.preco)}</td>
      <td class="px-4 py-2.5 text-center">
        <input type="number" min="1" value="${qtd}" data-qtd="${p.id}"
          class="w-16 bg-secondary border border-border rounded-md px-2 py-1 text-[13px] text-center text-foreground outline-none focus:border-primary">
      </td>
      <td class="px-4 py-2.5 text-right">
        <button type="button" data-rm="${p.id}" class="text-muted-foreground hover:text-destructive transition-colors p-1" title="Remover">
          <i data-lucide="trash-2" class="w-4 h-4"></i>
        </button>
      </td>
    </tr>`).join('');

  tbody.querySelectorAll('[data-qtd]').forEach((inp) =>
    inp.addEventListener('input', () => setQtd(+inp.dataset.qtd, inp.value)));
  tbody.querySelectorAll('[data-rm]').forEach((b) =>
    b.addEventListener('click', () => remover(+b.dataset.rm)));

  lucide.createIcons();
  atualizarTotal();
  renderPreview();
  salvarEtiquetas();
}

function atualizarTotal() {
  const total = [...lista.values()].reduce((s, i) => s + i.qtd, 0);
  $('total-etq').textContent = total;
  $('imprimir').disabled = total === 0;
}

// ---------- etiqueta (conteúdo compartilhado entre preview e impressão) ----------
function valorBarras(p) { return p.codigo || p.codigoBarras || String(p.id); }

function barcodeSVG(valor) {
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  try {
    JsBarcode(svg, valor, { format: 'CODE128', displayValue: false, margin: 0, height: 40, width: 1.7 });
  } catch (e) {
    return '<div style="font-size:7pt;color:#900">código inválido</div>';
  }
  return svg.outerHTML;
}

function etiquetaInner(p, cfg) {
  const v = valorBarras(p);
  return `<div class="pv-nome">${esc(p.nome)}</div>`
    + `<div class="pv-barcode">${barcodeSVG(v)}</div>`
    + `<div class="pv-codigo">${esc(v)}</div>`
    + (cfg.preco ? `<div class="pv-preco">${fmt(p.preco)}</div>` : '');
}

function renderPreview() {
  const cfg = config();
  const box = $('preview-etq');
  box.style.width = cfg.larg + 'mm';
  box.style.height = cfg.alt + 'mm';
  const primeiro = [...lista.values()][0];
  if (!primeiro) {
    box.innerHTML = '<span class="text-[12px]" style="color:#999">Adicione um produto</span>';
    return;
  }
  box.innerHTML = etiquetaInner(primeiro.produto, cfg);
}

// ---------- impressão ----------
function imprimir() {
  const cfg = config();
  const etiquetas = [];
  for (const { produto, qtd } of lista.values()) {
    const inner = etiquetaInner(produto, cfg);
    for (let i = 0; i < qtd; i++) etiquetas.push(`<div class="etq">${inner}</div>`);
  }
  if (!etiquetas.length) { toast('Adicione produtos à lista'); return; }

  const maxBarra = Math.max(8, Math.round(cfg.alt * 0.42));
  const html = `<!DOCTYPE html><html lang="pt-BR"><head><meta charset="UTF-8"><style>
    @page { size: ${cfg.larg}mm ${cfg.alt}mm; margin: 0; }
    * { margin: 0; padding: 0; box-sizing: border-box; }
    html, body { background: #fff; }
    .etq {
      width: ${cfg.larg}mm; height: ${cfg.alt}mm; padding: 1mm 1.5mm;
      display: flex; flex-direction: column; align-items: center; justify-content: center;
      text-align: center; overflow: hidden; page-break-after: always; break-after: page;
      font-family: Arial, Helvetica, sans-serif; color: #000;
    }
    .etq:last-child { page-break-after: auto; break-after: auto; }
    .pv-nome { font-size: 7pt; font-weight: 700; line-height: 1.05; width: 100%;
      display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
    .pv-barcode { width: 100%; }
    .pv-barcode svg { display: block; width: 100%; height: auto; max-height: ${maxBarra}mm; }
    .pv-codigo { font-family: 'Courier New', monospace; font-size: 7pt; letter-spacing: 1px; margin-top: 0.3mm; }
    .pv-preco { font-size: 12pt; font-weight: 700; margin-top: 0.4mm; }
  </style></head><body>${etiquetas.join('')}</body></html>`;

  const iframe = document.createElement('iframe');
  iframe.style.cssText = 'position:fixed;right:0;bottom:0;width:0;height:0;border:none;';
  iframe.srcdoc = html;
  iframe.onload = () => {
    iframe.contentWindow.focus();
    iframe.contentWindow.print();
    setTimeout(() => iframe.remove(), 3000);
  };
  document.body.appendChild(iframe);
}

// ---------- toast ----------
let toastTimer = null;
function toast(msg, tipo = '') {
  const t = $('toast');
  t.textContent = msg;
  t.className = `toast ${tipo}`;
  t.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { t.hidden = true; }, 3000);
}

// ---------- eventos de config ----------
$('preset').addEventListener('change', () => {
  const p = PRESETS[$('preset').value];
  if (p) { $('larg').value = p[0]; $('alt').value = p[1]; }
  salvarConfig(); renderPreview();
});
['larg', 'alt'].forEach((id) => $(id).addEventListener('input', () => {
  $('preset').value = presetDe(parseInt($('larg').value, 10), parseInt($('alt').value, 10));
  salvarConfig(); renderPreview();
}));
$('mostrar-preco').addEventListener('change', () => { salvarConfig(); renderPreview(); });
$('imprimir').addEventListener('click', imprimir);
$('limpar').addEventListener('click', () => { lista.clear(); render(); });

// ---------- init ----------
carregarConfig();
// restaura ANTES do primeiro render (senão o render vazio apagaria o rascunho)
if (!restaurarEtiquetas()) render();
$('busca').focus();
