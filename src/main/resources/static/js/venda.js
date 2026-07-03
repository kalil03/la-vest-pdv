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
let vendaFechada = null;   // resumo da venda recém-fechada (fica na tela até Nova venda)
let formaPagamento = 'DINHEIRO';
let loja = { nome: 'Loja', endereco: '', telefone: '' };
let vendedores = [];
let parcelas = [];         // [{numero, valor, vencimento(yyyy-mm-dd)}] no modal
let condicionalEmFechamento = null; // id da condicional sendo fechada via /?condicional=
let tipoNotinha = '';      // "Geral" | "Tênis" — obrigatório
let tipoManual = false;    // operador escolheu na mão (não deixa a sugestão sobrescrever)
let enviandoVenda = false; // trava de duplo-submit: F10 repetido não pode gerar duas vendas

fetch('/api/config').then((r) => r.json()).then((c) => { loja = c; });

// padrões do carnê configuráveis em Ajustes (com fallback se o config não carregou)
const vencPadraoIso = () => isoMaisDias(new Date(), Math.max(1, parseInt(loja.carneVencDias, 10) || 30));
const parcelasPadrao = () => String(Math.max(1, parseInt(loja.carneParcelas, 10) || 1));

// operador logado no cabeçalho do caixa
document.addEventListener('DOMContentLoaded', () => {
  const op = document.getElementById('caixa-operador');
  if (op && window.usuarioLogado) op.textContent = window.usuarioLogado.nome;
});

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
$('c-vendedor').addEventListener('change', atualizarAvancar);

// ---------- tipo da notinha (Geral/Tênis) — obrigatório ----------
function pintarTipo() {
  document.querySelectorAll('#tipo-notinha .tipo-btn').forEach((b) =>
    b.classList.toggle('ativa', b.dataset.tipo === tipoNotinha));
}
function setTipo(t, manual) {
  tipoNotinha = t;
  if (manual) tipoManual = true;
  pintarTipo();
  agendarRascunhoVenda();
}
/** Sugere o tipo pelo carrinho (categoria Calçados => Tênis), sem sobrescrever escolha manual. */
function sugerirTipo() {
  if (tipoManual || !itens.length) return;
  const temCalcado = itens.some((i) => /cal[çc]ad/i.test(i.categoria || ''));
  setTipo(temCalcado ? 'Tênis' : 'Geral', false);
}
document.getElementById('tipo-notinha').addEventListener('click', (e) => {
  const b = e.target.closest('.tipo-btn');
  if (!b || vendaFechada) return;
  setTipo(b.dataset.tipo, true);
});
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
  async (q) => (await fetch(`/api/produtos?q=${encodeURIComponent(extrairQtd(q).termo)}`)).json(),
  (p) => `<span class="cod">${p.codigo}</span>` +
    `<span style="flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; margin-right: 8px;" title="${p.nome}">${p.nome}</span>` +
    (p.marcaNome ? `<span class="cod" style="max-width: 100px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">${p.marcaNome}</span>` : '') +
    `<span class="preco" style="margin-left: auto; white-space: nowrap;">${fmt(p.preco)}</span>`,
  escolherProduto
);

/** "2x1504" / "2X1504" = duas unidades do produto 1504. */
function extrairQtd(texto) {
  const q = (texto || '').trim();
  const m = q.match(/^(\d{1,3})\s*[xX*]\s*(.+)$/);
  if (m) return { qtd: parseInt(m[1], 10), termo: m[2] };
  return { qtd: 1, termo: q };
}

function escolherProduto(p) {
  const qtd = extrairQtd($busca.value).qtd; // captura o "2x" antes de limpar
  $busca.value = '';
  const visiveis = p.variacoes.filter((v) => !v.padrao);
  if (visiveis.length === 0) {
    adicionarItem(p, p.variacoes[0], qtd); // perfume etc.: variação padrão escondida
  } else if (visiveis.length === 1) {
    adicionarItem(p, visiveis[0], qtd);
  } else {
    mostrarEscolhaVariacao(p, visiveis, qtd); // grade: botões rápidos de tamanho/cor
  }
}

