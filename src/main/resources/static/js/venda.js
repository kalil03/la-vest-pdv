/**
 * Tela de venda rápida + confirmação.
 * Fluxo: digita código/nome → Enter → (escolhe tamanho/cor se tiver grade)
 * → F10 abre a confirmação (desconto, vendedor, parcelamento) → F10 de novo
 * confirma, grava e imprime. Esc na confirmação volta sem perder nada.
 */

const fmt = (v) => Number(v).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
const round2 = (v) => Math.round(v * 100) / 100;

// ---------- estado ----------
let itens = [];            // {variacaoId, codigo, descricao, qtd, preco}
let cliente = null;        // {id, nome} selecionado no autocomplete
let formaPagamento = 'DINHEIRO';
let loja = { nome: 'Loja', endereco: '', telefone: '' };
let vendedores = [];
let parcelas = [];         // [{numero, valor, vencimento(yyyy-mm-dd)}] no modal

fetch('/api/config').then((r) => r.json()).then((c) => { loja = c; });

// ---------- elementos ----------
const $ = (id) => document.getElementById(id);
const $busca = $('busca');
const $itens = $('itens');
const $itensVazio = $('itens-vazio');
const $total = $('total');
const $cliente = $('cliente');
const $clienteBadge = $('cliente-badge');
const $clienteLimpar = $('cliente-limpar');
const $clienteNovoAviso = $('cliente-novo-aviso');
const $avancar = $('avancar');
const $formas = $('formas');
const $toast = $('toast');
const $overlay = $('modal-overlay');
const $mErro = $('m-erro');

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
  $('busca-resultados'),
  async (q) => (await fetch(`/api/produtos?q=${encodeURIComponent(q)}`)).json(),
  (p) => `<span class="cod">${p.codigo}</span><span>${p.nome}</span>` +
         (p.marcaNome ? `<span class="cod">${p.marcaNome}</span>` : '') +
         `<span class="preco">${fmt(p.preco)}</span>`,
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
    btn.textContent = [v.tamanho, v.cor].filter(Boolean).join(' ');
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

function subtotalVenda() {
  return round2(itens.reduce((s, i) => s + i.qtd * i.preco, 0));
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
  $total.textContent = fmt(subtotalVenda());
}

// ---------- cliente ----------
criarAutocomplete(
  $cliente,
  $('cliente-resultados'),
  async (q) => (await fetch(`/api/clientes?q=${encodeURIComponent(q)}`)).json(),
  (c) => `<span>${c.nome}</span>${c.telefone ? `<span class="cod">${c.telefone}</span>` : ''}` +
         (Number(c.saldoDevedor) > 0 ? `<span class="sem-estoque">deve ${fmt(c.saldoDevedor)}</span>` : ''),
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
}

$formas.addEventListener('click', (e) => {
  const btn = e.target.closest('button');
  if (btn) selecionarForma(btn.dataset.forma);
});

// ============================================================
// Modal de confirmação: revisão, desconto, vendedor, parcelas
// ============================================================

fetch('/api/vendedores').then((r) => r.json()).then((vs) => {
  vendedores = vs;
  const sel = $('m-vendedor');
  vs.forEach((v) => {
    const opt = document.createElement('option');
    opt.value = v.id;
    opt.textContent = v.nome;
    sel.appendChild(opt);
  });
});

// parcelas no cartão: 1x a 12x
(() => {
  const sel = $('m-parcelas-cartao');
  for (let n = 1; n <= 12; n++) {
    const opt = document.createElement('option');
    opt.value = n;
    opt.textContent = `${n}x`;
    sel.appendChild(opt);
  }
})();

const modalAberto = () => !$overlay.hidden;

function abrirModal() {
  if (itens.length === 0) { toast('Adicione pelo menos um item'); $busca.focus(); return; }

  // espelho dos itens, só leitura
  $('m-itens').innerHTML = itens
    .map((i) => `<div class="m-item"><span>${i.qtd}× ${i.descricao}</span><span>${fmt(i.qtd * i.preco)}</span></div>`)
    .join('');

  $('m-desconto').value = '0';
  $('m-desconto-modo').value = 'R$';
  $('m-forma').value = formaPagamento;
  $('m-entrada').value = '0';
  $('m-num-parcelas').value = '1';
  $('m-primeiro-venc').value = isoMaisDias(new Date(), 30);
  $mErro.hidden = true;

  atualizarModal(true);
  $overlay.hidden = false;
  $('m-desconto').focus();
  $('m-desconto').select();
}

