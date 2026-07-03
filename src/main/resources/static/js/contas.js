/** Contas a receber: todas as parcelas do crediário com filtros e paginação. */

const fmt = (v) => Number(v).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
const $ = (id) => document.getElementById(id);
const dataBr = (iso) => { const [a, m, d] = iso.split('-'); return `${d}/${m}/${a}`; };

let pagina = 1;
let totalPaginas = 1;

const CHIP = {
  ATRASADA: '<span class="chip atrasada">Atrasada</span>',
  ABERTA: '<span class="chip aberta">Em aberto</span>',
  PARCIAL: '<span class="chip parcial">Parcial</span>',
  QUITADA: '<span class="chip quitada">Quitada</span>',
};

async function carregar() {
  const params = new URLSearchParams({ pagina });
  if ($('f-q').value.trim()) params.set('q', $('f-q').value.trim());
  if ($('f-status').value) params.set('status', $('f-status').value);
  if ($('f-tipo').value) params.set('tipo', $('f-tipo').value);
  if ($('f-de').value) params.set('de', $('f-de').value);
  if ($('f-ate').value) params.set('ate', $('f-ate').value);

  const r = await (await fetch(`/api/contas-receber?${params}`)).json();

  // KPIs (gerais, independem do filtro)
  $('k-aberto').textContent = fmt(r.totais.totalAberto);
  $('k-vencido').textContent = fmt(r.totais.totalVencido);
  $('k-parcelas').textContent = Number(r.totais.parcelasAbertas).toLocaleString('pt-BR');
  $('k-recebido').textContent = fmt(r.totais.recebidoMes);

  // tabela
  $('lista').innerHTML = r.contas.map((c) => `
    <tr data-cliente="${c.clienteId}" ${c.notinha ? `data-notinha="${c.notinha}"` : ''}
        title="${c.notinha ? 'Ver o que foi comprado na venda nº ' + c.notinha : 'Abrir o carnê de ' + c.clienteNome}">
      <td class="font-medium">${c.clienteNome}</td>
      <td class="mono">${c.notinha ?? c.documento ?? '—'}</td>
      <td>${c.descricao}</td>
      <td class="mono">${dataBr(c.vencimento)}</td>
      <td>${CHIP[c.status] || c.status}</td>
      <td class="num">${fmt(c.valor)}</td>
      <td class="num font-semibold">${Number(c.valorAberto) > 0 ? fmt(c.valorAberto) : '—'}</td>
    </tr>`).join('')
    || '<tr><td colspan="7" class="text-center text-muted-foreground py-8">Nenhuma parcela com esses filtros</td></tr>';

  [...$('lista').querySelectorAll('tr[data-cliente]')].forEach((tr) => {
    tr.addEventListener('click', () => {
      // parcela de venda nossa: mostra os itens; carnê SET (sem venda no sistema): direto ao carnê
      if (tr.dataset.notinha) abrirDetalheVenda(tr.dataset.notinha, tr.dataset.cliente);
      else location.href = `/carne.html?cliente=${tr.dataset.cliente}`;
    });
  });

  // paginação
  totalPaginas = Math.max(1, Math.ceil(r.total / r.porPagina));
  const ini = r.total === 0 ? 0 : (r.pagina - 1) * r.porPagina + 1;
  const fim = Math.min(r.total, r.pagina * r.porPagina);
  $('pag-info').textContent = `${ini}–${fim} de ${Number(r.total).toLocaleString('pt-BR')} parcelas`;
  $('pag-ant').disabled = r.pagina <= 1;
  $('pag-prox').disabled = r.pagina >= totalPaginas;
}

let timer = null;
function recarregarComFiltro() {
  clearTimeout(timer);
  timer = setTimeout(() => { pagina = 1; carregar(); }, 250);
}

['f-q', 'f-status', 'f-tipo', 'f-de', 'f-ate'].forEach((id) => $(id).addEventListener('input', recarregarComFiltro));

$('f-limpar').addEventListener('click', () => {
  ['f-q', 'f-de', 'f-ate'].forEach((id) => { $(id).value = ''; });
  $('f-status').value = '';
  $('f-tipo').value = '';
  pagina = 1;
  carregar();
});

$('pag-ant').addEventListener('click', () => { if (pagina > 1) { pagina--; carregar(); } });
$('pag-prox').addEventListener('click', () => { if (pagina < totalPaginas) { pagina++; carregar(); } });

