/**
 * Tela de venda rápida — o coração do sistema.
 * Fluxo: digita código/nome → Enter → (escolhe tamanho/cor se tiver grade)
 * → F2/F3/F4/F6 escolhe pagamento → F10 fecha, imprime e baixa estoque.
 * Tudo operável só pelo teclado: precisa ser mais rápido que escrever à mão.
 */

const fmt = (v) => Number(v).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });

// ---------- estado ----------
let itens = [];            // {variacaoId, codigo, descricao, qtd, preco}
let cliente = null;        // {id, nome} selecionado no autocomplete
let formaPagamento = 'DINHEIRO';
let loja = { nome: 'Loja', endereco: '', telefone: '' };

fetch('/api/config').then((r) => r.json()).then((c) => { loja = c; });

// ---------- elementos ----------
const $busca = document.getElementById('busca');
const $buscaResultados = document.getElementById('busca-resultados');
const $itens = document.getElementById('itens');
const $itensVazio = document.getElementById('itens-vazio');
const $total = document.getElementById('total');
const $cliente = document.getElementById('cliente');
const $clienteResultados = document.getElementById('cliente-resultados');
const $clienteBadge = document.getElementById('cliente-badge');
const $clienteLimpar = document.getElementById('cliente-limpar');
const $clienteNovoAviso = document.getElementById('cliente-novo-aviso');
const $fechar = document.getElementById('fechar');
const $formas = document.getElementById('formas');
const $toast = document.getElementById('toast');

// ---------- autocomplete genérico (produto e cliente usam o mesmo) ----------
function criarAutocomplete($input, $lista, buscar, renderizar, escolher) {
  let resultados = [];
  let ativo = -1;
  let timer = null;

  function fechar() {
    $lista.hidden = true;
    resultados = [];
    ativo = -1;
  }

  function abrir(items) {
    resultados = items;
    ativo = items.length ? 0 : -1;
    $lista.innerHTML = '';
    items.forEach((item, i) => {
      const li = document.createElement('li');
      li.innerHTML = renderizar(item);
      li.classList.toggle('ativo', i === ativo);
      li.addEventListener('mousedown', (e) => { e.preventDefault(); escolher(item); fechar(); });
      $lista.appendChild(li);
    });
    $lista.hidden = items.length === 0;
  }

  function marcarAtivo() {
    [...$lista.children].forEach((li, i) => li.classList.toggle('ativo', i === ativo));
    $lista.children[ativo]?.scrollIntoView({ block: 'nearest' });
  }

  $input.addEventListener('input', () => {
    clearTimeout(timer);
    const q = $input.value.trim();
    if (!q) { fechar(); return; }
    timer = setTimeout(async () => abrir(await buscar(q)), 150);
  });

  $input.addEventListener('keydown', (e) => {
    if ($lista.hidden) return;
    if (e.key === 'ArrowDown') { e.preventDefault(); ativo = Math.min(ativo + 1, resultados.length - 1); marcarAtivo(); }
    else if (e.key === 'ArrowUp') { e.preventDefault(); ativo = Math.max(ativo - 1, 0); marcarAtivo(); }
    else if (e.key === 'Enter') { e.preventDefault(); if (ativo >= 0) { escolher(resultados[ativo]); fechar(); } }
    else if (e.key === 'Escape') { fechar(); }
  });

  $input.addEventListener('blur', () => setTimeout(fechar, 150));
}

// ---------- busca e escolha de produto ----------
criarAutocomplete(
  $busca,
  $buscaResultados,
  async (q) => (await fetch(`/api/produtos?q=${encodeURIComponent(q)}`)).json(),
  (p) => {
    const estoqueTotal = p.variacoes.reduce((s, v) => s + v.estoque, 0);
    return `<span class="cod">${p.codigo}</span><span>${p.nome}</span>` +
      (estoqueTotal <= 0 ? '<span class="sem-estoque">sem estoque</span>' : '') +
      `<span class="preco">${fmt(p.preco)}</span>`;
  },
  escolherProduto
);

function escolherProduto(p) {
  $busca.value = '';
  const visiveis = p.variacoes.filter((v) => !v.padrao);
  if (visiveis.length === 0) {
    adicionarItem(p, p.variacoes[0]); // perfume etc.: variação padrão escondida
  } else if (visiveis.length === 1) {
    adicionarItem(p, visiveis[0]);
  } else {
    mostrarEscolhaVariacao(p, visiveis); // grade: botões rápidos de tamanho/cor
  }
}