function fecharModal() {
  $overlay.hidden = true;
  $busca.focus();
}

function descontoValor() {
  const v = Math.max(0, parseFloat($('m-desconto').value) || 0);
  return $('m-desconto-modo').value === '%'
    ? round2(subtotalVenda() * Math.min(v, 100) / 100)
    : Math.min(v, subtotalVenda());
}

const totalFinal = () => round2(subtotalVenda() - descontoValor());

/** Divide o restante em n parcelas iguais; a última absorve os centavos. */
function gerarParcelas(restante, n, primeiraDataIso) {
  const cents = Math.round(restante * 100);
  const base = Math.floor(cents / n);
  return Array.from({ length: n }, (_, i) => ({
    numero: i + 1,
    valor: (i === n - 1 ? cents - base * (n - 1) : base) / 100,
    vencimento: isoMaisMeses(primeiraDataIso, i),
  }));
}

/** regerar = true recria o cronograma do zero (mudou nº de parcelas, entrada etc.). */
function atualizarModal(regerar) {
  const forma = $('m-forma').value;
  formaPagamento = forma;
  selecionarForma(forma);

  $('m-subtotal').textContent = fmt(subtotalVenda());
  $('m-total').textContent = fmt(totalFinal());
  $('m-parcelas-cartao').hidden = forma !== 'CARTAO';
  $('m-fiado').hidden = forma !== 'FIADO';
  $('m-confirmar').textContent = '';
  $('m-confirmar').insertAdjacentHTML('beforeend',
    (forma === 'FIADO' ? 'Confirmar e imprimir promissória ' : 'Confirmar e imprimir ') + '<kbd>F10</kbd>');

  if (forma === 'FIADO') {
    const entrada = Math.max(0, parseFloat($('m-entrada').value) || 0);
    const restante = round2(totalFinal() - entrada);
    const n = Math.max(1, parseInt($('m-num-parcelas').value, 10) || 1);
    if (regerar && restante > 0) {
      parcelas = gerarParcelas(restante, n, $('m-primeiro-venc').value);
    }
    renderParcelas(restante);
  }
}

function renderParcelas(restante) {
  const $corpo = $('m-parcelas-corpo');
  $corpo.innerHTML = '';
  if (restante <= 0) {
    $corpo.innerHTML = '<tr><td class="m-erro">Entrada deve ser menor que o total</td></tr>';
    return;
  }
  parcelas.forEach((p, i) => {
    const tr = document.createElement('tr');

    const tdNum = document.createElement('td');
    tdNum.textContent = `${p.numero}ª`;

    const tdValor = document.createElement('td');
    const inValor = document.createElement('input');
    inValor.type = 'number';
    inValor.min = '0.01';
    inValor.step = '0.01';
    inValor.value = p.valor.toFixed(2);
    // Editou uma parcela: as anteriores ficam, as seguintes redistribuem o resto
    inValor.addEventListener('change', () => {
      p.valor = Math.max(0.01, parseFloat(inValor.value) || 0.01);
      const fixado = parcelas.slice(0, i + 1).reduce((s, x) => s + x.valor, 0);
      const sobra = round2(restante - fixado);
      const seguintes = parcelas.length - (i + 1);
      if (seguintes > 0 && sobra > 0) {
        const novas = gerarParcelas(sobra, seguintes, p.vencimento);
        novas.forEach((nv, j) => {
          parcelas[i + 1 + j].valor = nv.valor;
        });
      }
      renderParcelas(restante);
    });
    tdValor.appendChild(inValor);

    const tdVenc = document.createElement('td');
    const inVenc = document.createElement('input');
    inVenc.type = 'date';
    inVenc.value = p.vencimento;
    inVenc.addEventListener('change', () => { p.vencimento = inVenc.value; });
    tdVenc.appendChild(inVenc);

    tr.append(tdNum, tdValor, tdVenc);
    $corpo.appendChild(tr);
  });

  const soma = round2(parcelas.reduce((s, p) => s + p.valor, 0));
  if (Math.abs(soma - restante) > 0.004) {
    $mErro.textContent = `Parcelas somam ${fmt(soma)}, mas o restante é ${fmt(restante)}`;
    $mErro.hidden = false;
  } else {
    $mErro.hidden = true;
  }
}

