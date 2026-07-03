/**
 * Caixa do dia: movimento por forma + conferência física da gaveta.
 * A operadora digita SALDO ANTERIOR e CONTAGEM; o resto é calculado do
 * movimento real. "Fechar o caixa" grava no servidor (que recalcula esperado
 * e diferença — a conta da tela é só espelho) e a contagem de hoje vira a
 * sugestão de saldo anterior de amanhã.
 */

const $ = (id) => document.getElementById(id);
const fmt = (v) => Number(v).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
const ROTULO = { DINHEIRO: 'Dinheiro', PIX: 'PIX', CARTAO: 'Cartão', FIADO: 'Fiado (a prazo)', VALE_CREDITO: 'Vale-crédito' };

let mov = null; // resposta de /api/vendas/caixa-dia

function toast(msg, tipo) {
  const t = $('toast');
  t.textContent = msg;
  t.style.background = tipo === 'erro' ? 'var(--destructive)' : 'var(--ok)';
  t.hidden = false;
  setTimeout(() => { t.hidden = true; }, 4000);
}

async function carregar() {
  if (!$('cx-data').value) $('cx-data').value = new Date().toLocaleDateString('sv-SE');
  mov = await (await fetch(`/api/vendas/caixa-dia?data=${$('cx-data').value}`)).json();

  const linhas = (lista) => lista.map((l) => `
    <tr><td>${ROTULO[l.rotulo] || l.rotulo}</td>
        <td class="num text-muted-foreground">${l.qtd}×</td>
        <td class="num font-semibold">${fmt(l.total)}</td></tr>`).join('')
    || '<tr><td colspan="3" class="text-center text-muted-foreground py-5">Nada neste dia</td></tr>';

  $('cx-vendas').innerHTML = linhas(mov.vendasPorForma);
  $('cx-recebimentos').innerHTML = linhas(mov.recebimentosPorTipo);
  $('cx-total-vendas').textContent = fmt(mov.totalVendas);
  $('cx-total-receb').textContent = fmt(mov.totalRecebimentos);
  $('cx-entrou').textContent = fmt(mov.entrouNoCaixa);

  $('cx-saidas').innerHTML = (mov.saidasCrossDay || []).map((s) => `
    <tr><td>Venda nº <b>${s.vendaId}</b> (de ${new Date(s.diaVenda + 'T12:00:00').toLocaleDateString('pt-BR')})</td>
        <td>${ROTULO[s.formaPagamento] || s.formaPagamento}</td>
        <td class="num font-semibold" style="color: var(--bad)">− ${fmt(s.total)}</td></tr>`).join('')
    || '<tr><td colspan="3" class="text-center text-muted-foreground py-5">Nenhuma devolução de dias anteriores hoje</td></tr>';

  // saldo anterior: 1º o fechamento já gravado deste dia, senão a contagem do último fechamento
  const f = mov.fechamento;
  const saldoIni = f ? f.saldoAnterior : (mov.saldoAnteriorSugerido ?? 0);
  $('cf-saldo-anterior').value = Number(saldoIni).toFixed(2).replace('.', ',');
  formatarMoeda($('cf-saldo-anterior'));
  $('cf-sugestao').textContent = f
    ? 'Vindo do fechamento já gravado deste dia'
    : (mov.saldoAnteriorSugerido != null
        ? `Sugerido: contagem do último fechamento (${fmt(mov.saldoAnteriorSugerido)})`
        : 'Nenhum fechamento anterior — digite o valor que ficou na gaveta');

  if (f) {
    $('cf-contagem').value = Number(f.contagem).toFixed(2).replace('.', ',');
    formatarMoeda($('cf-contagem'));
    $('cf-status').textContent = `Caixa deste dia já foi fechado${f.operador ? ' por ' + f.operador : ''} — dá para refazer`;
    $('cf-fechar').textContent = 'Refazer o fechamento';
  } else {
    $('cf-contagem').value = '';
    $('cf-status').textContent = '';
    $('cf-fechar').textContent = 'Fechar o caixa';
  }

  recalcular();
}

/** Espelho da conta do servidor: esperado = saldo anterior + entradas − saídas. */
function recalcular() {
  if (!mov) return;
  const saldoAnterior = lerMoeda($('cf-saldo-anterior'));
  const esperado = Math.round((saldoAnterior + Number(mov.entradasDinheiro) - Number(mov.saidasDinheiro)) * 100) / 100;

  $('cf-entradas').textContent = fmt(mov.entradasDinheiro);
  $('cf-saidas').textContent = `− ${fmt(mov.saidasDinheiro)}`;
  $('cf-esperado').textContent = fmt(esperado);

  const box = $('cf-diferenca-box');
  if (!$('cf-contagem').value.trim()) {
    $('cf-diferenca').textContent = '—';
    $('cf-diferenca-rotulo').textContent = 'digite a contagem da gaveta';
    box.style.background = 'var(--muted)';
    $('cf-diferenca').style.color = 'var(--foreground)';
    return;
  }
  const diferenca = Math.round((lerMoeda($('cf-contagem')) - esperado) * 100) / 100;
  $('cf-diferenca').textContent = fmt(diferenca);
  if (diferenca === 0) {
    box.style.background = 'var(--ok-bg)';
    $('cf-diferenca').style.color = 'var(--ok)';
    $('cf-diferenca-rotulo').textContent = 'Confere ✓';
  } else {
    box.style.background = 'var(--bad-bg)';
    $('cf-diferenca').style.color = 'var(--bad)';
    $('cf-diferenca-rotulo').textContent = diferenca > 0
      ? 'SOBRA na gaveta em relação ao esperado'
      : 'FALTA na gaveta em relação ao esperado';
  }
}

let fechando = false;
async function fecharCaixa() {
  if (fechando) return;
  if (!$('cf-contagem').value.trim()) {
    toast('Digite a contagem do caixa antes de fechar', 'erro');
    $('cf-contagem').focus();
    return;
  }
  fechando = true;
  $('cf-fechar').disabled = true;
  try {
    const resp = await fetch('/api/vendas/caixa-dia/fechar', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        data: $('cx-data').value,
        saldoAnterior: lerMoeda($('cf-saldo-anterior')).toFixed(2),
        contagem: lerMoeda($('cf-contagem')).toFixed(2),
        operador: window.usuarioLogado?.nome || null,
      }),
    });
    if (!resp.ok) {
      const erro = await resp.json().catch(() => ({}));
      toast(erro.erro || 'Não foi possível fechar o caixa', 'erro');
      return;
    }
    const f = await resp.json();
    toast(`Caixa fechado — diferença ${fmt(f.diferenca)}`, 'ok');
    await carregar();
  } catch {
    toast('Sem conexão com o servidor', 'erro');
  } finally {
    fechando = false;
    $('cf-fechar').disabled = false;
  }
}

instalarMoeda($('cf-saldo-anterior'), recalcular);
instalarMoeda($('cf-contagem'), recalcular);
$('cx-data').addEventListener('input', carregar);
$('cf-fechar').addEventListener('click', fecharCaixa);
carregar();
