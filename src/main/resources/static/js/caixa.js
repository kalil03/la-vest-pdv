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

  const vazio = (cols) => `<tr><td colspan="${cols}" class="text-center text-muted-foreground py-5">Nada neste dia</td></tr>`;
  const resumoFormas = (lista) => lista
    .map((l) => `${ROTULO[l.rotulo] || l.rotulo} ${fmt(l.total)} (${l.qtd}×)`).join(' · ');

  // linha a linha, com quem comprou/pagou
  $('cx-vendas').innerHTML = (mov.vendasDia || []).map((v) => `
    <tr><td class="mono text-muted-foreground">${v.id}</td>
        <td class="font-medium">${v.cliente ?? '<span class="text-muted-foreground">Consumidor</span>'}</td>
        <td><span class="chip forma">${ROTULO[v.formaPagamento] || v.formaPagamento}</span></td>
        <td class="num font-semibold">${fmt(v.total)}</td></tr>`).join('') || vazio(4);

  $('cx-recebimentos').innerHTML = (mov.recebimentosDia || []).map((r) => `
    <tr><td class="font-medium">${r.cliente}${r.vendaEntrada ? ` <span class="text-muted-foreground text-[11px]">entrada da venda nº ${r.vendaEntrada}</span>` : ''}</td>
        <td><span class="chip forma">${ROTULO[r.tipo] || r.tipo}</span></td>
        <td class="num font-semibold">${fmt(r.valor)}</td></tr>`).join('') || vazio(3);

  $('cx-vendas-formas').textContent = resumoFormas(mov.vendasPorForma);
  $('cx-receb-formas').textContent = resumoFormas(mov.recebimentosPorTipo);
  $('cx-total-vendas').textContent = fmt(mov.totalVendas);
  $('cx-total-receb').textContent = fmt(mov.totalRecebimentos);
  $('cx-entrou').textContent = fmt(mov.entrouNoCaixa);

  $('cx-saidas').innerHTML = (mov.saidasCrossDay || []).map((s) => `
    <tr><td>Venda nº <b>${s.vendaId}</b> (de ${new Date(s.diaVenda + 'T12:00:00').toLocaleDateString('pt-BR')})</td>
        <td>${ROTULO[s.formaPagamento] || s.formaPagamento}</td>
        <td class="num font-semibold" style="color: var(--bad)">− ${fmt(s.total)}</td></tr>`).join('')
    || '<tr><td colspan="3" class="text-center text-muted-foreground py-5">Nenhuma devolução de dias anteriores hoje</td></tr>';

  $('cx-retiradas').innerHTML = (mov.retiradasDia || []).map((r) => `
    <tr><td class="mono">${new Date(r.data).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit', timeZone: 'America/Sao_Paulo' })}</td>
        <td>${r.motivo ?? '<span class="text-muted-foreground">sem motivo</span>'}${r.operador ? ` <span class="text-muted-foreground text-[11px]">por ${r.operador}</span>` : ''}</td>
        <td class="num font-semibold" style="color: var(--bad)">− ${fmt(r.valor)}</td></tr>`).join('')
    || '<tr><td colspan="3" class="text-center text-muted-foreground py-5">Nenhuma retirada neste dia</td></tr>';
  $('cx-total-retiradas').textContent = fmt(mov.totalRetiradas || 0);

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

// ---------- retirada (sangria): valor + motivo, com confirmação ----------
let registrandoRetirada = false;
async function registrarRetirada() {
  if (registrandoRetirada) return;
  const valor = lerMoeda($('rt-valor'));
  if (!(valor > 0)) {
    toast('Digite o valor da retirada', 'erro');
    $('rt-valor').focus();
    return;
  }
  const motivo = $('rt-motivo').value.trim();

  const overlay = document.createElement('div');
  overlay.className = 'fixed inset-0 z-[100] flex items-center justify-center';
  overlay.style.background = 'rgba(0,0,0,.55)';
  overlay.innerHTML = `
    <div style="background: var(--background); border: 1px solid var(--border)" class="p-6 rounded-xl shadow-2xl max-w-sm w-full mx-4 flex flex-col gap-4">
      <p class="text-[14px] font-medium text-center leading-relaxed m-0">
        Registrar retirada de <b>${fmt(valor)}</b> da gaveta${motivo ? `<br><span class="text-muted-foreground text-[12px]">${motivo}</span>` : ''}?</p>
      <div class="flex gap-3">
        <button id="rt-nao" class="flex-1 py-2 rounded-lg font-semibold text-[13px]" style="background: var(--muted)">Não</button>
        <button id="rt-sim" class="flex-1 py-2 rounded-lg font-semibold text-[13px] text-white" style="background: var(--primary)">Sim, retirar</button>
      </div>
    </div>`;
  document.body.appendChild(overlay);
  overlay.querySelector('#rt-nao').addEventListener('click', () => overlay.remove());
  overlay.querySelector('#rt-sim').addEventListener('click', async () => {
    overlay.remove();
    registrandoRetirada = true;
    try {
      const resp = await fetch('/api/vendas/caixa-dia/retirada', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          valor: valor.toFixed(2), motivo: motivo || null,
          operador: window.usuarioLogado?.nome || null,
        }),
      });
      if (!resp.ok) {
        const erro = await resp.json().catch(() => ({}));
        toast(erro.erro || 'Não foi possível registrar a retirada', 'erro');
        return;
      }
      toast(`Retirada de ${fmt(valor)} registrada`, 'ok');
      $('rt-valor').value = '';
      $('rt-motivo').value = '';
      await carregar();
    } catch {
      toast('Sem conexão com o servidor', 'erro');
    } finally {
      registrandoRetirada = false;
    }
  });
}

instalarMoeda($('cf-saldo-anterior'), recalcular);
instalarMoeda($('cf-contagem'), recalcular);
instalarMoeda($('rt-valor'));
$('rt-registrar').addEventListener('click', registrarRetirada);
$('cx-data').addEventListener('input', carregar);
$('cf-fechar').addEventListener('click', fecharCaixa);
carregar();