// recalculo automático: qualquer mudança nesses campos refaz o cronograma
['m-desconto', 'm-desconto-modo', 'm-entrada', 'm-num-parcelas', 'm-primeiro-venc']
  .forEach((id) => $(id).addEventListener('input', () => atualizarModal(true)));
$('m-forma').addEventListener('change', () => atualizarModal(true));

$('m-cancelar').addEventListener('click', fecharModal);
$('m-confirmar').addEventListener('click', confirmarVenda);
$avancar.addEventListener('click', abrirModal);

// ---------- confirmar: o clique único que grava + imprime ----------
async function confirmarVenda() {
  const forma = $('m-forma').value;

  const body = {
    formaPagamento: forma,
    desconto: descontoValor().toFixed(2),
    itens: itens.map((i) => ({ variacaoId: i.variacaoId, quantidade: i.qtd, precoUnit: i.preco.toFixed(2) })),
  };
  const vendedorId = $('m-vendedor').value;
  if (vendedorId) body.vendedorId = Number(vendedorId);
  if (forma === 'CARTAO') body.parcelasCartao = Number($('m-parcelas-cartao').value);

  if (cliente) {
    body.clienteId = cliente.id;
  } else if ($cliente.value.trim()) {
    body.clienteNome = $cliente.value.trim();
  }

  if (forma === 'FIADO') {
    if (!body.clienteId && !body.clienteNome) {
      mostrarErroModal('Venda fiado precisa de um cliente — informe no campo Cliente');
      return;
    }
    const entrada = Math.max(0, parseFloat($('m-entrada').value) || 0);
    const restante = round2(totalFinal() - entrada);
    const soma = round2(parcelas.reduce((s, p) => s + p.valor, 0));
    if (restante <= 0) { mostrarErroModal('Entrada deve ser menor que o total'); return; }
    if (Math.abs(soma - restante) > 0.004) {
      mostrarErroModal(`Parcelas somam ${fmt(soma)}, mas o restante após a entrada é ${fmt(restante)}`);
      return;
    }
    if (parcelas.some((p) => !p.vencimento)) { mostrarErroModal('Toda parcela precisa de vencimento'); return; }
    body.fiado = {
      entradaValor: entrada.toFixed(2),
      entradaTipo: entrada > 0 ? $('m-entrada-tipo').value : null,
      parcelas: parcelas.map((p) => ({ numero: p.numero, valor: p.valor.toFixed(2), vencimento: p.vencimento })),
    };
  }

  $('m-confirmar').disabled = true;
  try {
    const resp = await fetch('/api/vendas', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
    if (!resp.ok) {
      const erro = await resp.json().catch(() => ({}));
      mostrarErroModal(erro.erro || 'Erro ao fechar a venda');
      return;
    }
    const venda = await resp.json();
    imprimirRecibo(venda, loja);
    toast(`Venda nº ${venda.id} registrada — ${fmt(venda.total)}`, 'ok');
    fecharModal();
    resetarVenda();
  } catch {
    mostrarErroModal('Falha de conexão com o servidor');
  } finally {
    $('m-confirmar').disabled = false;
  }
}

function mostrarErroModal(msg) {
  $mErro.textContent = msg;
  $mErro.hidden = false;
}

function resetarVenda() {
  itens = [];
  parcelas = [];
  renderItens();
  limparCliente();
  selecionarForma('DINHEIRO');
  $busca.value = '';
  $busca.focus();
}

// ---------- atalhos globais ----------
document.addEventListener('keydown', (e) => {
  if (modalAberto()) {
    if (e.key === 'Escape') { e.preventDefault(); fecharModal(); }
    else if (e.key === 'F10') { e.preventDefault(); confirmarVenda(); }
    return;
  }
  const atalhos = { F2: 'DINHEIRO', F3: 'PIX', F4: 'CARTAO', F6: 'FIADO' };
  if (atalhos[e.key]) { e.preventDefault(); selecionarForma(atalhos[e.key]); }
  else if (e.key === 'F10') { e.preventDefault(); abrirModal(); }
});

// ---------- utilitários de data ----------
function isoMaisDias(data, dias) {
  const d = new Date(data);
  d.setDate(d.getDate() + dias);
  return d.toISOString().slice(0, 10);
}

function isoMaisMeses(iso, meses) {
  const [a, m, dia] = iso.split('-').map(Number);
  const d = new Date(a, m - 1 + meses, dia);
  // se o dia não existe no mês (31 → fev), recua para o último dia do mês
  if (d.getDate() !== dia) d.setDate(0);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

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
