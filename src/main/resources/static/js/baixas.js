/**
 * Recebimentos de carnê — reverter: desfaz um recebimento lançado errado,
 * devolvendo o valor às parcelas do cliente e tirando o dinheiro do caixa do
 * dia em que entrou. Só aparecem recebimentos com detalhamento por parcela.
 */
const fmt = (v) => Number(v).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
const $ = (id) => document.getElementById(id);
const operador = () => (window.usuarioLogado && window.usuarioLogado.nome) || null;
const dataHora = (x) => new Date(x).toLocaleString('pt-BR', { timeZone: 'America/Sao_Paulo', day: '2-digit', month: '2-digit', year: '2-digit', hour: '2-digit', minute: '2-digit' });

const ROTULO_FORMA = { DINHEIRO: 'Dinheiro', PIX: 'PIX', CARTAO: 'Cartão', VALE_CREDITO: 'Vale-crédito', MISTO: 'Misto (2 formas)' };

async function carregarRecebimentos() {
  const rs = await (await fetch(`/api/baixas/recebimentos?dias=${$('r-dias').value}`)).json();
  $('lista-receb').innerHTML = rs.map((r) => `
    <tr>
      <td class="font-medium">${r.clienteNome}</td>
      <td class="num font-semibold">${fmt(r.valor)}</td>
      <td class="text-[12px]">${ROTULO_FORMA[r.tipo] || r.tipo}</td>
      <td class="text-[12px]">${dataHora(r.data)}</td>
      <td class="text-[12px]">${r.operador || '—'}</td>
      <td><button class="acao-btn perigo" data-reverter="${r.id}" title="Desfazer este recebimento — devolve as parcelas e tira do caixa"><i data-lucide="rotate-ccw" class="w-3.5 h-3.5"></i> Reverter</button></td>
    </tr>`).join('') || '<tr><td colspan="6" class="text-center text-muted-foreground py-10">Nenhum recebimento reversível no período.</td></tr>';
  lucide.createIcons();
}

$('lista-receb').addEventListener('click', async (e) => {
  const btn = e.target.closest('button[data-reverter]');
  if (!btn) return;
  if (!confirm('Reverter este recebimento?\n\nO valor volta às parcelas do cliente e SAI do caixa do dia em que entrou. Use só se foi lançado errado.')) return;
  const r = await fetch(`/api/baixas/recebimentos/${btn.dataset.reverter}/reverter?operador=${encodeURIComponent(operador() || '')}`, { method: 'POST' });
  if (r.ok) { toast('Recebimento revertido — valor devolvido às parcelas'); carregarRecebimentos(); }
  else { const er = await r.json().catch(() => ({})); toast(er.erro || 'Erro ao reverter', 'erro'); }
});
$('r-dias').addEventListener('change', carregarRecebimentos);

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

carregarRecebimentos();