function mostrarEscolhaVariacao(produto, variacoes) {
  document.querySelector('.variacoes-pendentes')?.remove();
  const tr = document.createElement('tr');
  tr.className = 'variacoes-pendentes';
  const td = document.createElement('td');
  td.colSpan = 6;
  td.append(`${produto.nome} — escolha: `);
  variacoes.forEach((v) => {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'vbtn';
    const rotulo = [v.tamanho, v.cor].filter(Boolean).join(' ');
    btn.innerHTML = `${rotulo} <small>(${v.estoque})</small>`;
    btn.addEventListener('click', () => { tr.remove(); adicionarItem(produto, v); });
    btn.addEventListener('keydown', (e) => {
      if (e.key === 'ArrowRight') btn.nextElementSibling?.focus();
      if (e.key === 'ArrowLeft') btn.previousElementSibling?.focus();
      if (e.key === 'Escape') { tr.remove(); $busca.focus(); }
    });
    td.appendChild(btn);
  });
  tr.appendChild(td);
  $itens.appendChild(tr);
  $itensVazio.hidden = true;
  td.querySelector('button')?.focus();
}

function adicionarItem(produto, variacao) {
  const existente = itens.find((i) => i.variacaoId === variacao.id);
  if (existente) {
    existente.qtd += 1;
  } else {
    const rotuloVar = [variacao.tamanho, variacao.cor].filter(Boolean).join(' ');
    itens.push({
      variacaoId: variacao.id,
      codigo: produto.codigo,
      descricao: produto.nome + (rotuloVar ? ` ${rotuloVar}` : ''),
      qtd: 1,
      preco: Number(produto.preco),
    });
  }
  renderItens();
  $busca.focus();
}

function renderItens() {
  $itens.innerHTML = '';
  itens.forEach((item, idx) => {
    const tr = document.createElement('tr');

    const tdCod = document.createElement('td');
    tdCod.textContent = item.codigo;
    const tdDesc = document.createElement('td');
    tdDesc.textContent = item.descricao;

    const tdQtd = document.createElement('td');
    tdQtd.className = 'num';
    const inQtd = document.createElement('input');
    inQtd.type = 'number';
    inQtd.min = '1';
    inQtd.value = item.qtd;
    inQtd.className = 'qtd';
    inQtd.addEventListener('change', () => {
      item.qtd = Math.max(1, parseInt(inQtd.value, 10) || 1);
      renderItens();
    });
    tdQtd.appendChild(inQtd);

    const tdPreco = document.createElement('td');
    tdPreco.className = 'num';
    const inPreco = document.createElement('input');
    inPreco.type = 'number';
    inPreco.min = '0';
    inPreco.step = '0.01';
    inPreco.value = item.preco.toFixed(2);
    inPreco.className = 'preco';
    inPreco.addEventListener('change', () => {
      item.preco = Math.max(0, parseFloat(inPreco.value) || 0);
      renderItens();
    });
    tdPreco.appendChild(inPreco);

    const tdSub = document.createElement('td');
    tdSub.className = 'num';
    tdSub.textContent = fmt(item.qtd * item.preco);

    const tdDel = document.createElement('td');
    const btnDel = document.createElement('button');
    btnDel.className = 'remover';
    btnDel.textContent = '×';
    btnDel.title = 'Remover item';
    btnDel.addEventListener('click', () => { itens.splice(idx, 1); renderItens(); $busca.focus(); });
    tdDel.appendChild(btnDel);

    tr.append(tdCod, tdDesc, tdQtd, tdPreco, tdSub, tdDel);
    $itens.appendChild(tr);
  });
  $itensVazio.hidden = itens.length > 0;
  $total.textContent = fmt(itens.reduce((s, i) => s + i.qtd * i.preco, 0));
}

// ---------- cliente ----------
criarAutocomplete(
  $cliente,
  $clienteResultados,
  async (q) => (await fetch(`/api/clientes?q=${encodeURIComponent(q)}`)).json(),
  (c) => `<span>${c.nome}</span>${c.telefone ? `<span class="cod">${c.telefone}</span>` : ''}`,
  selecionarCliente
);

