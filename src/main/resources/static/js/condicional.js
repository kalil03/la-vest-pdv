/**
 * Condicional: a cliente leva peças para provar em casa.
 * Abrir não baixa estoque (decisão do dono) — registra o que saiu e imprime um
 * comprovante. Fechar manda as peças que ela ficou para o caixa (?condicional=ID):
 * lá vira uma venda normal e a condicional é marcada FECHADA.
 */

const fmt = (v) => Number(v).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
const $ = (id) => document.getElementById(id);

let loja = { nome: 'Loja', endereco: '', telefone: '' };
fetch('/api/config').then((r) => r.json()).then((c) => { loja = c; });

let resumos = [];            // lista de condicionais
let itens = [];              // peças da nova condicional {variacaoId, codigo, descricao, qtd, preco}
let clienteSel = null;       // {id, nome}

// ---------- vendedores ----------
fetch('/api/vendedores').then((r) => r.json()).then((vs) => {
  $('n-vendedor').insertAdjacentHTML('beforeend',
    vs.map((v) => `<option value="${v.id}">${v.nome}</option>`).join(''));
});

// ---------- alternar lista / nova ----------
function mostrarNova(sim) {
  $('view-nova').hidden = !sim;
  $('view-nova').style.display = sim ? 'flex' : 'none';
  $('view-lista').hidden = sim;
  if (sim) { resetNova(); $('n-cliente').focus(); }
}
$('btn-nova').addEventListener('click', () => mostrarNova(true));
$('n-cancelar').addEventListener('click', () => mostrarNova(false));

function resetNova() {
  itens = [];
  clienteSel = null;
  $('n-cliente').value = '';
  $('n-cliente-badge').hidden = true;
  $('n-vendedor').value = '';
  $('n-obs').value = '';
  $('n-busca').value = '';
  renderItens();
}

// ---------- autocomplete cliente ----------
let tCliente = null;
$('n-cliente').addEventListener('input', () => {
  clienteSel = null;
  $('n-cliente-badge').hidden = true;
  clearTimeout(tCliente);
  const q = $('n-cliente').value.trim();
  if (!q) { $('n-cliente-res').hidden = true; return; }
  tCliente = setTimeout(async () => {
    const cs = await (await fetch(`/api/clientes?q=${encodeURIComponent(q)}`)).json();
    const ul = $('n-cliente-res');
    ul.innerHTML = cs.slice(0, 30).map((c) =>
      `<li data-id="${c.id}" data-nome="${c.nome.replace(/"/g, '&quot;')}">
         <span class="flex-1">${c.nome}</span>${c.cpf ? `<span class="cod">${c.cpf}</span>` : ''}</li>`).join('');
    ul.hidden = !cs.length;
  }, 200);
});
$('n-cliente-res').addEventListener('mousedown', (e) => {
  const li = e.target.closest('li[data-id]');
  if (!li) return;
  e.preventDefault();
  clienteSel = { id: Number(li.dataset.id), nome: li.dataset.nome };
  $('n-cliente').value = clienteSel.nome;
  $('n-cliente-badge').textContent = `Cliente nº ${clienteSel.id}`;
  $('n-cliente-badge').hidden = false;
  $('n-cliente-res').hidden = true;
  $('n-busca').focus();
});

// ---------- busca de produto ----------
function extrairQtd(texto) {
  const m = (texto || '').trim().match(/^(\d{1,3})\s*[xX*]\s*(.+)$/);
  return m ? { qtd: parseInt(m[1], 10), termo: m[2] } : { qtd: 1, termo: (texto || '').trim() };
}

