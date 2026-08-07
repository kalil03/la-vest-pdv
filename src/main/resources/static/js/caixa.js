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

// só DINHEIRO fica no caixa físico; PIX e CARTÃO "saem" (viram recebível, não dinheiro)
const naoEntraNaGaveta = (f) => f === 'PIX' || f === 'CARTAO';

// memória local do que a operadora digita (saldo anterior + contagem), por data —
// não perde ao recarregar/trocar de tela; some quando o caixa é fechado (aí o valor
// oficial passa a vir do fechamento gravado no servidor).
const draftKeyCaixa = (data) => 'pdv.caixa.' + (data || '');
function salvarDraftCaixa() {
  try {
    localStorage.setItem(draftKeyCaixa($('cx-data').value),
      JSON.stringify({ saldoAnterior: $('cf-saldo-anterior').value, contagem: $('cf-contagem').value }));
  } catch (e) { /* localStorage cheio/indisponível */ }
}
function lerDraftCaixa(data) {
  try { return JSON.parse(localStorage.getItem(draftKeyCaixa(data)) || 'null'); } catch (e) { return null; }
}
function limparDraftCaixa(data) {
  try { localStorage.removeItem(draftKeyCaixa(data)); } catch (e) { /* ok */ }
}

async function carregar() {
  if (!$('cx-data').value) $('cx-data').value = new Date().toLocaleDateString('sv-SE');
  mov = await (await fetch(`/api/vendas/caixa-dia?data=${$('cx-data').value}`)).json();

  let tAvista = 0, tAPrazo = 0, tReceb = 0, tRetirada = 0;
  const linhas = [];
  // colunas de valor: [A prazo, À vista, Recebimento, Retirada]
  const linhaCx = (vend, nota, desc, cls, vals) => {
    const [ap, av, re, rt] = vals.map((x) => (x ? fmt(x) : ''));
    return `<tr${cls ? ` class="${cls}"` : ''}>
      <td>${vend || ''}</td><td class="num">${nota != null ? nota : ''}</td><td>${desc}</td>
      <td class="num">${ap}</td><td class="num">${av}</td><td class="num">${re}</td><td class="num">${rt}</td></tr>`;
  };
  const naoGaveta = '<span style="color:#b45309">(cartão/PIX — não fica na gaveta)</span>';

  // vendas do dia: fiado→A PRAZO; dinheiro→À VISTA; cartão/PIX→À VISTA E RETIRADA
  // (é venda à vista, mas o valor não fica na gaveta — sai como saída, netando o caixa)
  (mov.vendasDia || []).forEach((v) => {
    const cli = v.cliente || 'Consumidor';
    const total = Number(v.total);
    const vend = v.vendedor || '';
    const forma = ROTULO[v.formaPagamento] || v.formaPagamento;
    if (v.formaPagamento === 'FIADO') {
      tAPrazo += total;
      linhas.push(linhaCx(vend, v.id, `${cli} · FIADO (a prazo)`, 'fiado', [total, 0, 0, 0]));
    } else if (naoEntraNaGaveta(v.formaPagamento)) {
      tAvista += total; tRetirada += total;
      linhas.push(linhaCx(vend, v.id, `${cli} · ${forma} ${naoGaveta}`, '', [0, total, 0, total]));
    } else {
      tAvista += total;
      linhas.push(linhaCx(vend, v.id, `${cli} · ${forma}`, '', [0, total, 0, 0]));
    }
  });

  // recebimentos de carnê / entradas: dinheiro→RECEBIMENTO; cartão/PIX→RECEBIMENTO E RETIRADA
  (mov.recebimentosDia || []).forEach((r) => {
    const valor = Number(r.valor);
    const forma = ROTULO[r.tipo] || r.tipo;
    const tipoTxt = r.vendaEntrada ? 'entrada' : 'recebimento';
    const desc = `${r.cliente} · ${tipoTxt} · ${forma}`;
    const nota = r.nota ? r.nota : (r.recibo != null ? `recibo ${r.recibo}` : '');
    if (naoEntraNaGaveta(r.tipo)) {
      tReceb += valor; tRetirada += valor;
      linhas.push(linhaCx('', nota, `${desc} ${naoGaveta}`, '', [0, 0, valor, valor]));
    } else {
      tReceb += valor;
      linhas.push(linhaCx('', nota, desc, '', [0, 0, valor, 0]));
    }
  });

  // devoluções de dias anteriores em dinheiro (dinheiro que saiu da gaveta hoje)
  (mov.saidasCrossDay || []).forEach((s) => {
    if (s.formaPagamento !== 'DINHEIRO') return;
    const total = Number(s.total);
    tRetirada += total;
    linhas.push(linhaCx('', s.vendaId,
      `Devolução da venda nº ${s.vendaId} (${new Date(s.diaVenda + 'T12:00:00').toLocaleDateString('pt-BR')})`,
      '', [0, 0, 0, total]));
  });

  // retiradas do dono (tira dinheiro pra depositar)
  (mov.retiradasDia || []).forEach((r) => {
    const valor = Number(r.valor);
    tRetirada += valor;
    const hora = new Date(r.data).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit', timeZone: 'America/Sao_Paulo' });
    linhas.push(linhaCx('', '',
      `RETIRADA · ${r.motivo || 'depósito'}${r.operador ? ` (${r.operador})` : ''} · ${hora}`,
      '', [0, 0, 0, valor]));
  });

  $('cx-mov').innerHTML = linhas.join('') || '<tr><td colspan="7" class="vazio">Nada neste dia</td></tr>';
  $('cx-t-avista').textContent = fmt(tAvista);
  $('cx-t-aprazo').textContent = fmt(tAPrazo);
  $('cx-t-receb').textContent = fmt(tReceb);
  $('cx-t-retirada').textContent = fmt(tRetirada);

  // resumo (rótulos da planilha)
  $('cx-venda-dia').textContent = fmt(mov.totalVendas);        // à prazo + à vista
  $('cx-recebimento').textContent = fmt(mov.entrouNoCaixa);    // à vista + recebimentos
  $('cx-saida-loja').textContent = fmt(tRetirada);             // tudo que saiu (não-dinheiro + retiradas)

  // saldo anterior + fechamento já gravado
  const f = mov.fechamento;
  const saldoIni = f ? f.saldoAnterior : (mov.saldoAnteriorSugerido ?? 0);
  $('cf-saldo-anterior').value = Number(saldoIni).toFixed(2).replace('.', ',');
  formatarMoeda($('cf-saldo-anterior'));
  $('cf-sugestao').textContent = f
    ? 'Saldo anterior vindo do fechamento já gravado deste dia'
    : (mov.saldoAnteriorSugerido != null
        ? `Sugerido: contagem do último fechamento (${fmt(mov.saldoAnteriorSugerido)})`
        : 'Nenhum fechamento anterior — digite o que ficou na gaveta');

  if (f) {
    $('cf-contagem').value = Number(f.contagem).toFixed(2).replace('.', ',');
    formatarMoeda($('cf-contagem'));
    $('cf-status').textContent = `Caixa deste dia já fechado${f.operador ? ' por ' + f.operador : ''} — dá para refazer`;
    $('cf-fechar').textContent = 'Refazer o fechamento';
  } else {
    $('cf-contagem').value = '';
    $('cf-status').textContent = '';
    $('cf-fechar').textContent = 'Fechar o caixa';
  }

  // memória: reaplica o que a operadora tinha digitado neste dia (sobre a sugestão)
  const draft = lerDraftCaixa($('cx-data').value);
  if (draft) {
    if (draft.saldoAnterior) { $('cf-saldo-anterior').value = draft.saldoAnterior; formatarMoeda($('cf-saldo-anterior')); }
    if (draft.contagem) { $('cf-contagem').value = draft.contagem; formatarMoeda($('cf-contagem')); }
  }

  recalcular();
}