carregar();

// ============================================================
// Aba VENDAS: conferência, estorno e edição
// ============================================================
let loja = { nome: 'Loja', endereco: '', telefone: '' };
fetch('/api/config').then((r) => r.json()).then((c) => { loja = c; });

let vPagina = 1;
let vTotalPaginas = 1;

const FORMA = { DINHEIRO: 'Dinheiro', PIX: 'PIX', CARTAO: 'Cartão', FIADO: 'Fiado' };
const TZ = 'America/Sao_Paulo';
const dataHoraBr = (iso) => new Date(iso).toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', year: '2-digit', hour: '2-digit', minute: '2-digit', timeZone: TZ });

// alternância de abas (o Caixa do dia virou tela própria: /caixa.html)
$('aba-parcelas').addEventListener('click', () => alternarAba('parcelas'));
$('aba-vendas').addEventListener('click', () => alternarAba('vendas'));

function alternarAba(qual) {
  for (const aba of ['parcelas', 'vendas']) {
    $(`aba-${aba}`).classList.toggle('ativa', qual === aba);
    $(`painel-${aba}`).hidden = qual !== aba;
  }
  if (qual === 'vendas') carregarVendas();
}

async function carregarVendas() {
  const params = new URLSearchParams({ pagina: vPagina });
  if ($('v-q').value.trim()) params.set('q', $('v-q').value.trim());
  if ($('v-forma').value) params.set('forma', $('v-forma').value);
  if ($('v-de').value) params.set('de', $('v-de').value);
  if ($('v-ate').value) params.set('ate', $('v-ate').value);

  const r = await (await fetch(`/api/vendas?${params}`)).json();

  $('v-lista').innerHTML = r.vendas.map((v) => `
    <tr${v.cancelada ? ' style="opacity:.55"' : ''}>
      <td class="mono font-bold">${v.id}</td>
      <td class="mono">${dataHoraBr(v.data)}</td>
      <td class="font-medium">${v.clienteNome ?? '<span class="text-muted-foreground">à vista</span>'}${v.observacao ? ` <span title="${v.observacao.replace(/"/g, '&quot;')}">📝</span>` : ''}</td>
      <td>${v.vendedorNome ?? ''}</td>
      <td><span class="chip forma">${FORMA[v.formaPagamento] || v.formaPagamento}</span>${v.cancelada ? ' <span class="chip" style="background:#fee2e2;color:#b91c1c;font-weight:700">CANCELADA</span>' : ''}</td>
      <td class="num font-semibold"${v.cancelada ? ' style="text-decoration:line-through"' : ''}>${fmt(v.total)}</td>
      <td>${v.cancelada
        ? `<button class="acao-btn" data-acao="recibo" data-id="${v.id}">Recibo</button>`
        : `<button class="acao-btn" data-acao="recibo" data-id="${v.id}">Recibo</button>
        <button class="acao-btn" data-acao="editar" data-id="${v.id}" ${v.temRecebimento ? 'disabled title="Parcela já recebida no carnê"' : ''}>Editar</button>
        <button class="acao-btn perigo" data-acao="estornar" data-id="${v.id}" ${v.temRecebimento ? 'disabled title="Parcela já recebida no carnê"' : ''}>Estornar</button>`}
      </td>
    </tr>`).join('')
    || '<tr><td colspan="7" class="text-center text-muted-foreground py-8">Nenhuma venda com esses filtros</td></tr>';

  vTotalPaginas = Math.max(1, Math.ceil(r.total / r.porPagina));
  const ini = r.total === 0 ? 0 : (r.pagina - 1) * r.porPagina + 1;
  const fim = Math.min(r.total, r.pagina * r.porPagina);
  $('v-pag-info').textContent = `${ini}–${fim} de ${Number(r.total).toLocaleString('pt-BR')} vendas`;
  $('v-soma').textContent = `Σ ${fmt(r.totais.soma)}`;
  $('v-pag-ant').disabled = r.pagina <= 1;
  $('v-pag-prox').disabled = r.pagina >= vTotalPaginas;
}

$('v-lista').addEventListener('click', async (e) => {
  const btn = e.target.closest('button[data-acao]');
  if (!btn || btn.disabled) return;
  const id = btn.dataset.id;

  if (btn.dataset.acao === 'recibo') {
    const resp = await fetch(`/api/vendas/${id}`);
    if (resp.ok) imprimirRecibo(await resp.json(), loja);
    return;
  }
  if (btn.dataset.acao === 'editar') {
    confirmarAcao(`Editar a venda nº ${id}? Ela será reaberta no caixa — o registro atual é desfeito e refeito quando você fechar de novo.`,
      () => { location.href = `/?editar=${id}`; });
    return;
  }
  if (btn.dataset.acao === 'estornar') {
    confirmarAcao(`Estornar a venda nº ${id}? O estoque volta e o fiado é desfeito. Não dá para desfazer.`, async () => {
      const op = encodeURIComponent(window.usuarioLogado?.nome || '');
      const resp = await fetch(`/api/vendas/${id}?operador=${op}&motivo=estorno`, { method: 'DELETE' });
      if (!resp.ok) {
        const erro = await resp.json().catch(() => ({}));
        toastContas(erro.erro || 'Não foi possível estornar', 'erro');
        return;
      }
      toastContas(`Venda nº ${id} estornada`, 'ok');
      if (!$('estornos-det').hidden) carregarEstornos();
      carregarVendas();
      carregar(); // KPIs e parcelas mudam junto
    });
  }
});

/** Modal com os itens da venda (endpoint que o Recibo já usa — nada novo no back). */
async function abrirDetalheVenda(vendaId, clienteId) {
  const resp = await fetch(`/api/vendas/${vendaId}`);
  if (!resp.ok) { toastContas('Não foi possível abrir a venda', 'erro'); return; }
  const v = await resp.json();

  const linhas = v.itens.map((i) => `
    <tr>
      <td class="py-1.5 pr-2 text-[13px]">${i.descricao}</td>
      <td class="py-1.5 pr-2 text-[13px] mono text-center">${i.quantidade}×</td>
      <td class="py-1.5 pr-2 text-[13px] mono text-right">${fmt(i.precoUnit)}</td>
      <td class="py-1.5 text-[13px] mono text-right font-semibold">${fmt(i.subtotal)}</td>
    </tr>`).join('');

  const overlay = document.createElement('div');
  overlay.className = 'fixed inset-0 z-[100] flex items-center justify-center';
  overlay.style.background = 'rgba(0,0,0,.55)';
  overlay.innerHTML = `
    <div style="background: var(--background); border: 1px solid var(--border)" class="rounded-xl shadow-2xl max-w-lg w-full mx-4 flex flex-col max-h-[85vh]">
      <div class="px-5 py-4 border-b border-border">
        <p class="m-0 text-[15px] font-bold">Venda nº ${v.id}</p>
        <p class="m-0 mt-1 text-[12px] text-muted-foreground">${v.clienteNome ?? 'à vista'} — ${dataHoraBr(v.data)}${v.observacao ? ' · 📝 ' + v.observacao : ''}</p>
      </div>
      <div class="px-5 py-3 overflow-y-auto">
        <table class="w-full" style="border-collapse: collapse">
          <thead><tr class="text-left text-[11px] uppercase tracking-wider text-muted-foreground">
            <th class="py-1 pr-2 font-semibold">Produto</th><th class="py-1 pr-2 font-semibold text-center">Qtd</th>
            <th class="py-1 pr-2 font-semibold text-right">Unit.</th><th class="py-1 font-semibold text-right">Subtotal</th>
          </tr></thead>
          <tbody>${linhas}</tbody>
        </table>
        <div class="border-t border-border mt-2 pt-2 text-[13px] flex flex-col gap-1">
          ${Number(v.desconto) > 0 ? `<div class="flex justify-between"><span>Desconto</span><span class="mono">− ${fmt(v.desconto)}</span></div>` : ''}
          <div class="flex justify-between font-bold text-[14px]"><span>Total</span><span class="mono">${fmt(v.total)}</span></div>
        </div>
      </div>
      <div class="flex gap-3 px-5 py-4 border-t border-border">
        <button id="dv-fechar" class="flex-1 py-2 rounded-lg font-semibold text-[13px]" style="background: var(--muted)">Fechar</button>
        <button id="dv-carne" class="flex-1 py-2 rounded-lg font-semibold text-[13px] text-white" style="background: var(--primary)">Abrir carnê do cliente</button>
      </div>
    </div>`;
  document.body.appendChild(overlay);
  const fechar = () => { overlay.remove(); document.removeEventListener('keydown', esc); };
  const esc = (e) => { if (e.key === 'Escape') fechar(); };
  document.addEventListener('keydown', esc);
  overlay.addEventListener('click', (e) => { if (e.target === overlay) fechar(); });
  overlay.querySelector('#dv-fechar').addEventListener('click', fechar);
  overlay.querySelector('#dv-carne').addEventListener('click', () => { location.href = `/carne.html?cliente=${clienteId}`; });
}

function confirmarAcao(msg, onYes) {
  const overlay = document.createElement('div');
  overlay.className = 'fixed inset-0 z-[100] flex items-center justify-center';
  overlay.style.background = 'rgba(0,0,0,.55)';
  overlay.innerHTML = `
    <div style="background: var(--background); border: 1px solid var(--border)" class="p-6 rounded-xl shadow-2xl max-w-sm w-full mx-4 flex flex-col gap-4">
      <p class="text-[14px] font-medium text-center leading-relaxed m-0">${msg}</p>
      <div class="flex gap-3">
        <button id="ca-nao" class="flex-1 py-2 rounded-lg font-semibold text-[13px]" style="background: var(--muted)">Não</button>
        <button id="ca-sim" class="flex-1 py-2 rounded-lg font-semibold text-[13px] text-white" style="background: var(--destructive)">Sim</button>
      </div>
    </div>`;
  document.body.appendChild(overlay);
  overlay.querySelector('#ca-nao').onclick = () => overlay.remove();
  overlay.querySelector('#ca-sim').onclick = () => { overlay.remove(); onYes(); };
}

let toastContasTimer = null;
function toastContas(msg, tipo = '') {
  const $t = $('toast');
  $t.textContent = msg;
  $t.className = `toast ${tipo}`;
  $t.hidden = false;
  clearTimeout(toastContasTimer);
  toastContasTimer = setTimeout(() => { $t.hidden = true; }, 4000);
}

let vTimer = null;
['v-q', 'v-forma', 'v-de', 'v-ate'].forEach((id) => $(id).addEventListener('input', () => {
  clearTimeout(vTimer);
  vTimer = setTimeout(() => { vPagina = 1; carregarVendas(); }, 250);
}));
$('v-limpar').addEventListener('click', () => {
  ['v-q', 'v-de', 'v-ate'].forEach((id) => { $(id).value = ''; });
  $('v-forma').value = '';
  vPagina = 1;
  carregarVendas();
});
$('v-pag-ant').addEventListener('click', () => { if (vPagina > 1) { vPagina--; carregarVendas(); } });
$('v-pag-prox').addEventListener('click', () => { if (vPagina < vTotalPaginas) { vPagina++; carregarVendas(); } });

// ---------- trilha de estornos ----------
$('ver-estornos').addEventListener('click', async () => {
  const det = $('estornos-det');
  det.hidden = !det.hidden;
  $('ver-estornos').textContent = det.hidden ? 'Ver estornos' : 'Ocultar estornos';
  if (!det.hidden) carregarEstornos();
});

async function carregarEstornos() {
  const lista = await (await fetch('/api/vendas/estornos')).json();
  $('estornos-corpo').innerHTML = lista.map((e) => `
    <tr>
      <td class="mono">${e.vendaId}</td>
      <td class="mono">${dataHoraBr(e.data)}</td>
      <td>${e.operador ?? '<span class="text-muted-foreground">?</span>'}</td>
      <td><span class="chip ${e.motivo === 'edicao' ? 'parcial' : 'atrasada'}">${e.motivo === 'edicao' ? 'Edição' : 'Estorno'}</span></td>
      <td>${e.clienteNome ?? 'à vista'}</td>
      <td class="text-muted-foreground" title="${(e.resumo || '').replace(/"/g, '&quot;')}">${(e.resumo || '').slice(0, 40)}${(e.resumo || '').length > 40 ? '…' : ''}</td>
      <td class="num font-semibold">${fmt(e.total)}</td>
    </tr>`).join('')
    || '<tr><td colspan="7" class="text-center text-muted-foreground py-6">Nenhum estorno registrado</td></tr>';
}
