/**
 * Recebimento de carnê.
 * Os pagamentos quitam sempre as parcelas mais antigas primeiro (FIFO) — por
 * isso a seleção é em "prefixo": marcar uma parcela marca todas as anteriores.
 * O status de cada parcela vem calculado do servidor, nada é gravado como flag.
 */

const fmt = (v) => Number(v).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
const $ = (id) => document.getElementById(id);
const round2 = (v) => Math.round(v * 100) / 100;

// ---------- estado ----------
let cliente = null;     // {id, nome, ...} selecionado
let carne = null;       // resposta de /api/clientes/{id}/carne
let selecionadas = [];  // seleção livre ordenada
let tipo = 'DINHEIRO';
let loja = { nome: 'Loja', endereco: '', telefone: '' };
let recebendo = false;

fetch('/api/config').then((r) => r.json()).then((c) => { loja = c; });

// ---------- operador (funcionário do caixa) ----------
const $operador = $('operador');

async function carregarOperadores() {
  const vendedores = await (await fetch('/api/vendedores')).json();
  vendedores.forEach((v) => {
    const opt = document.createElement('option');
    opt.value = v.id;
    opt.textContent = v.nome;
    $operador.appendChild(opt);
  });
  const salvo = localStorage.getItem('pdv.operadorId');
  if (salvo && vendedores.some((v) => String(v.id) === salvo)) $operador.value = salvo;
  atualizarOperador();
}

function atualizarOperador() {
  const nome = $operador.selectedOptions[0]?.textContent || '';
  const tem = !!$operador.value;
  localStorage.setItem('pdv.operadorId', $operador.value);
  const avatar = $('op-avatar'); // pode não existir (operador agora vive no painel)
  if (avatar) avatar.textContent = tem ? iniciais(nome) : '?';
  const indicador = $('header-operador-nome');
  if (indicador) indicador.textContent = tem ? `Operador: ${nome}` : 'Nenhum operador selecionado';
}

$operador.addEventListener('change', atualizarOperador);

// ---------- helpers visuais ----------
function iniciais(nome) {
  const partes = nome.trim().split(/\s+/);
  return ((partes[0]?.[0] || '') + (partes[1]?.[0] || '')).toUpperCase();
}

function corAvatar(nome) {
  let h = 0;
  for (const c of nome) h = (h * 31 + c.charCodeAt(0)) % 360;
  return `hsl(${h}, 60%, 42%)`;
}

function mascararCpf(cpf) {
  if (!cpf) return 'CPF não informado';
  const d = cpf.replace(/\D/g, '');
  return `***.***.***-${d.slice(-2)}`;
}

const dataBr = (iso) => { const [a, m, d] = iso.split('-'); return `${d}/${m}/${a}`; };

// ---------- autocomplete de cliente ----------
const $busca = $('busca-cliente');
const $resultados = $('busca-resultados');
let resultados = [];
let ativo = -1;
let timerBusca = null;

function fecharDropdown() {
  $resultados.hidden = true;
  resultados = [];
  ativo = -1;
}

function abrirDropdown(lista) {
  resultados = lista;
  ativo = lista.length ? 0 : -1;
  $resultados.innerHTML = '';
  if (!lista.length) {
    $resultados.innerHTML = '<li class="!cursor-default text-muted-foreground">Nenhum cliente encontrado</li>';
    $resultados.hidden = false;
    return;
  }
  lista.forEach((c, i) => {
    const li = document.createElement('li');
    li.className = i === ativo ? 'ativo' : '';
    const deve = Number(c.saldoDevedor) > 0;
    li.innerHTML = `
      <span class="w-8 h-8 rounded-full flex items-center justify-center text-[11px] font-bold text-white flex-shrink-0"
            style="background:${corAvatar(c.nome)}">${iniciais(c.nome)}</span>
      <span class="flex-1 min-w-0">
        <span class="block font-medium truncate">${c.nome}</span>
        <span class="block text-[11px] text-muted-foreground mono">${mascararCpf(c.cpf)}</span>
      </span>
      ${deve ? `<span class="chip atrasada">deve ${fmt(c.saldoDevedor)}</span>` : '<span class="chip prazo">em dia</span>'}`;
    li.style.alignItems = 'center';
    li.addEventListener('mousedown', (e) => { e.preventDefault(); selecionarCliente(c); fecharDropdown(); });
    $resultados.appendChild(li);
  });
  $resultados.hidden = false;
}