let tBusca = null;
$('n-busca').addEventListener('input', () => {
  clearTimeout(tBusca);
  const { termo } = extrairQtd($('n-busca').value);
  if (!termo) { $('n-busca-res').hidden = true; return; }
  tBusca = setTimeout(async () => {
    const ps = await (await fetch(`/api/produtos?q=${encodeURIComponent(termo)}`)).json();
    const ul = $('n-busca-res');
    ul.innerHTML = ps.slice(0, 30).map((p, i) =>
      `<li data-i="${i}"><span class="cod">${p.codigo}</span><span class="flex-1">${p.nome}</span>
         <span class="cod">${fmt(p.preco)}</span></li>`).join('');
    ul._ps = ps;
    ul.hidden = !ps.length;
  }, 200);
});
$('n-busca-res').addEventListener('mousedown', (e) => {
  const li = e.target.closest('li[data-i]');
  if (!li) return;
  e.preventDefault();
  escolherProduto($('n-busca-res')._ps[Number(li.dataset.i)]);
});
$('n-busca').addEventListener('keydown', async (e) => {
  if (e.key !== 'Enter') return;
  e.preventDefault();
  const { termo } = extrairQtd($('n-busca').value);
  if (!termo) return;
  const ps = await (await fetch(`/api/produtos?q=${encodeURIComponent(termo)}`)).json();
  if (ps.length === 1) escolherProduto(ps[0]);
  else if (ps.length > 1) $('n-busca').dispatchEvent(new Event('input'));
  else toast('Produto não encontrado', 'erro');
});

function escolherProduto(p) {
  const qtd = extrairQtd($('n-busca').value).qtd;
  $('n-busca').value = '';
  $('n-busca-res').hidden = true;
  const visiveis = p.variacoes.filter((v) => !v.padrao);
  if (visiveis.length <= 1) {
    adicionarItem(p, visiveis[0] || p.variacoes[0], qtd);
  } else {
    escolherVariacao(p, visiveis, qtd);
  }
}

function escolherVariacao(p, variacoes, qtd) {
  const ul = $('n-busca-res');
  ul.innerHTML = `<li class="!cursor-default" style="flex-wrap:wrap">${p.nome} — escolha: ` +
    variacoes.map((v, i) => `<button type="button" class="vbtn" data-vi="${i}">${[v.tamanho, v.cor].filter(Boolean).join(' ')}</button>`).join('') +
    `</li>`;
  ul._variacoes = variacoes; ul._prod = p; ul._qtd = qtd;
  ul.hidden = false;
}
$('n-busca-res').addEventListener('click', (e) => {
  const b = e.target.closest('button[data-vi]');
  if (!b) return;
  const ul = $('n-busca-res');
  adicionarItem(ul._prod, ul._variacoes[Number(b.dataset.vi)], ul._qtd);
  ul.hidden = true;
});

function adicionarItem(p, variacao, qtd = 1) {
  const desc = [p.nome, variacao.tamanho, variacao.cor].filter(Boolean).join(' ');
  const existente = itens.find((i) => i.variacaoId === variacao.id);
  if (existente) existente.qtd += qtd;
  else itens.push({ variacaoId: variacao.id, codigo: p.codigo, descricao: desc, qtd, preco: Number(p.preco) });
  renderItens();
  $('n-busca').focus();
}

function renderItens() {
  $('n-vazio').hidden = itens.length > 0;
  $('n-itens').innerHTML = itens.map((it, i) => `
    <tr>
      <td class="mono">${it.codigo}</td>
      <td>${it.descricao}</td>
      <td class="num"><input type="number" min="1" value="${it.qtd}" data-i="${i}" data-campo="qtd" class="inp !py-1 !text-right" style="width:70px"></td>
      <td class="num"><input type="text" inputmode="decimal" value="${fmt(it.preco)}" data-i="${i}" data-campo="preco" class="inp !py-1 !text-right mono" style="width:120px"></td>
      <td class="num mono">${fmt(it.qtd * it.preco)}</td>
      <td><button class="acao-btn perigo" data-rem="${i}" title="Remover"><i data-lucide="trash-2" class="w-3.5 h-3.5"></i></button></td>
    </tr>`).join('');
  $('n-total').textContent = fmt(itens.reduce((s, it) => s + it.qtd * it.preco, 0));
  lucide.createIcons();
}

