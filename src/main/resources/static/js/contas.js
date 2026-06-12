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
    <tr data-cliente="${c.clienteId}" title="Abrir o carnê de ${c.clienteNome}">
      <td class="font-medium">${c.clienteNome}</td>
      <td class="mono">${c.notinha ?? '<span class="text-muted-foreground">SET</span>'}</td>
      <td>${c.descricao}</td>
      <td class="mono">${dataBr(c.vencimento)}</td>
      <td>${CHIP[c.status] || c.status}</td>
      <td class="num">${fmt(c.valor)}</td>
      <td class="num font-semibold">${Number(c.valorAberto) > 0 ? fmt(c.valorAberto) : '—'}</td>
    </tr>`).join('')
    || '<tr><td colspan="7" class="text-center text-muted-foreground py-8">Nenhuma parcela com esses filtros</td></tr>';

  [...$('lista').querySelectorAll('tr[data-cliente]')].forEach((tr) => {
    tr.addEventListener('click', () => { location.href = `/carne.html?cliente=${tr.dataset.cliente}`; });
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

['f-q', 'f-status', 'f-de', 'f-ate'].forEach((id) => $(id).addEventListener('input', recarregarComFiltro));

$('f-limpar').addEventListener('click', () => {
  ['f-q', 'f-de', 'f-ate'].forEach((id) => { $(id).value = ''; });
  $('f-status').value = '';
  pagina = 1;
  carregar();
});

$('pag-ant').addEventListener('click', () => { if (pagina > 1) { pagina--; carregar(); } });
$('pag-prox').addEventListener('click', () => { if (pagina < totalPaginas) { pagina++; carregar(); } });

carregar();