$busca.addEventListener('input', () => {
  clearTimeout(timerBusca);
  const q = $busca.value.trim();
  if (q.length < 2) { fecharDropdown(); return; }
  timerBusca = setTimeout(async () => {
    try {
      if (/^\d+$/.test(q)) {
        // Se for só número, tenta achar por notinha
        const resp = await fetch(`/api/clientes/por-venda/${q}`);
        if (resp.ok) {
          abrirDropdown([await resp.json()]);
        } else {
          abrirDropdown([]);
        }
      } else {
        abrirDropdown(await (await fetch(`/api/clientes?q=${encodeURIComponent(q)}`)).json());
      }
    } catch {
      toast('Sem conexão com o servidor', 'erro');
    }
  }, 180);
});

$busca.addEventListener('keydown', (e) => {
  if ($resultados.hidden) return;
  const marcar = () => [...$resultados.children].forEach((li, i) => {
    li.classList.toggle('ativo', i === ativo);
    if (i === ativo) li.scrollIntoView({ block: 'nearest' });
  });
  if (e.key === 'ArrowDown') { e.preventDefault(); ativo = Math.min(ativo + 1, resultados.length - 1); marcar(); }
  else if (e.key === 'ArrowUp') { e.preventDefault(); ativo = Math.max(ativo - 1, 0); marcar(); }
  else if (e.key === 'Enter') { e.preventDefault(); if (ativo >= 0 && resultados[ativo]) { selecionarCliente(resultados[ativo]); fecharDropdown(); } }
  else if (e.key === 'Escape') fecharDropdown();
});

$busca.addEventListener('blur', () => setTimeout(fecharDropdown, 150));

// ---------- carregar e renderizar o carnê ----------
async function selecionarCliente(c) {
  cliente = c;
  $busca.value = c.nome;
  $('vazio').hidden = true;
  $('conteudo').hidden = true;
  $('skeleton').hidden = false;
  try {
    const resp = await fetch(`/api/clientes/${c.id}/carne`);
    if (!resp.ok) {
      const erro = await resp.json().catch(() => ({}));
      toast(erro.erro || 'Não foi possível carregar o carnê', 'erro');
      $('skeleton').hidden = true;
      $('vazio').hidden = false;
      return;
    }
    carne = await resp.json();
    $busca.value = carne.cliente.nome; // deep-link chega só com o id
    selecionadas = [];
    renderCarne();
  } catch {
    toast('Sem conexão com o servidor', 'erro');
    $('skeleton').hidden = true;
    $('vazio').hidden = false;
  }
}