$('n-itens').addEventListener('input', (e) => {
  const inp = e.target.closest('input[data-i]');
  if (!inp) return;
  const it = itens[Number(inp.dataset.i)];
  if (inp.dataset.campo === 'qtd') it.qtd = Math.max(1, parseInt(inp.value, 10) || 1);
  else it.preco = parseMoeda(inp.value);
  // atualiza só os totais sem re-render (não perder foco do input)
  $('n-total').textContent = fmt(itens.reduce((s, x) => s + x.qtd * x.preco, 0));
  inp.closest('tr').querySelector('td.num.mono').textContent = fmt(it.qtd * it.preco);
});
$('n-itens').addEventListener('click', (e) => {
  const b = e.target.closest('button[data-rem]');
  if (!b) return;
  itens.splice(Number(b.dataset.rem), 1);
  renderItens();
});

function parseMoeda(s) {
  const n = Number(String(s).replace(/[^\d,-]/g, '').replace('.', '').replace(',', '.'));
  return isNaN(n) ? 0 : n;
}

// ---------- salvar ----------
$('n-salvar').addEventListener('click', async () => {
  if (!clienteSel) { toast('Escolha o cliente', 'erro'); return; }
  if (!itens.length) { toast('Adicione ao menos uma peça', 'erro'); return; }
  const body = {
    clienteId: clienteSel.id,
    vendedorId: $('n-vendedor').value ? Number($('n-vendedor').value) : null,
    observacao: $('n-obs').value.trim() || null,
    itens: itens.map((i) => ({ variacaoId: i.variacaoId, quantidade: i.qtd, precoUnit: i.preco })),
  };
  $('n-salvar').disabled = true;
  try {
    const r = await fetch('/api/condicionais', {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body),
    });
    if (!r.ok) { const er = await r.json().catch(() => ({})); toast(er.erro || 'Erro ao salvar', 'erro'); return; }
    const c = await r.json();
    imprimirHTML(comprovanteHTML(c, loja));
    toast(`Condicional nº ${c.id} registrada`, 'ok');
    mostrarNova(false);
    carregar();
  } finally {
    $('n-salvar').disabled = false;
  }
});

// ---------- lista ----------
const CHIP = { ABERTA: 'aberta', FECHADA: 'fechada', CANCELADA: 'cancelada' };
const dataHora = (x) => new Date(x).toLocaleString('pt-BR', { timeZone: 'America/Sao_Paulo', day: '2-digit', month: '2-digit', year: '2-digit', hour: '2-digit', minute: '2-digit' });

async function carregar() {
  resumos = await (await fetch(`/api/condicionais?status=${$('f-status').value}`)).json();
  $('lista').innerHTML = resumos.map((c, i) => {
    const aberta = c.status === 'ABERTA';
    return `
    <tr>
      <td class="mono">${c.id}</td>
      <td class="font-medium">${c.clienteNome}</td>
      <td class="text-[12px]">${dataHora(c.dataSaida)}</td>
      <td class="num">${c.totalPecas}</td>
      <td class="num mono">${fmt(c.total)}</td>
      <td><span class="chip ${CHIP[c.status]}">${c.status[0] + c.status.slice(1).toLowerCase()}</span>${c.vendaId ? `<div class="text-[11px] text-muted-foreground mt-0.5">venda nº ${c.vendaId}</div>` : ''}</td>
      <td>
        <div class="flex flex-wrap gap-1.5">
          ${aberta ? `<button class="acao-btn primario" data-fechar="${c.id}" title="Fechar: leva o que ficou para o caixa"><i data-lucide="check" class="w-3.5 h-3.5"></i> Fechar</button>` : ''}
          <button class="acao-btn" data-comprov="${i}" title="Reimprimir comprovante"><i data-lucide="printer" class="w-3.5 h-3.5"></i></button>
          ${aberta ? `<button class="acao-btn perigo" data-cancelar="${c.id}" title="Cancelar (voltou tudo)"><i data-lucide="x" class="w-3.5 h-3.5"></i></button>` : ''}
        </div>
      </td>
    </tr>`;
  }).join('') || '<tr><td colspan="7" class="text-center text-muted-foreground py-10">Nenhuma condicional aqui.</td></tr>';
  lucide.createIcons();
}

