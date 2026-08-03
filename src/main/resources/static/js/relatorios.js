/** Relatórios: movimento do período + retrato do crediário. Um fetch alimenta tudo. */

const fmt = (v) => Number(v || 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
const fmtInt = (v) => Number(v || 0).toLocaleString('pt-BR');
const $ = (id) => document.getElementById(id);
const dataBr = (iso) => { const [a, m, d] = String(iso).split('-'); return `${d}/${m}/${a}`; };
const diaSemana = (iso) => new Date(iso + 'T12:00:00').toLocaleDateString('pt-BR', { weekday: 'short' }).replace('.', '');
const esc = (s) => String(s ?? '').replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));

const FORMA = { DINHEIRO: 'Dinheiro', PIX: 'PIX', CARTAO: 'Cartão', FIADO: 'Fiado', VALE_CREDITO: 'Vale-crédito' };
const COR_FORMA = { DINHEIRO: '#16a34a', PIX: '#0891b2', CARTAO: '#7c3aed', FIADO: '#d97706', VALE_CREDITO: '#64748b' };

// ---------- período ----------
function isoLocal(d) { return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`; }

function aplicarPreset(preset) {
  const hoje = new Date();
  let de = new Date(hoje), ate = new Date(hoje);
  if (preset === 'hoje') { /* de=ate=hoje */ }
  else if (preset === '7') { de.setDate(hoje.getDate() - 6); }
  else if (preset === 'mes') { de = new Date(hoje.getFullYear(), hoje.getMonth(), 1); }
  else if (preset === 'mespassado') { de = new Date(hoje.getFullYear(), hoje.getMonth() - 1, 1); ate = new Date(hoje.getFullYear(), hoje.getMonth(), 0); }
  else if (preset === 'ano') { de = new Date(hoje.getFullYear(), 0, 1); }
  $('f-de').value = isoLocal(de);
  $('f-ate').value = isoLocal(ate);
  document.querySelectorAll('.preset').forEach((b) => b.classList.toggle('ativo', b.dataset.preset === preset));
  carregar();
}

document.querySelectorAll('.preset').forEach((b) => b.addEventListener('click', () => aplicarPreset(b.dataset.preset)));
$('f-aplicar').addEventListener('click', () => {
  document.querySelectorAll('.preset').forEach((b) => b.classList.remove('ativo'));  // datas manuais
  carregar();
});
['f-de', 'f-ate'].forEach((id) => $(id).addEventListener('keydown', (e) => { if (e.key === 'Enter') $('f-aplicar').click(); }));
$('imprimir').addEventListener('click', () => window.print());

// ---------- carregar + render ----------
async function carregar() {
  const de = $('f-de').value, ate = $('f-ate').value;
  const params = new URLSearchParams();
  if (de) params.set('de', de);
  if (ate) params.set('ate', ate);
  let r;
  try { r = await (await fetch(`/api/relatorios?${params}`)).json(); }
  catch { toast('Falha ao carregar o relatório', 'erro'); return; }

  $('periodo-label').textContent = `${dataBr(r.de)} — ${dataBr(r.ate)}`;
  renderKpis(r.kpis);
  renderForma(r.porForma);
  renderVendedor(r.porVendedor);
  renderDia(r.porDia);
  renderProdutos(r.topProdutos);
  renderClientes(r.topClientes);
  renderCrediario(r.crediario);
  renderDevedores(r.maioresDevedores);
  if (window.lucide) lucide.createIcons();
}

function renderKpis(k) {
  $('k-fat').textContent = fmt(k.faturamento);
  $('k-fat-sub').textContent = `à vista ${fmt(k.vendidoAVista)} · fiado ${fmt(k.vendidoFiado)}`;
  $('k-qtd').textContent = fmtInt(k.qtdVendas);
  $('k-ticket').textContent = fmt(k.ticketMedio);
  $('k-itens').textContent = fmtInt(k.itensVendidos);
  $('k-receb').textContent = fmt(k.recebido);
  $('k-desc').textContent = fmt(k.descontoTotal);
}

/** Célula com barra proporcional ao maior valor da coluna. */
function celulaBarra(valor, max, cor, texto) {
  const pct = max > 0 ? Math.max(2, (Number(valor) / max) * 100) : 0;
  return `<td class="num barra-wrap"><span class="barra" style="width:${pct}%;background:${cor}"></span>
    <span style="position:relative">${texto}</span></td>`;
}

function renderForma(linhas) {
  const total = linhas.reduce((s, l) => s + Number(l.total), 0);
  const max = Math.max(0, ...linhas.map((l) => Number(l.total)));
  $('t-forma').innerHTML = linhas.map((l) => {
    const cor = COR_FORMA[l.rotulo] || '#64748b';
    const pct = total > 0 ? (Number(l.total) / total) * 100 : 0;
    return `<tr>
      <td><span class="chip forma" style="background:${cor}22;color:${cor}">${FORMA[l.rotulo] || l.rotulo}</span></td>
      <td class="num text-muted-foreground">${fmtInt(l.qtd)}</td>
      <td class="num text-muted-foreground">${pct.toFixed(0)}%</td>
      ${celulaBarra(l.total, max, cor, `<b>${fmt(l.total)}</b>`)}
    </tr>`;
  }).join('') || linhaVazia(4);
}

function renderVendedor(linhas) {
  $('t-vendedor').innerHTML = linhas.map((l) => `
    <tr>
      <td class="font-medium">${esc(l.vendedor)}</td>
      <td class="num text-muted-foreground">${fmtInt(l.qtd)}</td>
      <td class="num text-muted-foreground">${fmt(l.aVista)}</td>
      <td class="num text-muted-foreground">${fmt(l.aPrazo)}</td>
      <td class="num font-semibold">${fmt(l.total)}</td>
    </tr>`).join('') || linhaVazia(5);
}

function renderDia(linhas) {
  const max = Math.max(0, ...linhas.map((l) => Number(l.total)));
  $('t-dia').innerHTML = linhas.map((l) => `
    <tr>
      <td class="mono">${dataBr(l.rotulo)} <span class="text-muted-foreground text-[11px]">${diaSemana(l.rotulo)}</span></td>
      <td class="num text-muted-foreground">${fmtInt(l.qtd)}</td>
      ${celulaBarra(l.total, max, 'var(--azul)', `<b>${fmt(l.total)}</b>`)}
    </tr>`).join('') || linhaVazia(3);
}

function renderProdutos(linhas) {
  const max = Math.max(0, ...linhas.map((l) => Number(l.qtd)));
  $('t-produto').innerHTML = linhas.map((l, i) => `
    <tr>
      <td><span class="posto${i < 3 ? ' top' : ''}">${i + 1}</span></td>
      <td><div class="font-medium leading-tight">${esc(l.nome)}</div><div class="text-muted-foreground text-[11px] mono">${esc(l.codigo)}</div></td>
      ${celulaBarra(l.qtd, max, 'var(--roxo)', `<b>${fmtInt(l.qtd)}</b>`)}
      <td class="num text-muted-foreground">${fmt(l.total)}</td>
    </tr>`).join('') || linhaVazia(4);
}

function renderClientes(linhas) {
  $('t-cliente').innerHTML = linhas.map((l, i) => `
    <tr class="clicavel" data-cliente="${l.clienteId}" title="Abrir o carnê de ${esc(l.cliente)}">
      <td><span class="posto${i < 3 ? ' top' : ''}">${i + 1}</span></td>
      <td class="font-medium">${esc(l.cliente)}</td>
      <td class="num text-muted-foreground">${fmtInt(l.qtd)}</td>
      <td class="num font-semibold">${fmt(l.total)}</td>
    </tr>`).join('') || linhaVazia(4);
}

function renderCrediario(c) {
  $('c-aberto').textContent = fmt(c.totalAberto);
  $('c-vencido').textContent = fmt(c.totalVencido);
  const pctVenc = Number(c.totalAberto) > 0 ? (Number(c.totalVencido) / Number(c.totalAberto)) * 100 : 0;
  $('c-vencido-sub').textContent = `${pctVenc.toFixed(0)}% do total em aberto`;
  $('c-parcelas').textContent = fmtInt(c.parcelasAbertas);
  $('c-devedores').textContent = fmtInt(c.clientesDevedores);

  const total = Number(c.totalAberto) || 1;
  const faixas = [
    { t: 'A vencer', v: c.aVencer, cor: 'var(--ok)' },
    { t: 'Até 30 dias', v: c.ate30, cor: '#ca8a04' },
    { t: '31–60 dias', v: c.de31a60, cor: '#ea580c' },
    { t: '61–90 dias', v: c.de61a90, cor: '#dc2626' },
    { t: '+90 dias', v: c.mais90, cor: '#991b1b' },
  ];
  $('aging').innerHTML = faixas.map((f) => `
    <div class="col" style="border-top: 3px solid ${f.cor}">
      <div class="t">${f.t}</div>
      <div class="v" style="color:${f.cor}">${fmt(f.v)}</div>
      <div class="p">${((Number(f.v) / total) * 100).toFixed(0)}%</div>
    </div>`).join('');
}

function renderDevedores(linhas) {
  const max = Math.max(0, ...linhas.map((l) => Number(l.aberto)));
  $('t-devedor').innerHTML = linhas.map((l, i) => `
    <tr class="clicavel" data-cliente="${l.clienteId}" title="Abrir o carnê de ${esc(l.cliente)}">
      <td><span class="posto${i < 3 ? ' top' : ''}">${i + 1}</span></td>
      <td class="font-medium">${esc(l.cliente)}</td>
      <td class="num text-muted-foreground">${fmtInt(l.notas)}</td>
      <td class="num" style="color: var(--warn)">${Number(l.vencido) > 0 ? fmt(l.vencido) : '—'}</td>
      ${celulaBarra(l.aberto, max, 'var(--bad)', `<b>${fmt(l.aberto)}</b>`)}
    </tr>`).join('') || linhaVazia(5);
}

function linhaVazia(cols) {
  return `<tr><td colspan="${cols}" class="text-center text-muted-foreground py-6">Sem dados no período</td></tr>`;
}

// abrir carnê ao clicar em cliente/devedor
document.body.addEventListener('click', (e) => {
  const tr = e.target.closest('tr.clicavel[data-cliente]');
  if (tr) location.href = `/carne.html?cliente=${tr.dataset.cliente}`;
});

// ---------- toast ----------
let toastTimer = null;
function toast(msg, tipo = '') {
  const t = $('toast');
  t.textContent = msg;
  t.className = `toast ${tipo}`;
  t.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { t.hidden = true; }, 4000);
}

// arranca no mês corrente
aplicarPreset('mes');