function renderCarne() {
  $('skeleton').hidden = true;
  $('conteudo').hidden = false;

  // card do cliente
  const c = carne.cliente;
  $('cli-avatar').textContent = iniciais(c.nome);
  $('cli-avatar').style.background = corAvatar(c.nome);
  $('cli-nome').textContent = c.nome;
  $('cli-cpf').textContent = mascararCpf(c.cpf);
  const $tel = $('cli-tel');
  if (c.telefone) {
    $tel.hidden = false;
    $tel.href = `tel:${c.telefone.replace(/\D/g, '')}`;
    $tel.querySelector('span').textContent = c.telefone;
  } else {
    $tel.hidden = true;
  }

  $('m-saldo').textContent = fmt(carne.saldoDevedor);
  $('m-qtd').textContent = carne.parcelasAbertas;

  const $antiga = $('m-antiga');
  if (carne.vencimentoMaisAntigo) {
    const atraso = carne.parcelas[0]?.diasAtraso || 0;
    $antiga.innerHTML = dataBr(carne.vencimentoMaisAntigo) +
        (atraso > 90 ? `<span class="chip atrasada">${atraso} dias</span>`
         : atraso > 0 ? `<span class="chip hoje">${atraso} dias</span>` : '');
  } else {
    $antiga.innerHTML = '<span class="chip prazo">nada em aberto</span>';
  }

  // últimos pagamentos: 2 visíveis, o resto colapsado atrás do "Ver mais"
  // (o detalhe já traz cada parcela que o dinheiro quitou, separada por ";")
  const us = carne.ultimosPagamentos || [];
  $('ultimos').hidden = us.length === 0;
  const linhaPg = (p) => `
    <div class="flex gap-3 py-2 border-t border-border text-[13px] items-start">
      <span class="mono whitespace-nowrap">${new Date(p.data).toLocaleDateString('pt-BR', { timeZone: 'America/Sao_Paulo' })}</span>
      <span class="font-bold whitespace-nowrap">${fmt(p.valor)}</span>
      <span class="whitespace-nowrap">${rotuloTipo(p.tipo)}</span>
      <span class="flex-1 min-w-0">
        ${(p.detalhe || '').split(';').map((d) => d.trim()).filter(Boolean).map((d) => `<div>${d}</div>`).join('') || '<span class="text-muted-foreground">—</span>'}
        ${p.vendedorNome ? `<div class="text-[11px] text-muted-foreground">recebido por ${p.vendedorNome}</div>` : ''}
      </span>
    </div>`;
  $('ultimos-lista').innerHTML = us.slice(0, 2).map(linhaPg).join('');
  const extras = us.slice(2);
  const $extra = $('ultimos-extra');
  const $vermais = $('ultimos-vermais');
  $extra.innerHTML = extras.map(linhaPg).join('');
  $extra.style.maxHeight = '0';
  $vermais.hidden = extras.length === 0;
  $vermais.textContent = `Ver mais ${extras.length} pagamento${extras.length > 1 ? 's' : ''} ▾`;
  $vermais.onclick = () => {
    const fechado = $extra.style.maxHeight === '0px' || $extra.style.maxHeight === '0';
    $extra.style.maxHeight = fechado ? $extra.scrollHeight + 'px' : '0';
    $vermais.textContent = fechado
      ? 'Ver menos ▴'
      : `Ver mais ${extras.length} pagamento${extras.length > 1 ? 's' : ''} ▾`;
  };

  renderParcelas();
  if (window.lucide) lucide.createIcons();
}

function statusChip(p) {
  const parcial = Number(p.valorAberto) < Number(p.valor);
  const extra = parcial ? ` <span class="chip parcial">parcial · resta ${fmt(p.valorAberto)}</span>` : '';
  // datas digitadas erradas no sistema antigo (ex.: ano 0257) viram aviso, não "646 mil dias"
  if (p.diasAtraso > 36500) return `<span class="chip hoje">data inválida no SET</span>${extra}`;
  if (p.diasAtraso > 0) return `<span class="chip atrasada">Vencida há ${p.diasAtraso} dias</span>${extra}`;
  const hoje = new Date().toLocaleDateString('sv-SE'); // yyyy-mm-dd local
  if (p.vencimento === hoje) return `<span class="chip hoje">Vence hoje</span>${extra}`;
  const diasPrazo = p.diasAtraso < 0 ? Math.abs(p.diasAtraso) : 0;
  return `<span class="chip prazo">${diasPrazo > 0 ? `Vai vencer em ${diasPrazo} dias` : 'No prazo'}</span>${extra}`;
}