$('lista').addEventListener('click', async (e) => {
  const fechar = e.target.closest('button[data-fechar]');
  const cancelar = e.target.closest('button[data-cancelar]');
  const comprov = e.target.closest('button[data-comprov]');
  if (fechar) {
    location.href = `/?condicional=${fechar.dataset.fechar}`;
  } else if (cancelar) {
    if (!confirm('Cancelar esta condicional? (a cliente devolveu tudo)')) return;
    const r = await fetch(`/api/condicionais/${cancelar.dataset.cancelar}/cancelar`, { method: 'POST' });
    if (r.ok) { toast('Condicional cancelada'); carregar(); }
    else { const er = await r.json().catch(() => ({})); toast(er.erro || 'Erro', 'erro'); }
  } else if (comprov) {
    const full = await (await fetch(`/api/condicionais/${resumos[Number(comprov.dataset.comprov)].id}`)).json();
    imprimirHTML(comprovanteHTML(full, loja));
  }
});
$('f-status').addEventListener('change', carregar);

// ---------- comprovante 80mm ----------
function comprovanteHTML(c, loja) {
  const e = (s) => String(s ?? '').replace(/[&<>]/g, (ch) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[ch]));
  const linhas = c.itens.map((i) =>
    `<tr><td>${i.quantidade}x ${e(i.descricao)}</td><td class="dir">${fmt(i.precoUnit * i.quantidade)}</td></tr>`).join('');
  return `<!DOCTYPE html><html lang="pt-BR"><head><meta charset="UTF-8"><style>
    @page { size: 80mm auto; margin: 0; }
    body { width: 72mm; margin: 0 auto; font-family: 'Courier New', monospace; font-size: 12px; color: #000; }
    .centro { text-align: center; } .dir { text-align: right; }
    h1 { font-size: 15px; margin: 6px 0 2px; text-align: center; }
    .info { text-align: center; font-size: 11px; margin: 0; }
    .sep { border-top: 1px dashed #000; margin: 6px 0; }
    table { width: 100%; border-collapse: collapse; }
    td { padding: 1px 0; vertical-align: top; font-size: 12px; }
    .destaque { font-size: 14px; font-weight: bold; }
    .texto { font-size: 11px; margin: 6px 0; }
    .assinatura { margin-top: 30px; }
    .linha-assinatura { border-top: 1px solid #000; margin: 0 8px 2px; }
    .rodape { text-align: center; font-size: 10px; margin-top: 8px; }
  </style></head><body>
    <h1>${e(loja.nome)}</h1>
    <p class="info">${e(loja.endereco)}</p>
    <p class="info">${e(loja.telefone)}</p>
    <div class="sep"></div>
    <div class="centro" style="font-weight:bold">COMPROVANTE DE CONDICIONAL</div>
    <p class="info">Nº ${c.id} — ${dataHora(c.dataSaida)}</p>
    <p class="info">Cliente: ${e(c.clienteNome)}</p>
    ${c.vendedorNome ? `<p class="info">Vendedor(a): ${e(c.vendedorNome)}</p>` : ''}
    <div class="sep"></div>
    <table>${linhas}</table>
    <div class="sep"></div>
    <table><tr><td class="destaque">TOTAL DAS PEÇAS</td><td class="dir destaque">${fmt(c.total)}</td></tr></table>
    ${c.observacao ? `<p class="texto">Obs.: ${e(c.observacao)}</p>` : ''}
    <p class="texto">Estas peças foram levadas para prova/condicional. <b>Não é venda.</b>
      A cliente se compromete a devolver ou pagar as peças que ficar.</p>
    <div class="assinatura"><div class="linha-assinatura"></div><div class="centro">${e(c.clienteNome)}</div></div>
    <div class="rodape">Documento sem valor fiscal</div>
  </body></html>`;
}

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