/** SALDO FINAL = saldo anterior + dinheiro que entrou − dinheiro que saiu (só cash).
 *  Igual a: saldo anterior + À vista + Recebimento − Saída loja. */
function recalcular() {
  if (!mov) return;
  const saldoAnterior = lerMoeda($('cf-saldo-anterior'));
  const esperado = Math.round((saldoAnterior + Number(mov.entradasDinheiro) - Number(mov.saidasDinheiro)) * 100) / 100;
  $('cf-esperado').textContent = fmt(esperado);

  const el = $('cx-sobrou');
  if (!$('cf-contagem').value.trim()) {
    el.textContent = '—';
    el.style.color = 'var(--muted-foreground)';
    return;
  }
  const sobrou = Math.round((lerMoeda($('cf-contagem')) - esperado) * 100) / 100;
  el.textContent = fmt(sobrou);
  el.style.color = sobrou < 0 ? 'var(--bad)' : 'var(--ok)';
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
    limparDraftCaixa($('cx-data').value);   // fechado: valor oficial passa a vir do servidor
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
let tokenRetirada = null; // idempotência: 1 UUID por tentativa de sangria; reenvio reusa (servidor não duplica)
async function registrarRetirada() {
  if (registrandoRetirada) return;
  const valor = lerMoeda($('rt-valor'));
  if (!(valor > 0)) {
    toast('Digite o valor da retirada', 'erro');
    $('rt-valor').focus();
    return;
  }
  const motivo = $('rt-motivo').value.trim();
  // nova tentativa só se não há uma pendente: um reenvio após falha reusa o mesmo token
  if (!tokenRetirada) tokenRetirada = crypto.randomUUID();
  const diaSel = $('cx-data').value;                       // a retirada cai no dia que a tela mostra
  const diaBr = diaSel ? diaSel.split('-').reverse().join('/') : '';

  const overlay = document.createElement('div');
  overlay.className = 'fixed inset-0 z-[100] flex items-center justify-center';
  overlay.style.background = 'rgba(0,0,0,.55)';
  overlay.innerHTML = `
    <div style="background: var(--background); border: 1px solid var(--border)" class="p-6 rounded-xl shadow-2xl max-w-sm w-full mx-4 flex flex-col gap-4">
      <p class="text-[14px] font-medium text-center leading-relaxed m-0">
        Registrar retirada de <b>${fmt(valor)}</b> da gaveta${motivo ? `<br><span class="text-muted-foreground text-[12px]">${motivo}</span>` : ''}?
        <br><span class="text-muted-foreground text-[12px]">no caixa do dia <b>${diaBr}</b></span></p>
      <div class="flex gap-3">
        <button id="rt-nao" class="flex-1 py-2 rounded-lg font-semibold text-[13px]" style="background: var(--muted)">Não</button>
        <button id="rt-sim" class="flex-1 py-2 rounded-lg font-semibold text-[13px] text-white" style="background: var(--primary)">Sim, retirar</button>
      </div>
    </div>`;
  document.body.appendChild(overlay);
  // desistiu: a próxima retirada é uma tentativa nova (token novo)
  overlay.querySelector('#rt-nao').addEventListener('click', () => { overlay.remove(); tokenRetirada = null; });
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
          data: diaSel || null,
          idempotencyKey: tokenRetirada, // reenvio da mesma tentativa não duplica
        }),
      });
      if (!resp.ok) {
        const erro = await resp.json().catch(() => ({}));
        toast(erro.erro || 'Não foi possível registrar a retirada', 'erro');
        return; // mantém o token: se o operador tentar de novo, o servidor deduplica
      }
      toast(`Retirada de ${fmt(valor)} registrada`, 'ok');
      tokenRetirada = null; // sucesso: próxima retirada é nova
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

instalarMoeda($('cf-saldo-anterior'), () => { salvarDraftCaixa(); recalcular(); });
instalarMoeda($('cf-contagem'), () => { salvarDraftCaixa(); recalcular(); });
instalarMoeda($('rt-valor'));
$('rt-registrar').addEventListener('click', registrarRetirada);
$('cx-data').addEventListener('input', carregar);
$('cf-fechar').addEventListener('click', fecharCaixa);
carregar();