function renderParcelas() {
  const $corpo = $('parcelas');
  $corpo.innerHTML = '';
  carne.parcelas.forEach((p, i) => {
    const tr = document.createElement('tr');
    const isSel = selecionadas.some(s => s.id === p.id);
    tr.tabIndex = 0;
    tr.setAttribute('role', 'checkbox');
    tr.setAttribute('aria-checked', isSel);
    tr.classList.toggle('sel', isSel);
    // índice da ordem de seleção (1º, 2º…) — é a ordem em que o dinheiro abate
    const ordem = selecionadas.findIndex((s) => s.id === p.id) + 1;
    tr.innerHTML = `
      <td>${isSel
        ? `<span class="inline-flex w-5 h-5 rounded-full bg-primary text-primary-foreground text-[10px] font-bold items-center justify-center">${ordem}</span>`
        : '<input type="checkbox" tabindex="-1" class="pointer-events-none accent-current">'}</td>
      <td>${p.descricao}${p.tipo ? ` <span class="chip prazo">${p.tipo}</span>` : ''}${p.notinha ? ` <button type="button" class="nota-btn" data-venda="${p.notinha}" title="Imprimir a via da loja com o saldo atualizado desta nota">🖨 nota</button>` : ''}${p.observacao ? `<br><small class="text-muted-foreground"><i data-lucide="message-square-text" class="w-3 h-3 inline"></i> ${p.observacao}</small>` : ''}</td>
      <td class="mono">${dataBr(p.vencimento)}</td>
      <td>${statusChip(p)}</td>
      <td class="num font-semibold">${fmt(p.valorAberto)}</td>`;
    const alternar = () => {
      if (isSel) selecionadas = selecionadas.filter(x => x.id !== p.id);
      else selecionadas.push(p);
      renderParcelas();
    };
    // via da loja atualizada: imprime a promissória com o saldo de hoje,
    // sem selecionar a parcela (clique não pode virar seleção)
    tr.querySelector('.nota-btn')?.addEventListener('click', async (e) => {
      e.stopPropagation();
      const resp = await fetch(`/api/vendas/${e.currentTarget.dataset.venda}`);
      if (resp.ok) imprimirRecibo(await resp.json(), loja);
      else toast('Não foi possível abrir a nota', 'erro');
    });
    tr.addEventListener('click', alternar);
    tr.addEventListener('keydown', (e) => {
      if (e.key === ' ' || e.key === 'Enter') { e.preventDefault(); alternar(); }
    });
    $corpo.appendChild(tr);
  });

  const total = round2(selecionadas.reduce((s, p) => s + Number(p.valorAberto), 0));
  $('tot-rotulo').textContent = selecionadas.length
    ? `${selecionadas.length} parcela${selecionadas.length > 1 ? 's' : ''} selecionada${selecionadas.length > 1 ? 's' : ''}`
    : 'Nenhuma parcela selecionada';
  $('tot-valor').textContent = fmt(total);

  let minData = selecionadas.length ? selecionadas.reduce((min, p) => p.vencimento < min ? p.vencimento : min, selecionadas[0].vencimento) : '';
  let maxData = selecionadas.length ? selecionadas.reduce((max, p) => p.vencimento > max ? p.vencimento : max, selecionadas[0].vencimento) : '';
  
  $('resumo-sel').textContent = selecionadas.length
    ? `${selecionadas.length} parcela(s) · vencimentos de ${dataBr(minData)} a ${dataBr(maxData)}`
    : 'Selecione parcelas na lista ao lado.';

  $('valor-receber').value = total ? total.toFixed(2).replace('.', ',') : '';
  instalarMoeda($('valor-receber'), atualizarPainel);
  formatarMoeda($('valor-receber'));
  atualizarPainel();
  if (window.lucide) lucide.createIcons();
}

$('sel-atrasadas').addEventListener('click', () => {
  selecionadas = carne.parcelas.filter((p) => p.diasAtraso > 0);
  renderParcelas();
});

if ($('sel-tudo')) {
  $('sel-tudo').addEventListener('click', () => {
    selecionadas = [...carne.parcelas];
    renderParcelas();
  });
}

// ---------- painel de recebimento ----------
function valorDigitado() {
  return typeof lerMoeda === 'function' ? Math.max(0, lerMoeda($('valor-receber'))) : Math.max(0, parseFloat($('valor-receber').value) || 0);
}