async function selecionarCliente(c) {
  cliente = c;
  $cliente.value = c.nome;
  $clienteLimpar.hidden = false;
  $clienteNovoAviso.hidden = true;

  // "Score da casa": quanto deve e em quantos dias costuma pagar
  const score = await (await fetch(`/api/clientes/${c.id}/score`)).json();
  const deve = Number(score.saldoDevedor);
  if (deve > 0) {
    const prazo = score.prazoMedioDias != null ? ` · paga em ~${Math.round(score.prazoMedioDias)} dias` : '';
    $clienteBadge.textContent = `Deve ${fmt(deve)}${prazo}`;
    $clienteBadge.className = 'badge';
  } else {
    $clienteBadge.textContent = 'Em dia';
    $clienteBadge.className = 'badge em-dia';
  }
  $clienteBadge.hidden = false;
}

function limparCliente() {
  cliente = null;
  $cliente.value = '';
  $clienteBadge.hidden = true;
  $clienteLimpar.hidden = true;
  $clienteNovoAviso.hidden = true;
}

$clienteLimpar.addEventListener('click', () => { limparCliente(); $cliente.focus(); });

// Digitou um nome mas não escolheu ninguém → será cadastrado na hora (sem fricção)
$cliente.addEventListener('input', () => {
  if (cliente && $cliente.value !== cliente.nome) {
    cliente = null;
    $clienteBadge.hidden = true;
  }
  const temTexto = $cliente.value.trim().length > 0;
  $clienteLimpar.hidden = !temTexto;
  $clienteNovoAviso.hidden = !(temTexto && !cliente);
});

// ---------- forma de pagamento ----------
function selecionarForma(forma) {
  formaPagamento = forma;
  [...$formas.children].forEach((b) => b.classList.toggle('ativa', b.dataset.forma === forma));
  const fiado = forma === 'FIADO';
  $fechar.classList.toggle('fiado', fiado);
  $fechar.innerHTML = fiado
    ? 'Fechar venda e imprimir promissória <kbd>F10</kbd>'
    : 'Fechar venda <kbd>F10</kbd>';
}

$formas.addEventListener('click', (e) => {
  const btn = e.target.closest('button');
  if (btn) selecionarForma(btn.dataset.forma);
});

// ---------- fechar venda: o clique único ----------
async function fecharVenda() {
  if (itens.length === 0) { toast('Adicione pelo menos um item'); $busca.focus(); return; }

  const body = {
    formaPagamento,
    itens: itens.map((i) => ({ variacaoId: i.variacaoId, quantidade: i.qtd, precoUnit: i.preco.toFixed(2) })),
  };
  if (cliente) {
    body.clienteId = cliente.id;
  } else if ($cliente.value.trim()) {
    body.clienteNome = $cliente.value.trim();
  }
  if (formaPagamento === 'FIADO' && !body.clienteId && !body.clienteNome) {
    toast('Venda fiado precisa de um cliente');
    $cliente.focus();
    return;
  }

  $fechar.disabled = true;
  try {
    const resp = await fetch('/api/vendas', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
    if (!resp.ok) {
      const erro = await resp.json().catch(() => ({}));
      toast(erro.erro || 'Erro ao fechar a venda');
      return;
    }
    const venda = await resp.json();
    imprimirRecibo(venda, loja);
    toast(`Venda nº ${venda.id} registrada — ${fmt(venda.total)}`, 'ok');
    resetarVenda();
  } catch {
    toast('Falha de conexão com o servidor');
  } finally {
    $fechar.disabled = false;
  }
}

function resetarVenda() {
  itens = [];
  renderItens();
  limparCliente();
  selecionarForma('DINHEIRO');
  $busca.value = '';
  $busca.focus();
}

$fechar.addEventListener('click', fecharVenda);

// ---------- atalhos globais ----------
document.addEventListener('keydown', (e) => {
  const atalhos = { F2: 'DINHEIRO', F3: 'PIX', F4: 'CARTAO', F6: 'FIADO' };
  if (atalhos[e.key]) { e.preventDefault(); selecionarForma(atalhos[e.key]); }
  else if (e.key === 'F10') { e.preventDefault(); fecharVenda(); }
});

// ---------- toast ----------
let toastTimer = null;
function toast(msg, tipo = '') {
  $toast.textContent = msg;
  $toast.className = `toast ${tipo}`;
  $toast.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { $toast.hidden = true; }, 4000);
}

renderItens();
