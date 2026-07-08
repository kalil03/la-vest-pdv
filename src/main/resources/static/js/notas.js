/**
 * Gestão de notas fiscais (NFC-e). Lista as notas emitidas com filtro por status
 * e busca; reemite as que deram erro e reimprime a DANFE (com QR) das autorizadas.
 * A emissão em si é o mesmo endpoint da venda: POST /api/vendas/{id}/nfce
 * (idempotente — se já autorizada devolve a DANFE, se deu erro tenta de novo).
 * Depende de recibo.js (danfeNfceHTML/imprimirDanfeNfce, esc).
 */

const $ = (id) => document.getElementById(id);
const fmt = (v) => Number(v).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });

let loja = { nome: 'Loja', endereco: '', telefone: '' };
fetch('/api/config').then((r) => r.json()).then((c) => { loja = c; }).catch(() => {});

const CHIP = {
  AUTORIZADO: ['aut', 'Autorizada'],
  ERRO: ['err', 'Com erro'],
  PROCESSANDO: ['proc', 'Processando'],
  CANCELADO: ['canc', 'Cancelada'],
};

function toast(msg, tipo) {
  const t = $('toast');
  if (!t) return;
  t.textContent = msg;
  t.className = 'toast' + (tipo === 'erro' ? ' erro' : tipo === 'ok' ? ' ok' : '');
  t.hidden = false;
  clearTimeout(toast._t);
  toast._t = setTimeout(() => { t.hidden = true; }, 3500);
}

async function carregar() {
  const status = $('nf-status').value;
  const q = $('nf-busca').value.trim();
  let linhas = [];
  try {
    const resp = await fetch(`/api/nfce?status=${encodeURIComponent(status)}&q=${encodeURIComponent(q)}`);
    if (resp.ok) linhas = await resp.json();
  } catch {
    toast('Falha ao carregar as notas', 'erro');
  }
  $('nf-vazio').hidden = linhas.length > 0;
  $('nf-conteudo').hidden = linhas.length === 0;
  $('nf-corpo').innerHTML = linhas.map(linhaHTML).join('');
  lucide.createIcons();
}

function linhaHTML(n) {
  const [cls, rot] = CHIP[n.status] || ['canc', n.status];
  const dt = n.autorizadaEm || n.criadaEm;
  const data = dt ? new Date(dt).toLocaleString('pt-BR', { timeZone: 'America/Sao_Paulo' }) : '—';
  const chaveCurta = n.chaveAcesso ? n.chaveAcesso.replace(/(.{4})/g, '$1 ').trim() : '—';
  const motivo = n.status === 'AUTORIZADO'
    ? `<span class="mono" style="font-size:10px;color:var(--muted-foreground)">${chaveCurta}</span>`
    : `<span style="font-size:11px;color:var(--bad)">${esc(n.mensagem || 'Sem detalhe')}</span>`;
  const btnXml = n.temXml
    ? `<a class="acao-btn" href="/api/nfce/${n.id}/xml" title="Baixar XML para o contador"><i data-lucide="download" class="w-3.5 h-3.5"></i> XML</a>` : '';
  let acoes;
  if (n.status === 'AUTORIZADO') {
    acoes = `<button class="acao-btn primario" data-danfe="${n.vendaId}"><i data-lucide="printer" class="w-3.5 h-3.5"></i> DANFE</button>`
      + btnXml
      + `<button class="acao-btn" data-cancelar="${n.vendaId}" style="color:var(--bad)" title="Cancelar na SEFAZ"><i data-lucide="x-circle" class="w-3.5 h-3.5"></i> Cancelar</button>`;
  } else if (n.status === 'CANCELADO') {
    acoes = btnXml;
  } else {
    acoes = `<button class="acao-btn" data-reemitir="${n.vendaId}"><i data-lucide="send" class="w-3.5 h-3.5"></i> Reemitir</button>`;
  }
  return `<tr>
    <td class="mono">nº ${n.vendaId}</td>
    <td>${esc(n.clienteNome || 'Consumidor')}</td>
    <td style="font-size:12px">${data}</td>
    <td class="num">${fmt(n.total)}</td>
    <td><span class="chip ${cls}">${rot}</span>${n.vendaCancelada ? ' <span class="chip canc">venda estornada</span>' : ''}</td>
    <td style="max-width:280px">${motivo}</td>
    <td style="white-space:nowrap;display:flex;gap:6px;flex-wrap:wrap">
      ${acoes}
      <a class="acao-btn" href="/produtos.html" title="Corrigir dados do produto (NCM, CFOP, código)"><i data-lucide="pencil" class="w-3.5 h-3.5"></i></a>
    </td>
  </tr>`;
}

async function danfeDaVenda(vendaId) {
  const resp = await fetch(`/api/vendas/${vendaId}/nfce`, { method: 'POST' });
  const r = await resp.json().catch(() => ({}));
  return { ok: resp.ok, r };
}

async function reimprimirDanfe(vendaId) {
  toast('Preparando DANFE…');
  const { ok, r } = await danfeDaVenda(vendaId);
  if (!ok || !r.danfe) { toast(r.mensagem || r.erro || 'Não foi possível gerar a DANFE', 'erro'); return; }
  const venda = await (await fetch(`/api/vendas/${vendaId}`)).json();
  await imprimirDanfeNfce(venda, loja, r.danfe);
}

async function reemitir(vendaId) {
  toast('Reemitindo NFC-e…');
  const { r } = await danfeDaVenda(vendaId);
  toast(r.mensagem || 'Processado', r.status === 'AUTORIZADA' ? 'ok' : 'erro');
  if (r.status === 'AUTORIZADA' && r.danfe) {
    const venda = await (await fetch(`/api/vendas/${vendaId}`)).json();
    await imprimirDanfeNfce(venda, loja, r.danfe);
  }
  carregar();
}

async function cancelar(vendaId) {
  const just = prompt('Justificativa do cancelamento (mínimo 15 caracteres — exigência da SEFAZ):');
  if (just === null) return;
  if (just.trim().length < 15) { toast('Justificativa precisa de pelo menos 15 caracteres', 'erro'); return; }
  toast('Cancelando na SEFAZ…');
  try {
    const resp = await fetch(`/api/nfce/${vendaId}/cancelar`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ justificativa: just.trim() }),
    });
    const r = await resp.json().catch(() => ({}));
    if (!resp.ok) { toast(r.erro || r.mensagem || 'Falha ao cancelar', 'erro'); return; }
    toast(r.mensagem || 'Processado', r.cancelada ? 'ok' : 'erro');
  } catch {
    toast('Falha de conexão ao cancelar', 'erro');
  }
  carregar();
}

$('nf-corpo').addEventListener('click', (e) => {
  const danfe = e.target.closest('[data-danfe]');
  const reem = e.target.closest('[data-reemitir]');
  const canc = e.target.closest('[data-cancelar]');
  if (danfe) reimprimirDanfe(danfe.dataset.danfe);
  else if (reem) reemitir(reem.dataset.reemitir);
  else if (canc) cancelar(canc.dataset.cancelar);
});

$('nf-status').addEventListener('change', carregar);
$('nf-atualizar').addEventListener('click', carregar);
let buscaTimer;
$('nf-busca').addEventListener('input', () => {
  clearTimeout(buscaTimer);
  buscaTimer = setTimeout(carregar, 300);
});

carregar();