function atualizarPainel() {
  const valor = valorDigitado();
  const totalSel = round2(selecionadas.reduce((s, p) => s + Number(p.valorAberto), 0));
  const $aviso = $('aviso-parcial');
  const $erro = $('erro-receber');
  $erro.hidden = true;

  if (valor > 0 && valor > Number(carne.saldoDevedor)) {
    $erro.textContent = `Maior que o saldo devedor (${fmt(carne.saldoDevedor)})`;
    $erro.hidden = false;
    $aviso.hidden = true;
  } else if (valor > 0 && totalSel > 0 && valor < totalSel) {
    $aviso.textContent = `Pagamento parcial — continuam em aberto ${fmt(round2(totalSel - valor))} das parcelas selecionadas.`;
    $aviso.hidden = false;
  } else if (valor > 0 && totalSel > 0 && valor > totalSel) {
    $aviso.textContent = `Valor cobre mais que o selecionado — o excedente quita as próximas parcelas.`;
    $aviso.hidden = false;
  } else {
    $aviso.hidden = true;
  }

  const ok = valor > 0 && valor <= Number(carne.saldoDevedor) && !recebendo;
  $('btn-receber').disabled = !ok;
  $('btn-receber').textContent = valor > 0 ? `Receber ${fmt(valor)}` : 'Receber';
}

$('valor-receber').addEventListener('input', atualizarPainel);

$('formas-receber').addEventListener('click', (e) => {
  const btn = e.target.closest('button');
  if (!btn) return;
  tipo = btn.dataset.tipo;
  [...$('formas-receber').children].forEach((b) => b.classList.toggle('ativa', b === btn));
});


// lerMoeda/instalarMoeda/formatarMoeda vêm de /js/moeda.js
function formatarMoedaString(v) {
  return v ? v.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' }) : '';
}

let inputsAbater = [];

async function abrirModalReceber() {
  if (recebendo || !carne || selecionadas.length === 0) return;
  if (!$operador.value) {
    $('erro-receber').textContent = 'Selecione o operador no canto inferior esquerdo antes de receber.';
    $('erro-receber').hidden = false;
    $operador.focus();
    return;
  }
  
  $('erro-receber').hidden = true;
  $('mr-erro').hidden = true;

  const total = round2(selecionadas.reduce((s, p) => s + Number(p.valorAberto), 0));
  const $limite = $('mr-limite');
  $limite.value = total.toFixed(2).replace('.', ',');
  instalarMoeda($limite); // só instala uma vez (moeda.js ignora repetição)
  formatarMoeda($limite);
  
  const $tbody = $('mr-parcelas');
  $tbody.innerHTML = '';
  inputsAbater = [];

  selecionadas.forEach((p, idx) => {
    const tr = document.createElement('tr');
    tr.className = 'border-b border-border/50 hover:bg-muted/50 transition-colors';
    tr.innerHTML = `
      <td class="px-2 py-3 text-[13px] font-medium">${p.descricao}</td>
      <td class="px-2 py-3 text-[13px] text-right font-mono text-muted-foreground">${fmt(p.valorAberto)}</td>
      <td class="px-2 py-2 text-right">
        <input type="text" id="mr-abater-${idx}" data-idx="${idx}" class="w-full bg-secondary border border-border rounded px-2 py-1.5 text-[14px] font-bold text-primary text-right outline-none focus:border-primary focus:ring-1 focus:ring-primary" value="${Number(p.valorAberto).toFixed(2)}">
      </td>
    `;
    $tbody.appendChild(tr);
    const inp = $('mr-abater-' + idx);
    inputsAbater.push({ p, inp });
    instalarMoeda(inp, calcularSomaManual);
  });

  if (!$limite.dataset.waterfall) {
    $limite.dataset.waterfall = '1';
    $limite.addEventListener('input', aplicarWaterfall);
  }

  $('mr-soma-abatimentos').textContent = fmt(total);
  $('modal-receber').hidden = false;
  $limite.focus();
}

/**
 * Valor limite: distribui o que a cliente deu na ORDEM EM QUE ELA SELECIONOU
 * as parcelas (ex.: selecionou a de 20 e a de 30 mas só deu 30 → quita a de
 * 20 e abate 10 da de 30). O que não couber fica zerado.
 */
function aplicarWaterfall() {
  let sobra = lerMoeda($('mr-limite'));
  inputsAbater.forEach(({ p, inp }) => {
    const abate = Math.max(0, Math.min(sobra, Number(p.valorAberto)));
    inp.value = abate > 0 ? abate.toFixed(2).replace('.', ',') : '';
    formatarMoeda(inp);
    sobra = round2(sobra - abate);
  });
  calcularSomaManual();
}