function mostrarEscolhaVariacao(produto, variacoes, qtd = 1) {
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
    btn.addEventListener('click', () => { tr.remove(); adicionarItem(produto, v, qtd); });
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

$busca.addEventListener('keydown', async (e) => {
  if (e.key === 'Enter' && !e.defaultPrevented) {
    e.preventDefault();
    const q = $busca.value.trim();
    if (!q) return;
    const ext = extrairQtd(q);
    try {
      const resp = await fetch(`/api/produtos?q=${encodeURIComponent(ext.termo)}`);
      const lista = await resp.json();
      if (lista.length === 1) {
        // Exatamente um resultado = leitor de código de barras bateu em cheio
        escolherProduto(lista[0]);
      } else if (lista.length > 1) {
        // Vários resultados = dispara o input pra forçar o dropdown a abrir
        $busca.dispatchEvent(new Event('input'));
      } else {
        toast('Produto não encontrado', 'erro');
      }
    } catch {
      toast('Sem conexão', 'erro');
    }
  }
});

function adicionarItem(produto, variacao, qtd = 1) {
  const existente = itens.find((i) => i.variacaoId === variacao.id);
  if (existente) {
    existente.qtd += qtd;
  } else {
    const rotuloVar = [variacao.tamanho, variacao.cor].filter(Boolean).join(' ');
    itens.push({
      variacaoId: variacao.id,
      codigo: produto.codigo,
      descricao: produto.nome + (rotuloVar ? ` ${rotuloVar}` : ''),
      qtd,
      preco: Number(produto.preco),
      categoria: produto.categoria || '',
    });
  }
  sugerirTipo();
  renderItens();
  $busca.focus();
}

function subtotalVenda() {
  return round2(itens.reduce((s, i) => s + i.qtd * i.preco, 0));
}


function atualizarAvancar() {
  $('avancar').disabled = itens.length === 0;
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
    inQtd.disabled = !!vendaFechada;
    inQtd.addEventListener('change', () => {
      item.qtd = Math.max(1, parseInt(inQtd.value, 10) || 1);
      renderItens();
    });
    tdQtd.appendChild(inQtd);

    const tdPreco = document.createElement('td');
    tdPreco.className = 'num';
    const inPreco = document.createElement('input');
    inPreco.value = item.preco.toFixed(2).replace('.', ',');
    inPreco.className = 'preco';
    inPreco.disabled = !!vendaFechada;
    instalarMoeda(inPreco); // digitou "10" → mostra "R$ 10,00"
    inPreco.addEventListener('change', () => {
      item.preco = Math.max(0, lerMoeda(inPreco));
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
    btnDel.style.visibility = vendaFechada ? 'hidden' : 'visible';
    btnDel.addEventListener('click', () => { itens.splice(idx, 1); renderItens(); $busca.focus(); });
    tdDel.appendChild(btnDel);

    tr.append(tdCod, tdDesc, tdQtd, tdPreco, tdSub, tdDel);
    $itens.appendChild(tr);
  });
  $itensVazio.hidden = itens.length > 0;
  $total.innerHTML = fmt(subtotalVenda()).replace('R$\u00a0', '<span class="cifrao">R$</span>');
  if (typeof atualizarAvancar === 'function') atualizarAvancar();
  agendarRascunhoVenda();
}

// ---------- mem\u00f3ria de rascunho da venda (volta ao reabrir o caixa) ----------
function coletarRascunhoVenda() {
  return {
    itens,
    clienteId: cliente?.id || null,
    clienteNome: cliente?.nome || $cliente.value.trim() || null,
    vendedorId: $('c-vendedor').value || null,
    observacao: $('c-observacao').value,
    data: $('v-data').value,
    forma: formaPagamento,
    tipoNotinha,
  };
}

const _agendaVenda = (typeof Rascunho !== 'undefined')
  ? Rascunho.autoSave('venda', coletarRascunhoVenda, (d) => Array.isArray(d.itens) && d.itens.length > 0)
  : function () {};

// n\u00e3o persiste enquanto h\u00e1 venda fechada travada na tela (j\u00e1 virou hist\u00f3rico)
function agendarRascunhoVenda() { if (!vendaFechada) _agendaVenda(); }

// ---------- cliente ----------
criarAutocomplete(
  $cliente,
  $('cliente-resultados'),
  async (q) => (await fetch(`/api/clientes?q=${encodeURIComponent(q)}`)).json(),
  (c) => `<span style="flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; margin-right: 8px;" title="${c.nome}">${c.nome}</span>` +
    (Number(c.saldoDevedor) > 0 ? `<span class="sem-estoque" style="margin-left: auto; white-space: nowrap; background: #fee2e2; color: #dc2626; padding: 2px 6px; border-radius: 4px; font-size: 11px; font-weight: bold;">deve ${fmt(c.saldoDevedor)}</span>` : ''),
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
  const vencidas = Number(score.parcelasVencidas) || 0;
  if (deve > 0) {
    const prazo = score.prazoMedioDias != null ? ` · paga em ~${Math.round(score.prazoMedioDias)} dias` : '';
    const atraso = vencidas > 0
      ? ` · ⚠ ${vencidas} vencida${vencidas > 1 ? 's' : ''} (${fmt(score.valorVencido)})`
      : '';
    $clienteBadge.textContent = `Deve ${fmt(deve)}${atraso}${prazo}`;
    $clienteBadge.className = vencidas > 0 ? 'badge atrasado' : 'badge';
  } else {
    $clienteBadge.textContent = 'Em dia';
    $clienteBadge.className = 'badge em-dia';
  }
  $clienteBadge.hidden = false;
  agendarRascunhoVenda();
}

function limparCliente() {
  cliente = null;
  $cliente.value = '';
  $clienteBadge.hidden = true;
  $clienteLimpar.hidden = true;
  $clienteNovoAviso.hidden = true;
  agendarRascunhoVenda();
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
  agendarRascunhoVenda();
}

$formas.addEventListener('click', (e) => {
  const btn = e.target.closest('button');
  if (btn) selecionarForma(btn.dataset.forma);
});

// ============================================================
// Modal de confirmação: revisão, desconto, vendedor, parcelas
// ============================================================

const vendedoresProntos = fetch('/api/vendedores').then((r) => r.json()).then((vs) => {
  vendedores = vs;
  const sel = $('c-vendedor');
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
  if (!$('c-vendedor').value) {
    toast('Selecione um vendedor no rodapé para poder fechar a venda!', 'erro');
    $('c-vendedor').focus();
    $('c-vendedor').classList.add('ring-2', 'ring-destructive');
    setTimeout(() => $('c-vendedor').classList.remove('ring-2', 'ring-destructive'), 2000);
    return;
  }
  if (tipoNotinha !== 'Geral' && tipoNotinha !== 'Tênis') {
    toast('Informe o tipo da notinha: Geral ou Tênis', 'erro');
    const box = $('tipo-notinha');
    box.classList.add('ring-2', 'ring-destructive', 'rounded-lg');
    setTimeout(() => box.classList.remove('ring-2', 'ring-destructive', 'rounded-lg'), 2000);
    return;
  }

  // espelho dos itens, só leitura
  $('m-itens').innerHTML = itens
    .map((i) => `<div class="m-item"><span>${i.qtd}× ${i.descricao}</span><span>${fmt(i.qtd * i.preco)}</span></div>`)
    .join('');

  let regerarParcelas = true;

  if (window.reabrirContexto) {
    const c = window.reabrirContexto;
    formaPagamento = c.formaPagamento || 'DINHEIRO';
    $('m-forma').value = formaPagamento;
    $('m-desconto-modo').value = 'R$';
    $('m-desconto').value = Number(c.desconto || 0).toFixed(2);
    formatarMoeda($('m-desconto'));
    
    if (c.fiado) {
      $('m-entrada').value = Number(c.fiado.entradaValor || 0).toFixed(2);
      formatarMoeda($('m-entrada'));
      if (c.fiado.entradaTipo) $('m-entrada-tipo').value = c.fiado.entradaTipo;
      $('m-num-parcelas').value = c.fiado.parcelas ? c.fiado.parcelas.length : 1;
      if (c.fiado.parcelas && c.fiado.parcelas.length > 0) {
        parcelas = c.fiado.parcelas.map((p) => ({
          numero: p.numero,
          valor: Number(p.valor),
          vencimento: p.vencimento
        }));
        regerarParcelas = false;
      }
    } else {
      $('m-entrada').value = '0';
      formatarMoeda($('m-entrada'));
      $('m-num-parcelas').value = parcelasPadrao();
    }
    if (c.parcelasCartao) $('m-parcelas-cartao').value = c.parcelasCartao;
    $('m-primeiro-venc').value = vencPadraoIso();
    window.reabrirContexto = null;
  } else {
    // Sincroniza o modal com a forma de pagamento que o usuário escolheu na tela principal (ex: F6)
    $('m-forma').value = formaPagamento;
  }

  $mErro.hidden = true;

  atualizarModal(regerarParcelas);
  $overlay.hidden = false;
  $('m-desconto').focus();
  $('m-desconto').select();
}

function fecharModal() {
  $overlay.hidden = true;
  $busca.focus();
}

function descontoValor() {
  // no modo % o campo é número puro; no modo R$ é campo de moeda formatado
  const v = $('m-desconto-modo').value === '%'
    ? Math.max(0, parseFloat(String($('m-desconto').value).replace(',', '.')) || 0)
    : Math.max(0, lerMoeda($('m-desconto')));
  return $('m-desconto-modo').value === '%'
    ? round2(subtotalVenda() * Math.min(v, 100) / 100)
    : Math.min(v, subtotalVenda());
}

const totalFinal = () => round2(subtotalVenda() - descontoValor());

/** Divide o restante em n parcelas iguais; a última absorve os centavos. */
function gerarParcelas(restante, n, primeiraDataIso) {
  if (!primeiraDataIso) primeiraDataIso = vencPadraoIso();
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
  $('m-div-recebido').hidden = forma !== 'DINHEIRO';
  $('m-confirmar').textContent = '';
  $('m-confirmar').insertAdjacentHTML('beforeend',
    (forma === 'FIADO' ? 'Confirmar e imprimir promissória ' : 'Confirmar e imprimir ') + '<kbd>F10</kbd>');

  if (forma === 'FIADO') {
    const entrada = Math.max(0, lerMoeda($('m-entrada')));
    const restante = round2(totalFinal() - entrada);
    const n = Math.max(1, parseInt($('m-num-parcelas').value, 10) || 1);
    if (regerar && restante > 0) {
      parcelas = gerarParcelas(restante, n, $('m-primeiro-venc').value);
    }
    renderParcelas(restante);
  }

  if (typeof calcularTroco === 'function') calcularTroco();
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
    inValor.value = p.valor.toFixed(2).replace('.', ',');
    instalarMoeda(inValor);
    // Editou uma parcela: as anteriores ficam, as seguintes redistribuem o resto
    inValor.addEventListener('change', () => {
      p.valor = Math.max(0.01, lerMoeda(inValor) || 0.01);
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
  // o atalho F10 chama esta função direto, sem passar pelo disabled do botão
  if (enviandoVenda) return;
  const forma = $('m-forma').value;

  const body = {
    formaPagamento: forma,
    desconto: descontoValor().toFixed(2),
    tipoNotinha,
    itens: itens.map((i) => ({ variacaoId: i.variacaoId, quantidade: i.qtd, precoUnit: i.preco.toFixed(2) })),
  };
  const vendedorId = $('c-vendedor').value;
  if (!vendedorId) {
    mostrarErroModal('Selecione o vendedor antes de fechar a venda');
    $('c-vendedor').focus();
    return;
  }
  body.vendedorId = Number(vendedorId);
  localStorage.setItem('pdv.vendedorId', vendedorId);
  if ($('v-data').value) body.data = $('v-data').value;
  if ($('c-observacao').value.trim()) body.observacao = $('c-observacao').value.trim();
  if (forma === 'CARTAO') body.parcelasCartao = Number($('m-parcelas-cartao').value);

  if (cliente) {
    body.clienteId = cliente.id;
  } else if ($cliente.value.trim()) {
    body.clienteNome = $cliente.value.trim();
  }

  if (forma === 'FIADO') {
    if (!body.clienteId && !body.clienteNome) {
      mostrarErroModal('Venda fiado precisa de um cliente — feche e informe no rodapé');
      return;
    }
    const entrada = Math.max(0, lerMoeda($('m-entrada')));
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

  enviandoVenda = true;
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
    // a venda fica na tela, travada: dá para cancelar/editar ou abrir uma nova
    vendaFechada = venda;
    // se a venda veio do fechamento de uma condicional, marca-a FECHADA (liga à venda)
    if (condicionalEmFechamento) {
      const cid = condicionalEmFechamento;
      condicionalEmFechamento = null;
      fetch(`/api/condicionais/${cid}/fechar?vendaId=${venda.id}`, { method: 'POST' })
        .then((r) => { if (r.ok) toast(`Condicional nº ${cid} fechada e ligada à venda`, 'ok'); });
    }
    localStorage.removeItem('pdv.recuperacao');
    if (typeof Rascunho !== 'undefined') Rascunho.limpar('venda');
    fecharModal();
    aplicarEstado();
  } catch {
    mostrarErroModal('Falha de conexão com o servidor');
  } finally {
    enviandoVenda = false;
    $('m-confirmar').disabled = false;
  }
}

// snapshot de segurança: se a luz cair entre o estorno e o refechamento,
// a venda em edição é recuperável ao reabrir o caixa
function salvarSnapshotEdicao(numeroOriginal) {
  localStorage.setItem('pdv.recuperacao', JSON.stringify({
    numeroOriginal,
    itens,
    clienteId: cliente?.id || null,
    clienteNome: cliente?.nome || $cliente.value.trim(),
    vendedorId: $('c-vendedor').value || null,
    observacao: $('c-observacao').value,
    data: $('v-data').value,
    forma: formaPagamento,
    tipoNotinha,
  }));
}

// ---------- venda fechada na tela: editar, cancelar, nova ----------
/** Trava/destrava a tela conforme exista uma venda recém-fechada nela. */
function aplicarEstado() {
  const fechada = !!vendaFechada;
  $('v-codigo').value = fechada ? `Nº ${vendaFechada.id}` : '';
  $('acoes-fechada').hidden = !fechada;
  $avancar.hidden = fechada;
  // venda fechada: a forma já está definida — esconde os botões de forma para
  // o painel caber na tela sem rolar (e dá lugar às ações pós-venda)
  $formas.hidden = fechada;
  $('nova-venda').classList.toggle('inativa', !fechada);
  $busca.disabled = fechada;
  $busca.placeholder = fechada
    ? `Venda nº ${vendaFechada.id} fechada — clique em Nova venda para continuar`
    : 'Código, cód. de barras ou nome do produto (Enter adiciona)';
  $cliente.disabled = fechada;
  $('c-vendedor').disabled = fechada;
  $('c-observacao').disabled = fechada;
  $('v-data').disabled = fechada;
  [...$formas.children].forEach((b) => { b.disabled = fechada; });
  renderItens();
}

async function cancelarVendaServidor(motivoEstorno = 'estorno') {
  const op = encodeURIComponent(window.usuarioLogado?.nome || '');
  const resp = await fetch(`/api/vendas/${vendaFechada.id}?operador=${op}&motivo=${motivoEstorno}`, { method: 'DELETE' });
  if (!resp.ok) {
    const erro = await resp.json().catch(() => ({}));
    toast(erro.erro || 'Não foi possível cancelar a venda', 'erro');
    return false;
  }
  return true;
}

function confirmCustom(msg, onYes) {
  const overlay = document.createElement('div');
  overlay.className = 'fixed inset-0 bg-black/60 z-[100] flex items-center justify-center backdrop-blur-sm';
  overlay.innerHTML = `
    <div class="bg-card p-6 rounded-xl shadow-2xl max-w-sm w-full mx-4 border border-border flex flex-col gap-4">
      <p class="text-[15px] text-foreground font-medium text-center leading-relaxed">${msg}</p>
      <div class="flex gap-3 mt-2">
        <button id="cc-no" class="flex-1 py-2 rounded-lg bg-secondary text-secondary-foreground hover:bg-secondary/80 font-semibold text-[14px]">Não</button>
        <button id="cc-yes" class="flex-1 py-2 rounded-lg bg-destructive text-destructive-foreground hover:opacity-90 font-semibold text-[14px]">Sim</button>
      </div>
    </div>
  `;
  document.body.appendChild(overlay);
  overlay.querySelector('#cc-no').onclick = () => overlay.remove();
  overlay.querySelector('#cc-yes').onclick = () => { overlay.remove(); onYes(); };
}

// Editar = cancela no servidor e destrava a mesma venda na tela para ajustar
$('vf-editar').addEventListener('click', async () => {
  if (!vendaFechada) return;
  const numero = vendaFechada.id;
  if (!(await cancelarVendaServidor('edicao'))) return;
  salvarSnapshotEdicao(numero);
  vendaFechada = null;
  aplicarEstado();
  toast(`Venda nº ${numero} aberta para edição — ajuste e feche de novo`, 'ok');
  $busca.focus();
});

$('vf-cancelar').addEventListener('click', () => {
  if (!vendaFechada) return;
  confirmCustom(`Cancelar a venda nº ${vendaFechada.id}? O estoque volta e o fiado é desfeito.`, async () => {
    const numero = vendaFechada.id;
    if (!(await cancelarVendaServidor())) return;
    vendaFechada = null;
    resetarVenda();
    toast(`Venda nº ${numero} cancelada — estoque devolvido`, 'ok');
  });
});

$('vf-recibo').addEventListener('click', () => {
  if (vendaFechada) imprimirRecibo(vendaFechada, loja);
});

// Emitir NFC-e da venda fechada. Hoje monta o XML na SEFAZ-PR (fundação grátis);
// quando o certificado A1 + CSC estiverem configurados, passa a assinar/transmitir.
$('vf-nfce').addEventListener('click', async () => {
  if (!vendaFechada) return;
  const btn = $('vf-nfce');
  btn.disabled = true;
  try {
    const resp = await fetch(`/api/vendas/${vendaFechada.id}/nfce`, { method: 'POST' });
    const r = await resp.json().catch(() => ({}));
    if (!resp.ok) {
      toast(r.erro || r.mensagem || 'Falha ao emitir NFC-e', 'erro');
      return;
    }
    toast(r.mensagem || 'NFC-e processada', r.status === 'AUTORIZADA' ? 'ok' : 'erro');
  } catch {
    toast('Falha de conexão ao emitir NFC-e', 'erro');
  } finally {
    btn.disabled = false;
  }
});

function novaVenda() {
  if (!vendaFechada) {
    toast(itens.length
      ? 'Feche a venda atual antes de começar outra (F10 revisa e fecha)'
      : 'Nada para limpar — a tela já está pronta para vender', 'erro');
    return;
  }
  vendaFechada = null;
  if (typeof Rascunho !== 'undefined') Rascunho.limpar('venda');
  resetarVenda();
  $busca.focus();
}

$('nova-venda').addEventListener('click', novaVenda);

function mostrarErroModal(msg) {
  $mErro.textContent = msg;
  $mErro.hidden = false;
}

function resetarVenda() {
  itens = [];
  parcelas = [];
  tipoNotinha = '';
  tipoManual = false;
  pintarTipo();
  $('v-codigo').value = '';
  $('v-data').value = new Date().toLocaleDateString('sv-SE'); // hoje, editável
  aplicarEstado();
  limparCliente();
  selecionarForma('DINHEIRO');
  $('m-desconto').value = '0';
  formatarMoeda($('m-desconto'));
  $('m-desconto-modo').value = 'R$';
  $('m-entrada').value = '0';
  formatarMoeda($('m-entrada'));
  $('m-num-parcelas').value = parcelasPadrao();
  $('m-primeiro-venc').value = vencPadraoIso();
  $('c-observacao').value = ''; // observação é da venda, não do caixa (vendedor fica)
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
  else if (e.key === 'F10') { e.preventDefault(); vendaFechada ? novaVenda() : abrirModal(); }
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

// Formatadores de moeda no modal
if (typeof instalarMoeda === 'function') {
  instalarMoeda($('m-entrada'));
  instalarMoeda($('m-recebido'), calcularTroco);
}

function calcularTroco() {
  const rec = lerMoeda($('m-recebido'));
  const tot = totalFinal();
  $('m-troco').textContent = rec > tot ? fmt(rec - tot) : 'R$ 0,00';
}

resetarVenda();


// ============================================================
// /?editar=ID — venda aberta a partir do Contas a Receber:
// desfaz no servidor (estoque volta) e recoloca tudo no carrinho
// para corrigir e fechar de novo.
// ============================================================
(async function () {
  const editarId = new URLSearchParams(location.search).get('editar');
  if (!editarId) return;
  history.replaceState({}, '', '/'); // F5 não repete a edição

  try {
    const resp = await fetch(`/api/vendas/${editarId}`);
    if (!resp.ok) {
      const erro = await resp.json().catch(() => ({}));
      toast(erro.erro || `Venda nº ${editarId} não encontrada`, 'erro');
      return;
    }
    const venda = await resp.json();

    const op = encodeURIComponent(window.usuarioLogado?.nome || '');
    const del = await fetch(`/api/vendas/${editarId}?operador=${op}&motivo=edicao`, { method: 'DELETE' });
    if (!del.ok) {
      const erro = await del.json().catch(() => ({}));
      toast(erro.erro || 'Não foi possível abrir esta venda para edição', 'erro');
      return;
    }

    itens = venda.itens.map((i) => ({
      variacaoId: i.variacaoId,
      codigo: i.codigo,
      descricao: i.descricao,
      qtd: i.quantidade,
      preco: Number(i.precoUnit),
    }));
    if (venda.clienteId) selecionarCliente({ id: venda.clienteId, nome: venda.clienteNome });
    await vendedoresProntos;
    if (venda.vendedorId) $('c-vendedor').value = venda.vendedorId;
    $('c-observacao').value = venda.observacao || '';
    $('v-data').value = new Date(venda.data).toLocaleDateString('sv-SE', { timeZone: 'America/Sao_Paulo' });
    selecionarForma(venda.formaPagamento);
    renderItens();
    if (typeof atualizarAvancar === 'function') atualizarAvancar();
    salvarSnapshotEdicao(editarId);
    toast(`Venda nº ${editarId} aberta para edição — ajuste e feche de novo`, 'ok');
    $busca.focus();
  } catch {
    toast('Sem conexão com o servidor', 'erro');
  }
})();

// ============================================================
// /?condicional=ID — fechamento de condicional: carrega as peças
// que saíram no carrinho; a atendente tira o que voltou e fecha a
// venda normalmente. Ao fechar, a condicional é marcada FECHADA.
// ============================================================
(async function () {
  const condId = new URLSearchParams(location.search).get('condicional');
  if (!condId) return;
  history.replaceState({}, '', '/');
  try {
    const resp = await fetch(`/api/condicionais/${condId}`);
    if (!resp.ok) { toast(`Condicional nº ${condId} não encontrada`, 'erro'); return; }
    const c = await resp.json();
    if (c.status !== 'ABERTA') { toast(`Condicional nº ${condId} já foi ${c.status.toLowerCase()}`, 'erro'); return; }

    itens = c.itens.map((i) => ({
      variacaoId: i.variacaoId, codigo: i.codigo, descricao: i.descricao,
      qtd: i.quantidade, preco: Number(i.precoUnit),
    }));
    if (c.clienteId) selecionarCliente({ id: c.clienteId, nome: c.clienteNome });
    await vendedoresProntos;
    if (c.vendedorId) $('c-vendedor').value = c.vendedorId;
    if (c.observacao) $('c-observacao').value = c.observacao;
    selecionarForma('FIADO'); // condicional é loja de crediário: já vem fiado (cai no carnê); troca p/ à vista se for o caso
    condicionalEmFechamento = Number(condId);
    renderItens();
    if (typeof atualizarAvancar === 'function') atualizarAvancar();
    toast(`Condicional nº ${condId} — tire o que a cliente devolveu e feche a venda`, 'ok');
    $busca.focus();
  } catch {
    toast('Sem conexão com o servidor', 'erro');
  }
})();

// ============================================================
// Recuperação: havia uma venda em edição que não foi refechada
// (queda de luz/fechou a aba). O registro original já foi
// estornado — oferecemos remontar o carrinho.
// ============================================================
(function () {
  if (new URLSearchParams(location.search).get('editar')) return; // edição nova tem prioridade
  if (new URLSearchParams(location.search).get('condicional')) return; // fechamento de condicional tem prioridade
  let snap = null;
  try { snap = JSON.parse(localStorage.getItem('pdv.recuperacao') || 'null'); } catch (e) { /* corrompido */ }
  if (!snap || !snap.itens?.length) return;

  confirmCustom(`A venda nº ${snap.numeroOriginal} estava em edição e não foi refechada. Recuperar os itens?`, async () => {
    itens = snap.itens;
    if (snap.clienteId) selecionarCliente({ id: snap.clienteId, nome: snap.clienteNome });
    else if (snap.clienteNome) $cliente.value = snap.clienteNome;
    await vendedoresProntos;
    if (snap.vendedorId) $('c-vendedor').value = snap.vendedorId;
    $('c-observacao').value = snap.observacao || '';
    if (snap.data) $('v-data').value = snap.data;
    selecionarForma(snap.forma || 'DINHEIRO');
    if (snap.tipoNotinha) setTipo(snap.tipoNotinha, true);
    renderItens();
    if (typeof atualizarAvancar === 'function') atualizarAvancar();
    toast('Venda recuperada — confira e feche', 'ok');
  });
  // recusou = descartou de vez (o overlay do confirmCustom só tem Sim/Não)
  const limparAoRecusar = setInterval(() => {
    const overlay = document.querySelector('#cc-no');
    if (overlay && !overlay.dataset.recuperacao) {
      overlay.dataset.recuperacao = '1';
      overlay.addEventListener('click', () => localStorage.removeItem('pdv.recuperacao'));
      clearInterval(limparAoRecusar);
    }
  }, 100);
  setTimeout(() => clearInterval(limparAoRecusar), 3000);
})();

// ============================================================
// Rascunho do caixa: cliente/vendedor/observação/data também
// disparam o auto-save; ao reabrir a aba, o carrinho volta.
// (edição via ?editar= e a recuperação de estorno têm prioridade)
// ============================================================
['c-observacao', 'c-vendedor', 'v-data', 'cliente'].forEach((id) => {
  const el = $(id);
  if (!el) return;
  el.addEventListener('input', agendarRascunhoVenda);
  el.addEventListener('change', agendarRascunhoVenda);
});

(function () {
  if (typeof Rascunho === 'undefined') return;
  if (new URLSearchParams(location.search).get('editar')) return;
  if (new URLSearchParams(location.search).get('condicional')) return;
  let rec = null;
  try { rec = JSON.parse(localStorage.getItem('pdv.recuperacao') || 'null'); } catch (e) { /* corrompido */ }
  if (rec && Array.isArray(rec.itens) && rec.itens.length) return; // recuperação tem prioridade

  const d = Rascunho.carregar('venda');
  if (!d || !Array.isArray(d.itens) || !d.itens.length) return;

  itens = d.itens;
  if (d.clienteId) selecionarCliente({ id: d.clienteId, nome: d.clienteNome });
  else if (d.clienteNome) { $cliente.value = d.clienteNome; $cliente.dispatchEvent(new Event('input')); }
  vendedoresProntos.then(() => {
    if (d.vendedorId) $('c-vendedor').value = d.vendedorId;
    if (typeof atualizarAvancar === 'function') atualizarAvancar();
  });
  if (d.observacao) $('c-observacao').value = d.observacao;
  if (d.data) $('v-data').value = d.data;
  selecionarForma(d.forma || 'DINHEIRO');
  if (d.tipoNotinha) setTipo(d.tipoNotinha, true);
  renderItens();

  Rascunho.aviso('Rascunho de venda recuperado', () => {
    Rascunho.limpar('venda');
    itens = [];
    limparCliente();
    $('c-observacao').value = '';
    selecionarForma('DINHEIRO');
    renderItens();
    $busca.focus();
  });
})();
