/**
 * Baixas de fiado — baixa por incobrabilidade, REVERSÍVEL.
 * Busca o cliente, mostra o saldo, dá baixa (zera o em aberto) com um
 * PagamentoFiado tipo BAIXA que reduz o saldo sem ser dinheiro. A lista permite
 * RESTAURAR: devolve o saldo idêntico. Tudo auditado.
 */
const fmt = (v) => Number(v).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
const $ = (id) => document.getElementById(id);
const operador = () => (window.usuarioLogado && window.usuarioLogado.nome) || null;
const dataHora = (x) => new Date(x).toLocaleString('pt-BR', { timeZone: 'America/Sao_Paulo', day: '2-digit', month: '2-digit', year: '2-digit', hour: '2-digit', minute: '2-digit' });

let clienteSel = null;
let saldoSel = 0;
let loja = { nome: 'Loja', endereco: '', telefone: '' };
fetch('/api/config').then((r) => r.json()).then((c) => { loja = c; }).catch(() => {});

// ---------- autocomplete cliente ----------
let tCliente = null;
$('b-cliente').addEventListener('input', () => {
  clienteSel = null;
  $('b-resumo').hidden = true;
  clearTimeout(tCliente);
  const q = $('b-cliente').value.trim();
  if (!q) { $('b-cliente-res').hidden = true; return; }
  tCliente = setTimeout(async () => {
    const cs = await (await fetch(`/api/clientes?q=${encodeURIComponent(q)}`)).json();
    const ul = $('b-cliente-res');
    ul.innerHTML = cs.slice(0, 30).map((c) =>
      `<li data-id="${c.id}" data-nome="${(c.nome || '').replace(/"/g, '&quot;')}">
         <span class="flex-1">${c.nome}</span>${c.cpf ? `<span class="cod">${c.cpf}</span>` : ''}</li>`).join('');
    ul.hidden = !cs.length;
  }, 200);
});
$('b-cliente-res').addEventListener('mousedown', async (e) => {
  const li = e.target.closest('li[data-id]');
  if (!li) return;
  e.preventDefault();
  clienteSel = { id: Number(li.dataset.id), nome: li.dataset.nome };
  $('b-cliente').value = clienteSel.nome;
  $('b-cliente-res').hidden = true;
  // saldo
  const s = await (await fetch(`/api/clientes/${clienteSel.id}/score`)).json();
  saldoSel = Number(s.saldoDevedor) || 0;
  $('b-nome').textContent = clienteSel.nome;
  $('b-saldo').textContent = fmt(saldoSel);
  $('b-dar').disabled = saldoSel <= 0;
  $('b-dar').title = saldoSel <= 0 ? 'Cliente sem saldo em aberto' : '';
  $('b-resumo').hidden = false;
});

// ---------- dar baixa ----------
$('b-dar').addEventListener('click', async () => {
  if (!clienteSel || saldoSel <= 0) return;
  if (!confirm(`Dar baixa de ${fmt(saldoSel)} no saldo de ${clienteSel.nome}?\n\nÉ reversível — dá pra restaurar depois.`)) return;
  $('b-dar').disabled = true;
  const body = { clienteId: clienteSel.id, motivo: $('b-motivo').value.trim() || null, operador: operador() };
  const r = await fetch('/api/baixas', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
  if (r.ok) {
    const b = await r.json();
    toast(`Baixa de ${fmt(b.valor)} registrada — ${clienteSel.nome}`, 'ok');
    clienteSel = null; saldoSel = 0;
    $('b-cliente').value = ''; $('b-motivo').value = ''; $('b-resumo').hidden = true;
    carregar();
  } else {
    const er = await r.json().catch(() => ({}));
    toast(er.erro || 'Erro ao dar baixa', 'erro');
    $('b-dar').disabled = false;
  }
});

// ---------- lista ----------
const CHIP = { ATIVA: 'ativa', REVERTIDA: 'revertida' };
async function carregar() {
  const baixas = await (await fetch(`/api/baixas?status=${$('f-status').value}`)).json();
  $('lista').innerHTML = baixas.map((b) => `
    <tr>
      <td class="font-medium">${b.clienteNome}</td>
      <td class="num font-semibold">${fmt(b.valor)}</td>
      <td class="text-[12px]">${dataHora(b.data)}${b.operador ? `<div class="text-muted-foreground">${b.operador}</div>` : ''}</td>
      <td class="text-[12px]">${b.motivo || '<span class="text-muted-foreground">—</span>'}</td>
      <td class="text-[12px]">${b.operador || '—'}</td>
      <td><span class="chip ${CHIP[b.status]}">${b.status === 'ATIVA' ? 'Ativa' : 'Revertida'}</span>${b.status === 'REVERTIDA' && b.dataReversao ? `<div class="text-[11px] text-muted-foreground mt-0.5">${dataHora(b.dataReversao)}</div>` : ''}</td>
      <td style="white-space:nowrap;display:flex;gap:6px">
        <button class="acao-btn" data-promissoria="${b.id}" title="Reimprimir promissória com o saldo atualizado (para grampear)"><i data-lucide="printer" class="w-3.5 h-3.5"></i> Promissória</button>
        ${b.status === 'ATIVA' ? `<button class="acao-btn" data-restaurar="${b.id}" title="Devolver a dívida ao cliente"><i data-lucide="rotate-ccw" class="w-3.5 h-3.5"></i> Restaurar</button>` : ''}
      </td>
    </tr>`).join('') || '<tr><td colspan="7" class="text-center text-muted-foreground py-10">Nenhuma baixa aqui.</td></tr>';
  lucide.createIcons();
}

$('lista').addEventListener('click', async (e) => {
  const imp = e.target.closest('button[data-promissoria]');
  if (imp) {
    try {
      const c = await (await fetch(`/api/baixas/${imp.dataset.promissoria}/comprovante`)).json();
      await imprimirPromissoriaBaixa(c, loja);
    } catch { toast('Falha ao gerar a promissória', 'erro'); }
    return;
  }
  const btn = e.target.closest('button[data-restaurar]');
  if (!btn) return;
  if (!confirm('Restaurar esta baixa? A dívida volta idêntica para o cliente.')) return;
  const r = await fetch(`/api/baixas/${btn.dataset.restaurar}/restaurar?operador=${encodeURIComponent(operador() || '')}`, { method: 'POST' });
  if (r.ok) { toast('Baixa restaurada — dívida devolvida'); carregar(); }
  else { const er = await r.json().catch(() => ({})); toast(er.erro || 'Erro ao restaurar', 'erro'); }
});
$('f-status').addEventListener('change', carregar);

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

carregar();