function calcularSomaManual() {
  const soma = inputsAbater.reduce((acc, {inp}) => acc + lerMoeda(inp), 0);
  $('mr-soma-abatimentos').textContent = formatarMoedaString(soma) || 'R$ 0,00';
}

async function confirmarRecebimento() {
  // o F10 chama esta função direto, sem passar pelo disabled do botão
  if (recebendo) return;
  const alocacoes = [];
  let valorTotal = 0;
  
  inputsAbater.forEach(({ p, inp }) => {
    const v = lerMoeda(inp);
    if (v > 0) {
      alocacoes.push({ parcelaId: p.id, valor: v.toFixed(2) });
      valorTotal = round2(valorTotal + v);
    }
  });

  if (valorTotal <= 0) {
    $('mr-erro').textContent = 'O valor total a receber deve ser maior que zero.';
    $('mr-erro').hidden = false;
    return;
  }
  
  $('mr-erro').hidden = true;
  recebendo = true;
  const $btn = $('mr-confirmar');
  const rotuloOriginal = $btn.textContent;
  $btn.disabled = true;
  $btn.innerHTML = '<span class="spinner"></span> Recebendo…';

  try {
    const resp = await fetch('/api/recebimentos', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        clienteId: cliente.id,
        valor: valorTotal.toFixed(2),
        tipo,
        vendedorId: Number($operador.value),
        alocacoes
      }),
    });
    if (!resp.ok) {
      const erro = await resp.json().catch(() => ({}));
      $('mr-erro').textContent = erro.erro || 'Não foi possível registrar o recebimento';
      $('mr-erro').hidden = false;
      return;
    }
    const recibo = await resp.json();

    // maior controle: primeiro a(s) promissoria(s) atualizada(s) das notas
    // atingidas por este pagamento (pra grampear no notinha — via da loja),
    // e só depois o recibo do cliente. Parcela migrada do SET (sem notinha)
    // não tem venda nossa pra reimprimir.
    const notasAfetadas = [...new Set(recibo.itens.map((i) => i.notinha).filter(Boolean))];
    for (const notinha of notasAfetadas) {
      const respVenda = await fetch(`/api/vendas/${notinha}`);
      if (respVenda.ok) await imprimirRecibo(await respVenda.json(), loja);
    }
    await imprimirReciboCarne(recibo, loja);
    toast(`Recebido ${fmt(recibo.valor)} de ${recibo.clienteNome} — saldo agora ${fmt(recibo.saldoRestante)}`, 'ok');
    $('modal-receber').hidden = true;
    await selecionarCliente(cliente);
  } catch {
    $('mr-erro').textContent = 'Sem conexão com o servidor';
    $('mr-erro').hidden = false;
  } finally {
    recebendo = false;
    $btn.disabled = false;
    $btn.textContent = rotuloOriginal;
    atualizarPainel();
  }
}

$('btn-receber').addEventListener('click', abrirModalReceber);
  $('mr-confirmar').addEventListener('click', confirmarRecebimento);

document.addEventListener('keydown', (e) => {
  const modalAberto = !$('modal-receber').hidden;
  if (e.key === 'Escape' && modalAberto) {
    e.preventDefault();
    $('modal-receber').hidden = true;
  } else if (e.key === 'F10') {
    e.preventDefault();
    if (modalAberto) confirmarRecebimento();
    else if (!$('conteudo').hidden) abrirModalReceber();
  }
});

// ---------- util ----------
function rotuloTipo(t) {
  return { DINHEIRO: 'Dinheiro', PIX: 'Pix', CARTAO: 'Cartão', VALE_CREDITO: 'Vale-crédito' }[t] || t;
}

let toastTimer = null;
function toast(msg, tipoToast = '') {
  const $t = $('toast');
  $t.textContent = msg;
  $t.className = `toast-carne ${tipoToast}`;
  $t.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { $t.hidden = true; }, 4500);
}

carregarOperadores();

// aberto a partir do contas a receber: /carne.html?cliente=ID
const clienteParam = new URLSearchParams(location.search).get('cliente');
if (clienteParam) selecionarCliente({ id: Number(clienteParam), nome: '' });
